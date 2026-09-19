package com.smartbox.investory.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import com.smartbox.investory.ryczalt.migration.RyczaltMigrationReport;
import com.smartbox.investory.ryczalt.migration.RyczaltMigrationService;
import com.smartbox.investory.ryczalt.persistence.RyczaltCalculationJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltCalculationPersistenceAdapter;
import com.smartbox.investory.ryczalt.persistence.RyczaltFxRateEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltFxRateJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltInvoiceJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltObligationJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPersistenceAdapter;
import com.smartbox.investory.ryczalt.persistence.RyczaltSourceReferenceJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltTransactionJpaRepository;
import com.smartbox.investory.testsupport.WorkerDatabase;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import javax.sql.DataSource;
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

/** PostgreSQL contract for the new schema and controlled legacy import. */
@SpringBootTest(classes = RyczaltPersistenceMigrationIT.TestConfiguration.class)
class RyczaltPersistenceMigrationIT {
  private static final WorkerDatabase DATABASE = MigrationTestDatabase.open("ryczalt_stage3");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private RyczaltMigrationService migration;
  @Autowired private RyczaltPersistenceAdapter adapter;
  @Autowired private RyczaltPeriodJpaRepository periods;
  @Autowired private RyczaltInvoiceJpaRepository invoices;
  @Autowired private RyczaltTransactionJpaRepository transactions;
  @Autowired private RyczaltObligationJpaRepository obligations;
  @Autowired private RyczaltSourceReferenceJpaRepository sourceReferences;
  @Autowired private RyczaltCalculationJpaRepository calculations;
  @Autowired private RyczaltCalculationPersistenceAdapter calculationAdapter;
  @Autowired private RyczaltFxRateJpaRepository fxRates;
  @Autowired private DataSource dataSource;

  @BeforeAll
  static void migrateSchema() {
    MigrationTestDatabase.migrate(DATABASE);
  }

  @AfterAll
  static void closeDatabase() {
    DATABASE.close();
  }

  @BeforeEach
  void cleanStage3Tables() throws Exception {
    try (Connection connection = dataSource.getConnection();
        var statement = connection.createStatement()) {
      statement.execute(
          "TRUNCATE investory.ryczalt_source_reference, investory.ryczalt_obligation,"
              + " investory.ryczalt_transaction, investory.ryczalt_invoice,"
              + " investory.ryczalt_calculation, investory.ryczalt_period RESTART IDENTITY"
              + " CASCADE");
      statement.execute("DELETE FROM investory.accounting_poc_invoice WHERE profile_id=1");
      statement.execute("DELETE FROM investory.accounting_poc_expense_invoice WHERE profile_id=1");
      statement.execute("DELETE FROM investory.accounting_poc_bank_transaction WHERE profile_id=1");
      statement.execute("DELETE FROM investory.accounting_poc_obligation WHERE profile_id=1");
    }
  }

