package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.longterm.api.InterestTreatment;
import com.smartbox.investory.longterm.api.LongTermAssetType;
import com.smartbox.investory.retirement.profile.*;
import com.smartbox.investory.retirement.simulation.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Deterministic current-state to year-end bridge. It prorates only recurring funding and
 * continuously modelled market returns. Contractual payments, maturity redemptions and one-off
 * events remain calendar-bound and are therefore not invented by the bridge.
 */
@Service
public class CurrentYearProjectionBridge {
  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private final Clock clock;
  private final RetirementSimulationService simulations;
  private final ForwardSimulationContextFactory contexts;

  public CurrentYearProjectionBridge(Clock clock, RetirementSimulationService simulations) {
    this(clock, simulations, new ForwardSimulationContextFactory(clock));
  }

  @Autowired
  public CurrentYearProjectionBridge(
      Clock clock,
      RetirementSimulationService simulations,
      ForwardSimulationContextFactory contexts) {
    this.clock = clock;
    this.simulations = simulations;
    this.contexts = contexts;
  }

  public InvestmentProfile projectCurrentYearEnd(
      InvestmentProfile profile, SimulationAssumptions assumptions) {
    return projectCurrentYearEnd(contexts.create(profile, assumptions)).bridgedProfile();
  }

  public CurrentYearBridgeResult projectCurrentYearEnd(ForwardSimulationContext context) {
    InvestmentProfile profile = context.currentProfile();
    SimulationAssumptions assumptions = context.originalAssumptions();
    int currentYear = context.asOfYear();
    if (!context.requiresCurrentYearBridge())
      return result(
          context,
          profile,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO);
    BigDecimal fraction = remainingYearFraction(currentYear);
    LocalDate today = LocalDate.now(clock);
    SimulationYear expected =
        simulations
            .simulate(
                profile,
                assumptionsForYear(assumptions, currentYear),
                SimulationScenario.BASE,
                true)
            .years()
            .getFirst();
    BigDecimal eventExpenses =
        eventsFor(context.currentYearEvents(), SimulationEventType.ONE_OFF_EXPENSE);
    BigDecimal eventIncome =
        eventsFor(context.currentYearEvents(), SimulationEventType.ONE_OFF_INCOME);
    BigDecimal recurringExpenses = expected.coreExpenses().add(expected.discretionaryExpenses());
    BigDecimal fullYearContractualPayout =
        fullYearContractualPayout(profile.longTermAssets(), assumptions, currentYear, today);
    BigDecimal nonContractualPassive =
        expected.passiveIncome().subtract(fullYearContractualPayout).max(ZERO);
    ContractualSummary contractual =
        contractualSummary(profile.longTermAssets(), assumptions, currentYear, today, fraction);
    BigDecimal recurringIncome =
        nonContractualPassive
            .multiply(fraction)
            .add(contractual.payoutIncome())
            .add(expected.pensionIncome().multiply(fraction));
    BigDecimal netCashFlow =
        recurringIncome
            .subtract(recurringExpenses.multiply(fraction))
            .add(eventIncome)
            .subtract(eventExpenses);
    BigDecimal requiredFunding = netCashFlow.negate().max(ZERO);
    BigDecimal surplus = netCashFlow.max(ZERO);
    BigDecimal contribution = expected.preRetirementContribution().multiply(fraction);
    Map<Long, BigDecimal> manual = new LinkedHashMap<>();
    for (ProjectedLongTermAsset asset : profile.longTermAssets())
      manual.put(asset.id(), asset.currentValue());
    Map<Long, BigDecimal> originalManual = new LinkedHashMap<>(manual);
    EnumMap<EconomicBucket, BigDecimal> market = marketOnly(profile, originalManual);
    market.merge(EconomicBucket.LIQUID_CASH, contribution, BigDecimal::add);
    market.merge(
        EconomicBucket.LIQUID_CASH,
        contractual.redemptionCash().subtract(contractual.bondRedemptionCash()).max(ZERO),
        BigDecimal::add);
    BigDecimal remainingFunding = requiredFunding;
    if (assumptions.fundingStrategy() == SimulationFundingStrategy.RESERVE_AND_HARVEST)
      remainingFunding =
          withdrawManualReserve(
              profile.longTermAssets(), manual, remainingFunding, assumptions, currentYear);
    BigDecimal bondMaturityUsed = contractual.bondRedemptionCash().min(remainingFunding);
    BigDecimal bondReinvestment = contractual.bondRedemptionCash().subtract(bondMaturityUsed);
    remainingFunding = remainingFunding.subtract(bondMaturityUsed);
    remainingFunding = withdraw(market, EconomicBucket.LIQUID_CASH, remainingFunding);
    remainingFunding = withdraw(market, EconomicBucket.FIXED_INCOME, remainingFunding);
    if (assumptions.fundingStrategy() == SimulationFundingStrategy.SIMPLE_WATERFALL
        || assumptions.allowEmergencyEquityWithdrawal())
      remainingFunding = withdraw(market, EconomicBucket.EQUITY, remainingFunding);
    market.merge(EconomicBucket.LIQUID_CASH, surplus, BigDecimal::add);

    BigDecimal equityBeforeReturns = market.get(EconomicBucket.EQUITY);
    market.replaceAll(
        (bucket, amount) ->
            amount.multiply(BigDecimal.ONE.add(rateFor(bucket, assumptions).multiply(fraction))));
    List<ProjectedLongTermAsset> bridgedAssets =
        new ArrayList<>(
            profile.longTermAssets().stream()
                .map(
                    asset ->
                        isContractual(asset)
                            ? withValue(
                                asset,
                                contractualProjection(
                                        asset,
                                        manual.get(asset.id()),
                                        assumptions,
                                        currentYear,
                                        today,
                                        fraction)
                                    .endValue())
                            : asset.bucket() == EconomicBucket.REAL_ESTATE
                                ? withValue(asset, asset.currentValue())
                                : withValue(
                                    asset,
                                    manual
                                        .get(asset.id())
                                        .multiply(
                                            BigDecimal.ONE.add(
                                                manualRate(asset, assumptions, currentYear)
                                                    .multiply(fraction)))))
                .toList());
    if (bondReinvestment.signum() > 0)
      bridgedAssets.add(
          projectedLadderBond(-2_000_000_000L, currentYear, bondReinvestment, assumptions));
    BigDecimal target = expected.safeReserveTarget();
    if (assumptions.fundingStrategy() == SimulationFundingStrategy.RESERVE_AND_HARVEST)
      harvestEquity(
          market,
          target,
          bridgedAssets,
          assumptions,
          market.get(EconomicBucket.EQUITY).subtract(equityBeforeReturns).max(ZERO));
    InvestmentProfile bridged = rebuild(profile, market, bridgedAssets);
    return result(
        context,
        bridged,
        fraction,
        contribution,
        recurringExpenses.multiply(fraction),
        requiredFunding,
        nonContractualPassive.multiply(fraction).add(contractual.payoutIncome()),
        expected.pensionIncome().multiply(fraction),
        contractual.payoutIncome(),
        contractual.redemptionCash());
  }

