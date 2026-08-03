package com.trading.investory.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class DatabaseReportingContractTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("investory_test")
                    .withUsername("investory")
                    .withPassword("investory");

    @BeforeAll
    static void migrateDatabase() {
        POSTGRES.start();

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:sql/migration")
                .load()
                .migrate();
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @Test
    void everyViewCanBePlanned() throws SQLException {
        try (Connection connection = connection()) {
            List<String> views = relationNames(connection, "pg_views", "viewname");
            assertFalse(views.isEmpty(), "Expected reporting views in the investory schema");

            for (String view : views) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeQuery("SELECT * FROM investory." + quoteIdentifier(view) + " LIMIT 0");
                }
            }
        }
    }

    @Test
    void everyMaterializedViewCanBeRefreshedAndRead() throws SQLException {
        try (Connection connection = connection()) {
            List<String> materializedViews = relationNames(connection, "pg_matviews", "matviewname");
            assertFalse(
                    materializedViews.isEmpty(),
                    "Expected reporting materialized views in the investory schema");

            for (String materializedView : materializedViews) {
                String qualifiedName = "investory." + quoteIdentifier(materializedView);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("REFRESH MATERIALIZED VIEW " + qualifiedName);
                    statement.executeQuery("SELECT * FROM " + qualifiedName + " LIMIT 0");
                }
            }
        }
    }

    @Test
    void canonicalFxFunctionsHandleSameCurrencyAndMissingRates() throws SQLException {
        try (Connection connection = connection()) {
            try (PreparedStatement statement =
                    connection.prepareStatement(
                            "SELECT fx_rate_to_target, conversion_status "
                                    + "FROM investory.resolve_fx_rate(DATE '2026-01-31', ?, ?)")) {
                statement.setString(1, "USD");
                statement.setString(2, "USD");

                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(
                            0,
                            result.getBigDecimal("fx_rate_to_target").compareTo(BigDecimal.ONE));
                    assertEquals("SAME_CURRENCY", result.getString("conversion_status"));
                }

                statement.setString(1, "ZZZ");
                statement.setString(2, "YYY");

                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals("MISSING", result.getString("conversion_status"));
                    assertNull(result.getBigDecimal("fx_rate_to_target"));
                }
            }

            try (Statement statement = connection.createStatement();
                    ResultSet result =
                            statement.executeQuery(
                                    "SELECT portfolio_id, fx_rate_to_base, conversion_status "
                                            + "FROM investory.resolve_portfolio_fx_rate("
                                            + "(SELECT id FROM investory.portfolios ORDER BY id LIMIT 1), "
                                            + "DATE '2026-01-31', "
                                            + "(SELECT base_currency FROM investory.portfolios ORDER BY id LIMIT 1))")) {
                assertTrue(result.next());
                assertTrue(result.getLong("portfolio_id") > 0);
                assertEquals(0, result.getBigDecimal("fx_rate_to_base").compareTo(BigDecimal.ONE));
                assertEquals("SAME_CURRENCY", result.getString("conversion_status"));
            }
        }
    }

    @Test
    void everyCashOperationEnumHasAnExplicitClassificationContract() throws SQLException {
        Map<String, String> expectedCategories = new LinkedHashMap<>();
        expectedCategories.put("DEPOSIT", "EXTERNAL_DEPOSIT");
        expectedCategories.put("WITHDRAWAL", "EXTERNAL_WITHDRAWAL");
        expectedCategories.put("TRANSFER", "EXTERNAL_DEPOSIT");
        expectedCategories.put("SUBACCOUNT_TRANSFER", "INTERNAL_BOOKKEEPING");
        expectedCategories.put("STOCK_PURCHASE", "TRADE_PURCHASE");
        expectedCategories.put("STOCK_SELL", "TRADE_SALE");
        expectedCategories.put("CLOSE_TRADE", "REALIZED_TRADE_RESULT");
        expectedCategories.put("CORRECTION", "CORRECTION");
        expectedCategories.put("ROLLOVER", "REALIZED_TRADE_RESULT");
        expectedCategories.put("SWAP", "FEE");
        expectedCategories.put("DIVIDEND", "DIVIDEND");
        expectedCategories.put("FREE_FUNDS_INTEREST", "INTEREST");
        expectedCategories.put("COMMISSION", "FEE");
        expectedCategories.put("SEC_FEE", "FEE");
        expectedCategories.put("STAMP_DUTY", "OTHER_TAX");
        expectedCategories.put("TRANSACTION_TAX", "OTHER_TAX");
        expectedCategories.put("WITHHOLDING_TAX", "WITHHOLDING_TAX");
        expectedCategories.put("FREE_FUNDS_INTEREST_TAX", "OTHER_TAX");
        expectedCategories.put("UNKNOWN", "UNCLASSIFIED");

        try (Connection connection = connection()) {
            List<String> actualEnumValues = enumLabels(connection, "cash_operation_type");
            assertEquals(
                    new LinkedHashSet<>(expectedCategories.keySet()),
                    new LinkedHashSet<>(actualEnumValues),
                    "Update this classification contract whenever cash_operation_type changes");

            connection.setAutoCommit(false);
            try {
                long accountId;
                String currency;
                try (Statement statement = connection.createStatement();
                        ResultSet result =
                                statement.executeQuery(
                                        "SELECT id, currency FROM investory.accounts ORDER BY id LIMIT 1")) {
                    assertTrue(result.next(), "Initial data must contain at least one account");
                    accountId = result.getLong("id");
                    currency = result.getString("currency");
                }

                for (Map.Entry<String, String> classification : expectedCategories.entrySet()) {
                    String operation = classification.getKey();
                    BigDecimal amount = canonicalAmount(operation);
                    String comment =
                            operation.equals("TRANSFER")
                                    ? "cash transfer"
                                    : "classification contract " + operation;

                    long operationId;
                    try (PreparedStatement statement =
                            connection.prepareStatement(
                                    "INSERT INTO investory.cash_operations "
                                            + "(account_id, operation, amount, currency, comment, date) "
                                            + "VALUES (?, ?::investory.cash_operation_type, ?, ?, ?, ?) "
                                            + "RETURNING id")) {
                        statement.setLong(1, accountId);
                        statement.setString(2, operation);
                        statement.setBigDecimal(3, amount);
                        statement.setString(4, currency);
                        statement.setString(5, comment);
                        statement.setObject(6, OffsetDateTime.parse("2026-01-15T12:00:00Z"));
                        try (ResultSet result = statement.executeQuery()) {
                            assertTrue(result.next());
                            operationId = result.getLong(1);
                        }
                    }

                    try (PreparedStatement statement =
                            connection.prepareStatement(
                                    "SELECT normalized_category "
                                            + "FROM investory.normalized_cash_operations "
                                            + "WHERE operation_id = ?")) {
                        statement.setLong(1, operationId);
                        try (ResultSet result = statement.executeQuery()) {
                            assertTrue(result.next(), "Missing normalized row for " + operation);
                            assertEquals(
                                    classification.getValue(),
                                    result.getString("normalized_category"),
                                    "Unexpected classification for " + operation);
                        }
                    }
                }
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void positionOperationEnumAndSignedQuantityRemainExhaustive() throws SQLException {
        try (Connection connection = connection()) {
            assertEquals(
                    List.of("BUY", "SELL"),
                    enumLabels(connection, "positions_operation_type"),
                    "Position operations require an explicit signed-quantity contract");

            try (PreparedStatement statement =
                    connection.prepareStatement(
                            "SELECT investory.signed_position_quantity("
                                    + "?::investory.positions_operation_type, 5)")) {
                statement.setString(1, "BUY");
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(0, result.getBigDecimal(1).compareTo(new BigDecimal("5")));
                }

                statement.setString(1, "SELL");
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(0, result.getBigDecimal(1).compareTo(new BigDecimal("-5")));
                }
            }
        }
    }

    @Test
    void unresolvedCashOperationAppearsInDiagnosticsAndMonthlyReview() throws SQLException {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                long accountId;
                String currency;
                try (Statement statement = connection.createStatement();
                        ResultSet result =
                                statement.executeQuery(
                                        "SELECT id, currency FROM investory.accounts ORDER BY id LIMIT 1")) {
                    assertTrue(result.next(), "Initial data must contain at least one account");
                    accountId = result.getLong("id");
                    currency = result.getString("currency");
                }

                long operationId;
                try (PreparedStatement statement =
                        connection.prepareStatement(
                                "INSERT INTO investory.cash_operations "
                                        + "(account_id, operation, amount, currency, comment, date) "
                                        + "VALUES (?, 'UNKNOWN', 1, ?, ?, ?) RETURNING id")) {
                    statement.setLong(1, accountId);
                    statement.setString(2, currency);
                    statement.setString(3, "database contract test");
                    statement.setObject(4, OffsetDateTime.parse("2026-01-15T12:00:00Z"));
                    try (ResultSet result = statement.executeQuery()) {
                        assertTrue(result.next());
                        operationId = result.getLong(1);
                    }
                }

                try (PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT issue_code FROM investory.reporting_unsupported_transaction_states "
                                        + "WHERE source_table = 'cash_operations' AND row_id = ?")) {
                    statement.setLong(1, operationId);
                    try (ResultSet result = statement.executeQuery()) {
                        assertTrue(result.next());
                        assertEquals("UNKNOWN_CASH_OPERATION", result.getString("issue_code"));
                    }
                }

                try (Statement statement = connection.createStatement();
                        ResultSet result =
                                statement.executeQuery(
                                        "SELECT issue_count FROM investory.reporting_monthly_import_review "
                                                + "WHERE check_code = 'UNSUPPORTED_TRANSACTION_STATE'")) {
                    assertTrue(result.next());
                    assertTrue(result.getLong("issue_count") >= 1);
                }
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void ownershipAndTimezoneContractsRemainValid() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            try (ResultSet result =
                    statement.executeQuery(
                            "SELECT username, display_name FROM investory.app_users WHERE id = 1")) {
                assertTrue(result.next());
                assertEquals("alex", result.getString("username"));
                assertEquals("Alex", result.getString("display_name"));
            }

            try (ResultSet result =
                    statement.executeQuery(
                            "SELECT count(*) FROM investory.portfolios WHERE user_id IS NULL OR user_id <> 1")) {
                assertTrue(result.next());
                assertEquals(0, result.getLong(1));
            }

            try (ResultSet result =
                    statement.executeQuery(
                            "SELECT count(*) FROM investory.reporting_timezone_naive_columns")) {
                assertTrue(result.next());
                assertEquals(0, result.getLong(1));
            }
        }
    }

    private static BigDecimal canonicalAmount(String operation) {
        return switch (operation) {
            case "WITHDRAWAL",
                    "SWAP",
                    "COMMISSION",
                    "SEC_FEE",
                    "STAMP_DUTY",
                    "TRANSACTION_TAX",
                    "WITHHOLDING_TAX",
                    "FREE_FUNDS_INTEREST_TAX" -> new BigDecimal("-1");
            default -> new BigDecimal("1");
        };
    }

    private static Connection connection() throws SQLException {
        Connection connection =
                DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        assertNotNull(connection);
        return connection;
    }

    private static List<String> enumLabels(Connection connection, String typeName)
            throws SQLException {
        String sql =
                "SELECT enumlabel "
                        + "FROM pg_enum e "
                        + "JOIN pg_type t ON t.oid = e.enumtypid "
                        + "JOIN pg_namespace n ON n.oid = t.typnamespace "
                        + "WHERE n.nspname = 'investory' AND t.typname = ? "
                        + "ORDER BY e.enumsortorder";
        List<String> labels = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, typeName);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    labels.add(result.getString(1));
                }
            }
        }
        return labels;
    }

    private static List<String> relationNames(
            Connection connection, String catalogView, String nameColumn) throws SQLException {
        String sql =
                "SELECT "
                        + nameColumn
                        + " FROM "
                        + catalogView
                        + " WHERE schemaname = 'investory' ORDER BY "
                        + nameColumn;
        List<String> names = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                names.add(result.getString(1));
            }
        }
        return names;
    }

    private static String quoteIdentifier(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }
}
