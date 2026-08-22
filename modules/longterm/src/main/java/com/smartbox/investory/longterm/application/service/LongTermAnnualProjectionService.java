package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.LongTermAnnualProjectionApi;
import com.smartbox.investory.longterm.api.MaturityStrategy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** Owns annual rental, reserve, bond maturity, and reinvestment mechanics. */
@Service
public class LongTermAnnualProjectionService implements LongTermAnnualProjectionApi {
  @Override
  public CapitalProjection projectCapital(ProjectionRequest request) {
    AnnualProjection annual = project(request);
    BigDecimal start = request.bonds().stream()
        .map(Bond::principalValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal end = annual.nextBonds().stream()
        .map(Bond::principalValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal actual = annual.maturedFunding().min(annual.maturedFunding().max(BigDecimal.ZERO));
    return new CapitalProjection(
        annual.year(), start, annual.netBondIncome(), BigDecimal.ZERO,
        annual.maturedFunding(), request.requiredFunding(), actual, end, annual.source());
  }

  @Override
  public AnnualProjection project(ProjectionRequest request) {
    BigDecimal reserveStart = request.reserve();
    BigDecimal reserve = reserveStart;
    BigDecimal maturedFunding = BigDecimal.ZERO;
    BigDecimal fundGapProceeds = BigDecimal.ZERO;
    BigDecimal bondIncome = BigDecimal.ZERO;
    List<Bond> next = new ArrayList<>();
    for (Bond bond : request.bonds()) {
      bondIncome = bondIncome.add(bond.netAnnualIncome());
      if (bond.maturityDate() == null
          || bond.maturityDate().isAfter(LocalDate.of(request.year(), 12, 31))) {
        next.add(bond);
        continue;
      }
      BigDecimal proceeds = bond.redemptionValue();
      MaturityStrategy strategy = bond.maturityStrategy();
      if (strategy == MaturityStrategy.REINVEST) {
        next.add(
            new Bond(
                bond.id(),
                proceeds,
                LocalDate.of(request.year() + bond.renewalTermYears(), 12, 31),
                proceeds,
                proceeds.multiply(bond.renewalNetRate()),
                MaturityStrategy.REINVEST,
                bond.renewalTermYears(),
                bond.renewalNetRate()));
      } else if (strategy == MaturityStrategy.MOVE_TO_RESERVE) {
        reserve = reserve.add(proceeds);
      } else {
        fundGapProceeds = fundGapProceeds.add(proceeds);
      }
    }
    BigDecimal reserveBeforeFunding = reserve;
    BigDecimal reserveUsed = reserve.min(request.requiredFunding());
    reserve = reserve.subtract(reserveUsed);
    maturedFunding = fundGapProceeds.min(request.requiredFunding().subtract(reserveUsed).max(BigDecimal.ZERO));
    reserve = reserve.add(fundGapProceeds.subtract(maturedFunding));
    BigDecimal rental = request.rentalIncome().stream()
        .map(income -> projectedRentalIncome(income, request.year()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new AnnualProjection(
        request.year(), rental, bondIncome, reserveStart,
        reserveBeforeFunding, reserveUsed, maturedFunding, reserve, next,
        !request.rentalIncome().isEmpty()
                && request.rentalIncome().stream()
                    .allMatch(i -> i.source() == LongTermAnnualProjectionApi.Source.ACTUAL)
            ? LongTermAnnualProjectionApi.Source.ACTUAL
            : LongTermAnnualProjectionApi.Source.PROJECTED);
  }

  private static BigDecimal projectedRentalIncome(
      RentalIncome income, int year) {
    int elapsedYears = Math.max(0, year - income.baseYear());
    return income.monthlyNetIncome()
        .multiply(BigDecimal.ONE.add(income.annualGrowthRate()).pow(elapsedYears));
  }
}
