package com.rdenarie;

/**
 * The ranking formula, unchanged since the Boursorama scraper: the mean of the cumulative performances
 * over {@link #PERIODS}, plus the 3-year one (or 5, or 10 years) when more than four periods are known,
 * else plus the longest known one.
 */
public final class Scoring {

  /** The periods of a performance array, shortest first except the year to date. */
  public static final String[] PERIODS = {"1erjanvier", "1mois", "6mois", "1an", "3ans", "5ans", "10ans"};
  static final int THREE_YEARS = 4;
  static final int FIVE_YEARS = 5;
  static final int TEN_YEARS = 6;

  private Scoring() {
  }

  /** Score of a category, from its average performances. */
  public static double score(Double[] perf) {
    double sum = 0d;
    double last = 0d;
    int count = 0;
    for (Double value : perf) {
      if (value != null) {
        sum += value;
        last = value;
        count++;
      }
    }
    if (count == 0) {
      return 0d;
    }
    double score = sum / count;
    if (count > 4) {
      Double longTerm = perf[THREE_YEARS] != null ? perf[THREE_YEARS] : perf[FIVE_YEARS] != null ? perf[FIVE_YEARS] : perf[TEN_YEARS];
      score += longTerm != null ? longTerm : last;
    } else {
      score += last;
    }
    return Math.round(score * 100.0) / 100.0;
  }

  /**
   * Score of a fund. A fund with a hole in its history (a period missing before its longest known one) has the hole
   * filled with the category average first, so that a missing period neither helps nor hurts it.
   */
  public static double scoreFund(Double[] perf, Double[] categoryPerf) {
    return score(fillGaps(perf, categoryPerf));
  }

  /** True when a period is missing before the longest known one. */
  public static boolean hasGaps(Double[] perf) {
    int firstMissing = -1;
    int lastKnown = -1;
    for (int i = 0; i < perf.length; i++) {
      if (perf[i] == null) {
        if (firstMissing == -1) {
          firstMissing = i;
        }
      } else {
        lastKnown = i;
      }
    }
    return firstMissing != -1 && firstMissing < lastKnown;
  }

  static Double[] fillGaps(Double[] perf, Double[] categoryPerf) {
    Double[] filled = perf.clone();
    if (!hasGaps(perf)) {
      return filled;
    }
    boolean beforeLastKnown = false;
    for (int i = perf.length - 1; i >= 0; i--) {
      if (!beforeLastKnown) {
        beforeLastKnown = perf[i] != null;
      } else if (filled[i] == null) {
        filled[i] = categoryPerf[i];
      }
    }
    return filled;
  }

  /** Converts an annualised performance over a number of years into a cumulative one, as Boursorama shows it. */
  public static Double cumulative(Double annualised, int years) {
    if (annualised == null || years <= 1) {
      return annualised;
    }
    return Math.round((Math.pow(1 + annualised / 100, years) - 1) * 10000.0) / 100.0;
  }
}
