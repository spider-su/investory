package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.longterm.application.model.AnnualEconomics;
import com.smartbox.investory.longterm.application.model.BondPlanningSummary;
import com.smartbox.investory.longterm.application.model.LongTermAssetSummary;
import com.smartbox.investory.longterm.application.model.RealEstateGroupPlanningSummary;
import com.smartbox.investory.longterm.application.model.RealEstatePlanningSummary;
import com.smartbox.investory.longterm.infrastructure.asset.*;
import com.smartbox.investory.longterm.infrastructure.bond.*;
import com.smartbox.investory.longterm.infrastructure.deposit.*;
import com.smartbox.investory.longterm.infrastructure.lifecycle.*;
import com.smartbox.investory.longterm.infrastructure.rental.*;
import com.smartbox.investory.longterm.infrastructure.tax.*;
import com.smartbox.investory.longterm.infrastructure.valuation.*;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import com.smartbox.investory.shared.presentation.FinancialPresentation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LongTermAssetQueryService {
  public static final BigDecimal REAL_ESTATE_TAX_RATE = new BigDecimal("0.085");
  public static final BigDecimal BOND_TAX_RATE = new BigDecimal("0.19");
  private final LongTermAssetRepository assets;
  private final LongTermAssetValuationPeriodRepository valuations;
  private final RentalTaxPolicyRepository taxPolicies;
  private final PortfolioContextReader portfolioContextReader;
  private final CurrencyConversion currencyRates;
  private final LongTermAssetRentalContractRepository rentalContracts;
  private final LongTermAssetRelatedDataLoader relatedData;

  @Value("${app.long-term-assets.planning-currency:PLN}")
  private CurrencyType planningCurrency = CurrencyType.PLN;

  @Value("${app.history-start:2025-01-01}")
  private LocalDate historyStart = LocalDate.of(2025, 1, 1);

  private final RealEstatePlanningCalculator realEstatePlanningCalculator =
      new RealEstatePlanningCalculator();
  private final BondPlanningCalculator bondPlanningCalculator = new BondPlanningCalculator();

  public LongTermAssetQueryService(
      LongTermAssetRepository assets,
      LongTermAssetValuationPeriodRepository valuations,
      RentalTaxPolicyRepository taxPolicies,
      PortfolioContextReader portfolioContextReader,
      CurrencyConversion currencyRates,
      LongTermAssetRentalContractRepository rentalContracts,
      LongTermAssetRelatedDataLoader relatedData) {
    this.assets = assets;
    this.valuations = valuations;
    this.taxPolicies = taxPolicies;
    this.portfolioContextReader = portfolioContextReader;
    this.currencyRates = currencyRates;
    this.rentalContracts = rentalContracts;
    this.relatedData = relatedData;
  }

  @Transactional(readOnly = true)
  public List<LongTermAssetSummary> list(Long portfolioId, LocalDate date) {
    LocalDate effectiveDate = effectiveDate(date);
    List<LongTermAssetEntity> rows =
        assets.findAllByPortfolioIdAndActiveTrueOrderByName(portfolioId);
    SummaryData data = summaryData(portfolioId, rows, effectiveDate);
    return rows.stream().map(a -> summary(a, effectiveDate, data)).toList();
  }

  @Transactional(readOnly = true)
  public List<LongTermAssetSummary> archived(Long portfolioId, LocalDate date) {
    LocalDate effectiveDate = effectiveDate(date);
    List<LongTermAssetEntity> rows =
        assets.findAllByPortfolioIdAndActiveFalseOrderByName(portfolioId);
    SummaryData data = summaryData(portfolioId, rows, effectiveDate);
    return rows.stream().map(a -> summary(a, effectiveDate, data)).toList();
  }

  @Transactional(readOnly = true)
  public List<AssetGroupSummary> grouped(Long portfolioId, LocalDate date) {
    LocalDate effectiveDate = effectiveDate(date);
    List<LongTermAssetSummary> rows = list(portfolioId, effectiveDate);
    return groupSummaries(rows, planningCurrency, effectiveDate);
  }

  @Transactional(readOnly = true)
  public PageData page(Long portfolioId, LocalDate date) {
    LocalDate effectiveDate = effectiveDate(date);
    List<LongTermAssetSummary> rows = list(portfolioId, effectiveDate);
    return new PageData(
        rows,
        groupSummaries(rows, planningCurrency, effectiveDate),
        AggregateSummary.of(rows, planningCurrency, currencyRates, effectiveDate));
  }

  public record PageData(
      List<LongTermAssetSummary> assets,
      List<AssetGroupSummary> groups,
      AggregateSummary aggregate) {}

  public List<AssetGroupSummary> groupSummaries(
      List<LongTermAssetSummary> rows, CurrencyType base, LocalDate date) {
    LocalDate effectiveDate = effectiveDate(date);
    return List.of(
        group(
            "REAL_ESTATE",
            "Real estate",
            rows.stream()
                .filter(r -> r.type() == LongTermAssetType.REAL_ESTATE)
                .sorted(
                    Comparator.comparing(LongTermAssetSummary::name, String.CASE_INSENSITIVE_ORDER))
                .toList(),
            base,
            effectiveDate),
        group(
            "BOND",
            "Bonds",
            rows.stream()
                .filter(r -> r.type() == LongTermAssetType.BOND)
                .sorted(
                    Comparator.comparing(
                            LongTermAssetSummary::maturityDate,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(LongTermAssetSummary::name, String.CASE_INSENSITIVE_ORDER))
                .toList(),
            base,
            effectiveDate),
        group(
            "CASH_RESERVE",
            "Cash",
            rows.stream()
                .filter(r -> r.type() == LongTermAssetType.CASH_RESERVE)
                .sorted(
                    Comparator.comparing(LongTermAssetSummary::name, String.CASE_INSENSITIVE_ORDER))
                .toList(),
            base,
            effectiveDate),
        group(
            "OTHER",
            "Other",
            rows.stream()
                .filter(
                    r ->
                        r.type() == LongTermAssetType.DEPOSIT
                            || r.type() == LongTermAssetType.OTHER)
                .sorted(
                    Comparator.comparing(LongTermAssetSummary::name, String.CASE_INSENSITIVE_ORDER))
                .toList(),
            base,
            effectiveDate));
  }

  @Transactional(readOnly = true)
  public Optional<LongTermAssetEntity> get(Long portfolioId, Long id) {
    return assets.findByIdAndPortfolioId(id, portfolioId);
  }

  public LongTermAssetRentalContractEntity rentalContract(
      Long portfolioId, Long assetId, Long contractId) {
    owned(portfolioId, assetId);
    return rentalContracts
        .findById(contractId)
        .filter(contract -> Objects.equals(contract.getAssetId(), assetId))
        .orElseThrow(() -> new RentalContractNotFoundException(assetId, contractId));
  }

  public LongTermAssetValuationPeriodEntity valuation(
      Long portfolioId, Long assetId, Long periodId) {
    owned(portfolioId, assetId);
    return valuations
        .findById(periodId)
        .filter(period -> Objects.equals(period.getAssetId(), assetId))
        .orElseThrow(() -> new ValuationNotFoundException(assetId, periodId));
  }

  public RentalTaxPolicyEntity rentalTaxPolicy(Long portfolioId, Long policyId) {
    return taxPolicies
        .findById(policyId)
        .filter(policy -> Objects.equals(policy.getPortfolioId(), portfolioId))
        .orElseThrow(() -> new RentalTaxPolicyNotFoundException(portfolioId, policyId));
  }

  @Transactional(readOnly = true)
  public Optional<RentalTaxPolicyEntity> rentalTaxPolicy(Long portfolioId, LocalDate date) {
    LocalDate effectiveDate = effectiveDate(date);
    return taxPolicies
        .findFirstByPortfolioIdAndValidFromLessThanEqualAndValidToGreaterThanEqualOrderByValidFromDesc(
            portfolioId, effectiveDate, effectiveDate)
        .or(
            () ->
                taxPolicies
                    .findFirstByPortfolioIdAndValidFromLessThanEqualAndValidToIsNullOrderByValidFromDesc(
                        portfolioId, effectiveDate));
  }

  @Transactional(readOnly = true)
  public List<RentalTaxPolicyEntity> rentalTaxPolicies(Long portfolioId) {
    return taxPolicies.findAllByPortfolioIdOrderByValidFrom(portfolioId);
  }

  public LongTermAssetSummary summary(LongTermAssetEntity a, LocalDate date) {
    LocalDate effectiveDate = effectiveDate(date);
    return summary(a, effectiveDate, summaryData(a.getPortfolioId(), List.of(a), effectiveDate));
  }

  public AssetDetailData detail(Long portfolioId, Long id, LocalDate date) {
    LocalDate effectiveDate = effectiveDate(date);
    LongTermAssetEntity asset = owned(portfolioId, id);
    SummaryData data = summaryData(portfolioId, List.of(asset), effectiveDate);
    List<LongTermAssetValuationPeriodEntity> valuationPeriods =
        data.valuations().getOrDefault(id, List.of());
    BigDecimal expectedGrowth =
        valuationPeriods.stream()
            .filter(
                period ->
                    LongTermAssetCalculator.applies(
                        period.getValidFrom(), period.getValidTo(), effectiveDate))
            .max(Comparator.comparing(LongTermAssetValuationPeriodEntity::getValidFrom))
            .map(LongTermAssetValuationPeriodEntity::getExpectedAnnualGrowthRate)
            .orElse(null);
    return new AssetDetailData(
        asset,
        summary(asset, effectiveDate, data),
        data.bonds().get(id),
        data.deposits().get(id),
        valuationPeriods,
        expectedGrowth,
        data.contracts().getOrDefault(id, List.of()));
  }

  public record AssetDetailData(
      LongTermAssetEntity asset,
      LongTermAssetSummary summary,
      LongTermAssetBondDetailsEntity bondDetails,
      LongTermAssetDepositDetailsEntity depositDetails,
      List<LongTermAssetValuationPeriodEntity> valuationPeriods,
      BigDecimal expectedPropertyGrowth,
      List<LongTermAssetRentalContractEntity> contracts) {}

  List<LongTermAssetSummary> summaries(List<LongTermAssetEntity> rows, LocalDate date) {
    if (rows.isEmpty()) return List.of();
    LocalDate effectiveDate = effectiveDate(date);
    SummaryData data = summaryData(rows.getFirst().getPortfolioId(), rows, effectiveDate);
    return rows.stream().map(row -> summary(row, effectiveDate, data)).toList();
  }

  List<LongTermAssetSummary> summaries(
      List<LongTermAssetEntity> rows, LocalDate date, LongTermAssetRelatedDataLoader.Data loaded) {
    if (rows.isEmpty()) return List.of();
    LocalDate effectiveDate = effectiveDate(date);
    SummaryData data = SummaryData.from(loaded);
    return rows.stream().map(row -> summary(row, effectiveDate, data)).toList();
  }

  private LongTermAssetSummary summary(
      LongTermAssetEntity a, LocalDate effectiveDate, SummaryData data) {
    BigDecimal gross = BigDecimal.ZERO,
        expenses = BigDecimal.ZERO,
        tax = BigDecimal.ZERO,
        rate = BigDecimal.ZERO;
    LocalDate maturity = null;
    RealEstatePlanningSummary realEstatePlanning = null;
    BondPlanningSummary bondPlanning = null;
    LocalDate rentEnd = null;
    if (a.getType() == LongTermAssetType.REAL_ESTATE) {
      var contractModels =
          data.contracts().getOrDefault(a.getId(), List.of()).stream()
              .map(LongTermAssetQueryService::rentalContractModel)
              .toList();
      {
        rentEnd =
            contractModels.stream()
                .filter(
                    c ->
                        !effectiveDate.isBefore(c.startDate())
                            && (effectiveContractEnd(c) == null
                                || !effectiveDate.isAfter(effectiveContractEnd(c))))
                .filter(
                    c ->
                        c.terms().stream()
                            .anyMatch(
                                t ->
                                    t.type()
                                        == com.smartbox.investory.longterm.api.model.CashFlowType
                                            .RENT))
                .max(Comparator.comparing(RentalContractModel::startDate))
                .map(LongTermAssetQueryService::effectiveContractEnd)
                .orElse(null);
        realEstatePlanning =
            realEstatePlanningCalculator.calculate(
                a.getCurrentValue(),
                a.getTaxBase(),
                a.isRentalTaxPaidByTenant(),
                contractModels,
                effectiveDate,
                data.rentalTaxRate());
        gross = normalizeMoney(realEstatePlanning.monthlyIncome().multiply(BigDecimal.valueOf(12)));
        expenses =
            normalizeMoney(
                realEstatePlanning
                    .monthlyReduce()
                    .multiply(BigDecimal.valueOf(12))
                    .subtract(realEstatePlanning.annualTax()));
      }
      tax = realEstatePlanning.annualTax();
    } else if (a.getType() == LongTermAssetType.BOND) {
      LongTermAssetBondDetailsEntity d = data.bonds().get(a.getId());
      if (d != null) maturity = d.getMaturityDate();
      rate = currentRate(data.bondRates().getOrDefault(a.getId(), List.of()), effectiveDate);
      bondPlanning =
          bondPlanningCalculator.calculate(
              a.getCurrentValue(),
              rate,
              maturity,
              d == null ? null : d.getInterestTreatment(),
              d == null ? BOND_TAX_RATE : d.getTaxRate());
      gross = bondPlanning.grossInterest();
      tax = bondPlanning.annualTax();
    } else if (a.getType() == LongTermAssetType.DEPOSIT) {
      LongTermAssetDepositDetailsEntity d = data.deposits().get(a.getId());
      if (d != null) {
        maturity = d.getMaturityDate();
        rate = d.getAnnualInterestRate();
        gross = a.getCurrentValue().multiply(rate);
        tax = gross.multiply(d.getTaxRate());
      }
    } else if (a.getType() == LongTermAssetType.CASH_RESERVE) {
      rate =
          data.valuations().getOrDefault(a.getId(), List.of()).stream()
              .filter(
                  p ->
                      LongTermAssetCalculator.applies(
                          p.getValidFrom(), p.getValidTo(), effectiveDate))
              .reduce((first, second) -> second)
              .map(LongTermAssetValuationPeriodEntity::getExpectedAnnualGrowthRate)
              .orElse(BigDecimal.ZERO);
      // Cash reserve return is entered as a net planning rate. There is no separate tax policy for
      // this asset type, so the expected return is both gross and net annual income.
      gross = a.getCurrentValue().multiply(rate);
    }
    AnnualEconomics economics = AnnualEconomics.of(a.getCurrentValue(), gross, expenses, tax);
    return new LongTermAssetSummary(
        a.getId(),
        a.getName(),
        a.getType(),
        a.getCurrency(),
        a.getCurrentValue(),
        maturity,
        rate,
        economics,
        realEstatePlanning,
        bondPlanning,
        rentEnd);
  }

  @Transactional(readOnly = true)
  public AggregateSummary aggregate(Long portfolioId, LocalDate date) {
    date = effectiveDate(date);
    com.smartbox.investory.shared.currency.CurrencyType base =
        portfolioContextReader
            .findById(portfolioId)
            .orElseThrow(() -> new PortfolioNotFoundException(portfolioId))
            .baseCurrency();
    return AggregateSummary.of(list(portfolioId, date), base, currencyRates, date);
  }

  @Transactional(readOnly = true)
  public AggregateSummary aggregateForLongTermAssets(Long portfolioId, LocalDate date) {
    date = effectiveDate(date);
    return AggregateSummary.of(list(portfolioId, date), planningCurrency, currencyRates, date);
  }

  private LocalDate effectiveDate(LocalDate date) {
    if (date == null) throw new IllegalArgumentException("date must not be null");
    return date.isBefore(historyStart) ? historyStart : date;
  }

  private static BigDecimal normalizeMoney(BigDecimal value) {
    BigDecimal rounded = value.setScale(3, java.math.RoundingMode.HALF_UP);
    return rounded.stripTrailingZeros().scale() <= 0
        ? rounded.setScale(0, java.math.RoundingMode.UNNECESSARY)
        : rounded.stripTrailingZeros();
  }

  public record AggregateSummary(
      CurrencyType currency, BigDecimal totalCurrentValue, AnnualEconomics annualEconomics) {
    public String totalCurrentValueWholeDisplay() {
      return FinancialPresentation.wholeNumber(totalCurrentValue);
    }

    public String netAnnualIncomeWholeDisplay() {
      return FinancialPresentation.wholeNumber(annualEconomics.netAnnualIncomeAfterTax());
    }

    public String netYieldDisplay() {
      return FinancialPresentation.percentage(netYieldAfterTax());
    }

    public String netYieldWithLabelDisplay() {
      return netYieldDisplay() + " net yield";
    }

    public String monthlyNetIncomeWholeDisplay() {
      return FinancialPresentation.wholeNumber(annualEconomics.monthlyNetIncomeAfterTax());
    }

    public String grossAnnualIncomeWholeDisplay() {
      return FinancialPresentation.wholeNumber(annualEconomics.grossAnnualIncome());
    }

    public String annualExpensesAndTaxWholeDisplay() {
      return FinancialPresentation.wholeNumber(
          annualEconomics.annualExpenses().add(annualEconomics.annualTax()));
    }

    public String grossYieldDisplay() {
      return FinancialPresentation.percentage(annualEconomics.grossYield());
    }

    private BigDecimal netYieldAfterTax() {
      return annualEconomics.netYieldAfterTax();
    }

    public BigDecimal weightedGrossYield() {
      return annualEconomics.grossYield();
    }

    public BigDecimal weightedNetYield() {
      return annualEconomics.netYieldAfterTax();
    }

    static AggregateSummary of(
        List<LongTermAssetSummary> rows,
        CurrencyType base,
        CurrencyConversion rates,
        LocalDate date) {
      BigDecimal value = sum(rows, LongTermAssetSummary::currentValue, base, rates, date);
      BigDecimal gross = sum(rows, r -> r.annualEconomics().grossAnnualIncome(), base, rates, date);
      BigDecimal expenses = sum(rows, r -> r.annualEconomics().annualExpenses(), base, rates, date);
      BigDecimal tax = sum(rows, r -> r.annualEconomics().annualTax(), base, rates, date);
      return new AggregateSummary(
          base, value, AnnualEconomics.aggregateOf(value, gross, expenses, tax));
    }

    private static BigDecimal sum(
        List<LongTermAssetSummary> rows,
        java.util.function.Function<LongTermAssetSummary, BigDecimal> f,
        CurrencyType base,
        CurrencyConversion rates,
        LocalDate date) {
      return rows.stream()
          .map(
              r -> {
                BigDecimal amount = Optional.ofNullable(f.apply(r)).orElse(BigDecimal.ZERO);
                return r.currency() == base
                    ? amount
                    : rates.convertToBaseCurrency(amount, base, r.currency(), date);
              })
          .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
  }

  public record AssetGroupSummary(
      String key,
      String title,
      CurrencyType currency,
      List<LongTermAssetSummary> assets,
      BigDecimal totalValue,
      AnnualEconomics annualEconomics,
      RealEstateGroupPlanningSummary realEstatePlanning) {
    public BigDecimal netMonthlyIncome() {
      return realEstatePlanning == null ? BigDecimal.ZERO : realEstatePlanning.netMonthlyIncome();
    }

    public String shareDisplay(BigDecimal total) {
      if (total == null || total.signum() == 0) return "0.0%";
      return FinancialPresentation.percentage(
          totalValue.divide(total, 8, java.math.RoundingMode.HALF_UP));
    }

    public BigDecimal shareWidth(BigDecimal total) {
      if (total == null || total.signum() == 0) return BigDecimal.ZERO;
      return totalValue.divide(total, 8, java.math.RoundingMode.HALF_UP).movePointRight(2);
    }
  }

  private AssetGroupSummary group(
      String key,
      String title,
      List<LongTermAssetSummary> rows,
      CurrencyType base,
      LocalDate date) {
    BigDecimal value =
        AggregateSummary.sum(rows, LongTermAssetSummary::currentValue, base, currencyRates, date);
    BigDecimal income =
        AggregateSummary.sum(
            rows, r -> r.annualEconomics().grossAnnualIncome(), base, currencyRates, date);
    BigDecimal expenses =
        AggregateSummary.sum(
            rows, r -> r.annualEconomics().annualExpenses(), base, currencyRates, date);
    BigDecimal annualTax =
        AggregateSummary.sum(rows, r -> r.annualEconomics().annualTax(), base, currencyRates, date);
    BigDecimal payment =
        AggregateSummary.sum(
            rows,
            r -> planning(r, BigDecimal.ZERO).totalPaymentMonthly(),
            base,
            currencyRates,
            date);
    BigDecimal monthlyIncome =
        AggregateSummary.sum(
            rows, r -> planning(r, BigDecimal.ZERO).monthlyIncome(), base, currencyRates, date);
    BigDecimal monthlyReduce =
        AggregateSummary.sum(
            rows, r -> planning(r, BigDecimal.ZERO).monthlyReduce(), base, currencyRates, date);
    BigDecimal taxBase =
        AggregateSummary.sum(
            rows, r -> planning(r, BigDecimal.ZERO).taxBase(), base, currencyRates, date);
    BigDecimal monthlyRentTax =
        AggregateSummary.sum(
                rows, r -> planning(r, BigDecimal.ZERO).annualTax(), base, currencyRates, date)
            .divide(BigDecimal.valueOf(12), 18, java.math.RoundingMode.HALF_UP);
    AnnualEconomics economics = AnnualEconomics.aggregateOf(value, income, expenses, annualTax);
    RealEstateGroupPlanningSummary planning =
        "REAL_ESTATE".equals(key)
            ? new RealEstateGroupPlanningSummary(
                payment,
                monthlyIncome.subtract(monthlyReduce),
                monthlyReduce,
                taxBase,
                monthlyRentTax,
                LongTermAssetCalculator.ratio(
                    monthlyIncome.subtract(monthlyReduce).multiply(BigDecimal.valueOf(12)), value))
            : null;
    return new AssetGroupSummary(key, title, base, rows, value, economics, planning);
  }

  private static RealEstatePlanningSummary planning(LongTermAssetSummary row, BigDecimal zero) {
    return row.realEstatePlanning() == null
        ? new RealEstatePlanningSummary(zero, zero, zero, zero, zero, zero)
        : row.realEstatePlanning();
  }

  private static BigDecimal currentRate(
      List<LongTermAssetBondRatePeriodEntity> rates, LocalDate date) {
    return rates.stream()
        .filter(p -> LongTermAssetCalculator.applies(p.getValidFrom(), p.getValidTo(), date))
        .findFirst()
        .map(LongTermAssetBondRatePeriodEntity::getAnnualInterestRate)
        .orElse(BigDecimal.ZERO);
  }

  private SummaryData summaryData(
      Long portfolioId, List<LongTermAssetEntity> rows, LocalDate effectiveDate) {
    if (rows.isEmpty()) return SummaryData.empty();
    List<Long> ids = rows.stream().map(LongTermAssetEntity::getId).toList();
    LongTermAssetRelatedDataLoader.Data loaded = relatedData.load(portfolioId, ids, effectiveDate);
    return new SummaryData(
        loaded.contracts(),
        loaded.valuations(),
        loaded.bondRates(),
        loaded.bonds(),
        loaded.deposits(),
        loaded.rentalTaxRate());
  }

  private static List<Long> idsOfType(List<LongTermAssetEntity> rows, LongTermAssetType type) {
    return rows.stream()
        .filter(row -> row.getType() == type)
        .map(LongTermAssetEntity::getId)
        .toList();
  }

  private static RentalContractModel rentalContractModel(
      LongTermAssetRentalContractEntity contract) {
    return new RentalContractModel(
        contract.getId(),
        contract.getStartDate(),
        contract.getEndDate(),
        contract.getTerminatedDate(),
        contract.getRentalTaxPaidByTenant(),
        contract.getMonthlyTaxBase(),
        contract.getTenantName(),
        contract.getTenantEmail(),
        contract.getTenantPhone(),
        contract.getTerms().stream()
            .map(
                term ->
                    new RentalContractModel.Term(
                        term.getType(),
                        term.getAmount(),
                        term.getFrequency(),
                        term.isPaidByTenant()))
            .toList());
  }

  private record SummaryData(
      Map<Long, List<LongTermAssetRentalContractEntity>> contracts,
      Map<Long, List<LongTermAssetValuationPeriodEntity>> valuations,
      Map<Long, List<LongTermAssetBondRatePeriodEntity>> bondRates,
      Map<Long, LongTermAssetBondDetailsEntity> bonds,
      Map<Long, LongTermAssetDepositDetailsEntity> deposits,
      BigDecimal rentalTaxRate) {
    private static SummaryData from(LongTermAssetRelatedDataLoader.Data loaded) {
      return new SummaryData(
          loaded.contracts(),
          loaded.valuations(),
          loaded.bondRates(),
          loaded.bonds(),
          loaded.deposits(),
          loaded.rentalTaxRate());
    }

    private static SummaryData empty() {
      return new SummaryData(
          Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), REAL_ESTATE_TAX_RATE);
    }
  }

  private static LocalDate effectiveContractEnd(RentalContractModel c) {
    if (c.endDate() == null) return c.terminatedDate();
    if (c.terminatedDate() == null) return c.endDate();
    return c.endDate().isBefore(c.terminatedDate()) ? c.endDate() : c.terminatedDate();
  }

  private LongTermAssetEntity owned(Long portfolioId, Long id) {
    return assets
        .findByIdAndPortfolioId(id, portfolioId)
        .orElseThrow(() -> new AssetNotFoundException(portfolioId, id));
  }
}
