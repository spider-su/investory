package com.smartbox.investory.application.simulation;

import com.smartbox.investory.application.longterm.RentalIncomeProjection;
import com.smartbox.investory.application.profile.*;
import com.smartbox.investory.infrastructure.longterm.CashFlowType;
import com.smartbox.investory.infrastructure.longterm.InterestTreatment;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;

/** Pure deterministic annual projection. It never writes to accounting data. */
@Service
public class RetirementSimulationService {
  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private static final BigDecimal BOND_LADDER_YEARS = new BigDecimal("3");

  public SimulationResult simulate(
      InvestmentProfile profile, SimulationAssumptions assumptions, SimulationScenario scenario) {
    SimulationScenarioSettings settings =
        SimulationScenarioSettings.forScenario(scenario, assumptions);
    EnumMap<EconomicBucket, BigDecimal> market = initialMarket(profile);
    Map<Long, BigDecimal> manual = new LinkedHashMap<>();
    for (ProjectedLongTermAsset asset : profile.longTermAssets())
      if (!isRentalProperty(asset)) manual.put(asset.id(), asset.currentValue());
    Map<Long, Map<CashFlowType, BigDecimal>> rentalIncome = new LinkedHashMap<>();
    List<ProjectedLongTermAsset> ladderBonds = new ArrayList<>();
    BigDecimal coreExpenses = assumptions.annualLivingExpenses();
    BigDecimal discretionaryExpenses = assumptions.annualDiscretionaryExpenses();
    List<SimulationYear> years = new ArrayList<>();
    Integer failureAge = null;
    BigDecimal firstFailureShortfall = ZERO;
    BigDecimal totalUnfunded = ZERO;
    for (int age = assumptions.currentAge(); age <= assumptions.endAge(); age++) {
      int calendarYear = assumptions.startYear() + age - assumptions.currentAge();
      boolean retired = age >= assumptions.retirementAge();
      SimulationLifecyclePhase lifecyclePhase =
          retired ? SimulationLifecyclePhase.RETIRED : SimulationLifecyclePhase.WORKING;
      BigDecimal employmentIncome = retired ? ZERO : assumptions.annualEmploymentIncome();
      BigDecimal preRetirementContribution =
          retired ? ZERO : assumptions.annualPreRetirementContribution();
      BigDecimal yearCoreExpenses = coreExpenses;
      BigDecimal yearDiscretionaryExpenses = discretionaryExpenses;
      BigDecimal startNetWorth = sum(market.values()).add(sum(manual.values()));
      ManualYear manualYear =
          projectManualAssets(
              simulationAssets(profile.longTermAssets(), ladderBonds),
              manual,
              calendarYear,
              rentalIncome,
              calendarYear,
              settings);
      BigDecimal pension =
          age >= assumptions.pensionStartAge() ? assumptions.annualPension() : ZERO;
      BigDecimal passive = manualYear.passiveIncome();
      BigDecimal eventExpenses =
          eventsFor(assumptions.futureEvents(), calendarYear, SimulationEventType.ONE_OFF_EXPENSE);
      BigDecimal eventIncome =
          eventsFor(assumptions.futureEvents(), calendarYear, SimulationEventType.ONE_OFF_INCOME);
      BigDecimal retirementIncome = passive.add(pension).add(eventIncome);
      BigDecimal totalExpenses = yearCoreExpenses.add(yearDiscretionaryExpenses).add(eventExpenses);
      BigDecimal totalIncome = employmentIncome.add(retirementIncome);
      BigDecimal required = totalExpenses.subtract(totalIncome).max(ZERO);
      BigDecimal recurringFundingGap =
          yearCoreExpenses
              .add(yearDiscretionaryExpenses)
              .subtract(passive)
              .subtract(pension)
              .max(ZERO);
      BigDecimal safeReserveTarget =
          retired && assumptions.fundingStrategy() == SimulationFundingStrategy.RESERVE_AND_HARVEST
              ? recurringFundingGap.multiply(assumptions.safeReserveYears())
              : ZERO;
      ManualWithdrawal manualWithdrawal =
          assumptions.fundingStrategy() == SimulationFundingStrategy.RESERVE_AND_HARVEST
              ? withdrawManualCashReserve(manual, manualYear.liquidReserveRates(), required)
              : new ManualWithdrawal(new LinkedHashMap<>(manual), ZERO);
      EnumMap<EconomicBucket, BigDecimal> availableMarket = new EnumMap<>(market);
      availableMarket.merge(EconomicBucket.LIQUID_CASH, preRetirementContribution, BigDecimal::add);
      BigDecimal safeReserveStart =
          manualYear.manualLiquidReserveStart().add(safeReserve(availableMarket));
      BigDecimal fundingAfterManualReserve = required.subtract(manualWithdrawal.amount()).max(ZERO);
      BigDecimal bondMaturityUsed = manualYear.bondRedemptionCash().min(fundingAfterManualReserve);
      BigDecimal remainingAfterBondMaturity = fundingAfterManualReserve.subtract(bondMaturityUsed);
      BigDecimal otherRedemptionCash =
          manualYear.redemptionCash().subtract(manualYear.bondRedemptionCash()).max(ZERO);
      availableMarket.merge(EconomicBucket.LIQUID_CASH, otherRedemptionCash, BigDecimal::add);
      Withdrawal withdrawal = withdraw(availableMarket, remainingAfterBondMaturity, assumptions);
      EnumMap<EconomicBucket, BigDecimal> afterWithdrawal = withdrawal.balances();
      BigDecimal actualWithdrawal =
          manualWithdrawal
              .amount()
              .add(bondMaturityUsed)
              .add(spendable(availableMarket).subtract(spendable(afterWithdrawal)));
      BigDecimal missing = required.subtract(actualWithdrawal).max(ZERO);
      boolean yearUnfunded = missing.signum() > 0;
      if (yearUnfunded && failureAge == null) {
        failureAge = age;
        firstFailureShortfall = missing;
      }
      boolean failed = failureAge != null;
      totalUnfunded = totalUnfunded.add(missing);
      BigDecimal surplus = retirementIncome.subtract(totalExpenses).max(ZERO);
      afterWithdrawal.merge(EconomicBucket.LIQUID_CASH, surplus, BigDecimal::add);
      EnumMap<EconomicBucket, BigDecimal> marketEnd = applyMarketReturns(afterWithdrawal, settings);
      Map<Long, BigDecimal> manualAfterWithdrawal = new LinkedHashMap<>(manualYear.values());
      manualYear
          .liquidReserveRates()
          .keySet()
          .forEach(
              id ->
                  manualAfterWithdrawal.put(id, manualWithdrawal.values().getOrDefault(id, ZERO)));
      Map<Long, BigDecimal> manualEnd =
          applyManualCashReserveReturns(
              manualAfterWithdrawal, manualYear.liquidReserveRates(), settings);
      BigDecimal reinvestment =
          manualYear.bondRedemptionCash().subtract(bondMaturityUsed).max(ZERO);
      if (reinvestment.signum() > 0) {
        ProjectedLongTermAsset ladderBond =
            ladderBond(-1_000_000_000L - ladderBonds.size(), calendarYear, reinvestment, settings);
        ladderBonds.add(ladderBond);
        manualEnd.put(ladderBond.id(), reinvestment);
      }
      BigDecimal equityGain =
          marketEnd
              .get(EconomicBucket.EQUITY)
              .subtract(afterWithdrawal.get(EconomicBucket.EQUITY))
              .max(ZERO);
      BigDecimal manualLiquidReserveEnd =
          sum(
              manualYear.liquidReserveRates().keySet().stream()
                  .map(id -> manualEnd.getOrDefault(id, ZERO))
                  .toList());
      BigDecimal eligibleEquityHarvest =
          eligibleEquityHarvest(marketEnd, equityGain, settings, assumptions);
      BigDecimal equityToFixedIncomeTransfer =
          harvestEquity(
              marketEnd,
              safeReserveTarget,
              manualLiquidReserveEnd,
              eligibleEquityHarvest,
              settings,
              assumptions);
      BigDecimal bondHarvest =
          harvestBondDeficit(
              marketEnd,
              eligibleEquityHarvest.subtract(equityToFixedIncomeTransfer).max(ZERO),
              recurringFundingGap,
              simulationAssets(profile.longTermAssets(), ladderBonds),
              manualEnd,
              settings,
              assumptions,
              retired);
      if (bondHarvest.signum() > 0) {
        ProjectedLongTermAsset ladderBond =
            ladderBond(-1_000_000_000L - ladderBonds.size(), calendarYear, bondHarvest, settings);
        ladderBonds.add(ladderBond);
        manualEnd.put(ladderBond.id(), bondHarvest);
      }
      BigDecimal cashStart =
          market
              .get(EconomicBucket.LIQUID_CASH)
              .add(manualYear.startByBucket().get(EconomicBucket.LIQUID_CASH));
      BigDecimal cashEnd =
          marketEnd
              .get(EconomicBucket.LIQUID_CASH)
              .add(manualYear.endByBucket().get(EconomicBucket.LIQUID_CASH));
      BigDecimal fixedStart =
          market
              .get(EconomicBucket.FIXED_INCOME)
              .add(manualYear.startByBucket().get(EconomicBucket.FIXED_INCOME));
      BigDecimal fixedEnd =
          marketEnd
              .get(EconomicBucket.FIXED_INCOME)
              .add(manualYear.endByBucket().get(EconomicBucket.FIXED_INCOME));
      BigDecimal equityStart =
          market
              .get(EconomicBucket.EQUITY)
              .add(manualYear.startByBucket().get(EconomicBucket.EQUITY));
      BigDecimal equityEnd =
          marketEnd
              .get(EconomicBucket.EQUITY)
              .add(manualYear.endByBucket().get(EconomicBucket.EQUITY));
      BigDecimal otherStart =
          market
              .get(EconomicBucket.OTHER)
              .add(manualYear.startByBucket().get(EconomicBucket.OTHER));
      BigDecimal otherEnd =
          marketEnd
              .get(EconomicBucket.OTHER)
              .add(manualYear.endByBucket().get(EconomicBucket.OTHER));
      // Property values are accounting data, not retirement-funding wealth.
      BigDecimal realEstateStart = ZERO;
      BigDecimal realEstateEnd = ZERO;
      BigDecimal bondValueEnd =
          bondValue(simulationAssets(profile.longTermAssets(), ladderBonds), manualEnd);
      BigDecimal safeReserveEnd = manualLiquidReserveEnd.add(safeReserve(marketEnd));
      BigDecimal spendableAssetsEnd =
          safeReserveEnd.add(
              assumptions.allowEmergencyEquityWithdrawal()
                      || assumptions.fundingStrategy() == SimulationFundingStrategy.SIMPLE_WATERFALL
                  ? equityEnd
                  : ZERO);
      BigDecimal financialAssetsEnd = safeReserveEnd.add(equityEnd).add(otherEnd);
      BigDecimal contractualAssetsEnd =
          manualYear.contractualAssetsEnd().add(reinvestment).add(bondHarvest);
      BigDecimal safeReserveCoverage =
          recurringFundingGap.signum() == 0
              ? ZERO
              : safeReserveEnd.divide(recurringFundingGap, 8, RoundingMode.HALF_UP);
      years.add(
          new SimulationYear(
              age,
              age - assumptions.currentAge(),
              startNetWorth,
              yearCoreExpenses,
              yearDiscretionaryExpenses,
              eventExpenses,
              totalExpenses,
              passive,
              pension,
              eventIncome,
              totalIncome,
              required,
              actualWithdrawal,
              manualWithdrawal.amount(),
              recurringFundingGap,
              safeReserveStart,
              safeReserveTarget,
              safeReserveEnd,
              safeReserveCoverage,
              settings.equityReturnRate(),
              equityGain,
              equityToFixedIncomeTransfer,
              withdrawal.emergencyEquityWithdrawal(),
              cashStart,
              cashEnd,
              fixedStart,
              fixedEnd,
              equityStart,
              equityEnd,
              realEstateStart,
              realEstateEnd,
              otherStart,
              otherEnd,
              manualYear.manualLiquidReserveStart(),
              manualLiquidReserveEnd,
              manualYear.contractualAssetsStart(),
              contractualAssetsEnd,
              spendableAssetsEnd,
              financialAssetsEnd,
              spendableAssetsEnd,
              contractualAssetsEnd,
              financialAssetsEnd.add(contractualAssetsEnd),
              failed,
              missing,
              lifecyclePhase,
              employmentIncome,
              preRetirementContribution,
              age == assumptions.retirementAge(),
              manualYear.rentalIncomeAmount(),
              totalExpenses.subtract(manualYear.rentalIncomeAmount()).max(ZERO),
              bondValueEnd,
              manualYear.bondIncome()));
      market = marketEnd;
      manual = manualEnd;
      rentalIncome = manualYear.rentalIncome();
      coreExpenses = grow(coreExpenses, settings.spendingGrowthRate());
      discretionaryExpenses = grow(discretionaryExpenses, settings.spendingGrowthRate());
    }
    return new SimulationResult(
        scenario, failureAge != null, failureAge, firstFailureShortfall, totalUnfunded, years);
  }

