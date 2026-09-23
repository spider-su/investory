package com.smartbox.investory.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartbox.investory.ryczalt.application.bank.RyczaltBankImportResult;
import com.smartbox.investory.ryczalt.application.bank.RyczaltBankImportService;
import com.smartbox.investory.ryczalt.calculation.application.NativeMonthCalculationInput;
import com.smartbox.investory.ryczalt.calculation.application.NativeMonthCalculationService;
import com.smartbox.investory.ryczalt.calculation.vat.VatCalculationInput;
import com.smartbox.investory.ryczalt.calculation.zus.ZusCalculationInput;
import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import com.smartbox.investory.ryczalt.integration.bank.CsvBankTransactionSourceAdapter;
import com.smartbox.investory.ryczalt.persistence.FrozenPeriodMutationException;
import com.smartbox.investory.ryczalt.persistence.RyczaltInvoiceJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltNativeMonthInputJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltObligationJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodLifecycleService;
import com.smartbox.investory.ryczalt.persistence.RyczaltSourceReferenceJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltTransactionJpaRepository;
import com.smartbox.investory.ryczalt.settlement.RyczaltPaymentAccountResolver;
import com.smartbox.investory.ryczalt.settlement.SettlementService;
import com.smartbox.investory.testsupport.WorkerDatabase;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
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

/** Native bank acquisition writes canonical Ryczalt data and never legacy accounting rows. */
@SpringBootTest(classes = RyczaltNativeBankImportIT.TestConfiguration.class)
class RyczaltNativeBankImportIT {
  private static final WorkerDatabase DATABASE = MigrationTestDatabase.open("ryczalt_bank");
  private static final String HEADER =
      "booking_date,related_period,reference,counterparty,currency,amount,note\n";

  @Autowired private RyczaltBankImportService bankImport;
  @Autowired private NativeMonthCalculationService monthCalculation;
  @Autowired private RyczaltPeriodJpaRepository periods;
  @Autowired private RyczaltTransactionJpaRepository transactions;
  @Autowired private RyczaltSourceReferenceJpaRepository sourceReferences;
  @Autowired private JdbcTemplate jdbc;
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
  void clean() throws Exception {
    try (Connection connection = dataSource.getConnection();
        var statement = connection.createStatement()) {
      statement.execute(
          "TRUNCATE investory.ryczalt_payment_match, investory.ryczalt_source_reference,"
              + " investory.ryczalt_obligation, investory.ryczalt_transaction,"
              + " investory.ryczalt_invoice, investory.ryczalt_calculation,"
              + " investory.ryczalt_audit_event, investory.ryczalt_period RESTART IDENTITY CASCADE");
    }
  }

  private byte[] csv(String... rows) {
    return (HEADER + String.join("\n", rows)).getBytes(StandardCharsets.UTF_8);
  }

  private RyczaltBankImportResult importCsv(long profileId, String... rows) {
    return bankImport.importBank(profileId, csv(rows), "bank.csv", "text/csv");
  }

  @Test
  void importsCanonicalTransactionWithProvenanceAndNoLegacyWrite() {
    RyczaltBankImportResult result =
        importCsv(1, "2026-02-15,2026-02-01,BANK-REF-1,ACME,PLN,-498.35,ZUS");

    assertThat(result.imported()).isEqualTo(1);
    assertThat(transactions.count()).isEqualTo(1);
    var reference = sourceReferences.findAll().getFirst();
    assertThat(reference.getSource()).isEqualTo("BANK_CSV");
    assertThat(reference.getEntityType()).isEqualTo("TRANSACTION");
    assertThat(reference.getExternalId()).contains("CSV:JDG_MAIN_ACCOUNT:");
    assertThat(periods.findByProfileIdAndYearAndMonth(1, 2026, 2)).isPresent();
    assertThat(legacyBankRows()).isZero();
  }

  @Test
  void repeatImportIsIdempotent() {
    importCsv(1, "2026-02-15,2026-02-01,BANK-REF-1,ACME,PLN,-498.35,ZUS");
    RyczaltBankImportResult second =
        importCsv(1, "2026-02-15,2026-02-01,BANK-REF-1,ACME,PLN,-498.35,ZUS");

    assertThat(second.imported()).isZero();
    assertThat(second.duplicates()).isEqualTo(1);
    assertThat(transactions.count()).isEqualTo(1);
    assertThat(sourceReferences.count()).isEqualTo(1);
  }