  private static CurrentYearBridgeResult result(
      ForwardSimulationContext context,
      InvestmentProfile profile,
      BigDecimal fraction,
      BigDecimal contribution,
      BigDecimal spending,
      BigDecimal funding,
      BigDecimal passive,
      BigDecimal pension,
      BigDecimal contractualIncome,
      BigDecimal redemptionCash) {
    return new CurrentYearBridgeResult(
        profile,
        context.asOfYear(),
        context.firstProjectedYear(),
        context.asOfAge() >= context.originalAssumptions().retirementAge()
            ? SimulationLifecyclePhase.RETIRED
            : SimulationLifecyclePhase.WORKING,
        fraction,
        contribution,
        spending,
        funding,
        passive,
        pension,
        contractualIncome,
        redemptionCash,
        context.currentYearEvents());
  }

  private static BigDecimal eventsFor(List<SimulationEvent> events, SimulationEventType type) {
    return events.stream()
        .filter(event -> event.type() == type)
        .map(SimulationEvent::amount)
        .reduce(ZERO, BigDecimal::add);
  }

  private static BigDecimal fullYearContractualPayout(
      List<ProjectedLongTermAsset> assets,
      SimulationAssumptions assumptions,
      int year,
      LocalDate today) {
    return assets.stream()
        .filter(CurrentYearProjectionBridge::isContractual)
        .map(
            asset -> {
              if (asset.maturityDate() != null && year > asset.maturityDate().getYear())
                return ZERO;
              BigDecimal start = asset.currentValue();
              BigDecimal rate = contractualRate(asset, assumptions, year);
              BigDecimal gross = start.multiply(rate);
              return asset.interestTreatment() == InterestTreatment.PAY_OUT
                  ? netInterest(gross, asset)
                  : ZERO;
            })
        .reduce(ZERO, BigDecimal::add);
  }

