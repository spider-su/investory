package com.smartbox.investory.testsupport;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Shared Postgres Naming")
class SharedPostgresNamingTest {

  @DisplayName("worker Names Are Deterministic And Isolated")
  @Test
  void workerNamesAreDeterministicAndIsolated() {
    String oldRun = System.getProperty(SharedPostgres.RUN_ID_PROPERTY);
    String oldPrefix = System.getProperty(SharedPostgres.PREFIX_PROPERTY);
    try {
      System.setProperty(SharedPostgres.RUN_ID_PROPERTY, "ci-42");
      System.setProperty(SharedPostgres.PREFIX_PROPERTY, "it");
      assertTrue(SharedPostgres.databaseNameFor("1", null).startsWith("it_ci_42_w_1"));
      assertNotEquals(
          SharedPostgres.databaseNameFor("1", null), SharedPostgres.databaseNameFor("2", null));
      assertNotEquals(
          SharedPostgres.databaseNameFor("1", "migration"),
          SharedPostgres.databaseNameFor("1", "golden"));
    } finally {
      if (oldRun == null) System.clearProperty(SharedPostgres.RUN_ID_PROPERTY);
      else System.setProperty(SharedPostgres.RUN_ID_PROPERTY, oldRun);
      if (oldPrefix == null) System.clearProperty(SharedPostgres.PREFIX_PROPERTY);
      else System.setProperty(SharedPostgres.PREFIX_PROPERTY, oldPrefix);
    }
  }
}