  @Test
  void importAutomaticallySettlesMatchingObligations() {
    jdbc.update(
        "INSERT INTO investory.ryczalt_period(profile_id, period_year, period_month, status)"
            + " VALUES (1, 2026, 2, 'CALCULATED')");
    Long periodId =
        jdbc.queryForObject(
            "SELECT id FROM investory.ryczalt_period WHERE profile_id=1 AND period_year=2026 AND"
                + " period_month=2",
            Long.class);
    jdbc.update(
        "INSERT INTO investory.ryczalt_obligation(period_id, profile_id, obligation_type, amount,"
            + " currency, due_date, status) VALUES (?, 1, 'ZUS', 498.35, 'PLN', '2026-02-20',"
            + " 'OPEN')",
        periodId);

    importCsv(1, "2026-02-15,2026-02-01,BANK-REF-1,ZUS,PLN,-498.35,ZUS payment");

    Integer matches =
        jdbc.queryForObject("SELECT count(*) FROM investory.ryczalt_payment_match", Integer.class);
    String status =
        jdbc.queryForObject(
            "SELECT status FROM investory.ryczalt_obligation WHERE profile_id=1 AND"
                + " obligation_type='ZUS'",
            String.class);
    assertThat(matches).isEqualTo(1);
    assertThat(status).isEqualTo("PAID");
  }

