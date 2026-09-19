package com.smartbox.investory.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartbox.investory.ryczalt.application.ksef.RyczaltKsefImportService;
import com.smartbox.investory.ryczalt.application.ksef.RyczaltKsefSyncResult;
import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import com.smartbox.investory.ryczalt.integration.ksef.InvoiceSourcePort;
import com.smartbox.investory.ryczalt.integration.ksef.InvoiceSourceRecord;
import com.smartbox.investory.ryczalt.integration.ksef.KsefSyncMode;
import com.smartbox.investory.ryczalt.persistence.FrozenPeriodMutationException;
import com.smartbox.investory.ryczalt.persistence.InvoiceDirection;
import com.smartbox.investory.ryczalt.persistence.RyczaltInvoiceEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltInvoiceJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltObligationJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodLifecycleService;
import com.smartbox.investory.ryczalt.persistence.RyczaltSourceReferenceJpaRepository;
import com.smartbox.investory.testsupport.WorkerDatabase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Native KSeF acquisition writes canonical invoices with provenance and no legacy accounting rows.
 */
@SpringBootTest(classes = RyczaltNativeKsefImportIT.TestConfiguration.class)
class RyczaltNativeKsefImportIT {
  private static final WorkerDatabase DATABASE = MigrationTestDatabase.open("ryczalt_ksef");

