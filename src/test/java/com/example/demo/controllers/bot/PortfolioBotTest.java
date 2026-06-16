package com.example.demo.controllers.bot;

import com.example.demo.data.BrokerType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PortfolioBotTest {

    @ParameterizedTest
    @CsvSource({
            "account_51499241.xlsx, XTB",
            "XTB_export.bin, XTB",
            "ibkr-2026.csv, IBKR",
            "U1234.csv, IBKR",
            "U17959259.TRANSACTIONS.20250211.20260612.csv, IBKR"
    })
    void detectBroker_resolvesKnownBrokers(String fileName, BrokerType expected) {
        assertEquals(expected, PortfolioBot.detectBroker(fileName));
    }

    @Test
    void detectBroker_returnsNullForUnknown() {
        assertNull(PortfolioBot.detectBroker("statement.pdf"));
        assertNull(PortfolioBot.detectBroker(""));
        assertNull(PortfolioBot.detectBroker(null));
    }
}

