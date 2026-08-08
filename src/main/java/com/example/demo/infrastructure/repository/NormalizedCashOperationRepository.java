package com.example.demo.infrastructure.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface NormalizedCashOperationRepository extends Repository<CashOperation, Long> {

  /**
   * Returns the net-deposit data used by the dashboard, aggregated by account, day and category.
   *
   * <p>The dashboard previously loaded every row from {@code normalized_cash_operations} three
   * times and performed the grouping in Java. Keeping the category and day dimensions preserves the
   * existing portfolio and benchmark calculations while substantially reducing transferred rows and
   * projection work.
   */
  @Query(
      value =
          """
          select
              min(nco.operation_id) as operationId,
              nco.account_id as accountId,
              max(nco.account_currency::text) as accountCurrency,
              max(nco.currency::text) as currency,
              max(nco.base_currency::text) as baseCurrency,
              null::text as rawOperation,
              nco.normalized_category as normalizedCategory,
              null::bigint as assetId,
              null::text as symbol,
              sum(nco.account_flow_amount) as amount,
              sum(nco.account_flow_amount_in_portfolio_base_currency) as amountInPortfolioBaseCurrency,
              sum(nco.account_flow_amount_in_portfolio_base_currency) as accountFlowAmountInPortfolioBaseCurrency,
              null::text as portfolioConversionStatus,
              sum(nco.account_flow_amount_in_account_currency) as amountInAccountCurrency,
              null::text as accountConversionStatus,
              null::text as comment,
              nco.date::date as date,
              min(nco.rate_month) as rateMonth,
              null::double precision as fxRateToBase
          from investory.normalized_cash_operation_flows nco
          where nco.account_id in (:accountIds)
            and nco.normalized_category in (
                'EXTERNAL_DEPOSIT',
                'EXTERNAL_WITHDRAWAL',
                'INTERNAL_TRANSFER_IN',
                'INTERNAL_TRANSFER_OUT',
                'INTERNAL_BOOKKEEPING',
                'FX_CONVERSION',
                'CORRECTION'
            )
          group by nco.account_id, nco.date::date, nco.normalized_category
          order by nco.account_id, nco.date::date, nco.normalized_category
          """,
      nativeQuery = true)
  List<NormalizedCashOperationRow> findAllByAccountIdIn(
      @Param("accountIds") Collection<Long> accountIds);

  @Query(
      value =
          """
          select
              nco.operation_id as operationId,
              nco.account_id as accountId,
              nco.account_currency as accountCurrency,
              nco.currency as currency,
              nco.base_currency as baseCurrency,
              nco.raw_operation as rawOperation,
              nco.normalized_category as normalizedCategory,
              nco.asset_id as assetId,
              asset.symbol as symbol,
              nco.amount as amount,
              nco.amount_in_portfolio_base_currency as amountInPortfolioBaseCurrency,
              nco.account_flow_amount_in_portfolio_base_currency as accountFlowAmountInPortfolioBaseCurrency,
              nco.portfolio_conversion_status as portfolioConversionStatus,
              nco.amount_in_account_currency as amountInAccountCurrency,
              nco.account_conversion_status as accountConversionStatus,
              nco.comment as comment,
              nco.date::date as date,
              nco.rate_month as rateMonth,
              nco.fx_rate_to_base as fxRateToBase
          from investory.normalized_cash_operation_flows nco
          left join investory.assets asset on asset.id = nco.asset_id
          where nco.account_id in (:accountIds)
          order by nco.account_id, nco.date, nco.operation_id
          """,
      nativeQuery = true)
  List<NormalizedCashOperationRow> findAllDetailedByAccountIdIn(
      @Param("accountIds") Collection<Long> accountIds);

  interface NormalizedCashOperationRow {
    Long getOperationId();

    Long getAccountId();

    String getAccountCurrency();

    String getCurrency();

    String getBaseCurrency();

    String getRawOperation();

    String getNormalizedCategory();

    default Long getAssetId() {
      return null;
    }

    String getSymbol();

    Double getAmount();

    Double getAmountInPortfolioBaseCurrency();

    Double getAccountFlowAmountInPortfolioBaseCurrency();

    default Double getAmountInBaseCurrency() {
      return getAmountInPortfolioBaseCurrency();
    }

    String getPortfolioConversionStatus();

    Double getAmountInAccountCurrency();

    String getAccountConversionStatus();

    String getComment();

    LocalDate getDate();

    LocalDate getRateMonth();

    Double getFxRateToBase();
  }
}
