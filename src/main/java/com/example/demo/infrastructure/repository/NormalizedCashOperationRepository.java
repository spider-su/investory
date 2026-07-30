package com.example.demo.infrastructure.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface NormalizedCashOperationRepository extends Repository<CashOperation, Long> {

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
              nco.asset_id as symbol,
              nco.amount as amount,
              nco.amount_in_base_currency as amountInBaseCurrency,
              nco.comment as comment,
              nco.date::date as date,
              nco.rate_month as rateMonth,
              nco.base_to_operation_rate as baseToOperationRate
          from investory.normalized_cash_operations nco
          where nco.account_id in (:accountIds)
          order by nco.account_id, nco.date, nco.operation_id
          """,
      nativeQuery = true)
  List<NormalizedCashOperationRow> findAllByAccountIdIn(@Param("accountIds") Collection<Long> accountIds);

  interface NormalizedCashOperationRow {
    Long getOperationId();

    Long getAccountId();

    String getAccountCurrency();

    String getCurrency();

    String getBaseCurrency();

    String getRawOperation();

    String getNormalizedCategory();

    String getSymbol();

    Double getAmount();

    Double getAmountInBaseCurrency();

    String getComment();

    LocalDate getDate();

    LocalDate getRateMonth();

    Double getBaseToOperationRate();
  }
}
