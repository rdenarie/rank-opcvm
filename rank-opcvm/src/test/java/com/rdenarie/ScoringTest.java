package com.rdenarie;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ScoringTest {

  @Test
  public void addsTheThreeYearPerformanceWhenMoreThanFourPeriodsAreKnown() {
    //Carmignac Patrimoine as Boursorama stored it on 20/10/2026, scoreFond 49.11 in the Datastore
    Double[] perf = {7.38, -0.07, 7.65, 8.98, 33.75, 16.81, 33.05};

    assertEquals(49.11, Scoring.score(perf), 0.001);
  }

  @Test
  public void addsTheLongestKnownPerformanceOtherwise() {
    Double[] perf = {2.0, 1.0, 3.0, 6.0, null, null, null};

    assertEquals(3.0 + 6.0, Scoring.score(perf), 0.001);
  }

  @Test
  public void fillsAHoleWithTheCategoryAverage() {
    Double[] fund = {null, 1.0, null, 6.0, 20.0, null, null};
    Double[] category = {4.0, 0.5, 2.0, 5.0, 15.0, 30.0, 60.0};

    assertTrue(Scoring.hasGaps(fund));
    assertArrayEquals(new Double[] {4.0, 1.0, 2.0, 6.0, 20.0, null, null}, Scoring.fillGaps(fund, category));
    assertEquals(Scoring.score(new Double[] {4.0, 1.0, 2.0, 6.0, 20.0, null, null}), Scoring.scoreFund(fund, category), 0.001);
  }

  @Test
  public void leavesAHistoryWithoutHoleUnchanged() {
    Double[] fund = {1.0, 1.0, 2.0, 6.0, null, null, null};

    assertFalse(Scoring.hasGaps(fund));
    assertEquals(Scoring.score(fund), Scoring.scoreFund(fund, new Double[7]), 0.001);
  }

  @Test
  public void convertsAnnualisedToCumulative() {
    //Morningstar's 10.17833 %/year over 3 years is Boursorama's 33.75 %
    assertEquals(33.75, Scoring.cumulative(10.17833, 3), 0.001);
    assertEquals(8.98, Scoring.cumulative(8.98, 1), 0.001);
  }
}
