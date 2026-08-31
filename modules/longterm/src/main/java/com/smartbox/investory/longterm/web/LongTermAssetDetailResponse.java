package com.smartbox.investory.longterm.web;

import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.api.model.CashFlowType;
import com.smartbox.investory.longterm.api.model.Frequency;
import com.smartbox.investory.longterm.api.model.RentalContractStatusModel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** HTTP-safe detail response; tenant identity is kept inside the MVC/application boundary. */
record LongTermAssetDetailResponse(
    AssetView asset,
    AssetSummaryView summary,
    BondDetailsView bondDetails,
    DepositDetailsView depositDetails,
    List<ValuationView> valuationPeriods,
    BigDecimal expectedPropertyGrowth,
    List<RentalContractResponse> contracts) {
  static LongTermAssetDetailResponse from(DetailView detail) {
    return new LongTermAssetDetailResponse(
        detail.asset(),
        detail.summary(),
        detail.bondDetails(),
        detail.depositDetails(),
        detail.valuationPeriods(),
        detail.expectedPropertyGrowth(),
        detail.contracts().stream().map(RentalContractResponse::from).toList());
  }

  record RentalContractResponse(
      Long id,
      LocalDate startDate,
      LocalDate endDate,
      LocalDate terminatedDate,
      LocalDate effectiveEndDate,
      RentalContractStatusModel status,
      Boolean rentalTaxPaidByTenant,
      BigDecimal monthlyTaxBase,
      List<RentalTermResponse> terms) {
    static RentalContractResponse from(RentalContractView contract) {
      return new RentalContractResponse(
          contract.id(),
          contract.startDate(),
          contract.endDate(),
          contract.terminatedDate(),
          contract.effectiveEndDate(),
          contract.status(),
          contract.rentalTaxPaidByTenant(),
          contract.monthlyTaxBase(),
          contract.terms().stream().map(RentalTermResponse::from).toList());
    }
  }

  record RentalTermResponse(
      CashFlowType type, BigDecimal amount, Frequency frequency, boolean paidByTenant) {
    static RentalTermResponse from(RentalTermView term) {
      return new RentalTermResponse(
          term.type(), term.amount(), term.frequency(), term.paidByTenant());
    }
  }
}