  public Map<SimulationScenario, SimulationResult> compareScenarios(
      InvestmentProfile profile, SimulationAssumptions assumptions) {
    EnumMap<SimulationScenario, SimulationResult> result = new EnumMap<>(SimulationScenario.class);
    for (SimulationScenario scenario : SimulationScenario.values())
      result.put(scenario, simulate(profile, assumptions, scenario));
    return result;
  }

  private static BigDecimal eventsFor(
      List<SimulationEvent> events, int year, SimulationEventType type) {
    return events.stream()
        .filter(event -> event.year() == year && event.type() == type)
        .map(SimulationEvent::amount)
        .reduce(ZERO, BigDecimal::add);
  }

  private EnumMap<EconomicBucket, BigDecimal> initialMarket(InvestmentProfile profile) {
    EnumMap<EconomicBucket, BigDecimal> result = new EnumMap<>(EconomicBucket.class);
    for (EconomicBucket bucket : EconomicBucket.values()) result.put(bucket, ZERO);
    for (ProfileAllocation allocation : profile.allocations())
      result.merge(allocation.bucket(), allocation.value(), BigDecimal::add);
    for (ProjectedLongTermAsset asset : profile.longTermAssets())
      result.compute(
          asset.bucket(), (bucket, value) -> value.subtract(asset.currentValue()).max(ZERO));
    // Rental property is not a simulated portfolio bucket, even if the profile
    // contains its accounting allocation.
    result.put(EconomicBucket.REAL_ESTATE, ZERO);
    return result;
  }

