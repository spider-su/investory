package com.smartbox.investory.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.smartbox.investory.testsupport.FastDatabase;
import com.smartbox.investory.testsupport.WorkerDatabase;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Proves unrelated portfolio rows do not change HappyInvestor portfolio reporting. */
class HappyInvestorSharedDatabaseIsolationIT {
  private static final WorkerDatabase DATABASE =
      FastDatabase.scopedDatabase("happyinvestor_shared_database_isolation");

  @AfterAll
  static void closeDatabase() {
    DATABASE.close();
  }

  @Test
  void unrelatedPortfolioDataDoesNotChangeHappyInvestorFacts() throws Exception {
    try (Connection connection = DATABASE.openConnection()) {
      connection.setAutoCommit(false);
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/snapshot/happyinvestor-common.sql"));
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/snapshot/happyinvestor-broker.sql"));
      refresh(connection);

      Map<String, BigDecimal> before = happyInvestorFacts(connection);
      insertUnrelatedPortfolio(connection);
      refresh(connection);
      Map<String, BigDecimal> after = happyInvestorFacts(connection);

      assertEquals(before, after, "portfolio 1-style data must not alter portfolio 2 reporting");
      connection.rollback();
    }
  }

  private static Map<String, BigDecimal> happyInvestorFacts(Connection connection)
      throws Exception {
    Map<String, BigDecimal> facts = new LinkedHashMap<>();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT total_market_value, total_equity FROM investory.app_v_portfolio_kpi_summary_mv "
                + "WHERE portfolio_id = 2")) {
      try (ResultSet result = statement.executeQuery()) {
        assertNotNull(result);
        if (!result.next()) throw new AssertionError("HappyInvestor portfolio summary is missing");
        facts.put("total_market_value", result.getBigDecimal("total_market_value"));
        facts.put("total_equity", result.getBigDecimal("total_equity"));
      }
    }
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT total_value_in_base_currency FROM investory.app_v_portfolio_asset_allocation "
                + "WHERE portfolio_id = 2 AND asset_symbol = 'AAPL.US'")) {
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) throw new AssertionError("HappyInvestor AAPL allocation is missing");
        facts.put("aapl_value", result.getBigDecimal(1));
      }
    }
    return facts;
  }

  private static void insertUnrelatedPortfolio(Connection connection) throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO investory.portfolios(id, name, base_currency, local_currency, owner, user_id) "
                + "VALUES (991001, 'Unrelated portfolio', 'PLN', 'PLN', 'unrelated', 1) "
                + "ON CONFLICT (id) DO NOTHING")) {
      statement.executeUpdate();
    }
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO investory.accounts(id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only) "
                + "VALUES (991001, '991001', 'PLN', 'XTB', 'Unrelated account', 'unrelated', 991001, true) "
                + "ON CONFLICT (id) DO NOTHING")) {
      statement.executeUpdate();
    }
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO investory.cash_operations(id, account_id, operation, amount, currency, comment, date) "
                + "VALUES (991001, 991001, 'DEPOSIT', 999999, 'PLN', 'unrelated fixture', TIMESTAMP '2026-09-09 12:00:00') "
                + "ON CONFLICT (id) DO NOTHING")) {
      statement.executeUpdate();
    }
  }

  private static void refresh(Connection connection) throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT investory.refresh_app_views()")) {
      statement.execute();
    }
  }
}
