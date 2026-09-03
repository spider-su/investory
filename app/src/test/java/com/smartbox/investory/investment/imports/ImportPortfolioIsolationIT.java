package com.smartbox.investory.investment.imports;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.investment.api.importing.ImportSource;
import com.smartbox.investory.investment.api.importing.InvestmentImportApi;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Protects portfolio scope at the import-orchestration boundary. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("Import Portfolio Isolation")
class ImportPortfolioIsolationIT extends FastDatabaseTest {

  private static final long EXISTING_PORTFOLIO_ID = 1L;
  private static final long SECOND_PORTFOLIO_ID = 910001L;
  private static final long SECOND_ACCOUNT_ID = 910002L;
  private static final String EXTERNAL_ACCOUNT_ID = "17959259";
  private static final String FILE_NAME = "U17959259.TRANSACTIONS.20260801.csv";

  @Autowired private InvestmentImportApi investmentImport;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void cleanIsolationPortfolio() {
    jdbc.execute("SET session_replication_role = replica");
    try {
      jdbc.update("DELETE FROM investory.positions WHERE account_id = ?", SECOND_ACCOUNT_ID);
      jdbc.update("DELETE FROM investory.cash_operations WHERE account_id = ?", SECOND_ACCOUNT_ID);
      jdbc.update(
          "DELETE FROM investory.import_source_rows WHERE import_history_id IN "
              + "(SELECT id FROM investory.import_history WHERE portfolio_id = ?)",
          SECOND_PORTFOLIO_ID);
      jdbc.update(
          "DELETE FROM investory.import_source_files WHERE import_history_id IN "
              + "(SELECT id FROM investory.import_history WHERE portfolio_id = ?)",
          SECOND_PORTFOLIO_ID);
      jdbc.update(
          "DELETE FROM investory.import_history WHERE portfolio_id = ?", SECOND_PORTFOLIO_ID);
      jdbc.update("DELETE FROM investory.accounts WHERE id = ?", SECOND_ACCOUNT_ID);
      jdbc.update("DELETE FROM investory.portfolios WHERE id = ?", SECOND_PORTFOLIO_ID);
    } finally {
      jdbc.execute("SET session_replication_role = origin");
    }
  }

  @Test
  @DisplayName("keeps Same Broker File And Derived Rows Scoped To Each Portfolio")
  void keepsSameBrokerFileAndDerivedRowsScopedToEachPortfolio() {
    createSecondPortfolioAndAccount();

    long secondPortfolioOperationsBefore = countCashOperationsForPortfolio(SECOND_PORTFOLIO_ID);
    long secondPortfolioSnapshotsBefore = countAccountDailyForPortfolio(SECOND_PORTFOLIO_ID);

    InvestmentImportApi.ImportResult first = importFile(EXISTING_PORTFOLIO_ID, "first");

    assertThat(first.status())
        .isEqualTo(com.smartbox.investory.investment.api.importing.ImportStatus.COMPLETED);
    assertThat(first.duplicate()).isFalse();
    assertThat(countCashOperationsForPortfolio(EXISTING_PORTFOLIO_ID)).isGreaterThan(0);
    assertThat(countCashOperationsForPortfolio(SECOND_PORTFOLIO_ID))
        .isEqualTo(secondPortfolioOperationsBefore);
    assertThat(countAccountDailyForPortfolio(SECOND_PORTFOLIO_ID))
        .isEqualTo(secondPortfolioSnapshotsBefore);

    InvestmentImportApi.ImportResult second = importFile(SECOND_PORTFOLIO_ID, "second");

    assertThat(second.status())
        .isEqualTo(com.smartbox.investory.investment.api.importing.ImportStatus.COMPLETED);
    assertThat(second.duplicate()).isFalse();
    assertThat(second.batchId()).isNotEqualTo(first.batchId());
    assertThat(countCashOperationsForPortfolio(SECOND_PORTFOLIO_ID)).isGreaterThan(0);
    assertThat(countCashOperationsForPortfolio(EXISTING_PORTFOLIO_ID)).isGreaterThan(0);
    assertThat(countImportedBatches(EXISTING_PORTFOLIO_ID)).isEqualTo(1);
    assertThat(countImportedBatches(SECOND_PORTFOLIO_ID)).isEqualTo(1);

    InvestmentImportApi.ImportResult duplicate = importFile(SECOND_PORTFOLIO_ID, "duplicate");

    assertThat(duplicate.duplicate()).isFalse();
    assertThat(duplicate.batchId()).isNotEqualTo(second.batchId());
    assertThat(countImportedBatches(SECOND_PORTFOLIO_ID)).isEqualTo(2);
    assertThat(countCashOperationsForAccount(SECOND_ACCOUNT_ID)).isEqualTo(1);
    assertThat(countCashOperationsForAccount(17959259L)).isEqualTo(1);
  }

  private InvestmentImportApi.ImportResult importFile(long portfolioId, String sourceRef) {
    return investmentImport.importAuto(
        portfolioId,
        FILE_NAME,
        depositStatement().getBytes(StandardCharsets.UTF_8),
        ImportSource.MANUAL,
        sourceRef);
  }

  private void createSecondPortfolioAndAccount() {
    jdbc.update(
        "INSERT INTO investory.portfolios "
            + "(id, name, base_currency, local_currency, owner, user_id) "
            + "VALUES (?, ?, 'USD', 'PLN', ?, 1)",
        SECOND_PORTFOLIO_ID,
        "Isolation Portfolio",
        "Isolation Test");
    jdbc.update(
        "INSERT INTO investory.accounts "
            + "(id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only) "
            + "VALUES (?, ?, 'USD', 'IBKR', ?, ?, ?, false)",
        SECOND_ACCOUNT_ID,
        EXTERNAL_ACCOUNT_ID,
        "Isolation IBKR Account",
        "Isolation Test",
        SECOND_PORTFOLIO_ID);
  }

  private long countImportedBatches(long portfolioId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM investory.import_history WHERE portfolio_id = ?",
        Long.class,
        portfolioId);
  }

  private long countCashOperationsForPortfolio(long portfolioId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM investory.cash_operations co "
            + "JOIN investory.accounts a ON a.id = co.account_id "
            + "WHERE a.portfolio_id = ?",
        Long.class,
        portfolioId);
  }

  private long countCashOperationsForAccount(long accountId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM investory.cash_operations WHERE account_id = ? AND import_history_id IS NOT NULL",
        Long.class,
        accountId);
  }

  private long countAccountDailyForPortfolio(long portfolioId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM investory.account_daily ad "
            + "JOIN investory.accounts a ON a.id = ad.account_id "
            + "WHERE a.portfolio_id = ?",
        Long.class,
        portfolioId);
  }

  private static String depositStatement() {
    return String.join(
        "\n",
        "Transaction History,Header,Date,AccountEntity,Description,Transaction Type,Symbol,Quantity,Price,Price Currency,Gross Amount,Commission,Net Amount,Currency",
        "Transaction History,Data,2026-08-01,Sample Isolation User,Initial deposit,Deposit,-,-,-,-,1000,0,1000,USD");
  }
}
