package com.smartbox.investory.longterm.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Stable annual simulation boundary owned by Long-Term Assets. */
public interface LongTermAnnualProjectionApi {
  AnnualProjection project(ProjectionRequest request);

  record ProjectionRequest(
      int year,
      BigDecimal reserve,
      BigDecimal requiredFunding,
      List<Bond> bonds,
      List<RentalIncome> rentalIncome) {
    public ProjectionRequest {
      reserve = nz(reserve);
      requiredFunding = nz(requiredFunding);
      bonds = bonds == null ? List.of() : List.copyOf(bonds);
      rentalIncome = rentalIncome == null ? List.of() : List.copyOf(rentalIncome);
    }
  }

  record Bond(
      String id,
      BigDecimal principalValue,
      LocalDate maturityDate,
      BigDecimal redemptionValue,
      BigDecimal netAnnualIncome,
      MaturityStrategy maturityStrategy,
      int renewalTermYears,
      BigDecimal renewalNetRate) {
    public Bond {
      principalValue = nz(principalValue);
      redemptionValue = redemptionValue == null ? principalValue : redemptionValue;
      netAnnualIncome = nz(netAnnualIncome);
      maturityStrategy = maturityStrategy == null ? MaturityStrategy.REINVEST : maturityStrategy;
      renewalTermYears = renewalTermYears <= 0 ? 3 : renewalTermYears;
      renewalNetRate = nz(renewalNetRate);
    }
  }

  record RentalIncome(BigDecimal monthlyNetIncome, Source source) {
    public RentalIncome {
      monthlyNetIncome = nz(monthlyNetIncome);
      source = source == null ? Source.PROJECTED : source;
    }
  }

  record AnnualProjection(
      int year,
      BigDecimal monthlyNetRentalIncome,
      BigDecimal netBondIncome,
      BigDecimal reserveStart,
      BigDecimal reserveAfterMaturities,
      BigDecimal reserveUsed,
      BigDecimal maturedFunding,
      BigDecimal reserveEnd,
      List<Bond> nextBonds,
      Source source) {
    public AnnualProjection {
      monthlyNetRentalIncome = nz(monthlyNetRentalIncome);
      netBondIncome = nz(netBondIncome);
      reserveStart = nz(reserveStart);
      reserveAfterMaturities = nz(reserveAfterMaturities);
      reserveUsed = nz(reserveUsed);
      maturedFunding = nz(maturedFunding);
      reserveEnd = nz(reserveEnd);
      nextBonds = nextBonds == null ? List.of() : List.copyOf(nextBonds);
      source = source == null ? Source.PROJECTED : source;
    }
  }

  enum Source {
    ACTUAL,
    PROJECTED
  }

  private static BigDecimal nz(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