  private ManualYear projectManualAssets(
      List<ProjectedLongTermAsset> assets,
      Map<Long, BigDecimal> previous,
      int year,
      Map<Long, Map<CashFlowType, BigDecimal>> previousRentalIncome,
      int rentalYear,
      SimulationScenarioSettings settings) {
    Map<Long, BigDecimal> values = new LinkedHashMap<>();
    Map<Long, BigDecimal> liquidReserveRates = new LinkedHashMap<>();
    EnumMap<EconomicBucket, BigDecimal> starts = zeroBuckets(), ends = zeroBuckets();
    BigDecimal passive = ZERO,
        redemptionCash = ZERO,
        realEstateStart = ZERO,
        realEstateEnd = ZERO,
        contractualStart = ZERO,
        contractualEnd = ZERO,
        manualLiquidReserveStart = ZERO,
        bondRedemptionCash = ZERO,
        rentalIncomeAmount = ZERO,
        bondIncome = ZERO;
    Map<Long, Map<CashFlowType, BigDecimal>> rentalIncome = new LinkedHashMap<>();
    for (ProjectedLongTermAsset asset : assets) {
      BigDecimal start = previous.getOrDefault(asset.id(), ZERO);
      if (isRentalProperty(asset)) {
        RentalIncomeProjection.Result projection =
            RentalIncomeProjection.project(
                asset,
                previousRentalIncome.getOrDefault(asset.id(), Map.of()),
                rentalYear,
                settings.rentalIncomeGrowthRate());
        rentalIncome.put(asset.id(), projection.incomeByType());
        rentalIncomeAmount = rentalIncomeAmount.add(projection.netIncome());
        passive = passive.add(projection.netIncome());
        continue;
      }
      if (isManualCashReserve(asset)) {
        values.put(asset.id(), start);
        liquidReserveRates.put(
            asset.id(), activeRateIncludingZero(asset, year).orElse(settings.cashReturnRate()));
        manualLiquidReserveStart = manualLiquidReserveStart.add(start);
        continue;
      }
      if (isContractual(asset)) contractualStart = contractualStart.add(start);
      else starts.merge(asset.bucket(), start, BigDecimal::add);
      if (start.signum() == 0) {
        values.put(asset.id(), ZERO);
        continue;
      }
      if (isContractual(asset)) {
        if (asset.maturityDate() != null && year > asset.maturityDate().getYear()) {
          values.put(asset.id(), ZERO);
          continue;
        }
        BigDecimal rate = activeRate(asset, year).orElse(rateFor(asset.bucket(), settings));
        BigDecimal gross = start.multiply(rate);
        BigDecimal net =
            gross.subtract(gross.multiply(Optional.ofNullable(asset.taxRate()).orElse(ZERO)));
        if (asset.type() == com.smartbox.investory.infrastructure.longterm.LongTermAssetType.BOND)
          bondIncome = bondIncome.add(net);
        BigDecimal end =
            asset.interestTreatment() == InterestTreatment.CAPITALIZE ? start.add(net) : start;
        if (asset.interestTreatment() != InterestTreatment.CAPITALIZE) passive = passive.add(net);
        if (asset.maturityDate() != null && year == asset.maturityDate().getYear()) {
          BigDecimal proceeds = asset.redemptionValue() == null ? end : asset.redemptionValue();
          redemptionCash = redemptionCash.add(proceeds);
          if (asset.type() == com.smartbox.investory.infrastructure.longterm.LongTermAssetType.BOND)
            bondRedemptionCash = bondRedemptionCash.add(proceeds);
          end = ZERO;
        }
        values.put(asset.id(), end);
        contractualEnd = contractualEnd.add(end);
        continue;
      }
      BigDecimal income = activeIncome(asset, year);
      BigDecimal expense = activeExpense(asset, year);
      BigDecimal taxBase =
          asset.type()
                  == com.smartbox.investory.infrastructure.longterm.LongTermAssetType.REAL_ESTATE
              ? Optional.ofNullable(asset.taxBase()).orElse(ZERO)
              : income.max(ZERO);
      passive =
          passive.add(
              income
                  .subtract(expense)
                  .subtract(taxBase.multiply(Optional.ofNullable(asset.taxRate()).orElse(ZERO))));
      BigDecimal end =
          start.multiply(
              BigDecimal.ONE.add(
                  activeRate(asset, year).orElse(rateFor(asset.bucket(), settings))));
      values.put(asset.id(), end);
      ends.merge(asset.bucket(), end, BigDecimal::add);
    }
    return new ManualYear(
        values,
        passive,
        redemptionCash,
        bondRedemptionCash,
        realEstateStart,
        realEstateEnd,
        manualLiquidReserveStart,
        liquidReserveRates,
        contractualStart,
        contractualEnd,
        starts,
        ends,
        rentalIncome,
        rentalIncomeAmount,
        bondIncome);
  }

