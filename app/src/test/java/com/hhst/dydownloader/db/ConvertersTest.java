package com.hhst.dydownloader.db;

import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ConvertersTest {

  @Test
  public void platformFromString_returnsNullForUnknownValue() {
    assertNull(Converters.platformFromString("UNKNOWN_PLATFORM"));
  }

  @Test
  public void cardTypeFromString_returnsNullForUnknownValue() {
    assertNull(Converters.fromString("UNKNOWN_CARD_TYPE"));
  }
}
