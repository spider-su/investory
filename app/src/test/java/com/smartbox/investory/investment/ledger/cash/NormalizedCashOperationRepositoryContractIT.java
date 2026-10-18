package com.smartbox.investory.investment.ledger.cash;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.investment.ledger.cash.persistence.CashOperationEntity;
import com.smartbox.investory.investment.ledger.cash.persistence.NormalizedCashOperationRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.testsupport.FastDatabase;
import com.smartbox.investory.testsupport.WorkerDatabase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
    java.util.ArrayList<CashOperationEntity> javaInput = new java.util.ArrayList<>();
    for (int i = 0; i < 4; i++) {
      CashOperationEntity row = new CashOperationEntity();
      row.setId(OPERATION_IDS.get(i));
      row.setAccount(ACCOUNT_ID);
      row.setType(CashOperationType.SUBACCOUNT_TRANSFER);
      row.setAmount(new BigDecimal(amounts[i]));
      row.setCurrency(CurrencyType.USD);
      row.setComment(
          i < 2 ? "Transfer from 90000010 to 90000002" : "Transfer from 90000002 to 90000010");
      row.setDate(ZonedDateTime.parse("2099-01-01T22:59:00Z").plusMinutes(i));
      javaInput.add(row);
    }
    assertThat(new CashOperationNormalizer().normalize(javaInput))
        .allSatisfy(row -> assertThat(row.transferGroupId()).isNotNull());
    var javaAccount =
        new com.smartbox.investory.investment.infrastructure.persistence.account.AccountEntity();
    javaAccount.setId(ACCOUNT_ID);
    javaAccount.setExternalAccountId("90000002");
    javaAccount.setProvider("XTB");
    javaAccount.setCurrency(CurrencyType.USD);
    assertThat(
            new XtbAccountFundingCalculator(new CashOperationNormalizer())
                .calculate(javaInput, java.util.Map.of(ACCOUNT_ID, javaAccount))
                .get(ACCOUNT_ID))
        .isEqualTo(60.0);
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

  static Stream<String> economicCases() {
    return Stream.of(
        "deposit|XTB|DEPOSIT|100|funding|EXTERNAL_DEPOSIT|RAW_DEPOSIT|EXTERNAL_DEPOSIT|RAW_DEPOSIT|100|100|100",
        "withdrawal|IBKR|WITHDRAWAL|-100|withdrawal|EXTERNAL_WITHDRAWAL|RAW_WITHDRAWAL|EXTERNAL_WITHDRAWAL|RAW_WITHDRAWAL|-100|-100|-100",
        "dividend|IBKR|DIVIDEND|10|dividend|DIVIDEND|DIVIDEND|DIVIDEND|DIVIDEND|0|0|0",
        "dividend reversal|XTB|DIVIDEND|-10|Dividend correction|DIVIDEND_REVERSAL|DIVIDEND_REVERSAL|DIVIDEND_REVERSAL|DIVIDEND_REVERSAL|0|0|0",
        "tax reversal|IBKR|WITHHOLDING_TAX|3|Tax reversal|WITHHOLDING_TAX_REVERSAL|WITHHOLDING_TAX_REVERSAL|WITHHOLDING_TAX_REVERSAL|WITHHOLDING_TAX_REVERSAL|0|0|0",
        "fee|IBKR|COMMISSION|-5|commission|FEE|FEE|FEE|FEE|0|0|0",
        "fee refund metadata|IBKR|COMMISSION|5|commission refund|FEE|FEE_REVERSAL|FEE|FEE|0|0|0",
        "correction|XTB|CORRECTION|5|unresolved adjustment|CORRECTION|UNCLASSIFIED_CORRECTION|CORRECTION|UNCLASSIFIED_CORRECTION|0|0|0",
        "fee correction drift|IBKR|CORRECTION|5|commission refund|FEE|FEE_REVERSAL|CORRECTION|UNCLASSIFIED_CORRECTION|0|0|0",
        "bond redemption metadata|IBKR|TRANSFER|100|Full call at 100 per bond|BOND_REDEMPTION|BOND_REDEMPTION|BOND_REDEMPTION|IBKR_BOND_REDEMPTION|0|0|0",
        "IBKR cash transfer drift|IBKR|TRANSFER|100|cash transfer|UNCLASSIFIED|UNCLASSIFIED|EXTERNAL_DEPOSIT|EXTERNAL_DEPOSIT|100|100|100",
        "IBKR forex drift|IBKR|TRANSFER|100|Net amount in base from forex trade: EUR.USD|UNCLASSIFIED|UNCLASSIFIED|FX_CONVERSION|FX_CONVERSION|100|0|0",
        "XTB transfer metadata|XTB|DEPOSIT|100|Transfer in operation on account with id 90000002|INTERNAL_TRANSFER_IN|XTB_ACCOUNT_TRANSFER|INTERNAL_TRANSFER_IN|XTB_ACCOUNT_TRANSFER_IN|100|100|0",
        "XTB transfer missing leg|XTB|TRANSFER|-100|Transfer from 90000002 to 90000010|INTERNAL_TRANSFER_OUT|ACCOUNT_TRANSFER|INTERNAL_TRANSFER_OUT|ACCOUNT_TRANSFER|-100|-100|0",
        "XTB bookkeeping missing leg|XTB|SUBACCOUNT_TRANSFER|100|Transfer from 90000010 to 90000002|INTERNAL_BOOKKEEPING|SUBACCOUNT_TRANSFER|INTERNAL_BOOKKEEPING|SUBACCOUNT_TRANSFER|100|0|0",
        "deferred FX|XTB|TRANSFER|100|Currency conversion, PLN to USD from TA: 90000010 to: 90000002, Exchange rate:0.25|FX_CONVERSION|FX_CONVERSION|INTERNAL_TRANSFER_IN|ACCOUNT_TRANSFER|100|100|0");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("economicCases")
  void characterizesJavaSqlCategoriesDirectionsPairingAndSeparateFlows(String fixture) {
    String[] c = fixture.split("\\|", -1);
    long id = OPERATION_IDS.getFirst();
    BigDecimal amount = new BigDecimal(c[3]);
    Long assetId =
        c[0].startsWith("bond")
            ? jdbc.queryForObject(
                "SELECT min(id) FROM investory.assets WHERE asset_type='BOND'", Long.class)
            : null;
    if (c[0].startsWith("bond")) assertThat(assetId).isNotNull();
    jdbc.update(
        "UPDATE investory.accounts SET external_account_id='90000002', provider=? WHERE id=?",
        c[1],
        ACCOUNT_ID);
    jdbc.update(
        "UPDATE investory.cash_operations SET operation=?::investory.cash_operation_type, amount=?, comment=?, asset_id=? WHERE id=?",
        c[2],
        amount,
        c[4],
        assetId,
        id);
    jdbc.execute("REFRESH MATERIALIZED VIEW investory.app_v_normalized_cash_operations");
    CashOperationEntity operation = new CashOperationEntity();
    operation.setId(id);
    operation.setAccount(ACCOUNT_ID);
    operation.setType(CashOperationType.valueOf(c[2]));
    operation.setAmount(amount);
    operation.setCurrency(CurrencyType.USD);
    operation.setComment(c[4]);
    operation.setAssetId(assetId);
    operation.setDate(ZonedDateTime.parse("2099-01-01T22:59:00Z"));
    var javaRow = new CashOperationNormalizer().normalize(List.of(operation)).getFirst();
    var sql =
        jdbc.queryForMap(
            "SELECT * FROM investory.app_v_normalized_cash_operation_flows WHERE operation_id=?",
            id);
    assertThat(javaRow.normalizedCategory().name()).as(c[0] + " Java category").isEqualTo(c[5]);
    assertThat(javaRow.normalizedSubtype()).as(c[0] + " Java subtype").isEqualTo(c[6]);
    assertThat(sql.get("normalized_category")).as(c[0] + " SQL category").isEqualTo(c[7]);
    assertThat(sql.get("normalized_subtype")).as(c[0] + " SQL subtype").isEqualTo(c[8]);
    assertThat(sql.get("economic_direction"))
        .isEqualTo(javaRow.economicDirection().name())
        .isEqualTo(amount.signum() > 0 ? "INFLOW" : "OUTFLOW");
    assertThat(javaRow.transferGroupId()).isNull();
    assertThat(sql.get("pairing_status"))
        .isEqualTo(
            c[7].startsWith("INTERNAL_") || c[7].equals("FX_CONVERSION")
                ? "UNMATCHED"
                : "NOT_REQUIRED");
    assertThat((BigDecimal) sql.get("amount"))
        .isEqualByComparingTo(javaRow.operation().getAmount());
    for (int i = 0; i < 3; i++) {
      String column =
          List.of("account_flow_amount", "performance_flow_amount", "portfolio_flow_amount").get(i);
      assertThat((BigDecimal) sql.get(column))
          .as(c[0] + " " + column)
          .isEqualByComparingTo(c[9 + i]);
    }
    System.out.printf(
        "Java/SQL differential %s: category=%s/%s subtype=%s/%s amount=%s flows=%s/%s/%s%n",
        c[0], c[5], c[7], c[6], c[8], amount, c[9], c[10], c[11]);
  }

  @Test
  void overlappingExportRowsRemainExplicitUntilImporterDeduplication() {
    jdbc.update(
        "UPDATE investory.cash_operations SET operation='COMMISSION', amount=-5, comment='overlapping export commission', date='2099-01-01T22:59:00Z' WHERE id IN (?, ?)",
        OPERATION_IDS.get(0),
        OPERATION_IDS.get(1));
    List<CashOperationEntity> input = new java.util.ArrayList<>();
    for (long id : OPERATION_IDS.subList(0, 2)) {
      CashOperationEntity row = new CashOperationEntity();
      row.setId(id);
      row.setAccount(ACCOUNT_ID);
      row.setType(CashOperationType.COMMISSION);
      row.setAmount(new BigDecimal("-5"));
      row.setCurrency(CurrencyType.USD);
      row.setComment("overlapping export commission");
      row.setDate(ZonedDateTime.parse("2099-01-01T22:59:00Z"));
      input.add(row);
    }
    var javaRows = new CashOperationNormalizer().normalize(input);
    jdbc.execute("REFRESH MATERIALIZED VIEW investory.app_v_normalized_cash_operations");
    assertThat(javaRows)
        .hasSize(2)
        .allSatisfy(row -> assertThat(row.normalizedCategory().name()).isEqualTo("FEE"));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.app_v_normalized_cash_operations WHERE operation_id IN (?, ?)",
                Integer.class,
                OPERATION_IDS.get(0),
                OPERATION_IDS.get(1)))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT sum(amount) FROM investory.app_v_normalized_cash_operations WHERE operation_id IN (?, ?)",
                BigDecimal.class,
                OPERATION_IDS.get(0),
                OPERATION_IDS.get(1)))
        .isEqualByComparingTo("-10");
  }

  @Test
  void deferredCrossCurrencyPairPreservesKnownJavaSqlDifferenceAndConservesBaseCash() {
    jdbc.update(
        "UPDATE investory.accounts SET external_account_id='90000002' WHERE id=?", ACCOUNT_ID);
    jdbc.update(
        "INSERT INTO investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only) VALUES (?, '90000010', 'PLN', 'XTB', 'FX source', 'cash-flow-contract', ?, false)",
        ACCOUNT_ID - 1,
        PORTFOLIO_ID);
    jdbc.update(
        "INSERT INTO investory.exchange_rates(rate_date, base, to_currency, rate, purpose, source, method) VALUES ('2099-01-01', 'USD', 'PLN', 4, 'VALUATION', 'TEST', 'OBSERVED')");
    String comment =
        "Currency conversion, PLN to USD from TA: 90000010 to: 90000002, Exchange rate:0.25";
    java.util.ArrayList<CashOperationEntity> input = new java.util.ArrayList<>();
    for (int i = 0; i < 2; i++) {
      long account = i == 0 ? ACCOUNT_ID : ACCOUNT_ID - 1;
      String currency = i == 0 ? "USD" : "PLN";
      BigDecimal amount = new BigDecimal(i == 0 ? "100" : "-400");
      jdbc.update(
          "UPDATE investory.cash_operations SET account_id=?, operation='TRANSFER', amount=?, currency=?, comment=? WHERE id=?",
          account,
          amount,
          currency,
          comment,
          OPERATION_IDS.get(i));
      CashOperationEntity row = new CashOperationEntity();
      row.setId(OPERATION_IDS.get(i));
      row.setAccount(account);
      row.setType(CashOperationType.TRANSFER);
      row.setAmount(amount);
      row.setCurrency(CurrencyType.valueOf(currency));
      row.setComment(comment);
      row.setDate(ZonedDateTime.parse("2099-01-01T22:59:00Z").plusMinutes(i));
      input.add(row);
    }
    var javaRows = new CashOperationNormalizer().normalize(input);
    assertThat(javaRows)
        .allSatisfy(
            row -> {
              assertThat(row.normalizedCategory().name()).isEqualTo("FX_CONVERSION");
              assertThat(row.transferGroupId()).isNotNull();
              assertThat(row.externalFlow()).isFalse();
            });
    assertThat(javaRows.get(0).transferGroupId()).isEqualTo(javaRows.get(1).transferGroupId());
    jdbc.execute("REFRESH MATERIALIZED VIEW investory.app_v_normalized_cash_operations");
    assertThat(
            jdbc.queryForList(
                "SELECT normalized_category FROM investory.app_v_normalized_cash_operation_flows WHERE operation_id IN (?, ?) ORDER BY amount DESC",
                String.class,
                OPERATION_IDS.get(0),
                OPERATION_IDS.get(1)))
        .containsExactly("INTERNAL_TRANSFER_IN", "INTERNAL_TRANSFER_OUT");
    assertThat(
            jdbc.queryForList(
                "SELECT pairing_status FROM investory.app_v_normalized_cash_operation_flows WHERE operation_id IN (?, ?)",
                String.class,
                OPERATION_IDS.get(0),
                OPERATION_IDS.get(1)))
        .containsOnly("UNMATCHED");
    for (String column :
        List.of(
            "amount_in_portfolio_base_currency",
            "account_flow_amount_in_portfolio_base_currency",
            "performance_flow_amount_in_portfolio_base_currency",
            "portfolio_flow_amount")) {
      assertThat(
              jdbc.queryForObject(
                  "SELECT sum("
                      + column
                      + ") FROM investory.app_v_normalized_cash_operation_flows WHERE operation_id IN (?, ?)",
                  BigDecimal.class,
                  OPERATION_IDS.get(0),
                  OPERATION_IDS.get(1)))
          .as(column)
          .isEqualByComparingTo("0");
    }
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
