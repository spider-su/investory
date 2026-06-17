package com.example.demo.controllers.bot;

import com.example.demo.infrastructure.BrokerType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PortfolioBotTest {

    @ParameterizedTest
    @CsvSource({
            // explicit XTB keyword always wins
            "account_51499241.xlsx, XTB",
            "XTB_export.bin, XTB",
            "xtb-march-2026.csv, XTB",
            // explicit IBKR keyword always wins (even with .xlsx)
            "ibkr-2026.csv, IBKR",
            "IBKR_jan.xlsx, IBKR",
            // IBKR account-id files: U + digits + .csv
            "U1234.csv, IBKR",
            "U17959259.TRANSACTIONS.20250211.20260612.csv, IBKR",
            // generic extension fallbacks
            "statement-2026.xlsx, XTB",
            "transactions.csv, IBKR"
    })
    void detectBroker_resolvesKnownBrokers(String fileName, BrokerType expected) {
        assertEquals(expected, PortfolioBot.detectBroker(fileName));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "statement.pdf",
            // 'u' prefix without digits must NOT be auto-classified as IBKR.
            "upload.csv",
            "us-rates.csv",
            "users.txt"
    })
    void detectBroker_returnsNullForUnknown(String fileName) {
        assertNull(PortfolioBot.detectBroker(fileName));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void detectBroker_returnsNullForNullOrEmpty(String fileName) {
        assertNull(PortfolioBot.detectBroker(fileName));
    }

    @Test
    void detectBroker_isCaseInsensitive() {
        assertEquals(BrokerType.XTB, PortfolioBot.detectBroker("Account.XLSX"));
        assertEquals(BrokerType.IBKR, PortfolioBot.detectBroker("MyIbkrAccount.CSV"));
    }
}
