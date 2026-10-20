package com.rdenarie;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * The published files, read by the static pages:
 * <ul>
 *   <li>{@code index.json}: the imports, most recent first;</li>
 *   <li>{@code imports/<date>.json}: the categories and kept funds of one import;</li>
 *   <li>{@code history/<shard>.json}: per ISIN, its rank at each import, split in {@link #HISTORY_SHARDS} files.</li>
 * </ul>
 * tools/migrate_datastore.py writes the same format for the imports made before Morningstar.
 */
public final class ImportJson {

  public static final int HISTORY_SHARDS = 64;
  public static final String SOURCE = "morningstar";

  private ImportJson() {
  }

  public static String importPath(String date) {
    return "imports/" + date + ".json";
  }

  public static String historyPath(int shard) {
    return "history/" + shard + ".json";
  }

  /** Must stay identical to shard() in tools/migrate_datastore.py. */
  public static int shardOf(String isin) {
    int sum = 0;
    for (char c : isin.toCharArray()) {
      sum += c;
    }
    return sum % HISTORY_SHARDS;
  }

  public static JsonObject toJson(ImportBuilder.Result result) {
    JsonObject json = new JsonObject();
    json.addProperty("date", result.date.toString());
    json.addProperty("source", SOURCE);
    JsonArray periods = new JsonArray();
    for (String period : Scoring.PERIODS) {
      periods.add(new JsonPrimitive(period));
    }
    json.add("periods", periods);
    JsonArray categories = new JsonArray();
    for (Category category : result.categories) {
      JsonObject c = new JsonObject();
      c.addProperty("id", category.id);
      c.addProperty("name", category.name);
      c.addProperty("perso", category.perso);
      c.addProperty("score", category.score);
      c.addProperty("rank", category.rank);
      c.addProperty("funds", category.funds);
      c.add("perf", numbers(category.perf));
      categories.add(c);
    }
    json.add("categories", categories);
    JsonArray funds = new JsonArray();
    for (Fund fund : result.funds) {
      JsonObject f = new JsonObject();
      f.addProperty("isin", fund.isin);
      f.addProperty("name", fund.name);
      f.addProperty("cat", fund.categoryId);
      f.addProperty("score", fund.score);
      f.addProperty("rank", fund.rank);
      f.addProperty("n", fund.fundsInCategory);
      f.add("perf", numbers(fund.perf));
      if (fund.filled) {
        f.addProperty("filled", true);
      }
      add(f, "stars", fund.stars);
      add(f, "entree", fund.entryFee);
      add(f, "sortie", fund.exitFee);
      add(f, "courant", fund.ongoingCharge);
      f.addProperty("ticket", fund.ticketLabel());
      add(f, "priceEur", fund.priceEur);
      f.addProperty("currency", fund.currency);
      add(f, "assets", fund.assets);
      if (!fund.type.isEmpty()) {
        f.addProperty("type", fund.type);
      }
      funds.add(f);
    }
    json.add("funds", funds);
    return json;
  }

  /** Adds or replaces the entry of this import in the index, keeping the most recent first. */
  public static JsonObject addToIndex(JsonObject index, ImportBuilder.Result result) {
    JsonArray imports = new JsonArray();
    JsonObject entry = new JsonObject();
    entry.addProperty("date", result.date.toString());
    entry.addProperty("source", SOURCE);
    entry.addProperty("funds", result.funds.size());
    entry.addProperty("categories", result.categories.size());
    imports.add(entry);
    if (index != null && index.has("imports")) {
      for (JsonElement existing : index.getAsJsonArray("imports")) {
        if (!existing.getAsJsonObject().get("date").getAsString().equals(result.date.toString())) {
          imports.add(existing);
        }
      }
    }
    JsonArray sorted = sortByDateDesc(imports);
    JsonObject updated = new JsonObject();
    updated.add("imports", sorted);
    return updated;
  }

  /** Adds this import's rank of each fund of the shard to its history. */
  public static void addToHistory(JsonObject shard, ImportBuilder.Result result, Iterable<Fund> fundsOfShard) {
    String date = result.date.toString();
    for (Fund fund : fundsOfShard) {
      JsonArray points = shard.has(fund.isin) ? shard.getAsJsonArray(fund.isin) : new JsonArray();
      JsonArray kept = new JsonArray();
      for (JsonElement point : points) {
        if (!point.getAsJsonArray().get(0).getAsString().equals(date)) {
          kept.add(point);
        }
      }
      JsonArray point = new JsonArray();
      point.add(new JsonPrimitive(date));
      point.add(new JsonPrimitive(fund.rank));
      point.add(new JsonPrimitive(fund.fundsInCategory));
      point.add(new JsonPrimitive(fund.score));
      kept.add(point);
      shard.add(fund.isin, sortByDate(kept));
    }
  }

  private static JsonArray sortByDate(JsonArray points) {
    java.util.List<JsonElement> list = new java.util.ArrayList<>();
    points.forEach(list::add);
    list.sort(java.util.Comparator.comparing(p -> p.getAsJsonArray().get(0).getAsString()));
    JsonArray sorted = new JsonArray();
    list.forEach(sorted::add);
    return sorted;
  }

  private static JsonArray sortByDateDesc(JsonArray imports) {
    java.util.List<JsonElement> list = new java.util.ArrayList<>();
    imports.forEach(list::add);
    list.sort(java.util.Comparator.comparing((JsonElement e) -> e.getAsJsonObject().get("date").getAsString()).reversed());
    JsonArray sorted = new JsonArray();
    list.forEach(sorted::add);
    return sorted;
  }

  private static JsonArray numbers(Double[] values) {
    JsonArray array = new JsonArray();
    for (Double value : values) {
      array.add(value == null ? JsonNull.INSTANCE : new JsonPrimitive(value));
    }
    return array;
  }

  private static void add(JsonObject json, String field, Number value) {
    if (value != null) {
      json.addProperty(field, value);
    }
  }
}