  private static List<ProjectedLongTermAsset> simulationAssets(
      List<ProjectedLongTermAsset> persistedAssets, List<ProjectedLongTermAsset> ladderBonds) {
    List<ProjectedLongTermAsset> assets = new ArrayList<>(persistedAssets);
    assets.addAll(ladderBonds);
    return assets;
  }

  private static ProjectedLongTermAsset ladderBond(
      long id, int maturityYear, BigDecimal principal, SimulationScenarioSettings settings) {
    return new ProjectedLongTermAsset(
        id,
        "Projected bond ladder",
        com.smartbox.investory.infrastructure.longterm.LongTermAssetType.BOND,
        EconomicBucket.FIXED_INCOME,
        com.smartbox.investory.infrastructure.CurrencyType.PLN,
        principal,
        Liquidity.LIQUID,
        List.of(
            new ProjectedLongTermAsset.Period(
                LocalDate.of(maturityYear, 1, 1),
                null,
                ZERO,
                ZERO,
                settings.fixedIncomeReturnRate())),
        LocalDate.of(maturityYear + 3, 12, 31),
        principal,
        InterestTreatment.CAPITALIZE,
        ZERO,
        ZERO);
  }

  private Optional<BigDecimal> activeRate(ProjectedLongTermAsset asset, int year) {
    if (isRentalProperty(asset)) return Optional.empty();
    return asset.periods().stream()
        .filter(p -> applies(p, year) && p.annualReturnRate().signum() != 0)
        .map(ProjectedLongTermAsset.Period::annualReturnRate)
        .findFirst();
  }

