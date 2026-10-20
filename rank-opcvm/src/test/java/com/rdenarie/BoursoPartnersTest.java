package com.rdenarie;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BoursoPartnersTest {

  /** Two rows of https://www.boursorama.com/bourse/opcvm/recherche/?fundSearch[partners]=1 (October 2026). */
  private static final String SEARCH_ROWS =
      "<div class=\"o-pack__item c-table-search__label\"><a href=\"/bourse/opcvm/cours/MP-200012/\" title=\"JPM Taiwan A acc USD\" "
      + "class=\"c-link c-link--animated\" >JPM Taiwan A acc USD</a></div>"
      + "<div class=\"o-pack__item c-table-search__label\"><a href=\"/bourse/opcvm/cours/0P000102TM/\" "
      + "title=\"Janus Henderson Hrzn GlbTechLdrs H1USD\" class=\"c-link c-link--animated\" >Janus</a></div>"
      + "<div class=\"o-pack__item c-table-search__label\"><a href=\"/bourse/opcvm/cours/MP-1/\" "
      + "title=\"UBS (Lux) BF Euro Hi Yld � P-acc\" class=\"c-link\" >UBS</a></div>";

  @Test
  public void recognisesFundsByPerformanceIdOrName() {
    BoursoPartners partners = new BoursoPartners();
    BoursoPartners.addFunds(SEARCH_ROWS, partners.partnerIds, partners.partnerNames);

    assertEquals("partenaire", partners.typeOf(fund("0P000102TM", "another name")));
    assertEquals("partenaire", partners.typeOf(fund("0P0000XXXX", "JPM Taiwan A acc USD")));
    //Boursorama mangles the euro sign of the Morningstar name
    assertEquals("partenaire", partners.typeOf(fund(null, "UBS (Lux) BF Euro Hi Yld € P-acc")));
    assertEquals("", partners.typeOf(fund("0P0000YYYY", "Carmignac Patrimoine A EUR Acc")));
  }

  @Test
  public void normalisesEntitiesAndAccents() {
    assertEquals(BoursoPartners.normalize("Generali IS Centr&East Europ Eq DX"),
                 BoursoPartners.normalize("Generali IS Centr&amp;East Europ Eq DX"));
    assertEquals("allocation moderee", BoursoPartners.normalize("Allocation  Modérée"));
  }

  private static Fund fund(String performanceId, String name) {
    Fund fund = new Fund();
    fund.performanceId = performanceId;
    fund.name = name;
    return fund;
  }
}
