package com.smartbox.investory.investment.ledger.position;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PositionTypeTest {

  @Test
  void fromBrokerSideOrBuyMapsOnlyDatabaseSupportedSides() {
    assertEquals(PositionType.BUY, PositionType.fromBrokerSideOrBuy("BUY"));
    assertEquals(PositionType.SELL, PositionType.fromBrokerSideOrBuy("SELL"));
    assertEquals(PositionType.BUY, PositionType.fromBrokerSideOrBuy("LONG"));
    assertEquals(PositionType.SELL, PositionType.fromBrokerSideOrBuy("SHORT"));
    assertEquals(PositionType.BUY, PositionType.fromBrokerSideOrBuy("UNKNOWN BROKER LABEL"));
    assertEquals(PositionType.BUY, PositionType.fromBrokerSideOrBuy(null));
  }
}
