package com.smartbox.investory.application.longterm.bootstrap;

import com.smartbox.investory.infrastructure.longterm.CashFlowType;
import com.smartbox.investory.infrastructure.longterm.Frequency;
import com.smartbox.investory.infrastructure.longterm.InterestTreatment;
import com.smartbox.investory.infrastructure.longterm.LongTermAssetType;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record LongTermAssetBootstrapDocument(
    Long portfolioId, List<TaxPolicy> rentalTaxPolicies, List<Asset> assets) {
  public record TaxPolicy(LocalDate validFrom, LocalDate validTo, BigDecimal rate) {}

  public record Asset(
      String externalKey,
      LongTermAssetType type,
      String name,
      CurrencyType currency,
      LocalDate acquisitionDate,
      BigDecimal acquisitionValue,
      BigDecimal currentValue,
      LocalDate effectiveFrom,
      String notes,
      List<CashFlow> cashFlows,
      List<Period> valuationPeriods,
      List<Period> bondRatePeriods,
      Bond bond,
      Deposit deposit) {}

  public record CashFlow(
      CashFlowType type,
      BigDecimal amount,
      Frequency frequency,
      LocalDate validFrom,
      LocalDate validTo) {}

  public record Period(LocalDate validFrom, LocalDate validTo, BigDecimal annualRate) {}

  public record Bond(
      LocalDate maturityDate,
      InterestTreatment interestTreatment,
      BigDecimal taxRate,
      BigDecimal redemptionValue) {}

  public record Deposit(
      LocalDate maturityDate,
      InterestTreatment interestTreatment,
      BigDecimal annualInterestRate,
      BigDecimal taxRate) {}
}
