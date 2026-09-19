package com.smartbox.investory.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartbox.investory.ryczalt.application.bank.RyczaltBankImportResult;
import com.smartbox.investory.ryczalt.application.bank.RyczaltBankImportService;
import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import com.smartbox.investory.ryczalt.integration.bank.CsvBankTransactionSourceAdapter;
import com.smartbox.investory.ryczalt.persistence.FrozenPeriodMutationException;
import com.smartbox.investory.ryczalt.persistence.RyczaltObligationJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodLifecycleService;
import com.smartbox.investory.ryczalt.persistence.RyczaltSourceReferenceJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltTransactionJpaRepository;
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
        .extracting(com.smartbox.investory.ryczalt.persistence.RyczaltTransactionEntity::getProfileId)
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
        RyczaltTransactionJpaRepository.class,
        RyczaltSourceReferenceJpaRepository.class,
        RyczaltObligationJpaRepository.class,
        com.smartbox.investory.ryczalt.persistence.RyczaltCalculationJpaRepository.class
      })
  @Import({
    RyczaltBankImportService.class,
    CsvBankTransactionSourceAdapter.class,
    RyczaltPeriodLifecycleService.class
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
