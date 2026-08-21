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
  public AnnualProjection project(ProjectionRequest request) {
    BigDecimal reserveStart = request.reserve();
    BigDecimal reserve = reserveStart;
    BigDecimal maturedFunding = BigDecimal.ZERO;
    BigDecimal bondIncome = BigDecimal.ZERO;
    List<Bond> next = new ArrayList<>();
    for (Bond bond : request.bonds()) {
      bondIncome = bondIncome.add(bond.netAnnualIncome());
      if (bond.maturityDate() == null || bond.maturityDate().getYear() != request.year()) {
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
        BigDecimal direct = proceeds.min(request.requiredFunding());
        maturedFunding = maturedFunding.add(direct);
        reserve = reserve.add(proceeds.subtract(direct));
      }
    }
    BigDecimal reserveUsed = reserve.min(request.requiredFunding().subtract(maturedFunding).max(BigDecimal.ZERO));
    reserve = reserve.subtract(reserveUsed);
    BigDecimal rental = request.rentalIncome().stream()
        .map(LongTermAnnualProjectionApi.RentalIncome::monthlyNetIncome)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new AnnualProjection(
        request.year(), rental, bondIncome, reserveStart,
        reserve.add(reserveUsed), reserveUsed, maturedFunding, reserve, next,
        request.rentalIncome().stream().anyMatch(i -> i.source() == LongTermAnnualProjectionApi.Source.ACTUAL)
            ? LongTermAnnualProjectionApi.Source.ACTUAL
            : LongTermAnnualProjectionApi.Source.PROJECTED);
  }
}
