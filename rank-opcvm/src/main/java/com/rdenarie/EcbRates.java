package com.rdenarie;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ECB euro reference rates of the day: units of each currency for one euro. Read from the ECB, else from
 * Frankfurter, which republishes them: the ECB certificate chains to a recent root some Java trust stores lack.
 */
public class EcbRates {

  private static final Logger log = Logger.getLogger(EcbRates.class.getName());
  private static final String URL = "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml";
  private static final String FALLBACK_URL = "https://api.frankfurter.app/latest?from=EUR";
  static final Pattern RATE = Pattern.compile("currency='([A-Z]{3})' rate='([0-9.]+)'");
  static final Pattern JSON_RATE = Pattern.compile("\"([A-Z]{3})\":([0-9.]+)");

  /** Returns null when the ECB cannot be read: without rates no fund outside the euro could be priced. */
  public static Map<String, Double> fetch() {
    String xml = Utils.getMSResponse(URL);
    Map<String, Double> rates = xml == null ? new HashMap<>() : parse(xml, RATE);
    if (rates.isEmpty()) {
      log.warning("ECB rates unavailable, reading them from Frankfurter");
      String json = Utils.getMSResponse(FALLBACK_URL);
      rates = json == null ? new HashMap<>() : parse(json, JSON_RATE);
    }
    if (rates.isEmpty()) {
      log.severe("ECB rates unavailable");
      return null;
    }
    log.info("ECB rates: " + rates.size() + " currencies");
    return rates;
  }

  static Map<String, Double> parse(String text, Pattern pattern) {
    Map<String, Double> rates = new HashMap<>();
    Matcher matcher = pattern.matcher(text);
    while (matcher.find()) {
      rates.put(matcher.group(1), Double.valueOf(matcher.group(2)));
    }
    return rates;
  }
}
