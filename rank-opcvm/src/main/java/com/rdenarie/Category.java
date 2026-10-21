package com.rdenarie;

/** A Morningstar category of an import, with the personal category it belongs to. */
public class Category {

  public final String id;
  public String name;
  public final String perso;
  /** Average cumulative performances of every fund of the category in the universe, before any filter. */
  public Double[] perf = new Double[Scoring.PERIODS.length];
  public double score;
  public int rank;
  /** Number of funds and of ETFs of the category kept by the import. */
  public int funds;
  public int etfs;

  public Category(String id, String name, String perso) {
    this.id = id;
    this.name = name;
    this.perso = perso;
  }
}
