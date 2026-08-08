package com.example.demo.infrastructure.repository.reconciliation;

import com.example.demo.infrastructure.repository.account.AccountDaily;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface ReconciliationReportRepository extends Repository<AccountDaily, Long> {

  @Query(
      value =
          """
          SELECT
              vpv.account_id AS "accountId",
              account.name AS "accountName",
              account.provider AS "provider",
              vpv.asset_id AS "assetId",
              asset.symbol AS "symbol",
              vpv.valuation_date AS "valuationDate",
              vpv.severity AS "severity",
              vpv.validation_code AS "validationCode",
              vpv.expected_value AS "expectedValue",
              vpv.actual_value AS "actualValue",
              vpv.difference AS "difference",
              vpv.relative_difference AS "relativeDifference",
              vpv.message AS "message"
          FROM investory.v_position_valuation_validation vpv
          JOIN investory.accounts account ON account.id = vpv.account_id
          JOIN investory.assets asset ON asset.id = vpv.asset_id
          WHERE vpv.severity IN ('ERROR', 'WARN')
          ORDER BY
              CASE vpv.severity WHEN 'ERROR' THEN 0 ELSE 1 END,
              vpv.valuation_date DESC,
              account.name,
              asset.symbol
          LIMIT 250
          """,
      nativeQuery = true)
  List<PositionIssueRow> findPositionIssues();

  @Query(
      value =
          """
          SELECT
              COUNT(*) AS "totalIssues",
              COUNT(*) FILTER (WHERE vpv.severity = 'ERROR') AS "errors",
              COUNT(*) FILTER (WHERE vpv.severity = 'WARN') AS "warnings",
              COUNT(DISTINCT vpv.asset_id) AS "affectedAssets",
              COUNT(DISTINCT vpv.account_id) AS "affectedAccounts"
          FROM investory.v_position_valuation_validation vpv
          WHERE vpv.severity IN ('ERROR', 'WARN')
          """,
      nativeQuery = true)
  PositionIssueSummaryRow summarizePositionIssues();

  @Query(
      value =
          """
          SELECT
              adr.account_id AS "accountId",
              account.name AS "accountName",
              account.provider AS "provider",
              adr.valuation_date AS "valuationDate",
              adr.status AS "status",
              adr.severity AS "severity",
              adr.validation_message AS "validationMessage",
              adr.market_value_difference AS "marketValueDifference",
              adr.cash_difference AS "cashDifference",
              adr.equity_difference AS "equityDifference",
              adr.cost_base_difference AS "costBaseDifference",
              adr.unrealized_difference AS "unrealizedDifference"
          FROM investory.v_account_daily_reconciliation adr
          JOIN investory.accounts account ON account.id = adr.account_id
          WHERE adr.status <> 'PASS'
          ORDER BY
              CASE adr.status WHEN 'FAIL' THEN 0 ELSE 1 END,
              adr.valuation_date DESC,
              account.name
          LIMIT 250
          """,
      nativeQuery = true)
  List<AccountIssueRow> findAccountIssues();

  @Query(
      value =
          """
          SELECT
              COUNT(*) AS "totalIssues",
              COUNT(*) FILTER (WHERE adr.status = 'FAIL') AS "failures",
              COUNT(*) FILTER (WHERE adr.status = 'WARN') AS "warnings",
              COUNT(DISTINCT adr.account_id) AS "affectedAccounts"
          FROM investory.v_account_daily_reconciliation adr
          WHERE adr.status <> 'PASS'
          """,
      nativeQuery = true)
  AccountIssueSummaryRow summarizeAccountIssues();

  interface PositionIssueRow {
    Long getAccountId();

    String getAccountName();

    String getProvider();

    Long getAssetId();

    String getSymbol();

    LocalDate getValuationDate();

    String getSeverity();

    String getValidationCode();

    BigDecimal getExpectedValue();

    BigDecimal getActualValue();

    BigDecimal getDifference();

    BigDecimal getRelativeDifference();

    String getMessage();
  }

  interface PositionIssueSummaryRow {
    Long getTotalIssues();

    Long getErrors();

    Long getWarnings();

    Long getAffectedAssets();

    Long getAffectedAccounts();
  }

  interface AccountIssueRow {
    Long getAccountId();

    String getAccountName();

    String getProvider();

    LocalDate getValuationDate();

    String getStatus();

    String getSeverity();

    String getValidationMessage();

    BigDecimal getMarketValueDifference();

    BigDecimal getCashDifference();

    BigDecimal getEquityDifference();

    BigDecimal getCostBaseDifference();

    BigDecimal getUnrealizedDifference();
  }

  interface AccountIssueSummaryRow {
    Long getTotalIssues();

    Long getFailures();

    Long getWarnings();

    Long getAffectedAccounts();
  }
}
