package com.smartbox.investory.investment.infrastructure.persistence.reconciliation;

import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Read-only PostgreSQL query adapter for persisted reconciliation evidence.
 *
 * <p>The four reads intentionally remain native despite simple joins, predicates, and ordering
 * being expressible through ORM projections. Each returns filtered window counts over the full
 * optionally portfolio-scoped issue set, calculated before the 250-row limit. In particular, {@code
 * COUNT(*) FILTER (...) OVER ()} is PostgreSQL-specific; ORM pagination/count projections cannot
 * preserve that output contract. The typed interface projections avoid positional mapping.
 */
public interface ReconciliationEvidenceRepository extends Repository<AccountDailyEntity, Long> {

  /**
   * Native: unscoped position issues need full-result total and filtered severity window counts
   * before severity/date/name/symbol ordering and the 250-row cap; ORM pagination cannot preserve
   * this count contract.
   */
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
              vpv.message AS "message",
              COUNT(*) OVER () AS "totalIssues",
              COUNT(*) FILTER (WHERE vpv.severity = 'ERROR') OVER () AS "failures",
              COUNT(*) FILTER (WHERE vpv.severity = 'WARN') OVER () AS "reviews"
          FROM investory.recon_v_position_valuation_validation vpv
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

  /**
   * Native: portfolio-scoped position issues add the account portfolio predicate but retain
   * PostgreSQL filtered window counts over the scoped set before ordering and limit.
   */
  @Query(
      value =
          "SELECT * FROM (SELECT vpv.account_id AS \"accountId\", account.name AS \"accountName\", account.provider AS \"provider\", vpv.asset_id AS \"assetId\", asset.symbol AS \"symbol\", vpv.valuation_date AS \"valuationDate\", vpv.severity AS \"severity\", vpv.validation_code AS \"validationCode\", vpv.expected_value AS \"expectedValue\", vpv.actual_value AS \"actualValue\", vpv.difference AS \"difference\", vpv.relative_difference AS \"relativeDifference\", vpv.message AS \"message\", COUNT(*) OVER () AS \"totalIssues\", COUNT(*) FILTER (WHERE vpv.severity = 'ERROR') OVER () AS \"failures\", COUNT(*) FILTER (WHERE vpv.severity = 'WARN') OVER () AS \"reviews\" FROM investory.recon_v_position_valuation_validation vpv JOIN investory.accounts account ON account.id = vpv.account_id JOIN investory.assets asset ON asset.id = vpv.asset_id WHERE vpv.severity IN ('ERROR', 'WARN') AND account.portfolio_id = :portfolioId) scoped ORDER BY CASE \"severity\" WHEN 'ERROR' THEN 0 ELSE 1 END, \"valuationDate\" DESC, \"accountName\", \"symbol\" LIMIT 250",
      nativeQuery = true)
  List<PositionIssueRow> findPositionIssuesByPortfolioId(@Param("portfolioId") Long portfolioId);

  /**
   * Native: unscoped account issues use PostgreSQL filtered status window counts over all non-PASS
   * rows before FAIL/WARN ordering and the 250-row cap; an ORM count/page would count a different
   * set.
   */
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
              adr.diagnostic_code AS "diagnosticCode",
              adr.validation_message AS "validationMessage",
              adr.reconstructed_market_value AS "expectedMarketValue",
              adr.reported_market_value AS "actualMarketValue",
              adr.market_value_difference AS "marketValueDifference",
              adr.reconstructed_cash_balance AS "expectedCashBalance",
              adr.reported_cash_balance AS "actualCashBalance",
              adr.cash_difference AS "cashDifference",
              adr.reconstructed_equity AS "expectedEquity",
              adr.reported_equity AS "actualEquity",
              adr.equity_difference AS "equityDifference",
              adr.reconstructed_cost_base AS "expectedCostBase",
              adr.reported_cost_base AS "actualCostBase",
              adr.cost_base_difference AS "costBaseDifference",
              adr.reconstructed_unrealized_profit AS "expectedUnrealized",
              adr.reported_unrealized_profit AS "actualUnrealized",
              adr.unrealized_difference AS "unrealizedDifference",
              adr.reconstructed_total_realized_result AS "expectedRealized",
              adr.reported_realized_profit AS "actualRealized",
              adr.realized_difference AS "realizedDifference",
              COUNT(*) OVER () AS "totalIssues",
              COUNT(*) FILTER (WHERE adr.status = 'FAIL') OVER () AS "failures",
              COUNT(*) FILTER (WHERE adr.status = 'WARN') OVER () AS "reviews"
          FROM investory.recon_v_account_daily_diagnostic adr
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

