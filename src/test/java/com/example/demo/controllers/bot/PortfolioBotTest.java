package com.example.demo.controllers.bot;

import com.example.demo.infrastructure.BrokerType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.example.demo.infrastructure.BrokerType.IBKR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            // IBKR activity files: U + digits + dotted transaction name + .csv
            "U17959259.TRANSACTIONS.20250211.20260612.csv, IBKR",
            // extension fallbacks
            "statement-2026.xlsx, XTB"
    })
    void detectBroker_resolvesKnownBrokers(String fileName, BrokerType expected) {
        assertEquals(expected, PortfolioBot.detectBroker(fileName));
    }

    @ParameterizedTest
    @ValueSource(strings = {"statement.pdf", "statement.txt", "upload", "U1234.csv", "statement.csv", "transactions.csv", "us-rates.csv"})
    void detectBroker_returnsNullForUnsupportedFiles(String fileName) {
        assertNull(PortfolioBot.detectBroker(fileName));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void detectBroker_returnsNullForNullOrEmpty(String fileName) {
        assertNull(PortfolioBot.detectBroker(fileName));
    }

    @Test
    void authorization_requiresExactConfiguredChatId() {
        assertTrue(PortfolioBot.isAuthorized(" 123456 ", "123456"));
        assertFalse(PortfolioBot.isAuthorized("123456", "654321"));
        assertFalse(PortfolioBot.isAuthorized("", "123456"));
        assertFalse(PortfolioBot.isAuthorized(null, "123456"));
    }

    @Test
    void downloadLimit_allowsTwentyMbAndRejectsLargerFiles() {
        assertFalse(PortfolioBot.isDownloadTooLarge(PortfolioBot.MAX_DOWNLOAD_SIZE_BYTES));
        assertTrue(PortfolioBot.isDownloadTooLarge(PortfolioBot.MAX_DOWNLOAD_SIZE_BYTES + 1));
    }

    @Test
    void startCommand_supportsDirectAndGroupChatForms() {
        assertTrue(PortfolioBot.isStartCommand("/start", "investory_bot"));
        assertTrue(PortfolioBot.isStartCommand(" /start@Investory_Bot ", "investory_bot"));
        assertFalse(PortfolioBot.isStartCommand("/start@other_bot", "investory_bot"));
        assertFalse(PortfolioBot.isStartCommand(null, "investory_bot"));
    }

    @Test
    void resetCommand_supportsDirectAndGroupChatForms() {
        assertTrue(PortfolioBot.isResetCommand("/reset", "investory_bot"));
        assertTrue(PortfolioBot.isResetCommand(" /reset@Investory_Bot ", "investory_bot"));
        assertFalse(PortfolioBot.isResetCommand("/reset@other_bot", "investory_bot"));
        assertFalse(PortfolioBot.isResetCommand("reset", "investory_bot"));
    }

    @Test
    void detectBroker_isCaseInsensitive() {
        assertEquals(BrokerType.XTB, PortfolioBot.detectBroker("Account.XLSX"));
        assertEquals(IBKR, PortfolioBot.detectBroker("MyIbkrAccount.CSV"));
        assertEquals(IBKR, PortfolioBot.detectBroker("U17959259.TRANSACTIONS.20250211.20251231.CSV"));
    }
}
