#!/usr/bin/env python3
"""One-off migration of the Datastore imports (Boursorama era) to the JSON files the static pages read.

Reads every TimeElement and its DataEntry children through the Datastore REST API (read only), and writes, gzip
compressed, in the output directory:
  imports/<YYYY-MM-DD>.json   one file per import, same format as ImportJson.toJson
  history/<shard>.json        per ISIN, its rank at each import (shard = ImportJson.shardOf)
  index.json                  the imports, most recent first
then prints the gcloud commands that upload them. Run it before the first Morningstar import: that import adds itself
to the history files it finds in the bucket.

Usage: tools/migrate_datastore.py OUTPUT_DIR [--project rank-opcvm] [--workers 6]
Needs `gcloud auth login` (it uses `gcloud auth print-access-token`).
"""
import argparse
import concurrent.futures
import gzip
import json
import os
import re
import subprocess
import sys
import urllib.request

PERIODS = ["1erjanvier", "1mois", "6mois", "1an", "3ans", "5ans", "10ans"]
HISTORY_SHARDS = 64


def shard(isin):
    """Must stay identical to ImportJson.shardOf."""
    return sum(map(ord, isin)) % HISTORY_SHARDS


class Datastore:
    def __init__(self, project):
        self.url = "https://datastore.googleapis.com/v1/projects/%s:runQuery" % project
        self.token = subprocess.check_output(["gcloud", "auth", "print-access-token"], text=True).strip().splitlines()[-1]

    def query(self, gql):
        """Yields the properties of every entity, following the cursors."""
        cursor = None
        while True:
            q = {"gqlQuery": {"queryString": gql + (" OFFSET @c" if cursor else ""), "allowLiterals": True}}
            if cursor:
                q["gqlQuery"]["namedBindings"] = {"c": {"cursor": cursor}}
            req = urllib.request.Request(self.url, json.dumps(q).encode(),
                                         {"Authorization": "Bearer " + self.token, "Content-Type": "application/json"})
            batch = json.load(urllib.request.urlopen(req, timeout=300))["batch"]
            for result in batch.get("entityResults", []):
                entity = result["entity"]
                props = {k: value(v) for k, v in entity.get("properties", {}).items()}
                props["__key__"] = entity["key"]["path"][-1].get("name")
                yield props
            if batch["moreResults"] == "NO_MORE_RESULTS" or not batch.get("entityResults"):
                return
            cursor = batch["endCursor"]


def value(v):
    for k in ("stringValue", "doubleValue", "booleanValue", "timestampValue"):
        if k in v:
            return v[k]
    if "integerValue" in v:
        return int(v["integerValue"])
    return None


def iso_date(key_name):
    """TimeElement keys are 'YYYY-M-D'."""
    year, month, day = (int(x) for x in key_name.split("-"))
    return "%04d-%02d-%02d" % (year, month, day)


def percent(text):
    """'1,07 %' -> 1.07; 'ND', '-' or missing -> None."""
    if not isinstance(text, str):
        return text
    match = re.search(r"-?\d+(?:[.,]\d+)?", text)
    return float(match.group(0).replace(",", ".")) if match else None


def perf_array(values, series):
    data = values.get(series) or {}
    return [data.get(p) for p in PERIODS]


def has_gaps(perf):
    first_missing = next((i for i, v in enumerate(perf) if v is None), -1)
    last_known = max((i for i, v in enumerate(perf) if v is not None), default=-1)
    return first_missing != -1 and first_missing < last_known


