package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.LongTermAnnualProjectionApi;
import com.smartbox.investory.longterm.api.MaturityStrategy;
import com.smartbox.investory.longterm.api.model.InterestTreatmentModel;
import com.smartbox.investory.longterm.api.model.RentalIncomeProjectionModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** Owns annual rental, reserve, bond maturity, and reinvestment mechanics. */
@Service
public class LongTermAnnualProjectionService implements LongTermAnnualProjectionApi {
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  /**
   * Converts Long-Term source facts into yearly cash flows and capital. Retirement never sees
   * asset type, rate period, tax, rental contract, or maturity details.
   */
  @Override
  public PlanningProjection plan(PlanningRequest request) {
    List<PlannedCashFlow> flows = new ArrayList<>();
    List<LongTermAssetProjectionModel> nextAssets = new ArrayList<>();
    BigDecimal reserveTransfer = ZERO;
    BigDecimal availableCapital = ZERO;
    BigDecimal endCapital = ZERO;
    for (LongTermAssetProjectionModel asset : request.state().assets()) {
      if (asset.type() == LongTermAssetTypeModel.CASH_RESERVE) {
        reserveTransfer = reserveTransfer.add(nz(asset.currentValue()));
        continue;
      }
      if (asset.type() == LongTermAssetTypeModel.REAL_ESTATE) {
        BigDecimal rental = annualRentalIncome(asset, request.year(), request.state());
        if (rental.signum() != 0)
          flows.add(new PlannedCashFlow("long-term-rental-" + asset.id(), asset.name(), CashFlowKind.RENTAL_INCOME, rental,
              request.state().source()));
        nextAssets.add(asset);
        endCapital = endCapital.add(nz(asset.currentValue()));
        continue;
      }
      if (asset.type() == LongTermAssetTypeModel.BOND
          || asset.type() == LongTermAssetTypeModel.DEPOSIT) {
        BigDecimal value = nz(asset.currentValue());
        BigDecimal netInterest = netBondInterest(asset, request.year());
        boolean paysOut = asset.interestTreatment() != InterestTreatmentModel.CAPITALIZE;
        if (paysOut && netInterest.signum() != 0)
          flows.add(new PlannedCashFlow("long-term-interest-" + asset.id(), asset.name(), CashFlowKind.FIXED_INCOME, netInterest,
              request.state().source()));
        BigDecimal carriedValue = paysOut ? value : value.add(netInterest);
        boolean matured = asset.maturityDate() != null
            && !asset.maturityDate().isAfter(LocalDate.of(request.year(), 12, 31));
        if (matured) {
          availableCapital = availableCapital.add(
              asset.redemptionValue() == null ? carriedValue : asset.redemptionValue());
        } else {
          nextAssets.add(withCurrentValue(asset, carriedValue));
          endCapital = endCapital.add(carriedValue);
        }
        continue;
      }
      nextAssets.add(asset);
      endCapital = endCapital.add(nz(asset.currentValue()));
    }
    BigDecimal actual = availableCapital.min(request.requestedCapital());
    BigDecimal unspentMaturity = availableCapital.subtract(actual);
    // Unspent maturity remains Long-Term capital for the next deterministic year.
    if (unspentMaturity.signum() > 0) {
      nextAssets.add(new LongTermAssetProjectionModel(
          null, "matured-long-term-capital", LongTermAssetTypeModel.DEPOSIT,
          null, unspentMaturity, List.of(), null, unspentMaturity,
          InterestTreatmentModel.CAPITALIZE, ZERO));
      endCapital = endCapital.add(unspentMaturity);
    }
    PlanningState endState = new PlanningState(nextAssets, request.state().rentalIncomeGrowthRate(),
        request.state().rentalIncomeBaseYear(), request.state().source());
    return new PlanningProjection(request.year(), flows, reserveTransfer, request.requestedCapital(),
        actual, endCapital, endState, request.state().source());
  }

  private static BigDecimal annualRentalIncome(
      LongTermAssetProjectionModel asset, int year, PlanningState state) {
    if (asset.rentalContracts().isEmpty()
        && asset.periods().stream().noneMatch(period -> period.cashFlowType() != null)) {
      BigDecimal annual =
          asset.periods().stream()
              .filter(period -> applies(period, year))
              .map(period -> nz(period.annualIncome()).subtract(nz(period.annualExpense())))
              .reduce(ZERO, BigDecimal::add);
      int elapsed = Math.max(0, year - state.rentalIncomeBaseYear());
      return annual
          .multiply(BigDecimal.ONE.add(state.rentalIncomeGrowthRate()).pow(elapsed))
          .max(ZERO);
    }
    int baseYear =
        state.rentalIncomeBaseYear() > 0 && state.rentalIncomeBaseYear() <= year
            ? state.rentalIncomeBaseYear()
            : year;
    var previousIncome = java.util.Map.<com.smartbox.investory.longterm.api.model.CashFlowTypeModel, BigDecimal>of();
    RentalIncomeProjectionModel.Result projection = null;
    for (int projectedYear = baseYear; projectedYear <= year; projectedYear++) {
      projection =
          RentalIncomeProjectionModel.project(
              asset, previousIncome, projectedYear, state.rentalIncomeGrowthRate());
      previousIncome = projection.incomeByType();
    }
    // Ordinary growth never turns a rental cash flow into a negative income source. An explicit
    // zero replacement remains zero; asset-level expenses stay within Long-Term economics.
    return projection == null ? ZERO : projection.netIncome().max(ZERO);
  }

  private static BigDecimal netBondInterest(LongTermAssetProjectionModel asset, int year) {
    var activePeriods = asset.periods().stream().filter(period -> applies(period, year)).toList();
    BigDecimal declaredIncome = activePeriods.stream()
        .map(LongTermAssetProjectionModel.Period::annualIncome)
        .filter(java.util.Objects::nonNull)
        .reduce(ZERO, BigDecimal::add);
    if (declaredIncome.signum() != 0) return declaredIncome;
    BigDecimal rate = activePeriods.stream()
        .map(LongTermAssetProjectionModel.Period::annualReturnRate).filter(java.util.Objects::nonNull)
        .findFirst().orElse(ZERO);
    return nz(asset.currentValue()).multiply(rate).multiply(BigDecimal.ONE.subtract(nz(asset.taxRate())));
  }

  private static LongTermAssetProjectionModel withCurrentValue(
      LongTermAssetProjectionModel asset, BigDecimal currentValue) {
    return new LongTermAssetProjectionModel(
        asset.id(), asset.name(), asset.type(), asset.currency(), currentValue, asset.periods(),
        asset.rentalContracts(), asset.maturityDate(), asset.redemptionValue(),
        asset.interestTreatment(), asset.taxRate(), asset.taxBase(), asset.rentalTaxPaidByTenant());
  }

  private static boolean applies(LongTermAssetProjectionModel.Period period, int year) {
    return period.validFrom() == null || (period.validFrom().getYear() <= year
        && (period.validTo() == null || period.validTo().getYear() >= year));
  }

  private static BigDecimal nz(BigDecimal value) { return value == null ? ZERO : value; }

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
