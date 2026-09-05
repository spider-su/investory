package com.smartbox.investory.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.testsupport.WorkerDatabase;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Proves the explicit Long-Term tables and relationships. */
class LongTermExplicitMigrationIT {
  private static final WorkerDatabase DATABASE = MigrationTestDatabase.open("long_term_explicit");

  @BeforeAll
  static void migrateExplicitData() throws Exception {
    MigrationTestDatabase.migrate(DATABASE);
    try (Connection connection = MigrationTestDatabase.connection(DATABASE);
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          INSERT INTO investory.bond
              (id, portfolio_id, name, currency, value, acquisition_date, interest_rate, maturity_date, notes)
          VALUES (9405, 1, 'Treasury 2026', 'PLN', 10000, DATE '2024-07-31', 0.04625, DATE '2026-02-28', 'Happy Investor canonical fixed income');
          INSERT INTO investory.cash_reserve
              (id, portfolio_id, name, currency, value, acquisition_date, interest_rate, maturity_date, notes)
          VALUES (9406, 1, 'Term cash reserve', 'PLN', 50000, DATE '2024-08-01', 0.04, DATE '2027-08-01', 'Happy Investor interest-bearing cash reserve');
          INSERT INTO investory.real_estate
              (id, portfolio_id, name, currency, value, tax_base, acquisition_date, land_register_number, notes)
          VALUES (9402, 1, 'Apartment A', 'PLN', 400000, 3200, DATE '2024-08-01', 'KR1P/4322432/0', 'Happy Investor canonical profile');
          INSERT INTO investory.cash_reserve
              (id, portfolio_id, name, currency, value, acquisition_date, notes)
          VALUES (9401, 1, 'Cash reserve', 'PLN', 50000, DATE '2024-08-01', 'Happy Investor canonical profile');
          INSERT INTO investory.personal_asset
              (id, portfolio_id, name, category, currency, value, acquisition_date, notes)
          VALUES (9404, 1, 'Family Car', 'VEHICLE', 'PLN', 10000, DATE '2024-08-01', 'Happy Investor canonical profile');
          INSERT INTO investory.rental_contract
              (id, real_estate_id, start_date, notes)
          VALUES (9501, 9402, DATE '2024-08-01', 'Happy Investor canonical profile');
          INSERT INTO investory.rental_contract_term
              (rental_contract_id, cash_flow_type, amount, frequency, paid_by_tenant)
          VALUES (9501, 'RENT', 3200, 'MONTHLY', false);
          """);
    }
  }

  @AfterAll
  static void closeDatabase() {
    DATABASE.close();
  }

  @Test
  void persistsExplicitAssetFactsAndRelationships() throws Exception {
    try (Connection connection = MigrationTestDatabase.connection(DATABASE);
        Statement statement = connection.createStatement()) {
      assertEquals(
          1,
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM investory.bond WHERE id = 9405 AND value = 10000 AND interest_rate = 0.04625 AND acquisition_date = DATE '2024-07-31'"));
      assertEquals(
          1,
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM investory.cash_reserve WHERE id = 9406 AND value = 50000 AND interest_rate = 0.04 AND maturity_date = DATE '2027-08-01'"));
      assertEquals(
          1,
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM investory.real_estate WHERE id = 9402 AND land_register_number = 'KR1P/4322432/0' AND tax_base = 3200"));
      assertEquals(
          1,
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM investory.cash_reserve WHERE id = 9401 AND value = 50000 AND acquisition_date = DATE '2024-08-01'"));
      assertEquals(
          1,
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM investory.personal_asset WHERE id = 9404 AND category = 'VEHICLE'"));
      assertEquals(
          1,
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM investory.rental_contract c JOIN investory.rental_contract_term t "
                  + "ON t.rental_contract_id = c.id WHERE c.id = 9501 AND c.real_estate_id = 9402 "
                  + "AND t.amount = 3200 AND t.frequency = 'MONTHLY'"));
    }
  }
}
