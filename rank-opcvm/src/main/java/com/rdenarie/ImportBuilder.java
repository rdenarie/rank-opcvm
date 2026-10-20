package com.rdenarie;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the Morningstar universe into an import: keeps the funds of the personal categories, filters them as the
 * Boursorama scraper did, then scores and ranks them. Pure computation, no network.
 */
public class ImportBuilder {

  /** Why a fund of a personal category is not kept. */
  public enum Rejection {
    /** Morningstar knows no performance for it. */
    NO_PERFORMANCE,
    /** Its last net asset value is more than a year old. */
    STALE,
    /** Quoted in a currency the ECB gives no rate for. */
    NO_EUR_PRICE,
    /** Its minimum initial purchase exceeds {@link #MAX_TICKET_EUR}. */
    TOO_EXPENSIVE
  }

  static final double MAX_TICKET_EUR = 50000.0;

  /** The outcome of an import: categories and kept funds, both ranked. */
  public static class Result {
    public final LocalDate date;
    public final List<Category> categories;
    public final List<Fund> funds;
    public final Map<Rejection, Integer> rejections;

    Result(LocalDate date, List<Category> categories, List<Fund> funds, Map<Rejection, Integer> rejections) {
      this.date = date;
      this.categories = categories;
      this.funds = funds;
      this.rejections = rejections;
    }
  }

  /**
   * @param universe every share class Morningstar returned
   * @param categoryNames Morningstar category id to its current Morningstar name
   * @param persoByCategoryId Morningstar category id to the personal category it belongs to
   * @param eurRates units of each currency for one euro (ECB reference rates); EUR itself is implied
   * @param partners the Boursorama partner lists, null to skip the fund type
   */
  public static Result build(LocalDate date, Collection<Fund> universe, Map<String, String> categoryNames,
                             Map<String, String> persoByCategoryId, Map<String, Double> eurRates, BoursoPartners partners) {
    Map<String, Category> categories = new LinkedHashMap<>();
    Map<String, List<Fund>> fundsByCategory = new LinkedHashMap<>();
    for (Fund fund : universe) {
      String perso = persoByCategoryId.get(fund.categoryId);
      if (perso == null) {
        continue;
      }
      categories.computeIfAbsent(fund.categoryId, id -> new Category(id, categoryNames.getOrDefault(id, id), perso));
      fundsByCategory.computeIfAbsent(fund.categoryId, id -> new ArrayList<>()).add(fund);
    }

    Map<Rejection, Integer> rejections = new EnumMap<>(Rejection.class);
    List<Fund> kept = new ArrayList<>();
    for (Map.Entry<String, List<Fund>> entry : fundsByCategory.entrySet()) {
      Category category = categories.get(entry.getKey());
      category.perf = average(entry.getValue());
      category.score = Scoring.score(category.perf);
      List<Fund> keptInCategory = new ArrayList<>();
      for (Fund fund : entry.getValue()) {
        Rejection rejection = reject(fund, date, eurRates);
        if (rejection != null) {
          rejections.merge(rejection, 1, Integer::sum);
          continue;
        }
        fund.score = Scoring.scoreFund(fund.perf, category.perf);
        fund.filled = Scoring.hasGaps(fund.perf);
        if (partners != null) {
          fund.type = partners.typeOf(fund);
        }
        keptInCategory.add(fund);
      }
      for (Fund fund : keptInCategory) {
        fund.rank = 1 + (int) keptInCategory.stream().filter(other -> other.score > fund.score).count();
        fund.fundsInCategory = keptInCategory.size();
      }
      category.funds = keptInCategory.size();
      kept.addAll(keptInCategory);
    }

    List<Category> ranked = new ArrayList<>();
    for (Category category : categories.values()) {
      if (category.funds > 0) {
        ranked.add(category);
      }
    }
    for (Category category : ranked) {
      category.rank = 1 + (int) ranked.stream().filter(other -> other.score > category.score).count();
    }
    ranked.sort(Comparator.comparingInt(category -> category.rank));
    kept.sort(Comparator.comparingDouble((Fund fund) -> fund.score).reversed());
    return new Result(date, ranked, kept, rejections);
  }

  static Rejection reject(Fund fund, LocalDate date, Map<String, Double> eurRates) {
    if (!fund.hasPerformance()) {
      return Rejection.NO_PERFORMANCE;
    }
    if (fund.closePriceDate != null && fund.closePriceDate.isBefore(date.minusDays(365))) {
      return Rejection.STALE;
    }
    fund.priceEur = toEur(fund.closePrice, fund.currency, eurRates);
    if (fund.priceEur == null) {
      return Rejection.NO_EUR_PRICE;
    }
    Double ticketEur = ticketEur(fund, eurRates);
    if (ticketEur != null && ticketEur > MAX_TICKET_EUR) {
      return Rejection.TOO_EXPENSIVE;
    }
    return null;
  }

  /** The minimum initial purchase in euros, null when Morningstar does not give it in a usable unit. */
  static Double ticketEur(Fund fund, Map<String, Double> eurRates) {
    if (fund.initialPurchase == null) {
      return null;
    }
    if (Fund.UNIT_AMOUNT.equals(fund.initialPurchaseUnit)) {
      String currency = fund.initialPurchaseCurrency != null ? fund.initialPurchaseCurrency : fund.currency;
      return toEur(fund.initialPurchase, currency, eurRates);
    }
    return fund.ticketInShares() ? fund.initialPurchase * fund.priceEur : null;
  }

  static Double toEur(Double amount, String currency, Map<String, Double> eurRates) {
    if (amount == null || currency == null) {
      return null;
    }
    if (currency.equals("EUR")) {
      return amount;
    }
    Double rate = eurRates.get(currency);
    return rate == null ? null : Math.round(amount / rate * 100.0) / 100.0;
  }

  private static Double[] average(List<Fund> funds) {
    Double[] average = new Double[Scoring.PERIODS.length];
    for (int i = 0; i < average.length; i++) {
      double sum = 0d;
      int count = 0;
      for (Fund fund : funds) {
        if (fund.perf[i] != null) {
          sum += fund.perf[i];
          count++;
        }
      }
      average[i] = count == 0 ? null : Math.round(sum / count * 100.0) / 100.0;
    }
    return average;
  }
}
