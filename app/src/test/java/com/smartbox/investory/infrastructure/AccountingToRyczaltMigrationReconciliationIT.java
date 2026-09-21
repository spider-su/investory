package com.smartbox.investory.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.ryczalt.migration.RyczaltMigrationReport;
import com.smartbox.investory.ryczalt.migration.RyczaltMigrationService;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodEntity;
import com.smartbox.investory.testsupport.WorkerDatabase;
import java.math.BigDecimal;
import java.sql.Connection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Strict persisted-fact certification for the one-time Accounting -> Ryczalt import. */
@SpringBootTest(classes = AccountingToRyczaltMigrationReconciliationIT.TestConfiguration.class)
class AccountingToRyczaltMigrationReconciliationIT {
  private static final WorkerDatabase DATABASE =
      MigrationTestDatabase.open("ryczalt_reconciliation");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private RyczaltMigrationService migration;

  @BeforeAll
  static void migrateSchema() {
    MigrationTestDatabase.migrate(DATABASE);
  }

  @AfterAll
  static void closeDatabase() {
    DATABASE.close();
  }

  @BeforeEach
  void cleanAndSeed() throws Exception {
    try (Connection connection = MigrationTestDatabase.connection(DATABASE);
        var statement = connection.createStatement()) {
      statement.execute(
          "TRUNCATE investory.ryczalt_payment_match, investory.ryczalt_source_reference,"
              + " investory.ryczalt_obligation, investory.ryczalt_transaction,"
              + " investory.ryczalt_invoice, investory.ryczalt_calculation,"
              + " investory.ryczalt_period RESTART IDENTITY CASCADE");
      statement.execute("DELETE FROM investory.accounting_calculation_snapshot WHERE profile_id=1");
      statement.execute("DELETE FROM investory.accounting_poc_period_state WHERE profile_id=1");
      statement.execute("DELETE FROM investory.accounting_poc_tax_input WHERE profile_id=1");
      statement.execute("DELETE FROM investory.accounting_poc_invoice WHERE profile_id=1");
      statement.execute("DELETE FROM investory.accounting_poc_expense_invoice WHERE profile_id=1");
      statement.execute("DELETE FROM investory.accounting_poc_bank_transaction WHERE profile_id=1");
      statement.execute("DELETE FROM investory.accounting_poc_obligation WHERE profile_id=1");
    }
    jdbc.update(
        "INSERT INTO investory.accounting_poc_period_state(profile_id,tax_period,lifecycle_status,confirmed_at)"
            + " VALUES (1,DATE '2026-01-01','LOCKED',TIMESTAMPTZ '2026-09-19 12:00:00+02')");
    jdbc.update(
        "INSERT INTO investory.accounting_poc_invoice(profile_id,tax_period,issue_date,sale_date,reference,customer_alias,invoice_kind,currency,net_amount,vat_amount,gross_amount,expected_receivable,booked_net_pln,ryczalt_rate)"
            + " VALUES (1,DATE '2026-01-01',DATE '2026-01-10',DATE '2026-01-10','CERT-INCOME','Customer','SALE','PLN',1000,230,1230,1230,1000,.12)");
    jdbc.update(
        "INSERT INTO investory.accounting_poc_expense_invoice(profile_id,tax_period,invoice_date,reference,supplier_alias,category,currency,net_amount,vat_amount,gross_amount,vat_deduction_ratio,source_quality)"
            + " VALUES (1,DATE '2026-01-01',DATE '2026-01-11','CERT-COST','Supplier','OFFICE','PLN',100,23,123,.5,'CERT')");
    jdbc.update(
        "INSERT INTO investory.accounting_poc_bank_transaction(profile_id,booking_date,related_period,reference,counterparty_alias,currency,amount,transaction_type,scope,note,provider,external_account_id,external_transaction_id)"
            + " VALUES (1,DATE '2026-01-15',DATE '2026-01-01',NULL,'Bank','PLN',-123,'ZUS_PAYMENT','BUSINESS','certification transaction','CERTIFICATION_BANK','account-1','transaction-1')");
    jdbc.update(
        "INSERT INTO investory.accounting_poc_tax_input(profile_id,tax_period,input_type,amount,note)"
            + " VALUES (1,DATE '2026-01-01','HEALTH_CONTRIBUTION_PAID',100,'input is not an obligation')");
    jdbc.update(
        "INSERT INTO investory.accounting_calculation_snapshot(profile_id,tax_period,schema_version,payload,calculation_hash,calculated_at)"
            + " VALUES (1,DATE '2026-01-01',1,?::jsonb,? ,TIMESTAMPTZ '2026-09-19 12:30:00+02')",
        "{\"ryczalt\":{\"calculatedTax\":123},\"vat\":{\"calculatedVat\":45},\"zus\":{\"totalZus\":99}}",
        "certification-hash");
  }

