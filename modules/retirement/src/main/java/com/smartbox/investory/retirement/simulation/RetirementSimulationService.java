package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.longterm.api.model.RentalIncomeProjectionModel;
import com.smartbox.investory.longterm.infrastructure.InterestTreatment;
import com.smartbox.investory.longterm.infrastructure.rental.CashFlowType;
import com.smartbox.investory.retirement.profile.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;

/** Pure deterministic annual projection. It never writes to accounting data. */
@Service
public class RetirementSimulationService implements RetirementSimulation {
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  public SimulationResult simulate(
      InvestmentProfile profile, SimulationAssumptions assumptions, SimulationScenario scenario) {
    return simulate(profile, assumptions, scenario, false);
  }

  /**
   * Runs the annual simulation, optionally using effective-dated actual-year rental economics for
   * its first year. The latter is used only by the current-year bridge; later simulation years
   * always use forward latest-value inheritance.
   */
  public SimulationResult simulate(
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      SimulationScenario scenario,
      boolean actualRentalYear) {
    SimulationScenarioSettings settings =
        SimulationScenarioSettings.forScenario(scenario, assumptions);
    EnumMap<EconomicBucket, BigDecimal> market = initialMarket(profile);
    Map<Long, BigDecimal> manual = new LinkedHashMap<>();
    for (ProjectedLongTermAsset asset : profile.longTermAssets())
      if (!isRentalProperty(asset)) manual.put(asset.id(), asset.currentValue());
    Map<Long, Map<com.smartbox.investory.longterm.api.model.CashFlowTypeModel, BigDecimal>> rentalIncome = new LinkedHashMap<>();
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
      BigDecimal expenseProfileFactor =
          assumptions.expenseProfile().factorForYear(calendarYear - assumptions.startYear());
      if (!assumptions.expenseProfile().steps().isEmpty()) {
        yearCoreExpenses = yearCoreExpenses.multiply(expenseProfileFactor);
        yearDiscretionaryExpenses = yearDiscretionaryExpenses.multiply(expenseProfileFactor);
      }
      BigDecimal startNetWorth = sum(market.values()).add(sum(manual.values()));
      ManualYear manualYear =
          projectManualAssets(
              simulationAssets(profile.longTermAssets(), ladderBonds),
              manual,
              calendarYear,
              rentalIncome,
              calendarYear,
              settings,
              actualRentalYear && age == assumptions.currentAge());
      BigDecimal pension =
          age >= assumptions.pensionStartAge() ? assumptions.annualPension() : ZERO;
      BigDecimal passive = manualYear.passiveIncome();
      BigDecimal eventExpenses =
          eventsFor(assumptions.futureEvents(), calendarYear, SimulationEventType.ONE_OFF_EXPENSE);
      BigDecimal eventIncome =
          eventsFor(assumptions.futureEvents(), calendarYear, SimulationEventType.ONE_OFF_INCOME);
      CashFlowAggregationService.Result cashFlow =
          aggregateYearFlows(
              calendarYear,
              yearCoreExpenses,
              yearDiscretionaryExpenses,
              passive,
              pension,
              employmentIncome,
              eventIncome,
              eventExpenses);
      BigDecimal retirementIncome = passive.add(pension).add(eventIncome);
      BigDecimal totalExpenses = cashFlow.periodExpenses();
      BigDecimal totalIncome = cashFlow.periodIncome();
      BigDecimal required = cashFlow.fundingGap();
      BigDecimal recurringFundingGap =
          yearCoreExpenses
              .add(yearDiscretionaryExpenses)
              .subtract(passive)
              .subtract(pension)
              .subtract(employmentIncome)
              .max(ZERO);
      BigDecimal safeReserveTarget =
          retired
              ? recurringFundingGap.multiply(assumptions.safeReserveYears())
              : ZERO;
      ManualWithdrawal manualWithdrawal =
          assumptions.fundingStrategy() == SimulationFundingStrategy.RESERVE_AND_HARVEST
              ? toManualWithdrawal(
                  ManualCashReserveAllocator.withdraw(
                      manual, manualYear.liquidReserveRates(), required))
              : new ManualWithdrawal(new LinkedHashMap<>(manual), ZERO);
      EnumMap<EconomicBucket, BigDecimal> availableMarket = new EnumMap<>(market);
      availableMarket.merge(EconomicBucket.LIQUID_CASH, preRetirementContribution, BigDecimal::add);
      BigDecimal safeReserveStart =
          ReserveHarvestPolicy.defensiveReserve(
              availableMarket.get(EconomicBucket.LIQUID_CASH),
              manualYear.manualLiquidReserveStart(),
              simulationAssets(profile.longTermAssets(), ladderBonds),
              manual);
      BigDecimal fundingAfterManualReserve = required.subtract(manualWithdrawal.amount()).max(ZERO);
      BigDecimal bondMaturityUsed = manualYear.bondRedemptionCash().min(fundingAfterManualReserve);
      BigDecimal remainingAfterBondMaturity = fundingAfterManualReserve.subtract(bondMaturityUsed);
      BigDecimal otherRedemptionCash =
          manualYear.redemptionCash().subtract(manualYear.bondRedemptionCash()).max(ZERO);
      availableMarket.merge(EconomicBucket.LIQUID_CASH, otherRedemptionCash, BigDecimal::add);
      FundingAllocator.Result funding =
          FundingAllocator.fund(availableMarket, remainingAfterBondMaturity, assumptions);
      Withdrawal withdrawal = new Withdrawal(funding.balances(), funding.stocksWithdrawal());
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
      EnumMap<EconomicBucket, BigDecimal> marketEnd =
          PortfolioReturnCalculator.applyFullYear(afterWithdrawal, settings);
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
      // Year order: fund spending, apply returns, reinvest unused maturity proceeds, then harvest
      // eligible equity gains into a projected bond. Equity harvest never refills generic cash.
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
          ReserveHarvestPolicy.eligibleEquityHarvest(marketEnd, equityGain, settings, assumptions);
      BigDecimal equityToFixedIncomeTransfer =
          ReserveHarvestPolicy.harvestBondDeficit(
              marketEnd,
              eligibleEquityHarvest,
              safeReserveTarget,
              manualLiquidReserveEnd,
              simulationAssets(profile.longTermAssets(), ladderBonds),
              manualEnd,
              assumptions,
              retired);
      BigDecimal bondHarvest = equityToFixedIncomeTransfer;
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
          ReserveHarvestPolicy.bondValue(
              simulationAssets(profile.longTermAssets(), ladderBonds), manualEnd);
      BigDecimal safeReserveEnd =
          ReserveHarvestPolicy.defensiveReserve(
              marketEnd.get(EconomicBucket.LIQUID_CASH),
              manualLiquidReserveEnd,
              simulationAssets(profile.longTermAssets(), ladderBonds),
              manualEnd);
      // Bonds count toward reserve coverage, but remain contractual assets for net-worth and
      // spendability reporting. Keep them out of the liquid/financial totals here so they are
      // not counted a second time through contractualAssetsEnd.
      BigDecimal spendableCashAndFixedIncomeEnd =
          safeReserveEnd
              .subtract(bondValueEnd)
              .add(marketEnd.get(EconomicBucket.FIXED_INCOME))
              .max(ZERO);
      BigDecimal spendableAssetsEnd =
          spendableCashAndFixedIncomeEnd.add(
              assumptions.allowEmergencyEquityWithdrawal()
                      || assumptions.fundingStrategy() == SimulationFundingStrategy.SIMPLE_WATERFALL
                  ? equityEnd
                  : ZERO);
      BigDecimal financialAssetsEnd = spendableCashAndFixedIncomeEnd.add(equityEnd).add(otherEnd);
      BigDecimal contractualAssetsEnd =
          manualYear.contractualAssetsEnd().add(reinvestment).add(bondHarvest);
      BigDecimal safeReserveCoverage =
          recurringFundingGap.signum() == 0
              ? ZERO
              : safeReserveEnd.divide(recurringFundingGap, 8, RoundingMode.HALF_UP);
      years.add(
          new SimulationYear(
              age,
              calendarYear,
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
              totalExpenses.subtract(totalIncome).max(ZERO),
              bondValueEnd,
              manualYear.bondIncome()));
      market = marketEnd;
      manual = manualEnd;
      rentalIncome = manualYear.rentalIncome();
      // Retirement spending starts at the first retirement year. The configured first
      // retirement-year amount is the baseline; growth applies before the following year.
      if (retired) {
        coreExpenses = grow(coreExpenses, settings.spendingGrowthRate());
        discretionaryExpenses = grow(discretionaryExpenses, settings.spendingGrowthRate());
      }
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

  private static CashFlowAggregationService.Result aggregateYearFlows(
      int year,
      BigDecimal coreExpenses,
      BigDecimal discretionaryExpenses,
      BigDecimal passiveIncome,
      BigDecimal pension,
      BigDecimal employment,
      BigDecimal eventIncome,
      BigDecimal eventExpenses) {
    LocalDate firstDay = LocalDate.of(year, 1, 1);
    List<PlannedCashFlow> flows = new ArrayList<>();
    addAnnual(flows, "living-expenses", CashFlowDirection.EXPENSE, coreExpenses, firstDay);
    addAnnual(flows, "discretionary-expenses", CashFlowDirection.EXPENSE, discretionaryExpenses, firstDay);
    addAnnual(flows, "long-term-income", CashFlowDirection.INCOME, passiveIncome, firstDay);
    addAnnual(flows, "pension", CashFlowDirection.INCOME, pension, firstDay);
    addAnnual(flows, "employment", CashFlowDirection.INCOME, employment, firstDay);
    addAnnual(flows, "events-income", CashFlowDirection.INCOME, eventIncome, firstDay);
    addAnnual(flows, "events-expenses", CashFlowDirection.EXPENSE, eventExpenses, firstDay);
    return new CashFlowAggregationService()
        .aggregateProjected(new Period(firstDay, LocalDate.of(year, 12, 31)), flows);
  }

  private static void addAnnual(
      List<PlannedCashFlow> flows,
      String id,
      CashFlowDirection direction,
      BigDecimal amount,
      LocalDate date) {
    if (amount.signum() > 0) {
      flows.add(
          new PlannedCashFlow(
              id, id, direction, CashFlowCadence.ANNUAL, amount, date, ProjectionSource.PROJECTED));
    }
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
      Map<Long, Map<com.smartbox.investory.longterm.api.model.CashFlowTypeModel, BigDecimal>> previousRentalIncome,
      int rentalYear,
      SimulationScenarioSettings settings,
      boolean actualRentalYear) {
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
    Map<Long, Map<com.smartbox.investory.longterm.api.model.CashFlowTypeModel, BigDecimal>> rentalIncome = new LinkedHashMap<>();
    for (ProjectedLongTermAsset asset : assets) {
      BigDecimal start = previous.getOrDefault(asset.id(), ZERO);
      if (isRentalProperty(asset)) {
        RentalIncomeProjectionModel.Result projection =
            actualRentalYear
                ? RentalIncomeProjectionModel.actualYear(longTermProjection(asset), rentalYear)
                : RentalIncomeProjectionModel.project(
                    longTermProjection(asset),
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
      if (ContractualAssetProjector.isContractual(asset))
        contractualStart = contractualStart.add(start);
      else starts.merge(asset.bucket(), start, BigDecimal::add);
      if (start.signum() == 0) {
        values.put(asset.id(), ZERO);
        continue;
      }
      if (ContractualAssetProjector.isContractual(asset)) {
        ContractualAssetProjector.Projection projection =
            ContractualAssetProjector.project(
                asset,
                start,
                settings,
                year,
                LocalDate.of(year, 1, 1).minusDays(1),
                BigDecimal.ONE);
        if (asset.type()
            == com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType.BOND)
          bondIncome = bondIncome.add(projection.payoutIncome());
        passive = passive.add(projection.payoutIncome());
        redemptionCash = redemptionCash.add(projection.redemptionCash());
        if (asset.type()
            == com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType.BOND)
          bondRedemptionCash = bondRedemptionCash.add(projection.redemptionCash());
        values.put(asset.id(), projection.endValue());
        contractualEnd = contractualEnd.add(projection.endValue());
        continue;
      }
      BigDecimal income = activeIncome(asset, year);
      BigDecimal expense = activeExpense(asset, year);
      BigDecimal taxBase =
          asset.type()
                  == com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType
                      .REAL_ESTATE
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
                  activeRate(asset, year)
                      .orElse(PortfolioReturnCalculator.rateFor(asset.bucket(), settings))));
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
        com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType.BOND,
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

  private static boolean isManualCashReserve(ProjectedLongTermAsset asset) {
    return asset.type()
        == com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType.CASH_RESERVE;
  }

  private static boolean isRentalProperty(ProjectedLongTermAsset asset) {
    return asset.bucket() == EconomicBucket.REAL_ESTATE;
  }

  private static com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel
      longTermProjection(ProjectedLongTermAsset asset) {
    return new com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel(
        asset.id(),
        asset.name(),
        com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel.valueOf(asset.type().name()),
        asset.currency(),
        asset.currentValue(),
        asset.periods().stream()
            .map(
                period ->
                    new com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel
                        .Period(
                        period.validFrom(),
                        period.validTo(),
                        period.annualIncome(),
                        period.annualExpense(),
                        period.annualReturnRate(),
                        period.cashFlowType() == null ? null : com.smartbox.investory.longterm.api.model.CashFlowTypeModel.valueOf(period.cashFlowType().name()),
                        period.paidByTenant()))
            .toList(),
        asset.rentalContracts(),
        asset.maturityDate(),
        asset.redemptionValue(),
        asset.interestTreatment() == null
            ? null
            : com.smartbox.investory.longterm.api.model.InterestTreatmentModel.valueOf(asset.interestTreatment().name()),
        asset.taxRate(),
        asset.taxBase(),
        asset.rentalTaxPaidByTenant());
  }

  private static ManualWithdrawal toManualWithdrawal(ManualCashReserveAllocator.Result result) {
    return new ManualWithdrawal(result.values(), result.fundedAmount());
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

  private static BigDecimal sum(Collection<BigDecimal> values) {
    return values.stream().reduce(ZERO, BigDecimal::add);
  }

  private static BigDecimal spendable(Map<EconomicBucket, BigDecimal> values) {
    return values
        .get(EconomicBucket.LIQUID_CASH)
        .add(values.get(EconomicBucket.FIXED_INCOME))
        .add(values.get(EconomicBucket.EQUITY));
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
      Map<Long, Map<com.smartbox.investory.longterm.api.model.CashFlowTypeModel, BigDecimal>> rentalIncome,
      BigDecimal rentalIncomeAmount,
      BigDecimal bondIncome) {}
}
