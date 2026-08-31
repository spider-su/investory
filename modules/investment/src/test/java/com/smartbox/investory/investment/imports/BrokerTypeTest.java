package com.smartbox.investory.investment.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Broker Type")
class BrokerTypeTest {

  @DisplayName("from Value is Case Insensitive")
  @Test
  void fromValue_isCaseInsensitive() {
    assertEquals(BrokerType.XTB, BrokerType.fromValue("xtb"));
    assertEquals(BrokerType.XTB, BrokerType.fromValue("XTB"));
    assertEquals(BrokerType.IBKR, BrokerType.fromValue("ibkr"));
    assertEquals(BrokerType.IBKR, BrokerType.fromValue("IBKR"));
  }

  @DisplayName("from Value throws For Unknown Broker")
  @Test
  void fromValue_throwsForUnknownBroker() {
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> BrokerType.fromValue("etoro"));
    assertEquals("Unsupported broker: etoro", ex.getMessage());
  }
}