  private static ContractualSummary contractualSummary(
      List<ProjectedLongTermAsset> assets,
      SimulationAssumptions assumptions,
      int year,
      LocalDate today,
      BigDecimal fraction) {
    BigDecimal payout = ZERO;
    BigDecimal redemption = ZERO;
    BigDecimal bondRedemption = ZERO;
    for (ProjectedLongTermAsset asset : assets) {
      if (!isContractual(asset)) continue;
      ContractualProjection projection =
          contractualProjection(asset, asset.currentValue(), assumptions, year, today, fraction);
      payout = payout.add(projection.payoutIncome());
      redemption = redemption.add(projection.redemptionCash());
      if (asset.type() == LongTermAssetType.BOND)
        bondRedemption = bondRedemption.add(projection.redemptionCash());
    }
    return new ContractualSummary(payout, redemption, bondRedemption);
  }

  private static ContractualProjection contractualProjection(
      ProjectedLongTermAsset asset,
      BigDecimal start,
      SimulationAssumptions assumptions,
      int year,
      LocalDate today,
      BigDecimal fraction) {
    if (start.signum() == 0
        || (asset.maturityDate() != null && !asset.maturityDate().isAfter(today)))
      return new ContractualProjection(start, ZERO, ZERO);
    BigDecimal net =
        netInterest(start.multiply(contractualRate(asset, assumptions, year)), asset)
            .multiply(fraction);
    BigDecimal end =
        asset.interestTreatment() == InterestTreatment.CAPITALIZE ? start.add(net) : start;
    BigDecimal payout = asset.interestTreatment() == InterestTreatment.PAY_OUT ? net : ZERO;
    BigDecimal redemption = ZERO;
    if (asset.maturityDate() != null && asset.maturityDate().getYear() == year) {
      redemption = asset.redemptionValue() == null ? end : asset.redemptionValue();
      end = ZERO;
    }
    return new ContractualProjection(end, payout, redemption);
  }

  private static BigDecimal contractualRate(
      ProjectedLongTermAsset asset, SimulationAssumptions assumptions, int year) {
    return asset.periods().stream()
        .filter(
            period ->
                period.validFrom().getYear() <= year
                    && (period.validTo() == null || period.validTo().getYear() >= year))
        .map(ProjectedLongTermAsset.Period::annualReturnRate)
        .findFirst()
        .orElse(
            switch (asset.bucket()) {
              case FIXED_INCOME -> assumptions.fixedIncomeReturnRate();
              case LIQUID_CASH -> assumptions.cashReturnRate();
              default -> ZERO;
            });
  }

  private static BigDecimal netInterest(BigDecimal gross, ProjectedLongTermAsset asset) {
    return gross.subtract(gross.multiply(Optional.ofNullable(asset.taxRate()).orElse(ZERO)));
  }

  private static boolean isContractual(ProjectedLongTermAsset asset) {
    return asset.type() == LongTermAssetType.BOND
        || asset.type() == LongTermAssetType.DEPOSIT
        || (asset.bucket() == EconomicBucket.FIXED_INCOME && asset.interestTreatment() != null);
  }

  private record ContractualProjection(
      BigDecimal endValue, BigDecimal payoutIncome, BigDecimal redemptionCash) {}

  private record ContractualSummary(
      BigDecimal payoutIncome, BigDecimal redemptionCash, BigDecimal bondRedemptionCash) {}

  private static void harvestEquity(
      EnumMap<EconomicBucket, BigDecimal> market,
      BigDecimal target,
      List<ProjectedLongTermAsset> assets,
      SimulationAssumptions assumptions,
      BigDecimal equityGain) {
    if (assumptions.equityReturnRate().compareTo(assumptions.equityHarvestMinimumReturnRate()) < 0)
      return;
    BigDecimal reserve =
        market
            .getOrDefault(EconomicBucket.LIQUID_CASH, ZERO)
            .add(market.getOrDefault(EconomicBucket.FIXED_INCOME, ZERO))
            .add(
                assets.stream()
                    .filter(asset -> asset.type() == LongTermAssetType.CASH_RESERVE)
                    .map(ProjectedLongTermAsset::currentValue)
                    .reduce(ZERO, BigDecimal::add));
    BigDecimal transfer =
        target
            .subtract(reserve)
            .max(ZERO)
            .min(equityGain.multiply(assumptions.equityGainHarvestRate()))
            .min(market.getOrDefault(EconomicBucket.EQUITY, ZERO));
    market.merge(EconomicBucket.EQUITY, transfer.negate(), BigDecimal::add);
    market.merge(EconomicBucket.FIXED_INCOME, transfer, BigDecimal::add);
  }

