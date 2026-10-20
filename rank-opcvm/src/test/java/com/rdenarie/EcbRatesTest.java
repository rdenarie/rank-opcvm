package com.rdenarie;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class EcbRatesTest {

  @Test
  public void readsTheEcbXmlAndItsFrankfurterCopy() {
    String xml = "<Cube time='2026-10-20'><Cube currency='USD' rate='1.1215'/><Cube currency='GBP' rate='0.84028'/></Cube>";
    String json = "{\"amount\":1.0,\"base\":\"EUR\",\"date\":\"2026-10-20\",\"rates\":{\"USD\":1.1215,\"GBP\":0.84028}}";

    assertEquals(EcbRates.parse(xml, EcbRates.RATE),
                 EcbRates.parse(json, EcbRates.JSON_RATE));
    assertEquals(2, EcbRates.parse(json, EcbRates.JSON_RATE).size());
  }
}
