package com.smartbox.investory.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.smartbox.investory.testsupport.FastDatabase;
import com.smartbox.investory.testsupport.WorkerDatabase;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorLongTermFacts;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Executable contract for safely reapplying the canonical non-broker overlay. */
class HappyInvestorOverlayIdempotencyIT {
  private static final WorkerDatabase DATABASE =
      FastDatabase.scopedDatabase("happyinvestor_overlay_idempotency");

  @AfterAll
  static void closeDatabase() {
    DATABASE.close();
  }

  @Test
  void reapplicationRestoresEveryMutableCanonicalColumnAndRemainsStable() throws Exception {
    try (Connection connection = DATABASE.openConnection()) {
      connection.setAutoCommit(false);
      execute(connection, "UPDATE investory.long_term_assets SET tax_base = 1 WHERE id = 9402");
      execute(
          connection,
          "UPDATE investory.long_term_asset_rental_contracts "
              + "SET monthly_tax_base = 1 WHERE id = 9501");
      execute(
          connection,
          "UPDATE investory.simulation_plan_revisions "
              + "SET baseline_long_term_state = '{\"drift\":true}'::jsonb WHERE id = 9202");

      applyOverlay(connection);
      assertCanonicalValues(connection);
      applyOverlay(connection);
      assertCanonicalValues(connection);
      connection.rollback();
    }
  }

  private static void applyOverlay(Connection connection) {
    ScriptUtils.executeSqlScript(
        connection, new ClassPathResource("db/snapshot/happyinvestor-common.sql"));
  }

  private static void assertCanonicalValues(Connection connection) throws Exception {
    assertEquals(
        0,
        decimal(connection, "SELECT tax_base FROM investory.long_term_assets WHERE id = 9402")
            .compareTo(HappyInvestorLongTermFacts.APARTMENT_A_MONTHLY_TAX_BASE));
    assertEquals(
        0,
        decimal(
                connection,
                "SELECT monthly_tax_base FROM investory.long_term_asset_rental_contracts "
                    + "WHERE id = 9501")
            .compareTo(HappyInvestorLongTermFacts.APARTMENT_A_MONTHLY_TAX_BASE));
    assertNull(
        object(
            connection,
            "SELECT baseline_long_term_state FROM investory.simulation_plan_revisions "
                + "WHERE id = 9202"));
  }

  private static void execute(Connection connection, String sql) throws Exception {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.executeUpdate();
    }
  }

  private static BigDecimal decimal(Connection connection, String sql) throws Exception {
    return (BigDecimal) object(connection, sql);
  }

  private static Object object(Connection connection, String sql) throws Exception {
    try (PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet result = statement.executeQuery()) {
      if (!result.next()) throw new AssertionError("Canonical fixture row is missing: " + sql);
      return result.getObject(1);
    }
  }
}
