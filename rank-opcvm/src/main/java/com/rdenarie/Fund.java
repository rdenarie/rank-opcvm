package com.rdenarie;

import java.time.LocalDate;

/** A share class as read from Morningstar, then scored and ranked by {@link ImportBuilder}. */
public class Fund {

  /** Morningstar units of the initial purchase: an amount in a currency, or a number of shares. */
  static final String UNIT_AMOUNT = "1";
  static final String[] UNITS_SHARES = {"2", "4", "5"};

  /** Funds and ETFs are ranked separately in each category. */
  public enum Kind { FUND, ETF }

  public Kind kind = Kind.FUND;
  public String isin;
  public String name;
  /** Morningstar's PerformanceId: the "0P..." identifier Boursorama uses in its fund URLs. */
  public String performanceId;
  public String categoryId;
  public String currency;
  public Double closePrice;
  public LocalDate closePriceDate;
  /** Cumulative performances over {@link Scoring#PERIODS}, null when unknown. */
  public Double[] perf = new Double[Scoring.PERIODS.length];
  public Double ongoingCharge;
  public Double entryFee;
  public Double exitFee;
  public Double initialPurchase;
  public String initialPurchaseUnit;
  public String initialPurchaseCurrency;
  public Integer stars;
  public Double assets;

  public Double priceEur;
  /** "partenaire", "boursomarkets" or "" as on Boursorama. */
  public String type = "";
  public double score;
  /** True when a missing period was filled with the category average to compute the score. */
  public boolean filled;
  /** Rank among the share classes of the same kind in the category, and their number. */
  public int rank;
  public int fundsInCategory;
  /** Rank the share class would have among the other kind of the category, and their number; 0 when there is none. */
  public int otherKindRank;
  public int otherKindInCategory;

  public boolean hasPerformance() {
    for (Double value : perf) {
      if (value != null) {
        return true;
      }
    }
    return false;
  }

  public boolean ticketInShares() {
    for (String unit : UNITS_SHARES) {
      if (unit.equals(initialPurchaseUnit)) {
        return true;
      }
    }
    return false;
  }

  /** The initial purchase as Boursorama wrote it: "7500 USD", "1 PART" or "ND". */
  public String ticketLabel() {
    if (initialPurchase == null) {
      return "ND";
    }
    String amount = initialPurchase % 1 == 0 ? String.valueOf(initialPurchase.longValue()) : String.valueOf(initialPurchase);
    if (UNIT_AMOUNT.equals(initialPurchaseUnit)) {
      return amount + " " + (initialPurchaseCurrency != null ? initialPurchaseCurrency : currency);
    }
    return ticketInShares() ? amount + " PART" : "ND";
  }
}
