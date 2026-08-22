package com.smartbox.investory.longterm.api;

import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Stable annual simulation boundary owned by Long-Term Assets. */
public interface LongTermAnnualProjectionApi {
  AnnualProjection project(ProjectionRequest request);

  /**
   * Planning boundary used by Retirement.  The caller supplies Long-Term source facts and asks
   * only for annual cash flows and capital; all instrument interpretation stays here.
   */
  default PlanningProjection plan(PlanningRequest request) {
    throw new UnsupportedOperationException("Long-Term planning is not configured");
  }

  /** Non-consuming annual preparation. It never advances the Long-Term planning state. */
  default PlanningQuote quote(PlanningRequest request) {
    PlanningProjection projection = plan(new PlanningRequest(request.year(), BigDecimal.ZERO, request.state()));
    return new PlanningQuote(projection.year(), projection.plannedCashFlows(), projection.reserveTransfer(),
        projection.endCapital(), projection.source(), projection.capitalizedBondReturn());
  }

  record PlanningRequest(
      int year,
      BigDecimal requestedCapital,
      PlanningState state) {
    public PlanningRequest {
      requestedCapital = nz(requestedCapital).max(BigDecimal.ZERO);
      state = state == null ? PlanningState.EMPTY : state;
    }
  }

  /** Opaque-to-Retirement Long-Term planning state, based exclusively on Long-Term public data. */
  record PlanningState(
      List<LongTermAssetProjectionModel> assets,
      BigDecimal rentalIncomeGrowthRate,
      int rentalIncomeBaseYear,
      Source source) {
    public static final PlanningState EMPTY =
        new PlanningState(List.of(), BigDecimal.ZERO, 0, Source.PROJECTED);

    public PlanningState {
      assets = assets == null ? List.of() : List.copyOf(assets);
      rentalIncomeGrowthRate = nz(rentalIncomeGrowthRate);
      source = source == null ? Source.PROJECTED : source;
    }

    /** Active terminology; the legacy accessor remains source-compatible. */
    public BigDecimal rentalIncomeGrowthSpread() { return rentalIncomeGrowthRate; }
  }

  enum CashFlowKind { RENTAL_INCOME, FIXED_INCOME }

  record PlannedCashFlow(
      String id, String label, CashFlowKind kind, BigDecimal annualAmount, Source source) {
    public PlannedCashFlow {
      annualAmount = nz(annualAmount);
      source = source == null ? Source.PROJECTED : source;
    }
  }

  record PlanningProjection(
      int year,
      List<PlannedCashFlow> plannedCashFlows,
      BigDecimal reserveTransfer,
      BigDecimal requestedCapital,
      BigDecimal actualCapitalProvided,
      BigDecimal endCapital,
      PlanningState endState,
      Source source,
      BigDecimal capitalizedBondReturn) {
    public PlanningProjection(int year, List<PlannedCashFlow> plannedCashFlows,
        BigDecimal reserveTransfer, BigDecimal requestedCapital, BigDecimal actualCapitalProvided,
        BigDecimal endCapital, PlanningState endState, Source source) {
      this(year, plannedCashFlows, reserveTransfer, requestedCapital, actualCapitalProvided,
          endCapital, endState, source, BigDecimal.ZERO);
    }
    public PlanningProjection {
      plannedCashFlows = plannedCashFlows == null ? List.of() : List.copyOf(plannedCashFlows);
      reserveTransfer = nz(reserveTransfer);
      requestedCapital = nz(requestedCapital);
      actualCapitalProvided = nz(actualCapitalProvided);
      endCapital = nz(endCapital);
      endState = endState == null ? PlanningState.EMPTY : endState;
      source = source == null ? Source.PROJECTED : source;
      capitalizedBondReturn = nz(capitalizedBondReturn);
    }
  }

  record PlanningQuote(int year, List<PlannedCashFlow> plannedCashFlows,
      BigDecimal reserveTransfer, BigDecimal capitalAvailable, Source source,
      BigDecimal capitalizedBondReturn) {
    public PlanningQuote(int year, List<PlannedCashFlow> plannedCashFlows,
        BigDecimal reserveTransfer, BigDecimal capitalAvailable, Source source) {
      this(year, plannedCashFlows, reserveTransfer, capitalAvailable, source, BigDecimal.ZERO);
    }
    public PlanningQuote {
      plannedCashFlows = plannedCashFlows == null ? List.of() : List.copyOf(plannedCashFlows);
      reserveTransfer = nz(reserveTransfer);
      capitalAvailable = nz(capitalAvailable);
      source = source == null ? Source.PROJECTED : source;
      capitalizedBondReturn = nz(capitalizedBondReturn);
    }
  }

  /** Aggregate capital result; bond maturity remains inside the implementation. */
  default CapitalProjection projectCapital(ProjectionRequest request) {
    AnnualProjection annual = project(request);
    BigDecimal available = annual.maturedFunding();
    return new CapitalProjection(request.year(), BigDecimal.ZERO, annual.netBondIncome(),
        BigDecimal.ZERO, available, request.requiredFunding(), available, BigDecimal.ZERO,
        annual.source());
  }

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

  record RentalIncome(
      BigDecimal monthlyNetIncome,
      Source source,
      int baseYear,
      BigDecimal annualGrowthRate) {
    public RentalIncome(BigDecimal monthlyNetIncome, Source source) {
      this(monthlyNetIncome, source, 0, BigDecimal.ZERO);
    }

    public RentalIncome {
      monthlyNetIncome = nz(monthlyNetIncome);
      source = source == null ? Source.PROJECTED : source;
      annualGrowthRate = nz(annualGrowthRate);
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

  record CapitalProjection(
      int year,
      BigDecimal startValue,
      BigDecimal annualIncome,
      BigDecimal annualReturn,
      BigDecimal availableForWithdrawal,
      BigDecimal requestedWithdrawal,
      BigDecimal actualWithdrawal,
      BigDecimal endValue,
      Source source) {}

  enum Source {
    ACTUAL,
    PROJECTED
  }

  private static BigDecimal nz(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
