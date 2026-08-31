package com.smartbox.investory.investment.ledger.position;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Position Type")
class PositionTypeTest {

  @DisplayName("from Broker Side Or Buy Maps Only Database Supported Sides")
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
