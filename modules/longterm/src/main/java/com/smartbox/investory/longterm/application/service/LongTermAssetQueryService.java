package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.application.model.AnnualEconomics;
import com.smartbox.investory.longterm.application.model.LongTermAssetSummary;
import com.smartbox.investory.longterm.application.model.RealEstateGroupPlanningSummary;
import com.smartbox.investory.longterm.infrastructure.asset.*;
import com.smartbox.investory.longterm.infrastructure.bond.*;
import com.smartbox.investory.longterm.infrastructure.deposit.*;
import com.smartbox.investory.longterm.infrastructure.lifecycle.*;
import com.smartbox.investory.longterm.infrastructure.rental.*;
import com.smartbox.investory.longterm.infrastructure.tax.*;
import com.smartbox.investory.longterm.infrastructure.valuation.*;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.policy.FinancialPolicyDefaults;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import com.smartbox.investory.shared.presentation.FinancialPresentation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LongTermAssetQueryService {
  private final PortfolioContextReader portfolioContextReader;
  private final CurrencyConversion currencyRates;
  private final LongTermAssetPopulationLoader population;
  private final LongTermAssetEconomicsCalculator economics;
  private final LongTermAssetPageAssembler pageAssembler;

  @Value("${app.history-start:" + FinancialPolicyDefaults.HISTORY_START_TEXT + "}")
  private LocalDate historyStart = FinancialPolicyDefaults.HISTORY_START;

  @Autowired
  public LongTermAssetQueryService(
      PortfolioContextReader portfolioContextReader,
      CurrencyConversion currencyRates,
      LongTermAssetPopulationLoader population,
      LongTermAssetEconomicsCalculator economics,
      LongTermAssetPageAssembler pageAssembler) {
    this.portfolioContextReader = portfolioContextReader;
    this.currencyRates = currencyRates;
    this.population = population;
    this.economics = economics;
    this.pageAssembler = pageAssembler;
  }

  @Transactional(readOnly = true)
  public List<LongTermAssetSummary> list(Long portfolioId, LocalDate date) {
    LocalDate effectiveDate = effectiveDate(date);
    List<LongTermAssetEntity> rows = population.activeAt(portfolioId, effectiveDate);
    return economics.summaries(
        rows, effectiveDate, population.relatedData(portfolioId, rows, effectiveDate));
  }

  /**
   * Lists assets currently marked archived; {@code date} only controls their calculated economics.
   */
  @Transactional(readOnly = true)
  public List<LongTermAssetSummary> archived(Long portfolioId, LocalDate date) {
    LocalDate effectiveDate = effectiveDate(date);
    List<LongTermAssetEntity> rows = population.archived(portfolioId);
    return economics.summaries(
        rows, effectiveDate, population.relatedData(portfolioId, rows, effectiveDate));
  }

  @Transactional(readOnly = true)
  public List<AssetGroupSummary> grouped(Long portfolioId, LocalDate date) {
    LocalDate effectiveDate = effectiveDate(date);
    List<LongTermAssetSummary> rows = list(portfolioId, effectiveDate);
    return pageAssembler.groups(rows, localCurrency(portfolioId), effectiveDate);
  }

  @Transactional(readOnly = true)
  public PageData page(Long portfolioId, LocalDate date) {
    LocalDate effectiveDate = effectiveDate(date);
    List<LongTermAssetSummary> rows = list(portfolioId, effectiveDate);
    return new PageData(
        rows,
        pageAssembler.groups(rows, localCurrency(portfolioId), effectiveDate),
        AggregateSummary.of(rows, localCurrency(portfolioId), currencyRates, effectiveDate));
  }

  public record PageData(
      List<LongTermAssetSummary> assets,
      List<AssetGroupSummary> groups,
      AggregateSummary aggregate) {}

  public List<AssetGroupSummary> groupSummaries(
      List<LongTermAssetSummary> rows, CurrencyType base, LocalDate date) {
    return pageAssembler.groups(rows, base, effectiveDate(date));
  }

  private CurrencyType localCurrency(Long portfolioId) {
    return portfolioContextReader
        .findById(portfolioId)
        .map(context -> context.localCurrency())
        .orElseThrow(() -> new IllegalArgumentException("Portfolio not found: " + portfolioId));
  }

  @Transactional(readOnly = true)
  public Optional<LongTermAssetEntity> get(Long portfolioId, Long id) {
    return population.find(portfolioId, id);
  }

  public LongTermAssetRentalContractEntity rentalContract(
      Long portfolioId, Long assetId, Long contractId) {
    return population.rentalContract(portfolioId, assetId, contractId);
  }

  public LongTermAssetValuationPeriodEntity valuation(
      Long portfolioId, Long assetId, Long periodId) {
    return population.valuation(portfolioId, assetId, periodId);
  }

  public RentalTaxPolicyEntity rentalTaxPolicy(Long portfolioId, Long policyId) {
    return population.rentalTaxPolicy(portfolioId, policyId);
  }

  @Transactional(readOnly = true)
  public Optional<RentalTaxPolicyEntity> rentalTaxPolicy(Long portfolioId, LocalDate date) {
    LocalDate effectiveDate = effectiveDate(date);
    return population.rentalTaxPolicy(portfolioId, effectiveDate);
  }

  @Transactional(readOnly = true)
  public List<RentalTaxPolicyEntity> rentalTaxPolicies(Long portfolioId) {
    return population.rentalTaxPolicies(portfolioId);
  }

  public LongTermAssetSummary summary(LongTermAssetEntity a, LocalDate date) {
    LocalDate effectiveDate = effectiveDate(date);
    return economics.summary(
        a, effectiveDate, population.relatedData(a.getPortfolioId(), List.of(a), effectiveDate));
  }

  public AssetDetailData detail(Long portfolioId, Long id, LocalDate date) {
    LocalDate effectiveDate = effectiveDate(date);
    LongTermAssetEntity asset = owned(portfolioId, id);
    LongTermAssetRelatedDataLoader.Data data =
        population.relatedData(portfolioId, List.of(asset), effectiveDate);
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
        economics.summary(asset, effectiveDate, data),
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
    return economics.summaries(
        rows,
        effectiveDate,
        population.relatedData(rows.getFirst().getPortfolioId(), rows, effectiveDate));
  }

  List<LongTermAssetSummary> summaries(
      List<LongTermAssetEntity> rows, LocalDate date, LongTermAssetRelatedDataLoader.Data loaded) {
    if (rows.isEmpty()) return List.of();
    LocalDate effectiveDate = effectiveDate(date);
    return economics.summaries(rows, effectiveDate, loaded);
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
    return AggregateSummary.of(
        list(portfolioId, date), localCurrency(portfolioId), currencyRates, date);
  }

  private LocalDate effectiveDate(LocalDate date) {
    if (date == null) throw new IllegalArgumentException("date must not be null");
    return date.isBefore(historyStart) ? historyStart : date;
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
          .filter(row -> row.type().contributesToCalculations())
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

  private LongTermAssetEntity owned(Long portfolioId, Long id) {
    return population
        .find(portfolioId, id)
        .orElseThrow(() -> new AssetNotFoundException(portfolioId, id));
  }
}