  private BigDecimal activeIncome(ProjectedLongTermAsset asset, int year) {
    return asset.periods().stream()
        .filter(p -> applies(p, year))
        .map(ProjectedLongTermAsset.Period::annualIncome)
        .reduce(ZERO, BigDecimal::add);
  }

  private BigDecimal activeExpense(ProjectedLongTermAsset asset, int year) {
    return asset.periods().stream()
        .filter(p -> applies(p, year))
        .map(ProjectedLongTermAsset.Period::annualExpense)
        .reduce(ZERO, BigDecimal::add);
  }

  /** Shared applicability rule for analyses that need to know whether global growth is used. */
  static boolean usesGlobalReturn(ProjectedLongTermAsset asset, int year) {
    if (isRentalProperty(asset)) return false;
    return asset.periods().stream()
        .noneMatch(p -> applies(p, year) && p.annualReturnRate().signum() != 0);
  }

  private Optional<BigDecimal> activeRateIncludingZero(ProjectedLongTermAsset asset, int year) {
    return asset.periods().stream()
        .filter(p -> applies(p, year))
        .map(ProjectedLongTermAsset.Period::annualReturnRate)
        .findFirst();
  }

  private static boolean applies(ProjectedLongTermAsset.Period period, int year) {
    return period.validFrom().getYear() <= year
        && (period.validTo() == null || period.validTo().getYear() >= year);
  }

