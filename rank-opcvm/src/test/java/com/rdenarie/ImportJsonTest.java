package com.rdenarie;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class ImportJsonTest {

  @Test
  public void shardsAnIsinLikeTheMigrationScript() {
    //tools/migrate_datastore.py: sum(map(ord, "FR0010135103")) % 64
    assertEquals(6, ImportJson.shardOf("FR0010135103"));
  }

  @Test
  public void putsTheNewImportFirstInTheIndexAndReplacesARerun() {
    JsonObject index = new JsonParser().parse("{\"imports\":[{\"date\":\"2026-10-01\",\"funds\":1},"
                                              + "{\"date\":\"2026-07-13\",\"funds\":20197}]}").getAsJsonObject();

    JsonObject updated = ImportJson.addToIndex(index, result("2026-10-01", 20500));

    assertEquals(2, updated.getAsJsonArray("imports").size());
    assertEquals("2026-10-01", updated.getAsJsonArray("imports").get(0).getAsJsonObject().get("date").getAsString());
    assertEquals(20500, updated.getAsJsonArray("imports").get(0).getAsJsonObject().get("funds").getAsInt());
    assertEquals(20197, ImportService.previousFunds(updated, "2026-10-01"));
  }

  @Test
  public void appendsTheRankToTheFundHistory() {
    Fund fund = new Fund();
    fund.isin = "FR0010135103";
    fund.rank = 3;
    fund.fundsInCategory = 660;
    fund.score = 49.11;
    JsonObject shard = new JsonParser().parse("{\"FR0010135103\":[[\"2026-07-13\",118,660,47.0]]}").getAsJsonObject();

    ImportJson.addToHistory(shard, result("2026-10-01", 1), Collections.singletonList(fund));

    assertEquals("[[\"2026-07-13\",118,660,47.0],[\"2026-10-01\",3,660,49.11]]", shard.get("FR0010135103").toString());
  }

  @Test
  public void writesCategoriesAndFunds() {
    Fund fund = new Fund();
    fund.isin = "LU0942195156";
    fund.name = "Janus";
    fund.categoryId = "EUCA000542";
    fund.currency = "USD";
    fund.type = "partenaire";
    Category category = new Category("EUCA000542", "Actions Secteur Technologies", "Sectoriels");
    ImportBuilder.Result result = new ImportBuilder.Result(LocalDate.parse("2026-10-01"), Collections.singletonList(category),
                                                           Collections.singletonList(fund), new HashMap<>());

    JsonObject json = ImportJson.toJson(result);

    assertEquals("Sectoriels", json.getAsJsonArray("categories").get(0).getAsJsonObject().get("perso").getAsString());
    JsonObject written = json.getAsJsonArray("funds").get(0).getAsJsonObject();
    assertEquals("partenaire", written.get("type").getAsString());
    assertEquals("ND", written.get("ticket").getAsString());
    assertFalse(written.has("filled"));
  }

  private static ImportBuilder.Result result(String date, int funds) {
    Map<ImportBuilder.Rejection, Integer> rejections = new HashMap<>();
    return new ImportBuilder.Result(LocalDate.parse(date), Collections.emptyList(),
                                    Collections.nCopies(funds, new Fund()), rejections);
  }
}
