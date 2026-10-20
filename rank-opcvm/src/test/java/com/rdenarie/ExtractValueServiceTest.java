package com.rdenarie;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

/**
 * Parses Boursorama pages saved under src/test/resources/bourso. When Boursorama changes its page format,
 * save a page in the new format next to the others and add a test for it.
 */
public class ExtractValueServiceTest {

  private static final Instant OCTOBER_2026 = Instant.parse("2026-10-19T00:00:00Z");

  @Test
  public void parsesFundPageWithPeriodsAsRows() throws IOException {
    Document doc = load("bourso/fund-LU0942195156-2026-10.html");

    ExtractValueService.Extraction extraction = ExtractValueService.parse(doc, "Sectoriels", OCTOBER_2026);

    assertNull(extraction.detail, extraction.rejection);
    JsonObject fund = extraction.value;
    assertEquals("LU0942195156", fund.get("id").getAsString());
    assertEquals("Janus Henderson Hrzn GlbTechLdrs H1USD", fund.get("name").getAsString());
    assertEquals("Actions Secteur Technologies", fund.get(Utils.CATEGORY_MS_PROPERTY).getAsString());
    assertEquals("USD", fund.get(Utils.CURRENCY_PROPERTY).getAsString());
    assertEquals("Sectoriels", fund.get(Utils.CATEGORY_PERSO_PROPERTY).getAsString());
    assertNotNull(fund.get(Utils.PRICE_EUR_PROPERTY));
    assertEquals("5 %", fund.get(Utils.ENTREE_PROPERTY).getAsString());
    assertEquals("1 %", fund.get(Utils.SORTIE_PROPERTY).getAsString());
    assertEquals("1,07 %", fund.get(Utils.COURANT_PROPERTY).getAsString());
    assertEquals("7500 USD", fund.get(Utils.TICKET_IN_PROPERTY).getAsString());
    assertEquals("2500 USD", fund.get(Utils.TICKET_RENEW_PROPERTY).getAsString());
    assertEquals("4", fund.get(Utils.MSRATING_PROPERTY).getAsString());
    assertEquals("Alison Porter", fund.get(Utils.GERANT_PROPERTY).getAsString());

    JsonObject values = fund.getAsJsonObject("values");
    assertPeriods(values.getAsJsonObject("fond"), 34.59, 6.58, 42.82, 37.03, 131.78, 131.87, 567.88);
    assertPeriods(values.getAsJsonObject("category"), 10.99, -0.60, 13.29, 10.99, 91.30, 58.36, 336.54);
    assertPeriods(values.getAsJsonObject("percentile"), 40, 30, 41, 38, 33, 27, 37);
  }

  @Test
  public void parsesPerformanceTableWithPeriodsAsColumns() {
    Document doc = Jsoup.parse("<div class='c-fund-performances__table'><table>"
        + "<tr class='c-table__row'><th></th><th class='c-table__title'>1er janv.</th><th class='c-table__title'>1 semaine</th>"
        + "<th class='c-table__title'>1 mois</th><th class='c-table__title'>1 an</th></tr>"
        + "<tr class='c-table__row'><th>Fonds</th><td class='c-table__cell'>5,10%</td><td class='c-table__cell'>0,2%</td>"
        + "<td class='c-table__cell'>-1,25%</td><td class='c-table__cell'>-</td></tr>"
        + "<tr class='c-table__row'><th>Catégorie</th><td class='c-table__cell'>4%</td><td class='c-table__cell'>0,1%</td>"
        + "<td class='c-table__cell'>2,5%</td><td class='c-table__cell'>7%</td></tr>"
        + "<tr class='c-table__row'><th>Rang</th><td class='c-table__cell'>12</td><td class='c-table__cell'>50</td>"
        + "<td class='c-table__cell'>80</td><td class='c-table__cell'>-</td></tr>"
        + "</table></div>");
    JsonObject result = new JsonObject();

    ExtractValueService.extractValues(result, doc);

    JsonObject values = result.getAsJsonObject("values");
    assertEquals("{\"1erjanvier\":5.1,\"1mois\":-1.25}", values.getAsJsonObject("fond").toString());
    assertEquals("{\"1erjanvier\":4.0,\"1mois\":2.5,\"1an\":7.0}", values.getAsJsonObject("category").toString());
    assertEquals("{\"1erjanvier\":12,\"1mois\":80}", values.getAsJsonObject("percentile").toString());
  }

  @Test
  public void rejectsFundQuotedInCurrencyWithoutEuroValue() throws IOException {
    Document doc = load("bourso/fund-LU2351347138-CNH-2026-10.html");

    ExtractValueService.Extraction extraction = ExtractValueService.parse(doc, "Pays Emergents", OCTOBER_2026);

    assertEquals(ExtractValueService.Rejection.NO_EUR_PRICE, extraction.rejection);
  }

  @Test
  public void rejectsPageWithoutPerformanceTable() {
    Document doc = Jsoup.parse("<h2 class='c-faceplate__isin'>FR0000000000 - OPCVM</h2>");

    ExtractValueService.Extraction extraction = ExtractValueService.parse(doc, "Sectoriels", OCTOBER_2026);

    assertEquals(ExtractValueService.Rejection.NO_SCORES, extraction.rejection);
  }

  @Test
  public void rejectsPageThatIsNotAFund() {
    Document doc = Jsoup.parse("<title>Recherche</title><p>Aucun résultat</p>");

    ExtractValueService.Extraction extraction = ExtractValueService.parse(doc, "Sectoriels", OCTOBER_2026);

    assertEquals(ExtractValueService.Rejection.NO_ISIN, extraction.rejection);
  }

  private static void assertPeriods(JsonObject series, double... expected) {
    for (int i = 0; i < expected.length; i++) {
      String period = ExtractValueService.indexes[i];
      assertNotNull("missing " + period + " in " + series, series.get(period));
      assertEquals(period, expected[i], series.get(period).getAsDouble(), 0.001);
    }
  }

  private static Document load(String resource) throws IOException {
    try (InputStream in = ExtractValueServiceTest.class.getClassLoader().getResourceAsStream(resource)) {
      return Jsoup.parse(in, StandardCharsets.UTF_8.name(), "https://www.boursorama.com/");
    }
  }
}