  private static boolean isContractual(ProjectedLongTermAsset asset) {
    return asset.type() == com.smartbox.investory.infrastructure.longterm.LongTermAssetType.BOND
        || asset.type() == com.smartbox.investory.infrastructure.longterm.LongTermAssetType.DEPOSIT
        || (asset.bucket() == EconomicBucket.FIXED_INCOME && asset.interestTreatment() != null);
  }

  private static boolean isManualCashReserve(ProjectedLongTermAsset asset) {
    return asset.type()
        == com.smartbox.investory.infrastructure.longterm.LongTermAssetType.CASH_RESERVE;
  }

  private static boolean isRentalProperty(ProjectedLongTermAsset asset) {
    return asset.bucket() == EconomicBucket.REAL_ESTATE;
  }

  private static Withdrawal withdraw(
      EnumMap<EconomicBucket, BigDecimal> source,
      BigDecimal amount,
      SimulationAssumptions assumptions) {
    EnumMap<EconomicBucket, BigDecimal> result = new EnumMap<>(source);
    BigDecimal left = amount;
    for (EconomicBucket bucket : List.of(EconomicBucket.LIQUID_CASH, EconomicBucket.FIXED_INCOME)) {
      BigDecimal used = result.get(bucket).min(left);
      result.put(bucket, result.get(bucket).subtract(used));
      left = left.subtract(used);
    }
    BigDecimal emergency = ZERO;
    if (assumptions.fundingStrategy() == SimulationFundingStrategy.SIMPLE_WATERFALL
        || assumptions.allowEmergencyEquityWithdrawal()) {
      emergency = result.get(EconomicBucket.EQUITY).min(left);
      result.put(EconomicBucket.EQUITY, result.get(EconomicBucket.EQUITY).subtract(emergency));
    }
    return new Withdrawal(result, emergency);
  }