  @Test
  void newMonthCalculatesAllTaxesCreatesObligationsAndSettlesBankPayments() {
    var result =
        monthCalculation.calculate(
            1,
            java.time.YearMonth.of(2026, 9),
            new NativeMonthCalculationInput(
                java.util.Map.of(
                    new java.math.BigDecimal("0.12"), new java.math.BigDecimal("10000")),
                new VatCalculationInput(
                    new java.math.BigDecimal("2300"),
                    java.math.BigDecimal.ZERO,
                    new java.math.BigDecimal("100")),
                new ZusCalculationInput(
                    true,
                    false,
                    "JDG",
                    false,
                    new java.math.BigDecimal("10000"),
                    new java.math.BigDecimal("2000")),
                java.math.BigDecimal.ZERO));

    assertThat(result.ryczalt()).isPositive();
    assertThat(result.vat()).isEqualByComparingTo("2200");
    assertThat(result.zus()).isPositive();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.ryczalt_calculation WHERE profile_id=1",
                Integer.class))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM investory.ryczalt_period WHERE profile_id=1"
                    + " AND period_year=2026 AND period_month=9",
                String.class))
        .isEqualTo("CALCULATED");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.ryczalt_calculation WHERE profile_id=1"
                    + " AND status='CALCULATED' AND is_current=true",
                Integer.class))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.ryczalt_obligation WHERE profile_id=1",
                Integer.class))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.ryczalt_obligation WHERE profile_id=1"
                    + " AND obligation_type IN ('RYCZALT', 'VAT', 'ZUS')",
                Integer.class))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "SELECT amount FROM investory.ryczalt_obligation WHERE profile_id=1"
                    + " AND obligation_type='RYCZALT'",
                java.math.BigDecimal.class))
        .isEqualByComparingTo(result.ryczalt());
    assertThat(
            jdbc.queryForObject(
                "SELECT amount FROM investory.ryczalt_obligation WHERE profile_id=1"
                    + " AND obligation_type='VAT'",
                java.math.BigDecimal.class))
        .isEqualByComparingTo(result.vat());
    assertThat(
            jdbc.queryForObject(
                "SELECT amount FROM investory.ryczalt_obligation WHERE profile_id=1"
                    + " AND obligation_type='ZUS'",
                java.math.BigDecimal.class))
        .isEqualByComparingTo(result.zus());

    importCsv(
        1,
        "2026-09-10,2026-09-01,PPE-SEP,Tax Office,PLN,-"
            + result.ryczalt().toPlainString()
            + ",PPE ryczalt payment",
        "2026-09-11,2026-09-01,VAT-SEP,Tax Office,PLN,-"
            + result.vat().toPlainString()
            + ",VAT payment",
        "2026-09-12,2026-09-01,ZUS-SEP,ZUS,PLN,-" + result.zus().toPlainString() + ",ZUS payment");

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.ryczalt_payment_match", Integer.class))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.ryczalt_obligation WHERE profile_id=1 AND status='PAID'",
                Integer.class))
        .isEqualTo(3);
  }

  @Test
  void importTreatsSmallUnderpaymentWithinToleranceAsPaid() {
    jdbc.update(
        "INSERT INTO investory.ryczalt_period(profile_id, period_year, period_month, status)"
            + " VALUES (1, 2026, 2, 'CALCULATED')");
    Long periodId =
        jdbc.queryForObject(
            "SELECT id FROM investory.ryczalt_period WHERE profile_id=1 AND period_year=2026 AND"
                + " period_month=2",
            Long.class);
    jdbc.update(
        "INSERT INTO investory.ryczalt_obligation(period_id, profile_id, obligation_type, amount,"
            + " currency, due_date, status) VALUES (?, 1, 'ZUS', 1495.04, 'PLN', '2026-02-20',"
            + " 'OPEN')",
        periodId);

    importCsv(1, "2026-02-15,2026-02-01,BANK-REF-1,ZUS,PLN,-1495.00,ZUS payment");

    String status =
        jdbc.queryForObject(
            "SELECT status FROM investory.ryczalt_obligation WHERE profile_id=1 AND"
                + " obligation_type='ZUS'",
            String.class);
    assertThat(status).isEqualTo("PAID");
  }

  @Test
  void duplicateHumanReferenceDoesNotCollapseDistinctTransactions() {
    RyczaltBankImportResult result =
        importCsv(
            1,
            "2026-02-15,2026-02-01,SHARED-REF,ACME,PLN,-100.00,first",
            "2026-02-16,2026-02-01,SHARED-REF,BETA,PLN,-200.00,second");

    assertThat(result.imported()).isEqualTo(2);
    assertThat(transactions.count()).isEqualTo(2);
    assertThat(sourceReferences.count()).isEqualTo(2);
  }

  @Test
  void profileIsolationKeepsImportsSeparate() {
    importCsv(1, "2026-02-15,2026-02-01,BANK-REF-1,ACME,PLN,-498.35,ZUS");
    importCsv(2, "2026-02-15,2026-02-01,BANK-REF-1,ACME,PLN,-498.35,ZUS");

    assertThat(transactions.findAll())
        .extracting(
            com.smartbox.investory.ryczalt.persistence.RyczaltTransactionEntity::getProfileId)
        .containsExactlyInAnyOrder(1L, 2L);
    assertThat(sourceReferences.count()).isEqualTo(2);
  }

  @Test
  void frozenPeriodRejectsImportAndKeepsHistoryIntact() {
    jdbc.update(
        "INSERT INTO investory.ryczalt_period(profile_id, period_year, period_month, status)"
            + " VALUES (1, 2026, 2, 'FROZEN')");

    assertThatThrownBy(() -> importCsv(1, "2026-02-15,2026-02-01,BANK-REF-1,ACME,PLN,-498.35,ZUS"))
        .isInstanceOf(FrozenPeriodMutationException.class);
    assertThat(transactions.count()).isZero();
    assertThat(sourceReferences.count()).isZero();
  }

  @Test
  void importDoesNotOverDirtyAlreadyCalculatedPeriod() {
    jdbc.update(
        "INSERT INTO investory.ryczalt_period(profile_id, period_year, period_month, status)"
            + " VALUES (1, 2026, 2, 'CALCULATED')");

    importCsv(1, "2026-02-15,2026-02-01,BANK-REF-1,ACME,PLN,-498.35,ZUS");

    RyczaltPeriodEntity period = periods.findByProfileIdAndYearAndMonth(1, 2026, 2).orElseThrow();
    assertThat(period.getStatus()).isEqualTo(PeriodStatus.CALCULATED);
    assertThat(transactions.count()).isEqualTo(1);
    Integer audits =
        jdbc.queryForObject(
            "SELECT count(*) FROM investory.ryczalt_audit_event WHERE profile_id=1 AND"
                + " event_type='CALCULATION_INVALIDATED'",
            Integer.class);
    assertThat(audits).isEqualTo(1);
  }

  private int legacyBankRows() {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM investory.accounting_poc_bank_transaction", Integer.class);
    return count == null ? 0 : count;
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = RyczaltPeriodEntity.class)
  @EnableJpaRepositories(
      basePackageClasses = {
        RyczaltPeriodJpaRepository.class,
        RyczaltInvoiceJpaRepository.class,
        RyczaltNativeMonthInputJpaRepository.class,
        RyczaltTransactionJpaRepository.class,
        RyczaltSourceReferenceJpaRepository.class,
        RyczaltObligationJpaRepository.class,
        com.smartbox.investory.ryczalt.persistence.RyczaltPaymentMatchJpaRepository.class,
        com.smartbox.investory.ryczalt.persistence.RyczaltCalculationJpaRepository.class
      })
  @Import({
    RyczaltBankImportService.class,
    NativeMonthCalculationService.class,
    com.smartbox.investory.ryczalt.calculation.application.NativeMonthInputAggregator.class,
    com.smartbox.investory.ryczalt.persistence.RyczaltCalculationPersistenceAdapter.class,
    CsvBankTransactionSourceAdapter.class,
    RyczaltPeriodLifecycleService.class,
    com.smartbox.investory.ryczalt.persistence.JdbcRyczaltAuditEventWriter.class,
    com.smartbox.investory.ryczalt.persistence.JdbcRyczaltPaymentAccountRulesReader.class,
    SettlementService.class,
    RyczaltPaymentAccountResolver.class
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
