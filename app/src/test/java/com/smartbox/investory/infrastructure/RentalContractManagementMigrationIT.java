package com.smartbox.investory.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RentalContractManagementMigrationIT {
  @Container
  private final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withDatabaseName("investory_rental_migration_test")
          .withUsername("investory_test")
          .withPassword("investory_test");

  @BeforeEach
  void resetSchema() throws Exception {
    try (var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var statement = connection.createStatement()) {
      statement.execute("DROP SCHEMA IF EXISTS investory CASCADE");
    }
  }

  @Test
  void migratesPreviousSchemaTenantFieldsAndDuplicateTermInvariant() throws Exception {
    migrate("01.022");
    long contractId;
    try (var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          INSERT INTO investory.long_term_assets
              (portfolio_id, name, asset_type, currency, current_value)
          VALUES (1, 'Migration rental', 'REAL_ESTATE', 'PLN', 100000)
          """);
      try (var result =
          statement.executeQuery(
              """
              INSERT INTO investory.long_term_asset_rental_contracts(asset_id, start_date)
              SELECT id, DATE '2026-01-01'
              FROM investory.long_term_assets
              WHERE name = 'Migration rental'
              RETURNING id
              """)) {
        result.next();
        contractId = result.getLong(1);
      }
      statement.executeUpdate(
          """
          INSERT INTO investory.long_term_asset_rental_contract_terms
              (contract_id, cash_flow_type, amount, frequency, paid_by_tenant)
          VALUES
              (%d, 'RENT', 3000, 'MONTHLY', false),
              (%d, 'RENT', 3200, 'MONTHLY', false)
          """
              .formatted(contractId, contractId));
    }

    migrate(null);

    try (var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var statement = connection.createStatement()) {
      try (var columns =
          statement.executeQuery(
              """
              SELECT count(*)
              FROM information_schema.columns
              WHERE table_schema = 'investory'
                AND table_name = 'long_term_asset_rental_contracts'
                AND column_name IN ('tenant_name', 'tenant_email', 'tenant_phone')
              """)) {
        columns.next();
        assertThat(columns.getInt(1)).isEqualTo(3);
      }
      try (var terms =
          statement.executeQuery(
              "SELECT id, amount FROM investory.long_term_asset_rental_contract_terms "
                  + "WHERE contract_id = "
                  + contractId
                  + " ORDER BY id")) {
        assertThat(terms.next()).isTrue();
        assertThat(terms.getBigDecimal("amount")).isEqualByComparingTo("6200");
        assertThat(terms.next()).isFalse();
      }
      try (var constraint =
          statement.executeQuery(
              """
              SELECT count(*)
              FROM pg_constraint
              WHERE conname = 'ux_rental_contract_term_type'
              """)) {
        constraint.next();
        assertThat(constraint.getInt(1)).isEqualTo(1);
      }
    }
  }

  @Test
  void abortsWhenDuplicateTermsHaveIncompatibleEconomicSemantics() throws Exception {
    migrate("01.022");
    try (var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          INSERT INTO investory.long_term_assets
              (portfolio_id, name, asset_type, currency, current_value)
          VALUES (1, 'Incompatible migration rental', 'REAL_ESTATE', 'PLN', 100000)
          """);
      statement.executeUpdate(
          """
          INSERT INTO investory.long_term_asset_rental_contracts(asset_id, start_date)
          SELECT id, DATE '2026-01-01'
          FROM investory.long_term_assets
          WHERE name = 'Incompatible migration rental'
          """);
      statement.executeUpdate(
          """
          INSERT INTO investory.long_term_asset_rental_contract_terms
              (contract_id, cash_flow_type, amount, frequency, paid_by_tenant)
          SELECT contract.id, 'RENT', input.amount, input.frequency, false
          FROM investory.long_term_asset_rental_contracts contract
          CROSS JOIN (VALUES (3000, 'MONTHLY'), (3200, 'ANNUAL')) input(amount, frequency)
          WHERE contract.asset_id = (
              SELECT id FROM investory.long_term_assets
              WHERE name = 'Incompatible migration rental'
          )
          """);
    }

    assertThatThrownBy(() -> migrate(null))
        .hasStackTraceContaining("Cannot normalize duplicate rental contract terms")
        .hasStackTraceContaining("frequency or payer differs");
  }

  private void migrate(String target) {
    var configuration =
        Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .schemas("investory")
            .defaultSchema("investory")
            .locations("classpath:sql/migration");
    if (target != null) {
      configuration.target(org.flywaydb.core.api.MigrationVersion.fromVersion(target));
    }
    configuration.load().migrate();
  }
}