  @Autowired private RyczaltKsefImportService ksefImport;
  @Autowired private ProgrammableInvoiceSource source;
  @Autowired private RyczaltPeriodJpaRepository periods;
  @Autowired private RyczaltInvoiceJpaRepository invoices;
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
    source.records.clear();
    try (var connection = dataSource.getConnection();
        var statement = connection.createStatement()) {
      statement.execute(
          "TRUNCATE investory.ryczalt_payment_match, investory.ryczalt_source_reference,"
              + " investory.ryczalt_obligation, investory.ryczalt_transaction,"
              + " investory.ryczalt_invoice, investory.ryczalt_calculation,"
              + " investory.ryczalt_audit_event, investory.ryczalt_period RESTART IDENTITY CASCADE");
    }
  }

  private InvoiceSourceRecord income(String ksefNumber, String amount) {
    BigDecimal net = new BigDecimal(amount);
    return new InvoiceSourceRecord(
        ksefNumber,
        InvoiceDirection.INCOME,
        "FV/" + ksefNumber,
        LocalDate.of(2026, 2, 10),
        LocalDate.of(2026, 2, 10),
        net,
        BigDecimal.ZERO,
        net,
        "PLN",
        "Buyer",
        "2222222222",
        null,
        null);
  }

  private InvoiceSourceRecord cost(String ksefNumber, String amount) {
    BigDecimal net = new BigDecimal(amount);
    return new InvoiceSourceRecord(
        ksefNumber,
        InvoiceDirection.COST,
        "FZ/" + ksefNumber,
        LocalDate.of(2026, 2, 12),
        LocalDate.of(2026, 2, 12),
        net,
        BigDecimal.ZERO,
        net,
        "PLN",
        "Seller",
        "1111111111",
        null,
        null);
  }

  @Test
  void syncCreatesCanonicalInvoiceWithKsefProvenanceAndNoLegacyWrite() {
    source.records.add(income("KSEF-1", "29600"));

    RyczaltKsefSyncResult result =
        ksefImport.sync(1, YearMonth.of(2026, 2), Set.of(KsefSyncMode.SALES));

    assertThat(result.imported()).isEqualTo(1);
    assertThat(invoices.count()).isEqualTo(1);
    var reference = sourceReferences.findAll().getFirst();
    assertThat(reference.getSource()).isEqualTo("KSEF");
    assertThat(reference.getEntityType()).isEqualTo("INVOICE");
    assertThat(reference.getExternalId()).isEqualTo("KSEF-1");
    assertThat(legacyInvoiceRows()).isZero();
  }

  @Test
  void repeatSyncIsIdempotent() {
    source.records.add(income("KSEF-1", "29600"));
    ksefImport.sync(1, YearMonth.of(2026, 2), Set.of(KsefSyncMode.SALES));
    RyczaltKsefSyncResult second =
        ksefImport.sync(1, YearMonth.of(2026, 2), Set.of(KsefSyncMode.SALES));

    assertThat(second.imported()).isZero();
    assertThat(second.duplicates()).isEqualTo(1);
    assertThat(invoices.count()).isEqualTo(1);
    assertThat(sourceReferences.count()).isEqualTo(1);
  }

  @Test
  void mapsSellerAndPurchaseDirections() {
    source.records.add(income("KSEF-SALE", "1000"));
    source.records.add(cost("KSEF-BUY", "500"));

    ksefImport.sync(1, YearMonth.of(2026, 2), Set.of(KsefSyncMode.SALES, KsefSyncMode.PURCHASES));

    assertThat(invoices.findAll())
        .extracting(RyczaltInvoiceEntity::getDirection)
        .containsExactlyInAnyOrder(InvoiceDirection.INCOME, InvoiceDirection.COST);
  }

  @Test
  void doesNotGuessUnresolvedAccountingClassifications() {
    source.records.add(cost("KSEF-BUY", "500"));

    ksefImport.sync(1, YearMonth.of(2026, 2), Set.of(KsefSyncMode.PURCHASES));

    RyczaltInvoiceEntity invoice = invoices.findAll().getFirst();
    assertThat(invoice.getRyczaltRate()).isNull();
    assertThat(invoice.getDeductibleVat()).isNull();
  }

  @Test
  void reimportUpdatesExistingInvoice() {
    source.records.add(income("KSEF-1", "29600"));
    ksefImport.sync(1, YearMonth.of(2026, 2), Set.of(KsefSyncMode.SALES));

    source.records.clear();
    source.records.add(income("KSEF-1", "31000"));
    RyczaltKsefSyncResult result = ksefImport.reimport(1, YearMonth.of(2026, 2));

    assertThat(result.updated()).isEqualTo(1);
    assertThat(invoices.count()).isEqualTo(1);
    assertThat(invoices.findAll().getFirst().getNetAmount()).isEqualByComparingTo("31000");
    assertThat(sourceReferences.count()).isEqualTo(1);
  }

  @Test
  void profileIsolationKeepsInvoicesSeparate() {
    source.records.add(income("KSEF-1", "29600"));
    ksefImport.sync(1, YearMonth.of(2026, 2), Set.of(KsefSyncMode.SALES));
    ksefImport.sync(2, YearMonth.of(2026, 2), Set.of(KsefSyncMode.SALES));

    assertThat(invoices.findAll())
        .extracting(RyczaltInvoiceEntity::getProfileId)
        .containsExactlyInAnyOrder(1L, 2L);
    assertThat(sourceReferences.count()).isEqualTo(2);
  }

  @Test
  void frozenPeriodRejectsSync() {
    jdbc.update(
        "INSERT INTO investory.ryczalt_period(profile_id, period_year, period_month, status)"
            + " VALUES (1, 2026, 2, 'FROZEN')");
    source.records.add(income("KSEF-1", "29600"));

    assertThatThrownBy(() -> ksefImport.sync(1, YearMonth.of(2026, 2), Set.of(KsefSyncMode.SALES)))
        .isInstanceOf(FrozenPeriodMutationException.class);
    assertThat(invoices.count()).isZero();
  }

  @Test
  void invoiceChangeMarksCalculatedPeriodDirty() {
    jdbc.update(
        "INSERT INTO investory.ryczalt_period(profile_id, period_year, period_month, status)"
            + " VALUES (1, 2026, 2, 'CALCULATED')");
    source.records.add(income("KSEF-1", "29600"));

    ksefImport.sync(1, YearMonth.of(2026, 2), Set.of(KsefSyncMode.SALES));

    RyczaltPeriodEntity period = periods.findByProfileIdAndYearAndMonth(1, 2026, 2).orElseThrow();
    assertThat(period.getStatus()).isEqualTo(PeriodStatus.DIRTY);
  }

  private int legacyInvoiceRows() {
    Integer count =
        jdbc.queryForObject("SELECT count(*) FROM investory.accounting_poc_invoice", Integer.class);
    return count == null ? 0 : count;
  }

  /** In-memory stand-in for the external KSeF transport. */
  static class ProgrammableInvoiceSource implements InvoiceSourcePort {
    final List<InvoiceSourceRecord> records = new ArrayList<>();

    @Override
    public List<InvoiceSourceRecord> fetch(KsefSyncCommand command) {
      return List.copyOf(records);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = RyczaltPeriodEntity.class)
  @EnableJpaRepositories(
      basePackageClasses = {
        RyczaltPeriodJpaRepository.class,
        RyczaltInvoiceJpaRepository.class,
        RyczaltSourceReferenceJpaRepository.class,
        RyczaltObligationJpaRepository.class,
        com.smartbox.investory.ryczalt.persistence.RyczaltCalculationJpaRepository.class
      })
  @Import({RyczaltKsefImportService.class, RyczaltPeriodLifecycleService.class})
  static class TestConfiguration {
    @Bean
    ProgrammableInvoiceSource programmableInvoiceSource() {
      return new ProgrammableInvoiceSource();
    }
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::jdbcUrl);
    registry.add("spring.datasource.username", DATABASE::username);
    registry.add("spring.datasource.password", DATABASE::password);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    registry.add("spring.flyway.enabled", () -> "false");
  }
}