def convert(date, entities):
    """Returns (import json, history points by isin, skipped count)."""
    categories, funds, history, skipped = {}, [], {}, 0
    for e in entities:
        if e.get("rankInCategory") is None or e.get("scoreFond") is None or not e.get("categMS"):
            skipped += 1
            continue
        try:
            values = json.loads(e.get("values") or "{}")
        except ValueError:
            values = {}
        cat = e["categMS"]
        if cat not in categories:
            categories[cat] = {"id": cat, "name": cat, "perso": e.get("categPerso"), "score": e.get("scoreCategory"),
                               "rank": e.get("categoryRank"), "funds": 0, "perf": perf_array(values, "category")}
        categories[cat]["funds"] += 1
        perf = perf_array(values, "fond")
        stars = e.get("msrating")
        fund = {"isin": e.get("id") or e["__key__"], "name": e.get("name"), "cat": cat, "score": e["scoreFond"],
                "rank": e["rankInCategory"], "n": e.get("numberFundsInCategory"), "perf": perf}
        if has_gaps(perf):
            fund["filled"] = True
        if isinstance(stars, (int, float)) or (isinstance(stars, str) and stars.isdigit()):
            fund["stars"] = int(stars)
        for target, source in (("entree", "entree"), ("sortie", "sortie"), ("courant", "courant")):
            if percent(e.get(source)) is not None:
                fund[target] = percent(e.get(source))
        fund["ticket"] = e.get("ticketIn") or "ND"
        if e.get("priceEur") is not None:
            fund["priceEur"] = e["priceEur"]
        fund["currency"] = e.get("currency")
        if e.get("type"):
            fund["type"] = e["type"]
        if e.get("gerant"):
            fund["gerant"] = e["gerant"]
        funds.append(fund)
        history[fund["isin"]] = [date, fund["rank"], fund["n"], fund["score"]]
    funds.sort(key=lambda f: -f["score"])
    cats = sorted(categories.values(), key=lambda c: (c["rank"] is None, c["rank"] or 0))
    doc = {"date": date, "source": "boursorama", "periods": PERIODS, "categories": cats, "funds": funds}
    return doc, history, skipped


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, "wt", encoding="utf-8") as out:
        json.dump(data, out, ensure_ascii=False, separators=(",", ":"))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("out")
    parser.add_argument("--project", default="rank-opcvm")
    parser.add_argument("--workers", type=int, default=6)
    args = parser.parse_args()
    ds = Datastore(args.project)

    time_elements = [t["__key__"] for t in ds.query("SELECT __key__ FROM TimeElement")]
    print("%d imports in the Datastore" % len(time_elements), file=sys.stderr)

    def migrate(key):
        date = iso_date(key)
        entities = list(ds.query("SELECT * FROM DataEntry WHERE __key__ HAS ANCESTOR KEY(TimeElement, '%s')" % key))
        doc, history, skipped = convert(date, entities)
        if doc["funds"]:
            write(os.path.join(args.out, "imports", date + ".json"), doc)
        print("%s: %d entities, %d funds, %d skipped" % (date, len(entities), len(doc["funds"]), skipped), file=sys.stderr)
        return date, doc, history

    index, shards = [], [dict() for _ in range(HISTORY_SHARDS)]
    with concurrent.futures.ThreadPoolExecutor(args.workers) as pool:
        for date, doc, history in pool.map(migrate, time_elements):
            if not doc["funds"]:
                continue
            index.append({"date": date, "source": "boursorama", "funds": len(doc["funds"]), "categories": len(doc["categories"])})
            for isin, point in history.items():
                shards[shard(isin)].setdefault(isin, []).append(point)

    for i, content in enumerate(shards):
        for points in content.values():
            points.sort(key=lambda p: p[0])
        write(os.path.join(args.out, "history", "%d.json" % i), content)
    index.sort(key=lambda e: e["date"], reverse=True)
    write(os.path.join(args.out, "index.json"), {"imports": index})
    print("%d imports written to %s" % (len(index), args.out), file=sys.stderr)
    bucket = "gs://rank-opcvm-data"
    meta = "--content-encoding=gzip --content-type='application/json; charset=utf-8'"
    print("\nUpload with:")
    print("gcloud storage cp %s --cache-control='public, max-age=86400' '%s/imports/*.json' %s/imports/" % (meta, args.out, bucket))
    print("gcloud storage cp %s --cache-control='public, max-age=3600' '%s/history/*.json' %s/history/" % (meta, args.out, bucket))
    print("gcloud storage cp %s --cache-control='public, max-age=300' '%s/index.json' %s/index.json" % (meta, args.out, bucket))


if __name__ == "__main__":
    main()