  private static BigDecimal harvestEquity(
      EnumMap<EconomicBucket, BigDecimal> marketEnd,
      BigDecimal safeReserveTarget,
      BigDecimal manualLiquidReserveEnd,
      BigDecimal eligibleHarvest,
      SimulationScenarioSettings settings,
      SimulationAssumptions assumptions) {
    if (assumptions.fundingStrategy() != SimulationFundingStrategy.RESERVE_AND_HARVEST
        || settings.equityReturnRate().compareTo(assumptions.equityHarvestMinimumReturnRate()) < 0)
      return ZERO;
    BigDecimal reserveShortfall =
        safeReserveTarget.subtract(manualLiquidReserveEnd.add(safeReserve(marketEnd))).max(ZERO);
    BigDecimal transfer =
        reserveShortfall.min(eligibleHarvest).min(marketEnd.get(EconomicBucket.EQUITY));
    marketEnd.merge(EconomicBucket.EQUITY, transfer.negate(), BigDecimal::add);
    marketEnd.merge(EconomicBucket.FIXED_INCOME, transfer, BigDecimal::add);
    return transfer;
  }

  private static BigDecimal eligibleEquityHarvest(
      EnumMap<EconomicBucket, BigDecimal> marketEnd,
      BigDecimal equityGain,
      SimulationScenarioSettings settings,
      SimulationAssumptions assumptions) {
    if (assumptions.fundingStrategy() != SimulationFundingStrategy.RESERVE_AND_HARVEST
        || settings.equityReturnRate().compareTo(assumptions.equityHarvestMinimumReturnRate()) < 0)
      return ZERO;
    return equityGain
        .multiply(assumptions.equityGainHarvestRate())
        .min(marketEnd.get(EconomicBucket.EQUITY))
        .max(ZERO);
  }

  private static BigDecimal harvestBondDeficit(
      EnumMap<EconomicBucket, BigDecimal> marketEnd,
      BigDecimal remainingEligibleHarvest,
      BigDecimal recurringFundingGap,
      List<ProjectedLongTermAsset> assets,
      Map<Long, BigDecimal> manualEnd,
      SimulationScenarioSettings settings,
      SimulationAssumptions assumptions,
      boolean retired) {
    if (!retired
        || assumptions.fundingStrategy() != SimulationFundingStrategy.RESERVE_AND_HARVEST
        || settings.equityReturnRate().compareTo(assumptions.equityHarvestMinimumReturnRate()) < 0)
      return ZERO;
    BigDecimal bondDeficit =
        recurringFundingGap
            .multiply(BOND_LADDER_YEARS)
            .subtract(bondValue(assets, manualEnd))
            .max(ZERO);
    BigDecimal harvest =
        bondDeficit.min(remainingEligibleHarvest).min(marketEnd.get(EconomicBucket.EQUITY));
    marketEnd.merge(EconomicBucket.EQUITY, harvest.negate(), BigDecimal::add);
    return harvest;
  }

  private static ManualWithdrawal withdrawManualCashReserve(
      Map<Long, BigDecimal> source, Map<Long, BigDecimal> reserveRates, BigDecimal amount) {
    Map<Long, BigDecimal> result = new LinkedHashMap<>(source);
    BigDecimal left = amount, funded = ZERO;
    for (Long id :
        reserveRates.keySet().stream()
            .sorted(
                java.util.Comparator.comparing((Long id) -> reserveRates.getOrDefault(id, ZERO))
                    .thenComparing(Long::compareTo))
            .toList()) {
      BigDecimal used = result.getOrDefault(id, ZERO).min(left);
      result.put(id, result.getOrDefault(id, ZERO).subtract(used));
      left = left.subtract(used);
      funded = funded.add(used);
    }
    return new ManualWithdrawal(result, funded);
  }

