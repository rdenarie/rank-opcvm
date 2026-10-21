package com.rdenarie;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ImportBuilderTest {

  private static final LocalDate DAY = LocalDate.parse("2026-10-20");
  private static final Map<String, Double> RATES = new HashMap<>();

  static {
    RATES.put("USD", 1.1215);
    RATES.put("GBP", 0.84028);
  }

  @Test
  public void readsTheMorningstarScreenerRow() throws IOException {
    Fund janus = sample().get(1);

    assertEquals("LU0942195156", janus.isin);
    assertEquals("0P000102TM", janus.performanceId);
    assertEquals("EUCA000542", janus.categoryId);
    assertEquals("USD", janus.currency);
    assertEquals(LocalDate.parse("2026-10-19"), janus.closePriceDate);
    //3 and 5 years are annualised by Morningstar, cumulative here as on Boursorama
    assertEquals(131.73, janus.perf[Scoring.THREE_YEARS], 0.01);
    assertEquals(104.13, janus.perf[Scoring.FIVE_YEARS], 0.01);
    assertEquals(Integer.valueOf(4), janus.stars);
    assertEquals("7500 USD", janus.ticketLabel());
  }

  @Test
  public void convertsPricesAndTicketsToEuros() throws IOException {
    List<Fund> funds = sample();
    Fund janus = funds.get(1);
    Fund bnpShares = funds.get(2);

    assertNull(ImportBuilder.reject(janus, DAY, RATES));
    assertEquals(72.48, janus.priceEur, 0.01);
    assertEquals(6687.47, ImportBuilder.ticketEur(janus, RATES), 0.01);
    //20 shares of 4168.41 EUR: above the 50 000 EUR limit
    assertEquals(ImportBuilder.Rejection.TOO_EXPENSIVE, ImportBuilder.reject(bnpShares, DAY, RATES));
    assertEquals("20 PART", bnpShares.ticketLabel());
  }

  @Test
  public void keepsAFundWithoutTicket() throws IOException {
    Fund templeton = sample().get(3);

    assertNull(templeton.initialPurchase);
    assertNull(ImportBuilder.reject(templeton, DAY, RATES));
    assertEquals("ND", templeton.ticketLabel());
  }

  @Test
  public void rejectsStaleUnpricedAndEmptyFunds() {
    Fund stale = fund("A", "C1", "EUR", 10.0);
    stale.closePriceDate = DAY.minusDays(366);
    Fund cnh = fund("B", "C1", "CNH", 10.0);
    Fund empty = fund("C", "C1", "EUR", null);

    assertEquals(ImportBuilder.Rejection.STALE, ImportBuilder.reject(stale, DAY, RATES));
    assertEquals(ImportBuilder.Rejection.NO_EUR_PRICE, ImportBuilder.reject(cnh, DAY, RATES));
    assertEquals(ImportBuilder.Rejection.NO_PERFORMANCE, ImportBuilder.reject(empty, DAY, RATES));
  }

  @Test
  public void ranksFundsInTheirCategoryAndCategoriesBetweenThem() {
    Fund best = fund("A", "C1", "EUR", 20.0);
    Fund second = fund("B", "C1", "EUR", 10.0);
    Fund tied = fund("C", "C1", "EUR", 10.0);
    Fund other = fund("D", "C2", "EUR", 1.0);
    Fund ignored = fund("E", "C3", "EUR", 50.0);
    Fund rejected = fund("F", "C1", "CNH", 30.0);
    Map<String, String> perso = new HashMap<>();
    perso.put("C1", "Sectoriels");
    perso.put("C2", "Obligataires");

    ImportBuilder.Result result = ImportBuilder.build(DAY, Arrays.asList(best, second, tied, other, ignored, rejected),
                                                      new HashMap<>(), perso, RATES, null);

    assertEquals(4, result.funds.size());
    assertEquals(1, best.rank);
    assertEquals(2, second.rank);
    assertEquals(2, tied.rank);
    assertEquals(3, best.fundsInCategory);
    assertEquals(Integer.valueOf(1), result.rejections.get(ImportBuilder.Rejection.NO_EUR_PRICE));
    //the category average covers its whole universe, rejected funds included: (20 + 10 + 10 + 30) / 4
    assertEquals(17.5, result.categories.get(0).perf[0], 0.001);
    assertEquals("C1", result.categories.get(0).id);
    assertEquals(1, result.categories.get(0).rank);
    assertEquals(2, result.categories.size());
  }

  @Test
  public void ranksEtfsApartAndAgainstTheFunds() {
    Fund fund1 = fund("F1", "C1", "EUR", 20.0);
    Fund fund2 = fund("F2", "C1", "EUR", 10.0);
    Fund etf1 = etf("E1", "C1", 15.0);
    Fund etf2 = etf("E2", "C1", 5.0);
    Fund etfAlone = etf("E3", "C2", 8.0);
    Map<String, String> perso = new HashMap<>();
    perso.put("C1", "Internationales");
    perso.put("C2", "Sectoriels");

    ImportBuilder.Result result = ImportBuilder.build(DAY, Arrays.asList(fund1, fund2, etf1, etf2, etfAlone),
                                                      new HashMap<>(), perso, RATES, null);

    //each kind has its own ranking...
    assertEquals(1, fund1.rank);
    assertEquals(2, fund2.fundsInCategory);
    assertEquals(1, etf1.rank);
    assertEquals(2, etf2.rank);
    assertEquals(2, etf1.fundsInCategory);
    //...and the rank it would have among the other kind
    assertEquals(2, etf1.otherKindRank);
    assertEquals(2, etf1.otherKindInCategory);
    assertEquals(3, etf2.otherKindRank);
    assertEquals(2, fund2.otherKindRank);
    assertEquals(0, etfAlone.otherKindInCategory);
    //the category average ignores the ETFs, so that scores stay comparable with the history
    assertEquals(15.0, result.categories.get(0).perf[0], 0.001);
    assertEquals(2, result.categories.get(0).etfs);
    //a category holding only ETFs is listed, unranked, last
    assertEquals("C2", result.categories.get(1).id);
    assertEquals(0, result.categories.get(1).rank);
  }

  private static Fund etf(String isin, String category, Double ytd) {
    Fund etf = fund(isin, category, "EUR", ytd);
    etf.kind = Fund.Kind.ETF;
    return etf;
  }

  private static Fund fund(String isin, String category, String currency, Double ytd) {
    Fund fund = new Fund();
    fund.isin = isin;
    fund.name = isin;
    fund.categoryId = category;
    fund.currency = currency;
    fund.closePrice = 100.0;
    fund.closePriceDate = DAY;
    fund.perf[0] = ytd;
    return fund;
  }

  static List<Fund> sample() throws IOException {
    try (InputStream in = ImportBuilderTest.class.getClassLoader().getResourceAsStream("morningstar/screener-sample.json")) {
      JsonObject json = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
      List<Fund> funds = new ArrayList<>();
      for (JsonElement row : json.getAsJsonArray("rows")) {
        funds.add(MorningstarClient.toFund(row.getAsJsonObject()));
      }
      return funds;
    }
  }
}
