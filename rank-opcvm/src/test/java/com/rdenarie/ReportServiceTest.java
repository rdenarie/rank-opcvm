package com.rdenarie;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReportServiceTest {

  @Test
  public void writesFeesAsBoursoramaDid() {
    assertEquals("5 %", ReportService.percent(5.0));
    assertEquals("1,07 %", ReportService.percent(1.07));
    assertEquals("ND", ReportService.percent(null));
  }

  @Test
  public void showsTheTopOfEachCategory() {
    assertTrue(ReportService.shown(fund(100, 500)));
    assertFalse(ReportService.shown(fund(101, 500)));
    assertTrue(ReportService.shown(fund(200, 1000)));
    assertFalse(ReportService.shown(fund(201, 1000)));
  }

  private static Fund fund(int rank, int funds) {
    Fund fund = new Fund();
    fund.rank = rank;
    fund.fundsInCategory = funds;
    return fund;
  }
}