  private static InvestmentProfile rebuild(
      InvestmentProfile profile,
      Map<EconomicBucket, BigDecimal> market,
      List<ProjectedLongTermAsset> assets) {
    EnumMap<EconomicBucket, BigDecimal> totals = new EnumMap<>(EconomicBucket.class);
    for (EconomicBucket bucket : EconomicBucket.values())
      totals.put(bucket, market.getOrDefault(bucket, ZERO));
    for (ProjectedLongTermAsset asset : assets)
      totals.merge(asset.bucket(), asset.currentValue(), BigDecimal::add);
    List<ProfileAllocation> allocations =
        totals.entrySet().stream()
            .map(
                entry ->
                    new ProfileAllocation(
                        entry.getKey(), entry.getValue(), ZERO, liquidity(entry.getKey())))
            .toList();
    BigDecimal longTerm =
        assets.stream().map(ProjectedLongTermAsset::currentValue).reduce(ZERO, BigDecimal::add);
    BigDecimal total = totals.values().stream().reduce(ZERO, BigDecimal::add);
    BigDecimal liquid =
        totals
            .getOrDefault(EconomicBucket.LIQUID_CASH, ZERO)
            .add(totals.getOrDefault(EconomicBucket.FIXED_INCOME, ZERO))
            .add(totals.getOrDefault(EconomicBucket.EQUITY, ZERO));
    return new InvestmentProfile(
        profile.portfolioId(),
        profile.currency(),
        total.subtract(longTerm).max(ZERO),
        longTerm,
        total,
        profile.historicalMarketInvestmentIncome(),
        profile.expectedLongTermAssetIncome(),
        profile.totalInvestmentIncome(),
        liquid,
        total.subtract(liquid),
        allocations,
        assets);
  }

  BigDecimal remainingYearFraction(int year) {
    LocalDate today = LocalDate.now(clock);
    if (today.getYear() != year)
      return today.isAfter(Year.of(year).atDay(1)) ? ZERO : BigDecimal.ONE;
    return BigDecimal.valueOf(Year.of(year).length() - today.getDayOfYear())
        .divide(BigDecimal.valueOf(Year.of(year).length()), 12, java.math.RoundingMode.HALF_UP);
  }

  private static EnumMap<EconomicBucket, BigDecimal> marketOnly(
      InvestmentProfile profile, Map<Long, BigDecimal> manual) {
    EnumMap<EconomicBucket, BigDecimal> values = new EnumMap<>(EconomicBucket.class);
    for (EconomicBucket bucket : EconomicBucket.values()) values.put(bucket, ZERO);
    for (ProfileAllocation allocation : profile.allocations())
      values.merge(allocation.bucket(), allocation.value(), BigDecimal::add);
    for (ProjectedLongTermAsset asset : profile.longTermAssets())
      values.merge(asset.bucket(), manual.get(asset.id()).negate(), BigDecimal::add);
    values.replaceAll((bucket, value) -> value.max(ZERO));
    return values;
  }

  private static BigDecimal withdrawManualReserve(
      List<ProjectedLongTermAsset> assets,
      Map<Long, BigDecimal> manual,
      BigDecimal need,
      SimulationAssumptions assumptions,
      int year) {
    BigDecimal left = need;
    for (ProjectedLongTermAsset asset :
        assets.stream()
            .filter(a -> a.type() == LongTermAssetType.CASH_RESERVE)
            .sorted(
                Comparator.comparing(
                        (ProjectedLongTermAsset asset) -> manualRate(asset, assumptions, year))
                    .thenComparing(ProjectedLongTermAsset::id))
            .toList()) {
      BigDecimal used = manual.get(asset.id()).min(left);
      manual.put(asset.id(), manual.get(asset.id()).subtract(used));
      left = left.subtract(used);
    }
    return left;
  }

  private static BigDecimal withdraw(
      Map<EconomicBucket, BigDecimal> values, EconomicBucket bucket, BigDecimal need) {
    BigDecimal used = values.getOrDefault(bucket, ZERO).min(need);
    values.merge(bucket, used.negate(), BigDecimal::add);
    return need.subtract(used);
  }