  @Test
  void certifiesEveryMigratedFactAgainstItsLegacySource() {
    RyczaltMigrationReport first = migration.migrate(1);
    RyczaltMigrationReport second = migration.migrate(1);

    assertThat(first.incomeInvoices()).isEqualTo(sourceCount("accounting_poc_invoice"));
    assertThat(first.costInvoices()).isEqualTo(sourceCount("accounting_poc_expense_invoice"));
    assertThat(first.transactions()).isEqualTo(sourceCount("accounting_poc_bank_transaction"));
    assertThat(first.obligations()).isZero();
    assertThat(second.sourceReferences()).as(report("idempotency", second)).isZero();

    assertThat(count("SELECT count(*) FROM investory.ryczalt_period WHERE profile_id=1"))
        .as(report("periods", first))
        .isEqualTo(
            count(
                "SELECT count(*) FROM investory.accounting_calculation_snapshot WHERE profile_id=1"));
    assertThat(count("SELECT count(*) FROM investory.ryczalt_invoice WHERE profile_id=1"))
        .as(report("invoices", first))
        .isEqualTo(2);
    assertThat(count("SELECT count(*) FROM investory.ryczalt_transaction WHERE profile_id=1"))
        .as(report("transactions", first))
        .isEqualTo(1);
    assertThat(count("SELECT count(*) FROM investory.ryczalt_calculation WHERE profile_id=1"))
        .as(report("calculations", first))
        .isEqualTo(3);
    assertThat(count("SELECT count(*) FROM investory.ryczalt_obligation WHERE profile_id=1"))
        .as(report("obligations", first))
        .isEqualTo(3);

    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM investory.ryczalt_period WHERE profile_id=1 AND period_year=2026 AND period_month=1",
                String.class))
        .isEqualTo("FROZEN");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.ryczalt_calculation WHERE profile_id=1 AND status='FROZEN'",
                Integer.class))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.ryczalt_obligation WHERE profile_id=1 AND status='FROZEN' AND calculation_id IS NOT NULL",
                Integer.class))
        .isEqualTo(3);

    assertThat(
            jdbc.queryForObject(
                "SELECT result_json->>'calculatedTax' FROM investory.ryczalt_calculation c JOIN investory.ryczalt_period p ON p.id=c.period_id WHERE c.profile_id=1 AND c.calculation_type='RYCZALT'",
                String.class))
        .isEqualTo("123");
    assertThat(
            jdbc.queryForObject(
                "SELECT result_json->>'calculatedVat' FROM investory.ryczalt_calculation c JOIN investory.ryczalt_period p ON p.id=c.period_id WHERE c.profile_id=1 AND c.calculation_type='VAT'",
                String.class))
        .isEqualTo("45");
    assertThat(
            jdbc.queryForObject(
                "SELECT result_json->>'totalZus' FROM investory.ryczalt_calculation c JOIN investory.ryczalt_period p ON p.id=c.period_id WHERE c.profile_id=1 AND c.calculation_type='ZUS'",
                String.class))
        .isEqualTo("99");

    assertThat(
            jdbc.queryForObject(
                "SELECT net_amount FROM investory.ryczalt_invoice WHERE direction='COST' AND reference='CERT-COST'",
                BigDecimal.class))
        .isEqualByComparingTo("100");
    assertThat(
            jdbc.queryForObject(
                "SELECT deductible_vat FROM investory.ryczalt_invoice WHERE direction='COST' AND reference='CERT-COST'",
                BigDecimal.class))
        .isEqualByComparingTo("11.5");
    assertThat(
            jdbc.queryForObject(
                "SELECT reference FROM investory.ryczalt_transaction WHERE profile_id=1",
                String.class))
        .isEqualTo(
            "legacy-bank-"
                + jdbc.queryForObject(
                    "SELECT id FROM investory.accounting_poc_bank_transaction WHERE profile_id=1",
                    Long.class));

    assertThat(
            count(
                "SELECT count(*) FROM investory.ryczalt_source_reference WHERE profile_id=1 AND source='ACCOUNTING_POC_INVOICE'"))
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM investory.ryczalt_source_reference WHERE profile_id=1 AND source='ACCOUNTING_POC_EXPENSE_INVOICE'"))
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM investory.ryczalt_source_reference WHERE profile_id=1 AND source='ACCOUNTING_POC_BANK_TRANSACTION'"))
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM investory.ryczalt_source_reference WHERE profile_id=1 AND source='ACCOUNTING_CALCULATION_SNAPSHOT'"))
        .isEqualTo(6);
    assertThat(
            count(
                "SELECT count(*) FROM investory.ryczalt_source_reference WHERE profile_id=1 AND source='ACCOUNTING_POC_TAX_INPUT'"))
        .isZero();

    assertThat(count("SELECT count(*) FROM investory.ryczalt_payment_match WHERE profile_id=1"))
        .isZero();
    assertThat(count("SELECT count(*) FROM investory.ryczalt_fx_rate")).isZero();
  }

  private int sourceCount(String table) {
    return count("SELECT count(*) FROM investory." + table + " WHERE profile_id=1");
  }

  private int count(String sql) {
    return jdbc.queryForObject(sql, Integer.class);
  }

  private String report(String section, RyczaltMigrationReport report) {
    return "Accounting -> Ryczalt migration reconciliation\n"
        + section
        + ": periods="
        + report.periods()
        + ", income="
        + report.incomeInvoices()
        + ", costs="
        + report.costInvoices()
        + ", transactions="
        + report.transactions()
        + ", obligations="
        + report.obligations()
        + ", sourceReferences="
        + report.sourceReferences();
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = RyczaltPeriodEntity.class)
  @EnableJpaRepositories(basePackages = "com.smartbox.investory.ryczalt.persistence")
  @Import(RyczaltMigrationService.class)
  static class TestConfiguration {}

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::jdbcUrl);
    registry.add("spring.datasource.username", DATABASE::username);
    registry.add("spring.datasource.password", DATABASE::password);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    registry.add("spring.flyway.enabled", () -> "false");
  }
}