  private static Map<Long, BigDecimal> applyManualCashReserveReturns(
      Map<Long, BigDecimal> source,
      Map<Long, BigDecimal> reserveRates,
      SimulationScenarioSettings settings) {
    Map<Long, BigDecimal> result = new LinkedHashMap<>(source);
    reserveRates.forEach(
        (id, rate) ->
            result.put(
                id,
                result
                    .getOrDefault(id, ZERO)
                    .multiply(
                        BigDecimal.ONE.add(rate == null ? settings.cashReturnRate() : rate))));
    return result;
  }

  private static EnumMap<EconomicBucket, BigDecimal> applyMarketReturns(
      EnumMap<EconomicBucket, BigDecimal> source, SimulationScenarioSettings settings) {
    EnumMap<EconomicBucket, BigDecimal> result = new EnumMap<>(source);
    result.replaceAll(
        (bucket, value) -> value.multiply(BigDecimal.ONE.add(rateFor(bucket, settings))));
    return result;
  }

  private static BigDecimal rateFor(EconomicBucket bucket, SimulationScenarioSettings settings) {
    java.util.Objects.requireNonNull(bucket, "bucket");
    if (bucket == EconomicBucket.LIQUID_CASH) return settings.cashReturnRate();
    if (bucket == EconomicBucket.FIXED_INCOME) return settings.fixedIncomeReturnRate();
    if (bucket == EconomicBucket.EQUITY) return settings.equityReturnRate();
    if (bucket == EconomicBucket.REAL_ESTATE) return ZERO;
    return settings.otherReturnRate();
  }

  private static BigDecimal sum(Collection<BigDecimal> values) {
    return values.stream().reduce(ZERO, BigDecimal::add);
  }

  private static BigDecimal bondValue(
      List<ProjectedLongTermAsset> assets, Map<Long, BigDecimal> values) {
    return assets.stream()
        .filter(
            asset ->
                asset.type()
                    == com.smartbox.investory.infrastructure.longterm.LongTermAssetType.BOND)
        .map(asset -> values.getOrDefault(asset.id(), ZERO))
        .reduce(ZERO, BigDecimal::add);
  }

  private static BigDecimal spendable(Map<EconomicBucket, BigDecimal> values) {
    return values
        .get(EconomicBucket.LIQUID_CASH)
        .add(values.get(EconomicBucket.FIXED_INCOME))
        .add(values.get(EconomicBucket.EQUITY));
  }

  private static BigDecimal safeReserve(Map<EconomicBucket, BigDecimal> values) {
    // Safe reserve contains market cash and market fixed income only; equity and contractual
    // assets are intentionally excluded. Manual liquid reserves are added by the caller.
    return values.get(EconomicBucket.LIQUID_CASH).add(values.get(EconomicBucket.FIXED_INCOME));
  }

  private static BigDecimal grow(BigDecimal value, BigDecimal rate) {
    return value.multiply(BigDecimal.ONE.add(rate)).setScale(8, RoundingMode.HALF_UP);
  }

  private static EnumMap<EconomicBucket, BigDecimal> zeroBuckets() {
    EnumMap<EconomicBucket, BigDecimal> values = new EnumMap<>(EconomicBucket.class);
    for (EconomicBucket bucket : EconomicBucket.values()) values.put(bucket, ZERO);
    return values;
  }

  private record Withdrawal(
      EnumMap<EconomicBucket, BigDecimal> balances, BigDecimal emergencyEquityWithdrawal) {}

  private record ManualWithdrawal(Map<Long, BigDecimal> values, BigDecimal amount) {}

  private record ManualYear(
      Map<Long, BigDecimal> values,
      BigDecimal passiveIncome,
      BigDecimal redemptionCash,
      BigDecimal bondRedemptionCash,
      BigDecimal realEstateStart,
      BigDecimal realEstateValue,
      BigDecimal manualLiquidReserveStart,
      Map<Long, BigDecimal> liquidReserveRates,
      BigDecimal contractualAssetsStart,
      BigDecimal contractualAssetsEnd,
      EnumMap<EconomicBucket, BigDecimal> startByBucket,
      EnumMap<EconomicBucket, BigDecimal> endByBucket,
      Map<Long, Map<CashFlowType, BigDecimal>> rentalIncome,
      BigDecimal rentalIncomeAmount,
      BigDecimal bondIncome) {}
}