  private static BigDecimal rateFor(EconomicBucket bucket, SimulationAssumptions a) {
    return switch (bucket) {
      case LIQUID_CASH -> a.cashReturnRate();
      case FIXED_INCOME -> a.fixedIncomeReturnRate();
      case EQUITY -> a.equityReturnRate();
      case REAL_ESTATE -> ZERO;
      case OTHER -> a.otherReturnRate();
    };
  }

  private static BigDecimal manualRate(
      ProjectedLongTermAsset asset, SimulationAssumptions a, int year) {
    Optional<BigDecimal> active =
        asset.periods().stream()
            .filter(
                period ->
                    period.validFrom().getYear() <= year
                        && (period.validTo() == null || period.validTo().getYear() >= year))
            .map(ProjectedLongTermAsset.Period::annualReturnRate)
            .findFirst();
    if (asset.type() == LongTermAssetType.CASH_RESERVE) return active.orElse(a.cashReturnRate());
    if (asset.bucket() == EconomicBucket.REAL_ESTATE) return ZERO;
    return asset.bucket() == EconomicBucket.OTHER
        ? active.filter(rate -> rate.signum() != 0).orElse(a.otherReturnRate())
        : ZERO;
  }

  private static Liquidity liquidity(EconomicBucket bucket) {
    return switch (bucket) {
      case LIQUID_CASH, FIXED_INCOME, EQUITY -> Liquidity.LIQUID;
      default -> Liquidity.ILLIQUID;
    };
  }

  private static ProjectedLongTermAsset withValue(ProjectedLongTermAsset asset, BigDecimal value) {
    return new ProjectedLongTermAsset(
        asset.id(),
        asset.name(),
        asset.type(),
        asset.bucket(),
        asset.currency(),
        value,
        asset.liquidity(),
        asset.periods(),
        asset.maturityDate(),
        asset.redemptionValue(),
        asset.interestTreatment(),
        asset.taxRate(),
        asset.taxBase());
  }

  private static ProjectedLongTermAsset projectedLadderBond(
      long id, int maturityYear, BigDecimal principal, SimulationAssumptions assumptions) {
    return new ProjectedLongTermAsset(
        id,
        "Projected bond ladder",
        LongTermAssetType.BOND,
        EconomicBucket.FIXED_INCOME,
        com.smartbox.investory.shared.currency.CurrencyType.USD,
        principal,
        Liquidity.LIQUID,
        List.of(
            new ProjectedLongTermAsset.Period(
                LocalDate.of(maturityYear, 1, 1),
                null,
                ZERO,
                ZERO,
                assumptions.fixedIncomeReturnRate())),
        LocalDate.of(maturityYear + 3, 12, 31),
        principal,
        InterestTreatment.CAPITALIZE,
        ZERO,
        ZERO);
  }

  private static int age(SimulationAssumptions a, int year) {
    return a.currentAge() + year - a.startYear();
  }

  private static SimulationAssumptions assumptionsForYear(SimulationAssumptions a, int year) {
    int offset = year - a.startYear();
    return new SimulationAssumptions(
        age(a, year),
        a.endAge(),
        grow(a.annualLivingExpenses(), a.spendingGrowthRate(), offset),
        a.inflationRate(),
        a.cashReturnRate(),
        a.fixedIncomeReturnRate(),
        a.equityReturnRate(),
        a.realEstateReturnRate(),
        a.otherReturnRate(),
        a.pensionStartAge(),
        a.annualPension(),
        a.capitalGainTaxRate(),
        year,
        grow(a.annualDiscretionaryExpenses(), a.spendingGrowthRate(), offset),
        a.futureEvents(),
        a.rentalIncomeGrowthRate(),
        a.spendingGrowthRate(),
        a.fundingStrategy(),
        a.safeReserveYears(),
        a.equityHarvestMinimumReturnRate(),
        a.equityGainHarvestRate(),
        a.allowEmergencyEquityWithdrawal(),
        a.retirementAge(),
        a.annualEmploymentIncome(),
        a.annualPreRetirementContribution());
  }

  private static BigDecimal grow(BigDecimal value, BigDecimal rate, int years) {
    BigDecimal result = value;
    for (int i = 0; i < Math.max(0, years); i++) result = result.multiply(BigDecimal.ONE.add(rate));
    return result;
  }
}
