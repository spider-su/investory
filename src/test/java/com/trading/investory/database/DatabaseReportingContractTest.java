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
import java.util.List;
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
                                                + "WHERE check_name = 'UNSUPPORTED_TRANSACTION_STATES'")) {
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

    private static Connection connection() throws SQLException {
        Connection connection =
                DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        assertNotNull(connection);
        return connection;
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