  /**
   * Native: portfolio-scoped account issues apply the portfolio predicate before PostgreSQL
   * filtered status window counts, then order and cap that scoped result.
   */
  @Query(
      value =
          "SELECT * FROM (SELECT adr.account_id AS \"accountId\", account.name AS \"accountName\", account.provider AS \"provider\", adr.valuation_date AS \"valuationDate\", adr.status AS \"status\", adr.severity AS \"severity\", adr.diagnostic_code AS \"diagnosticCode\", adr.validation_message AS \"validationMessage\", adr.reconstructed_market_value AS \"expectedMarketValue\", adr.reported_market_value AS \"actualMarketValue\", adr.market_value_difference AS \"marketValueDifference\", adr.reconstructed_cash_balance AS \"expectedCashBalance\", adr.reported_cash_balance AS \"actualCashBalance\", adr.cash_difference AS \"cashDifference\", adr.reconstructed_equity AS \"expectedEquity\", adr.reported_equity AS \"actualEquity\", adr.equity_difference AS \"equityDifference\", adr.reconstructed_cost_base AS \"expectedCostBase\", adr.reported_cost_base AS \"actualCostBase\", adr.cost_base_difference AS \"costBaseDifference\", adr.reconstructed_unrealized_profit AS \"expectedUnrealized\", adr.reported_unrealized_profit AS \"actualUnrealized\", adr.unrealized_difference AS \"unrealizedDifference\", adr.reconstructed_total_realized_result AS \"expectedRealized\", adr.reported_realized_profit AS \"actualRealized\", adr.realized_difference AS \"realizedDifference\", COUNT(*) OVER () AS \"totalIssues\", COUNT(*) FILTER (WHERE adr.status = 'FAIL') OVER () AS \"failures\", COUNT(*) FILTER (WHERE adr.status = 'WARN') OVER () AS \"reviews\" FROM investory.recon_v_account_daily_diagnostic adr JOIN investory.accounts account ON account.id = adr.account_id WHERE adr.status <> 'PASS' AND account.portfolio_id = :portfolioId) scoped ORDER BY CASE \"status\" WHEN 'FAIL' THEN 0 ELSE 1 END, \"valuationDate\" DESC, \"accountName\" LIMIT 250",
      nativeQuery = true)
  List<AccountIssueRow> findAccountIssuesByPortfolioId(@Param("portfolioId") Long portfolioId);

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

    Long getTotalIssues();

    Long getFailures();

    Long getReviews();
  }

  interface AccountIssueRow {
    Long getAccountId();

    String getAccountName();

    String getProvider();

    LocalDate getValuationDate();

    String getStatus();

    String getDiagnosticCode();

    String getSeverity();

    String getValidationMessage();

    BigDecimal getExpectedMarketValue();

    BigDecimal getActualMarketValue();

    BigDecimal getMarketValueDifference();

    BigDecimal getExpectedCashBalance();

    BigDecimal getActualCashBalance();

    BigDecimal getCashDifference();

    BigDecimal getExpectedEquity();

    BigDecimal getActualEquity();

    BigDecimal getEquityDifference();

    BigDecimal getExpectedCostBase();

    BigDecimal getActualCostBase();

    BigDecimal getCostBaseDifference();

    BigDecimal getExpectedUnrealized();

    BigDecimal getActualUnrealized();

    BigDecimal getUnrealizedDifference();

    BigDecimal getExpectedRealized();

    BigDecimal getActualRealized();

    BigDecimal getRealizedDifference();

    Long getTotalIssues();

    Long getFailures();

    Long getReviews();
  }
}
