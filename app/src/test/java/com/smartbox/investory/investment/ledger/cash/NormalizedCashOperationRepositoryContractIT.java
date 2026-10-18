package com.smartbox.investory.investment.ledger.cash;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.investment.ledger.cash.persistence.CashOperationEntity;
import com.smartbox.investory.investment.ledger.cash.persistence.NormalizedCashOperationRepository;
import com.smartbox.investory.testsupport.FastDatabase;
import com.smartbox.investory.testsupport.WorkerDatabase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = NormalizedCashOperationRepositoryContractIT.TestConfiguration.class)
@ActiveProfiles("test-fast")
@DisplayName("Normalized Cash Operation Repository Contract")
class NormalizedCashOperationRepositoryContractIT {

  private static final WorkerDatabase DATABASE =
      FastDatabase.scopedDatabase("normalized_cash_repository_contract");
  private static final long PORTFOLIO_ID = -174001L;
  private static final long ACCOUNT_ID = -174002L;
  private static final List<Long> OPERATION_IDS = List.of(-174011L, -174012L, -174013L, -174014L);

  @Autowired private JdbcTemplate jdbc;
  @Autowired private NormalizedCashOperationRepository operations;

  @AfterAll
  static void closeDatabase() {
    DATABASE.close();
  }

  @DynamicPropertySource
  protected static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::jdbcUrl);
    registry.add("spring.datasource.username", DATABASE::username);
    registry.add("spring.datasource.password", DATABASE::password);
  }

  @BeforeEach
  void seedCashFlows() {
    // This class owns a disposable scoped database. Remove snapshot FX for a deterministic
    // missing-rate case.
    jdbc.update(
        "DELETE FROM investory.exchange_rates WHERE (base = 'USD' AND to_currency = 'PLN') OR (base = 'PLN' AND to_currency = 'USD')");
    Long userId =
        jdbc.queryForObject("SELECT id FROM investory.app_users ORDER BY id LIMIT 1", Long.class);
    jdbc.update(
        "INSERT INTO investory.portfolios (id, name, base_currency, owner, user_id) VALUES (?, ?, 'PLN', 'cash-flow-contract', ?)",
        PORTFOLIO_ID,
        "Cash flow repository contract",
        userId);
    jdbc.update(
        "INSERT INTO investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only) VALUES (?, ?, 'USD', 'XTB', ?, 'cash-flow-contract', ?, false)",
        ACCOUNT_ID,
        "cash-flow-contract",
        "Cash flow repository contract",
        PORTFOLIO_ID);

    insertOperation(OPERATION_IDS.get(0), "7.25", "2099-01-01T22:59:00Z");
    insertOperation(OPERATION_IDS.get(1), "10.10", "2099-01-01T23:01:00Z");
    insertOperation(OPERATION_IDS.get(2), "15.25", "2099-01-01T23:02:00Z");
    insertOperation(OPERATION_IDS.get(3), "-4.40", "2099-01-01T23:03:00Z");
    jdbc.execute("REFRESH MATERIALIZED VIEW investory.app_v_normalized_cash_operations");
  }

  @AfterEach
  void removeCashFlows() {
    jdbc.update(
        "DELETE FROM investory.cash_operations WHERE id IN (?, ?, ?, ?)", OPERATION_IDS.toArray());
    jdbc.update(
        "DELETE FROM investory.accounts WHERE portfolio_id IN (?, ?)",
        PORTFOLIO_ID,
        PORTFOLIO_ID - 1);
    jdbc.update(
        "DELETE FROM investory.portfolios WHERE id IN (?, ?)", PORTFOLIO_ID, PORTFOLIO_ID - 1);
    jdbc.execute("REFRESH MATERIALIZED VIEW investory.app_v_normalized_cash_operations");
  }

  @Test
  @DisplayName(
      "preserves Warsaw boundary dates, detail order, grouped sums, and null FX aggregates")
  void preservesDetailedAndGroupedReadContract() {
    List<NormalizedCashOperationRepository.NormalizedCashOperationRow> detailed =
        operations.findAllDetailedByAccountIdIn(List.of(ACCOUNT_ID));

    assertThat(detailed).hasSize(4);
    assertThat(detailed)
        .extracting(NormalizedCashOperationRepository.NormalizedCashOperationRow::getOperationId)
        .containsExactlyElementsOf(OPERATION_IDS);
    assertThat(detailed)
        .extracting(NormalizedCashOperationRepository.NormalizedCashOperationRow::getDate)
        .containsExactly(
            LocalDate.of(2099, 1, 1),
            LocalDate.of(2099, 1, 2),
            LocalDate.of(2099, 1, 2),
            LocalDate.of(2099, 1, 2));
    assertThat(detailed)
        .extracting(NormalizedCashOperationRepository.NormalizedCashOperationRow::getAmount)
        .containsExactly(7.25, 10.10, 15.25, -4.40);
    assertThat(detailed)
        .extracting(NormalizedCashOperationRepository.NormalizedCashOperationRow::getCurrency)
        .containsOnly("USD");
    assertThat(detailed)
        .extracting(NormalizedCashOperationRepository.NormalizedCashOperationRow::getRawOperation)
        .containsOnly("TRANSFER");
    assertThat(detailed)
        .extracting(
            NormalizedCashOperationRepository.NormalizedCashOperationRow::getNormalizedCategory)
        .containsExactly(
            "EXTERNAL_DEPOSIT", "EXTERNAL_DEPOSIT", "EXTERNAL_DEPOSIT", "EXTERNAL_WITHDRAWAL");
    assertThat(detailed)
        .allSatisfy(row -> assertThat(row.getAmountInPortfolioBaseCurrency()).isNull());

    List<NormalizedCashOperationRepository.NormalizedCashOperationRow> grouped =
        operations.findAllByAccountIdIn(List.of(ACCOUNT_ID));
    assertThat(grouped).hasSize(3);
    assertThat(grouped)
        .extracting(NormalizedCashOperationRepository.NormalizedCashOperationRow::getDate)
        .containsExactly(
            LocalDate.of(2099, 1, 1), LocalDate.of(2099, 1, 2), LocalDate.of(2099, 1, 2));
    assertThat(grouped)
        .extracting(
            NormalizedCashOperationRepository.NormalizedCashOperationRow::getNormalizedCategory)
        .containsExactly("EXTERNAL_DEPOSIT", "EXTERNAL_DEPOSIT", "EXTERNAL_WITHDRAWAL");
    assertThat(grouped)
        .extracting(NormalizedCashOperationRepository.NormalizedCashOperationRow::getOperationId)
        .containsExactly(OPERATION_IDS.get(0), OPERATION_IDS.get(2), OPERATION_IDS.get(3));
    assertThat(grouped)
        .extracting(NormalizedCashOperationRepository.NormalizedCashOperationRow::getCurrency)
        .containsOnly("USD");
    assertThat(grouped)
        .extracting(NormalizedCashOperationRepository.NormalizedCashOperationRow::getBaseCurrency)
        .containsOnly("PLN");
    assertThat(grouped.get(0).getAccountFlowAmountInAccountCurrency()).isEqualTo(7.25);
    assertThat(grouped.get(1).getAccountFlowAmountInAccountCurrency()).isEqualTo(25.35);
    assertThat(grouped.get(2).getAccountFlowAmountInAccountCurrency()).isEqualTo(-4.40);
    assertThat(grouped)
        .allSatisfy(
            row -> {
              assertThat(row.getAccountFlowAmountInPortfolioBaseCurrency()).isNull();
              assertThat(row.getPerformanceFlowAmountInPortfolioBaseCurrency()).isNull();
              assertThat(row.getRawOperation()).isNull();
              assertThat(row.getAssetId()).isNull();
            });
  }

  @Test
  @DisplayName("preserves non-null detailed and grouped amounts with a known FX rate")
  void preservesConvertedAggregates() {
    jdbc.update(
        "INSERT INTO investory.exchange_rates(rate_date, base, to_currency, rate, purpose, source, method) VALUES ('2099-01-01', 'USD', 'PLN', 4, 'VALUATION', 'TEST', 'OBSERVED')");
    jdbc.execute("REFRESH MATERIALIZED VIEW investory.app_v_normalized_cash_operations");
    assertThat(operations.findAllDetailedByAccountIdIn(List.of(ACCOUNT_ID)))
        .extracting(
            NormalizedCashOperationRepository.NormalizedCashOperationRow
                ::getAmountInPortfolioBaseCurrency)
        .containsExactly(29.0, 40.4, 61.0, -17.6);
    assertThat(operations.findAllByAccountIdIn(List.of(ACCOUNT_ID)))
        .extracting(
            NormalizedCashOperationRepository.NormalizedCashOperationRow
                ::getAccountFlowAmountInPortfolioBaseCurrency)
        .containsExactly(29.0, 101.4, -17.6);
  }

  @ParameterizedTest
  @ValueSource(strings = {"UNTRACKED", "XTB", "OTHER", "OTHER_PORTFOLIO"})
  void brokerIdentifiersDriveFundingAndBoundaryNettingInMaterializedConsumers(String counterparty) {
    if (!"UNTRACKED".equals(counterparty)) {
      long targetPortfolio = PORTFOLIO_ID;
      if ("OTHER_PORTFOLIO".equals(counterparty)) {
        targetPortfolio = PORTFOLIO_ID - 1;
        jdbc.update(
            "INSERT INTO investory.portfolios (id, name, base_currency, owner, user_id) SELECT ?, 'other test portfolio', base_currency, owner, user_id FROM investory.portfolios WHERE id=?",
            targetPortfolio,
            PORTFOLIO_ID);
      }
      jdbc.update(
          "INSERT INTO investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only) VALUES (?, '90000010', 'USD', ?, 'counterparty', 'cash-flow-contract', ?, false)",
          ACCOUNT_ID - 1,
          "OTHER".equals(counterparty) ? "IBKR" : "XTB",
          targetPortfolio);
    }
    String expectedContribution = "XTB".equals(counterparty) ? "0" : "240";
    jdbc.update(
        "UPDATE investory.accounts SET external_account_id='90000002' WHERE id=?", ACCOUNT_ID);
    jdbc.update(
        "INSERT INTO investory.exchange_rates(rate_date, base, to_currency, rate, purpose, source, method) VALUES ('2099-01-01', 'USD', 'PLN', 4, 'VALUATION', 'TEST', 'OBSERVED')");
    String[] amounts = {"-100", "100", "-40", "40"};
    for (int i = 0; i < 4; i++) {
      jdbc.update(
          "UPDATE investory.cash_operations SET operation='SUBACCOUNT_TRANSFER', amount=?, comment=? WHERE id=?",
          new BigDecimal(amounts[i]),
          i < 2 ? "Transfer from 90000010 to 90000002" : "Transfer from 90000002 to 90000010",
          OPERATION_IDS.get(i));
    }
    jdbc.execute("REFRESH MATERIALIZED VIEW investory.app_v_normalized_cash_operations");
    assertThat(
            jdbc.queryForObject(
                "SELECT sum(account_flow_amount) FROM investory.app_v_normalized_cash_operation_flows WHERE account_id=?",
                BigDecimal.class,
                ACCOUNT_ID))
        .isEqualByComparingTo("60");
    assertThat(
            jdbc.queryForObject(
                "SELECT sum(performance_flow_amount) FROM investory.app_v_normalized_cash_operation_flows WHERE account_id=?",
                BigDecimal.class,
                ACCOUNT_ID))
        .isEqualByComparingTo("0");
    assertThat(
            jdbc.queryForObject(
                "SELECT sum(amount) FROM investory.app_v_normalized_cash_operations WHERE account_id=?",
                BigDecimal.class,
                ACCOUNT_ID))
        .isEqualByComparingTo("0");
    jdbc.execute("REFRESH MATERIALIZED VIEW investory.app_v_account_statistics");
    jdbc.execute("REFRESH MATERIALIZED VIEW investory.app_v_portfolio_contribution_summary_mv");
    assertThat(
            jdbc.queryForObject(
                "SELECT net_deposit FROM investory.app_v_account_statistics WHERE account_id=?",
                BigDecimal.class,
                ACCOUNT_ID))
        .isEqualByComparingTo(expectedContribution);
    assertThat(
            jdbc.queryForObject(
                "SELECT net_deposits FROM investory.app_v_portfolio_contribution_summary_mv WHERE portfolio_id=?",
                BigDecimal.class,
                PORTFOLIO_ID))
        .isEqualByComparingTo(expectedContribution);
    assertThat(
            jdbc.queryForObject(
                "SELECT total_deposits FROM investory.app_v_portfolio_contribution_summary_mv WHERE portfolio_id=?",
                BigDecimal.class,
                PORTFOLIO_ID))
        .isEqualByComparingTo(expectedContribution);
    assertThat(
            jdbc.queryForObject(
                "SELECT total_withdrawals FROM investory.app_v_portfolio_contribution_summary_mv WHERE portfolio_id=?",
                BigDecimal.class,
                PORTFOLIO_ID))
        .isEqualByComparingTo("0");
  }

  @Test
  void unmatchedBookkeepingRemainsDiagnosticWithoutInventedFunding() {
    jdbc.update(
        "UPDATE investory.cash_operations SET operation='SUBACCOUNT_TRANSFER', comment='unresolved broker note' WHERE account_id=?",
        ACCOUNT_ID);
    jdbc.execute("REFRESH MATERIALIZED VIEW investory.app_v_normalized_cash_operations");
    assertThat(
            jdbc.queryForList(
                "SELECT pairing_status FROM investory.app_v_normalized_cash_operations WHERE account_id=?",
                String.class,
                ACCOUNT_ID))
        .containsOnly("UNMATCHED");
    assertThat(
            jdbc.queryForObject(
                "SELECT sum(account_flow_amount) FROM investory.app_v_normalized_cash_operation_flows WHERE account_id=?",
                BigDecimal.class,
                ACCOUNT_ID))
        .isEqualByComparingTo("0");
  }

  private void insertOperation(long operationId, String amount, String timestamp) {
    jdbc.update(
        "INSERT INTO investory.cash_operations (id, account_id, operation, amount, currency, comment, date) VALUES (?, ?, 'TRANSFER', ?, 'USD', 'cash transfer', ?::timestamptz)",
        operationId,
        ACCOUNT_ID,
        new BigDecimal(amount),
        timestamp);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = CashOperationEntity.class)
  @EnableJpaRepositories(basePackageClasses = NormalizedCashOperationRepository.class)
  static class TestConfiguration {}
}