  @Test
  void importsHappyInvestorLikeFactsLoadsCanonicalPeriodAndIsIdempotent() {
    jdbc.update(
        """
        INSERT INTO investory.accounting_poc_invoice
            (profile_id, tax_period, issue_date, sale_date, reference, customer_alias, invoice_kind, currency, net_amount, vat_amount, gross_amount, expected_receivable, booked_net_pln, ryczalt_rate)
        VALUES (1, DATE '2026-02-01', DATE '2026-02-10', DATE '2026-02-10', 'HI-STAGE3-1', 'customer', 'SALE', 'PLN', 29600, 6808, 36408, 36408, 29600, .12)
        """);
    jdbc.update(
        """
        INSERT INTO investory.accounting_poc_expense_invoice
            (profile_id, tax_period, invoice_date, reference, supplier_alias, category, currency, net_amount, vat_amount, gross_amount, vat_deduction_ratio, source_quality)
        VALUES (1, DATE '2026-02-01', DATE '2026-02-12', 'HI-STAGE3-COST', 'supplier', 'OFFICE', 'PLN', 298, 68.54, 366.54, 1.00, 'FIXTURE')
        """);
    jdbc.update(
        """
        INSERT INTO investory.accounting_poc_bank_transaction
            (profile_id, booking_date, related_period, reference, counterparty_alias, currency, amount, transaction_type, scope, provider, external_account_id, external_transaction_id)
        VALUES (1, DATE '2026-02-15', DATE '2026-02-01', 'HI-STAGE3-BANK', 'bank', 'PLN', -498.35, 'ZUS_PAYMENT', 'BUSINESS', 'FIXTURE', 'ACCOUNT', 'HI-STAGE3-BANK')
        """);
    jdbc.update(
        """
        INSERT INTO investory.accounting_poc_obligation
            (profile_id, tax_period, obligation_type, expected_amount, status)
        VALUES (1, DATE '2026-02-01', 'RYCZALT', 3552, 'OPEN')
        """);

    RyczaltMigrationReport first = migration.migrate(1);
    RyczaltMigrationReport second = migration.migrate(1);

    assertThat(first.incomeInvoices()).isEqualTo(1);
    assertThat(first.costInvoices()).isEqualTo(1);
    assertThat(first.transactions()).isEqualTo(1);
    assertThat(first.obligations()).isEqualTo(1);
    assertThat(periods.count()).isEqualTo(1);
    assertThat(invoices.count()).isEqualTo(2);
    assertThat(transactions.count()).isEqualTo(1);
    assertThat(obligations.count()).isEqualTo(1);
    assertThat(sourceReferences.count()).isEqualTo(4);
    assertThat(second.sourceReferences()).isEqualTo(0);
    assertThat(adapter.load(1, YearMonth.of(2026, 2)))
        .get()
        .satisfies(
            period -> {
              assertThat(period.status()).isEqualTo(PeriodStatus.OPEN);
              assertThat(period.incomeInvoices().meta().count()).isEqualTo(1);
              assertThat(period.costInvoices().meta().count()).isEqualTo(1);
              assertThat(period.transactions().meta().count()).isEqualTo(1);
              assertThat(period.obligations().meta().count()).isEqualTo(1);
              assertThat(period.incomeInvoices().items().getFirst().bookedNetPln())
                  .isEqualByComparingTo("29600");
            });
    assertThat(adapter.load(2, YearMonth.of(2026, 2))).isEmpty();

    RyczaltPeriodEntity period = periods.findByProfileIdAndYearAndMonth(1, 2026, 2).orElseThrow();
    calculationAdapter.saveCurrent(
        period,
        1,
        com.smartbox.investory.ryczalt.persistence.CalculationType.RYCZALT,
        "{\"tax\":3552}",
        "stage3-fingerprint",
        "RYCZALT_2026_POC_V1",
        "RyczaltCalculator-1");
    fxRates.save(
        new RyczaltFxRateEntity(
            "EUR",
            LocalDate.of(2026, 2, 1),
            new BigDecimal("4.20"),
            "FIXTURE",
            "HI-STAGE3-FX",
            Instant.now()));
    assertThat(calculations.count()).isEqualTo(1);
    assertThat(
            fxRates.findByProviderAndCurrencyAndEffectiveDate(
                "FIXTURE", "EUR", LocalDate.of(2026, 2, 1)))
        .isPresent();
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = RyczaltPeriodEntity.class)
  @EnableJpaRepositories(
      basePackageClasses = {
        RyczaltPeriodJpaRepository.class,
        RyczaltInvoiceJpaRepository.class,
        RyczaltTransactionJpaRepository.class,
        RyczaltObligationJpaRepository.class,
        RyczaltSourceReferenceJpaRepository.class,
        RyczaltCalculationJpaRepository.class,
        RyczaltFxRateJpaRepository.class
      })
  @Import({
    RyczaltMigrationService.class,
    RyczaltPersistenceAdapter.class,
    RyczaltCalculationPersistenceAdapter.class
  })
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
