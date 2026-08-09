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
import java.time.LocalDate;
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
          assertEquals(0, result.getBigDecimal("fx_rate_to_target").compareTo(BigDecimal.ONE));
          assertEquals("SAME_CURRENCY", result.getString("conversion_status"));
        }

        statement.setString(1, "ZZZ");
        statement.setString(2, "YYY");

        try (ResultSet result = statement.executeQuery()) {
          assertTrue(result.next());
          assertEquals("MISSING_RATE", result.getString("conversion_status"));
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
  void reconstructedValuationUsesNormalizedPriceCurrencyAndOneFxConversion() throws SQLException {
    long plnPortfolioId = 910013L;
    long plnAccountId = 910013L;
    long usdPortfolioId = 910014L;
    long usdAccountId = 910014L;
    LocalDate firstDate = java.time.LocalDate.of(2026, 1, 15);

    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute("DELETE FROM investory.portfolios WHERE id IN (910013, 910014)");
      statement.execute(
          "INSERT INTO investory.portfolios (id, name, base_currency, owner, user_id) VALUES "
              + "(910013, 'Currency semantics PLN', 'PLN', 'contract', 1), "
              + "(910014, 'Currency semantics USD', 'USD', 'contract', 1)");
      statement.execute(
          "INSERT INTO investory.accounts (id, currency, provider, name, owner, portfolio_id, cash_only) VALUES "
              + "(910013, 'PLN', 'XTB', 'Currency semantics PLN', 'contract', 910013, false), "
              + "(910014, 'USD', 'IBKR', 'Currency semantics USD', 'contract', 910014, false)");

      long usdAssetId = insertAsset(connection, "SEMUSD.US", "USD");
      long eurAssetId = insertAsset(connection, "SEMEUR.DE", "EUR");
      long scaledAssetId = insertAsset(connection, "SEMSCALED.DE", "EUR");
      long transitionAssetId = insertAsset(connection, "SEMTRANS.DE", "EUR");
      long baseAssetId = insertAsset(connection, "SEMBASE.US", "USD");

      insertPosition(connection, plnAccountId, usdAssetId, "USD", firstDate);
      insertPosition(connection, plnAccountId, eurAssetId, "EUR", firstDate);
      insertPosition(connection, plnAccountId, scaledAssetId, "EUR", firstDate);
      insertPosition(connection, plnAccountId, transitionAssetId, "EUR", firstDate);
      insertPosition(connection, usdAccountId, baseAssetId, "USD", firstDate);

      for (int day = 0; day < 4; day++) {
        insertAccountDay(connection, plnAccountId, firstDate.plusDays(day), "PLN");
      }
      insertAccountDay(connection, usdAccountId, firstDate, "USD");

      insertPrice(connection, usdAssetId, firstDate, "XTB_TRADE_OPEN", "USD", 100, 1, false);
      insertPrice(connection, eurAssetId, firstDate, "XTB_TRADE_OPEN", "EUR", 100, 1, false);
      insertPrice(connection, scaledAssetId, firstDate, "XTB_TRADE_OPEN", "EUR", 2724, 0.01, false);
      insertPrice(connection, baseAssetId, firstDate, "XTB_TRADE_OPEN", "USD", 100, 1, false);
      insertPrice(connection, transitionAssetId, firstDate, "XTB_TRADE_OPEN", "EUR", 100, 1, false);
      insertPrice(
          connection,
          transitionAssetId,
          firstDate.plusDays(1),
          "INTERPOLATED_XTB",
          "EUR",
          100,
          1,
          true);
      insertPrice(
          connection,
          transitionAssetId,
          firstDate.plusDays(2),
          "STALE_CARRY_FORWARD",
          "EUR",
          100,
          1,
          false);
      insertPrice(
          connection,
          transitionAssetId,
          firstDate.plusDays(3),
          "INTERPOLATED_XTB",
          "EUR",
          100,
          1,
          true);

      try (PreparedStatement query =
          connection.prepareStatement(
              "SELECT account_id, asset_id, valuation_date, selected_price, price_currency, "
                  + "fx_rate_to_base, reconstructed_market_value_base "
                  + "FROM investory.v_reconstructed_position_daily "
                  + "WHERE account_id IN (910013, 910014) ORDER BY account_id, asset_id, valuation_date")) {
        try (ResultSet rows = query.executeQuery()) {
          Map<Long, List<Map<String, Object>>> values = new LinkedHashMap<>();
          while (rows.next()) {
            values
                .computeIfAbsent(rows.getLong("asset_id"), ignored -> new ArrayList<>())
                .add(
                    Map.of(
                        "account", rows.getLong("account_id"),
                        "date", rows.getObject("valuation_date", java.time.LocalDate.class),
                        "price", rows.getBigDecimal("selected_price"),
                        "currency", rows.getString("price_currency"),
                        "fx", rows.getBigDecimal("fx_rate_to_base"),
                        "market", rows.getBigDecimal("reconstructed_market_value_base")));
          }

          double usdFx = ((BigDecimal) values.get(usdAssetId).getFirst().get("fx")).doubleValue();
          double eurFx = ((BigDecimal) values.get(eurAssetId).getFirst().get("fx")).doubleValue();
          assertValuation(values.get(usdAssetId).getFirst(), "USD", 100, usdFx, 200 * usdFx);
          assertValuation(values.get(eurAssetId).getFirst(), "EUR", 100, eurFx, 200 * eurFx);
          assertValuation(values.get(scaledAssetId).getFirst(), "EUR", 27.24, eurFx, 2 * 27.24 * eurFx);

          try (PreparedStatement normalizedQuery =
              connection.prepareStatement(
                  "SELECT selected_price FROM investory.v_normalized_daily_price "
                      + "WHERE asset_id = ? AND valuation_date = ?")) {
            normalizedQuery.setLong(1, scaledAssetId);
            normalizedQuery.setObject(2, firstDate);
            try (ResultSet normalized = normalizedQuery.executeQuery()) {
              assertTrue(normalized.next());
              assertEquals(
                  0,
                  normalized.getBigDecimal("selected_price").compareTo(new BigDecimal("27.24")));
            }
          }
          assertValuation(values.get(baseAssetId).getFirst(), "USD", 100, 1, 200.0);

          List<Map<String, Object>> transitions = values.get(transitionAssetId);
          assertEquals(4, transitions.size());
          BigDecimal expectedMarket = BigDecimal.valueOf(200 * eurFx);
          for (Map<String, Object> transition : transitions) {
            assertEquals("EUR", transition.get("currency"));
            assertEquals(
                0, ((BigDecimal) transition.get("price")).compareTo(BigDecimal.valueOf(100)));
            assertEquals(
                expectedMarket.doubleValue(),
                ((BigDecimal) transition.get("market")).doubleValue(),
                0.0001);
            assertEquals(eurFx, ((BigDecimal) transition.get("fx")).doubleValue(), 0.0001);
          }
        }
      } finally {
        statement.execute("DELETE FROM investory.portfolios WHERE id IN (910013, 910014)");
      }
    }
  }

  private static long insertAsset(Connection connection, String symbol, String currency)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO investory.assets (name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, active) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'ETF', true) RETURNING id")) {
      statement.setString(1, symbol);
      statement.setString(2, symbol);
      statement.setString(3, symbol.substring(0, symbol.indexOf('.')));
      statement.setString(4, symbol);
      statement.setString(5, symbol);
      statement.setString(6, symbol.endsWith(".DE") ? "DE" : "US");
      statement.setString(7, currency);
      try (ResultSet result = statement.executeQuery()) {
        result.next();
        return result.getLong(1);
      }
    }
  }

  private static void insertPosition(
      Connection connection, long accountId, long assetId, String currency, LocalDate openDate)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO investory.positions "
                + "(account_id, asset_id, source_asset_symbol, operation, settlement_model, volume, "
                + "price_currency, cost_currency, profit_currency, commission_currency, open_time, "
                + "open_price, purchase_value) VALUES (?, ?, 'contract', 'BUY', 'CASH_SETTLED', 2, ?, ?, ?, ?, ?, 100, 200)")) {
      statement.setLong(1, accountId);
      statement.setLong(2, assetId);
      statement.setString(3, currency);
      statement.setString(4, currency);
      statement.setString(5, currency);
      statement.setString(6, currency);
      statement.setObject(7, openDate.atStartOfDay(java.time.ZoneOffset.UTC).toOffsetDateTime());
      statement.executeUpdate();
    }
  }

  private static void insertAccountDay(
      Connection connection, long accountId, LocalDate date, String currency) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO investory.account_daily (account_id, snapshot_date, valuation_currency) VALUES (?, ?, ?)")) {
      statement.setLong(1, accountId);
      statement.setObject(2, date);
      statement.setString(3, currency);
      statement.executeUpdate();
    }
  }

  private static void insertPrice(
      Connection connection,
      long assetId,
      LocalDate date,
      String origin,
      String currency,
      double close,
      double scale,
      boolean interpolated)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO investory.asset_price_history "
                + "(asset_id, price_date, source, source_symbol, price_origin, price_currency, close_price, "
                + "estimated, interpolation_method, interpolation_left_date, interpolation_right_date, "
                + "quality_score, quality_class, is_observed, price_scale_factor) "
                + "VALUES (?, ?, ?, 'contract', ?, ?, ?, ?, ?, ?, ?, 90, ?, ?, ?)")) {
      statement.setLong(1, assetId);
      statement.setObject(2, date);
      statement.setString(3, origin.equals("STALE_CARRY_FORWARD") ? "CARRY_FORWARD" : "XTB");
      statement.setString(4, origin);
      statement.setString(5, currency);
      statement.setDouble(6, close);
      statement.setBoolean(7, interpolated);
      statement.setString(8, interpolated ? "LINEAR_BUSINESS_DAY" : null);
      statement.setObject(9, interpolated ? date.minusDays(1) : null);
      statement.setObject(10, interpolated ? date.plusDays(1) : null);
      statement.setString(
          11,
          origin.equals("STALE_CARRY_FORWARD") ? "STALE_CARRY_FORWARD" : origin + "_OBSERVATION");
      statement.setBoolean(12, !interpolated);
      statement.setDouble(13, scale);
      statement.executeUpdate();
    }
  }

  private static void assertValuation(
      Map<String, Object> value,
      String currency,
      double selectedPrice,
      double fx,
      double marketValue) {
    assertNotNull(value);
    assertEquals(currency, value.get("currency"));
    assertEquals(selectedPrice, ((BigDecimal) value.get("price")).doubleValue(), 0.0001);
    assertEquals(fx, ((BigDecimal) value.get("fx")).doubleValue(), 0.0001);
    assertEquals(marketValue, ((BigDecimal) value.get("market")).doubleValue(), 0.0001);
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
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
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

  @Test
  void scopedSubaccountTransfersCountOnlyTheDirectionalAccountEffect() throws SQLException {
    try (Connection connection = connection()) {
      connection.setAutoCommit(false);
      try {
        long targetNegative = insertSubaccountTransfer(connection, 51499241L, -801.47, "Transfer from 51993106 to 51499241");
        long targetPositive = insertSubaccountTransfer(connection, 51499241L, 801.47, "Transfer from 51993106 to 51499241");
        assertFlowSums(connection, targetNegative, targetPositive, 801.47, 0.0, 0.0);

        long sourceNegative = insertSubaccountTransfer(connection, 51499241L, -801.47, "Transfer from 51499241 to 51993106");
        long sourcePositive = insertSubaccountTransfer(connection, 51499241L, 801.47, "Transfer from 51499241 to 51993106");
        assertFlowSums(connection, sourceNegative, sourcePositive, -801.47, 0.0, 0.0);

        long virtualDepositNegative =
            insertSubaccountTransfer(connection, 51499241L, -801.47, "Transfer from 99999999 to 51499241");
        long virtualDepositPositive =
            insertSubaccountTransfer(connection, 51499241L, 801.47, "Transfer from 99999999 to 51499241");
        assertFlowSums(
            connection, virtualDepositNegative, virtualDepositPositive, 801.47, 801.47, 0.0);

        long virtualWithdrawalNegative =
            insertSubaccountTransfer(connection, 51499241L, -801.47, "Transfer from 51499241 to 99999999");
        long virtualWithdrawalPositive =
            insertSubaccountTransfer(connection, 51499241L, 801.47, "Transfer from 51499241 to 99999999");
        assertFlowSums(
            connection, virtualWithdrawalNegative, virtualWithdrawalPositive, -801.47, -801.47, 0.0);
      } finally {
        connection.rollback();
      }
    }
  }

  @Test
  void performanceFlowSeparatesCapitalFromBookkeepingCashEffects() throws SQLException {
    try (Connection connection = connection()) {
      connection.setAutoCommit(false);
      try {
        long externalDeposit =
            insertCashOperation(connection, "DEPOSIT", 51499241L, 100.0, "external funding");
        long externalWithdrawal =
            insertCashOperation(connection, "WITHDRAWAL", 51499241L, -50.0, "external funding");
        long internalTransferIn =
            insertCashOperation(
                connection,
                "DEPOSIT",
                51499241L,
                25.0,
                "Transfer in operation on account 51499241");
        long internalTransferOut =
            insertCashOperation(
                connection,
                "DEPOSIT",
                51499241L,
                -25.0,
                "Transfer out operation on account 51499241");
        long trackedTransferOut =
            insertCashOperation(
                connection,
                "DEPOSIT",
                51499241L,
                -40.0,
                "Transfer out operation on account 51993106");
        long trackedTransferIn =
            insertCashOperation(
                connection,
                "DEPOSIT",
                51993106L,
                40.0,
                "Transfer in operation on account 51499241");
        long bookkeeping =
            insertCashOperation(
                connection,
                "SUBACCOUNT_TRANSFER",
                51499241L,
                6044.12,
                "Transfer from 51993106 to 51499241");
        long fxConversion =
            insertCashOperation(
                connection,
                "TRANSFER",
                51499241L,
                75.0,
                "Currency conversion, USD to EUR, exchange rate: 1.1");
        long correction =
            insertCashOperation(
                connection, "CORRECTION", 51499241L, 30.0, "bookkeeping correction");

        assertPerformanceFlow(connection, externalDeposit, 100.0);
        assertPerformanceFlow(connection, externalWithdrawal, -50.0);
        assertPerformanceFlow(connection, internalTransferIn, 25.0);
        assertPerformanceFlow(connection, internalTransferOut, -25.0);
        assertPerformanceFlow(connection, trackedTransferOut, -40.0);
        assertPerformanceFlow(connection, trackedTransferIn, 40.0);
        assertFlowSums(connection, trackedTransferOut, trackedTransferIn, 0.0, 0.0, 0.0);
        assertPerformanceFlow(connection, bookkeeping, 0.0);
        assertPerformanceFlow(connection, fxConversion, 0.0);
        assertPerformanceFlow(connection, correction, 0.0);
      } finally {
        connection.rollback();
      }
    }
  }

  private static void assertPerformanceFlow(
      Connection connection, long operationId, double expected) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT performance_flow_amount "
                + "FROM investory.normalized_cash_operation_flows "
                + "WHERE operation_id = ?")) {
      statement.setLong(1, operationId);
      try (ResultSet result = statement.executeQuery()) {
        assertTrue(result.next());
        assertEquals(expected, result.getBigDecimal(1).doubleValue(), 0.001);
      }
    }
  }

  private static long insertCashOperation(
      Connection connection, String operation, long accountId, double amount, String comment)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO investory.cash_operations "
                + "(account_id, operation, amount, currency, comment, date) "
                + "VALUES (?, ?::investory.cash_operation_type, ?, 'USD', ?, "
                + "TIMESTAMPTZ '2026-01-31 12:00:00+00') RETURNING id")) {
      statement.setLong(1, accountId);
      statement.setString(2, operation);
      statement.setDouble(3, amount);
      statement.setString(4, comment);
      try (ResultSet result = statement.executeQuery()) {
        assertTrue(result.next());
        return result.getLong(1);
      }
    }
  }

  private static long insertSubaccountTransfer(
      Connection connection, long accountId, double amount, String comment) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO investory.cash_operations "
                + "(account_id, operation, amount, currency, comment, date) "
                + "VALUES (?, 'SUBACCOUNT_TRANSFER'::investory.cash_operation_type, ?, 'USD', ?, "
                + "TIMESTAMPTZ '2026-01-31 12:00:00+00') RETURNING id")) {
      statement.setLong(1, accountId);
      statement.setDouble(2, amount);
      statement.setString(3, comment);
      try (ResultSet result = statement.executeQuery()) {
        assertTrue(result.next());
        return result.getLong(1);
      }
    }
  }

  private static void assertFlowSums(
      Connection connection, long firstOperationId, long secondOperationId,
      double expectedAccountFlow, double expectedPortfolioFlow, double expectedPerformanceFlow)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT COALESCE(SUM(account_flow_amount), 0), "
                + "COALESCE(SUM(portfolio_flow_amount), 0), "
                + "COALESCE(SUM(performance_flow_amount), 0) "
                + "FROM investory.normalized_cash_operation_flows "
                + "WHERE operation_id IN (?, ?)")) {
      statement.setLong(1, firstOperationId);
      statement.setLong(2, secondOperationId);
      try (ResultSet result = statement.executeQuery()) {
        assertTrue(result.next());
        assertEquals(expectedAccountFlow, result.getBigDecimal(1).doubleValue(), 0.001);
        assertEquals(expectedPortfolioFlow, result.getBigDecimal(2).doubleValue(), 0.001);
        assertEquals(expectedPerformanceFlow, result.getBigDecimal(3).doubleValue(), 0.001);
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
          "FREE_FUNDS_INTEREST_TAX" ->
          new BigDecimal("-1");
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
