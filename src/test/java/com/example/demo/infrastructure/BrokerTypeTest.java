package com.example.demo.infrastructure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrokerTypeTest {

    @Test
    void fromValue_isCaseInsensitive() {
        assertEquals(BrokerType.XTB, BrokerType.fromValue("xtb"));
        assertEquals(BrokerType.XTB, BrokerType.fromValue("XTB"));
        assertEquals(BrokerType.IBKR, BrokerType.fromValue("ibkr"));
        assertEquals(BrokerType.IBKR, BrokerType.fromValue("IBKR"));
    }

    @Test
    void fromValue_throwsForUnknownBroker() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> BrokerType.fromValue("etoro"));
        assertEquals("Unsupported broker: etoro", ex.getMessage());
    }
}

