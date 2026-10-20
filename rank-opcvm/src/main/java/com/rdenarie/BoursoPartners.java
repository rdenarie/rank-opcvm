package com.rdenarie;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The funds BoursoBank sells, read from the Boursorama fund search: "partenaire" (fundSearch[partners]) and
 * "boursomarkets" (fundSearch[saving]). The lists give no ISIN: a fund is recognised by its Boursorama id when it is
 * a Morningstar PerformanceId ("0P..."), else by its normalised name.
 */
public class BoursoPartners {

  private static final Logger log = Logger.getLogger(BoursoPartners.class.getName());
  private static final String SEARCH = "https://www.boursorama.com/bourse/opcvm/recherche/";
  private static final Pattern FUND_LINK = Pattern.compile("href=\"/bourse/opcvm/cours/([^/\"]+)/\"\\s+title=\"([^\"]*)\"");
  private static final Pattern PAGE_LINK = Pattern.compile("/bourse/opcvm/recherche/page-(\\d+)");
  /** Above this, the page loop is broken: Boursorama lists about 125 pages of partners. */
  private static final int MAX_PAGES = 400;
  private static final int ATTEMPTS = 3;
  private static final long RETRY_DELAY_MS = 20000;

  final Set<String> partnerIds = new HashSet<>();
  final Set<String> partnerNames = new HashSet<>();
  final Set<String> boursomarketsIds = new HashSet<>();
  final Set<String> boursomarketsNames = new HashSet<>();

  public static BoursoPartners fetch() {
    BoursoPartners partners = new BoursoPartners();
    partners.readList("fundSearch%5Bpartners%5D=1", partners.partnerIds, partners.partnerNames);
    partners.readList("fundSearch%5Bsaving%5D=1", partners.boursomarketsIds, partners.boursomarketsNames);
    log.info("Boursorama lists " + (partners.partnerIds.size() + partners.partnerNames.size()) + " partner funds and "
             + (partners.boursomarketsIds.size() + partners.boursomarketsNames.size()) + " Boursomarkets funds");
    return partners;
  }

  public int size() {
    return partnerIds.size() + partnerNames.size() + boursomarketsIds.size() + boursomarketsNames.size();
  }

  public String typeOf(Fund fund) {
    String name = normalize(fund.name);
    if (partnerIds.contains(fund.performanceId) || partnerNames.contains(name)) {
      return "partenaire";
    }
    if (boursomarketsIds.contains(fund.performanceId) || boursomarketsNames.contains(name)) {
      return "boursomarkets";
    }
    return "";
  }

  private void readList(String filter, Set<String> ids, Set<String> names) {
    int lastPage = 1;
    for (int page = 1; page <= lastPage && page <= MAX_PAGES; page++) {
      String url = page == 1 ? SEARCH + "?" + filter : SEARCH + "page-" + page + "?" + filter;
      String html = null;
      for (int attempt = 1; html == null && attempt <= ATTEMPTS; attempt++) {
        html = Utils.getBoursoResponse(url);
        if (html == null && attempt < ATTEMPTS) {
          //HTTP 429 or a closed connection: Boursorama throttles, wait before retrying
          sleep(RETRY_DELAY_MS * attempt);
        }
      }
      if (html == null) {
        log.warning("Boursorama did not serve " + url + ": the fund types of this page are lost");
        continue;
      }
      addFunds(html, ids, names);
      Matcher pages = PAGE_LINK.matcher(html);
      while (pages.find()) {
        lastPage = Math.max(lastPage, Integer.parseInt(pages.group(1)));
      }
    }
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  static void addFunds(String html, Set<String> ids, Set<String> names) {
    Matcher link = FUND_LINK.matcher(html);
    while (link.find()) {
      if (link.group(1).startsWith("0P")) {
        ids.add(link.group(1));
      } else {
        names.add(normalize(link.group(2)));
      }
    }
  }

  /** Boursorama shows the Morningstar name, give or take HTML entities, the euro sign's encoding and spaces. */
  static String normalize(String name) {
    if (name == null) {
      return "";
    }
    String text = name.replace("&amp;", "&").replace("&#039;", "'").replace("&quot;", "\"")
                      .replace("\u20ac", "").replace("\u0080", "").replace("EUR", "");
    text = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
  }
}
