package com.rdenarie;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Reads the funds registered for sale in France from the Morningstar screener, the API behind its public widgets
 * (the key in the URL is the widget's, not ours). About 60 pages of 1000 funds.
 */
public class MorningstarClient {

  private static final Logger log = Logger.getLogger(MorningstarClient.class.getName());
  private static final int PAGE_SIZE = 1000;
  /** Performances in the fund's own currency, as Boursorama shows them; 3, 5 and 10 years are annualised. */
  private static final String URL = "http://lt.morningstar.com/api/rest.svc/klr5zyak8x/security/screener?pageSize=" + PAGE_SIZE
      + "&outputType=json&version=1&languageId=fr-FR&currencyId=BAS&universeIds=FOFRA%24%24ALL&term=&subUniverseId="
      + "&sortOrder=SecId%20asc&securityDataPoints="
      + String.join("%7C", "Isin", "Name", "PerformanceId", "CategoryId", "CategoryName", "PriceCurrency", "ClosePrice",
                    "ClosePriceDate", "ReturnM0", "ReturnM1", "ReturnM6", "ReturnM12", "ReturnM36", "ReturnM60", "ReturnM120",
                    "OngoingCharge", "MaxFrontEndLoad", "MaximumExitCostAcquired", "InitialPurchase", "InitialPurchaseUnit",
                    "InitialPurchaseCurrency", "StarRatingM255", "FundTNAV");
  private static final int MAX_PAGES = 200;

  /**
   * Returns every share class with an ISIN, and fills {@code categoryNames} with the current Morningstar name of each
   * category id. Returns null when a page cannot be read: a partial universe would rank funds against a partial category.
   */
  public static List<Fund> fetchUniverse(Map<String, String> categoryNames) {
    List<Fund> funds = new ArrayList<>();
    int total = Integer.MAX_VALUE;
    int read = 0;
    for (int page = 1; read < total && page <= MAX_PAGES; page++) {
      String response = Utils.getMSResponse(URL + "&page=" + page);
      if (response == null) {
        log.severe("Morningstar did not serve page " + page);
        return null;
      }
      JsonObject json = new JsonParser().parse(response).getAsJsonObject();
      total = json.get("total").getAsInt();
      JsonArray rows = json.getAsJsonArray("rows");
      if (rows.size() == 0) {
        break;
      }
      read += rows.size();
      for (JsonElement row : rows) {
        Fund fund = toFund(row.getAsJsonObject());
        if (fund.isin != null) {
          funds.add(fund);
          String categoryName = string(row.getAsJsonObject(), "CategoryName");
          if (fund.categoryId != null && categoryName != null) {
            categoryNames.put(fund.categoryId, categoryName.trim());
          }
        }
      }
    }
    log.info("Morningstar universe: " + read + " share classes read, " + funds.size() + " with an ISIN, total announced " + total);
    return read < total ? null : funds;
  }

  static Fund toFund(JsonObject row) {
    Fund fund = new Fund();
    fund.isin = string(row, "Isin");
    fund.name = string(row, "Name");
    fund.performanceId = string(row, "PerformanceId");
    fund.categoryId = string(row, "CategoryId");
    fund.currency = string(row, "PriceCurrency");
    fund.closePrice = number(row, "ClosePrice");
    String date = string(row, "ClosePriceDate");
    fund.closePriceDate = date == null ? null : LocalDate.parse(date.substring(0, 10));
    fund.perf[0] = number(row, "ReturnM0");
    fund.perf[1] = number(row, "ReturnM1");
    fund.perf[2] = number(row, "ReturnM6");
    fund.perf[3] = number(row, "ReturnM12");
    fund.perf[Scoring.THREE_YEARS] = Scoring.cumulative(number(row, "ReturnM36"), 3);
    fund.perf[Scoring.FIVE_YEARS] = Scoring.cumulative(number(row, "ReturnM60"), 5);
    fund.perf[Scoring.TEN_YEARS] = Scoring.cumulative(number(row, "ReturnM120"), 10);
    fund.ongoingCharge = number(row, "OngoingCharge");
    fund.entryFee = number(row, "MaxFrontEndLoad");
    fund.exitFee = number(row, "MaximumExitCostAcquired");
    fund.initialPurchase = number(row, "InitialPurchase");
    fund.initialPurchaseUnit = string(row, "InitialPurchaseUnit");
    fund.initialPurchaseCurrency = string(row, "InitialPurchaseCurrency");
    Double stars = number(row, "StarRatingM255");
    fund.stars = stars == null ? null : stars.intValue();
    fund.assets = number(row, "FundTNAV");
    return fund;
  }

  private static String string(JsonObject row, String field) {
    JsonElement value = row.get(field);
    return value == null || value.isJsonNull() ? null : value.getAsString();
  }

  private static Double number(JsonObject row, String field) {
    JsonElement value = row.get(field);
    return value == null || value.isJsonNull() ? null : value.getAsDouble();
  }
}
