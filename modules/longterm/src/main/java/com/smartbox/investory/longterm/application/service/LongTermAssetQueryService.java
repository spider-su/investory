package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.longterm.application.model.AnnualEconomics;
import com.smartbox.investory.longterm.application.model.BondPlanningSummary;
import com.smartbox.investory.longterm.application.model.LongTermAssetSummary;
import com.smartbox.investory.longterm.application.model.RealEstateGroupPlanningSummary;
import com.smartbox.investory.longterm.application.model.RealEstatePlanningSummary;
import com.smartbox.investory.longterm.infrastructure.asset.*;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.longterm.infrastructure.bond.*;
import com.smartbox.investory.longterm.infrastructure.deposit.*;
import com.smartbox.investory.longterm.infrastructure.lifecycle.*;
import com.smartbox.investory.longterm.infrastructure.rental.*;
import com.smartbox.investory.longterm.infrastructure.rental.CashFlowType;
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
  private final LongTermAssetCashFlowRepository cashFlows;
  private final LongTermAssetValuationPeriodRepository valuations;
  private final LongTermAssetBondRatePeriodRepository bondRates;
  private final LongTermAssetBondDetailsRepository bonds;
  private final LongTermAssetDepositDetailsRepository deposits;
  private final RentalTaxPolicyRepository taxPolicies;
  private final PortfolioContextReader portfolioContextReader;
  private final CurrencyConversion currencyRates;
  private final LongTermAssetRentalContractRepository rentalContracts;

  @Value("${app.long-term-assets.planning-currency:PLN}")
  private CurrencyType planningCurrency = CurrencyType.PLN;

  @Value("${app.history-start:2025-01-01}")
  private LocalDate historyStart = LocalDate.of(2025, 1, 1);

  private final RealEstatePlanningCalculator realEstatePlanningCalculator =
      new RealEstatePlanningCalculator();
  private final BondPlanningCalculator bondPlanningCalculator = new BondPlanningCalculator();

  public LongTermAssetQueryService(
      LongTermAssetRepository assets,
      LongTermAssetCashFlowRepository cashFlows,
      LongTermAssetValuationPeriodRepository valuations,
      LongTermAssetBondRatePeriodRepository bondRates,
      LongTermAssetBondDetailsRepository bonds,
      LongTermAssetDepositDetailsRepository deposits,
      RentalTaxPolicyRepository taxPolicies,
      PortfolioContextReader portfolioContextReader,
      CurrencyConversion currencyRates,
      LongTermAssetRentalContractRepository rentalContracts) {
    this.assets = assets;
    this.cashFlows = cashFlows;
    this.valuations = valuations;
    this.bondRates = bondRates;
    this.bonds = bonds;
    this.deposits = deposits;
    this.taxPolicies = taxPolicies;
    this.portfolioContextReader = portfolioContextReader;
    this.currencyRates = currencyRates;
    this.rentalContracts = rentalContracts;
  }

  @Transactional(readOnly = true)
  public List<LongTermAssetSummary> list(Long portfolioId, LocalDate date) {
    LocalDate effectiveDate = effectiveDate(date);
    return assets.findAllByPortfolioIdOrderByName(portfolioId).stream()
        .filter(LongTermAssetEntity::isActive)
        .map(a -> summary(a, effectiveDate))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<LongTermAssetSummary> archived(Long portfolioId, LocalDate date) {
    LocalDate effectiveDate = effectiveDate(date);
    return assets.findAllByPortfolioIdOrderByName(portfolioId).stream()
        .filter(a -> !a.isActive())
        .map(a -> summary(a, effectiveDate))
        .toList();
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
            "Cash reserves",
            rows.stream()
                .filter(r -> r.type() == LongTermAssetType.CASH_RESERVE)
                .sorted(
                    Comparator.comparing(LongTermAssetSummary::name, String.CASE_INSENSITIVE_ORDER))
                .toList(),
            base,
            effectiveDate),
        group(
            "OTHER",
            "Other assets",
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

  @Transactional(readOnly = true)
  public List<LongTermAssetCashFlowEntity> cashFlows(Long portfolioId, Long id) {
    owned(portfolioId, id);
    return cashFlows.findAllByAssetIdOrderByValidFrom(id).stream()
        .sorted(
            Comparator.comparingInt(
                    (LongTermAssetCashFlowEntity flow) -> cashFlowOrder(flow.getType()))
                .thenComparing(LongTermAssetCashFlowEntity::getValidFrom)
                .thenComparing(flow -> Optional.ofNullable(flow.getId()).orElse(Long.MAX_VALUE)))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<LongTermAssetCashFlowEntity> currentCashFlows(
      Long portfolioId, Long id, LocalDate date) {
    return cashFlows(portfolioId, id).stream()
        .filter(flow -> LongTermAssetCalculator.applies(flow, effectiveDate(date)))
        .toList();
  }

  @Transactional(readOnly = true)
  public RentalPeriod rentalPeriod(Long portfolioId, Long id, LocalDate date) {
    List<LongTermAssetCashFlowEntity> current = currentCashFlows(portfolioId, id, date);
    return current.stream()
        .min(Comparator.comparing(LongTermAssetCashFlowEntity::getValidFrom))
        .map(
            flow ->
                new RentalPeriod(
                    flow.getValidFrom(),
                    current.stream()
                        .map(LongTermAssetCashFlowEntity::getValidTo)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null)))
        .orElse(new RentalPeriod(null, null));
  }

  @Transactional(readOnly = true)
  public List<CashFlowType> availableCashFlowTypes(Long portfolioId, Long id, LocalDate date) {
    Set<CashFlowType> currentTypes =
        currentCashFlows(portfolioId, id, date).stream()
            .map(LongTermAssetCashFlowEntity::getType)
            .collect(java.util.stream.Collectors.toSet());
    return Arrays.stream(CashFlowType.values())
        .filter(type -> !currentTypes.contains(type))
        .sorted(Comparator.comparingInt(LongTermAssetQueryService::cashFlowOrder))
        .toList();
  }

  private static int cashFlowOrder(CashFlowType type) {
    return switch (type) {
      case RENT -> 0;
      case PARKING_RENT -> 1;
      case ADMIN_FEE -> 2;
      case UTILITIES -> 3;
      case PROPERTY_TAX -> 4;
      case OTHER_INCOME, INSURANCE, OTHER_EXPENSE -> 5;
    };
  }

  @Transactional(readOnly = true)
  public Optional<LongTermAssetBondDetailsEntity> bondDetails(Long portfolioId, Long id) {
    owned(portfolioId, id);
    return bonds.findById(id);
  }

  @Transactional(readOnly = true)
  public Optional<LongTermAssetDepositDetailsEntity> depositDetails(Long portfolioId, Long id) {
    owned(portfolioId, id);
    return deposits.findById(id);
  }

  @Transactional(readOnly = true)
  public List<LongTermAssetValuationPeriodEntity> valuationPeriods(Long portfolioId, Long id) {
    owned(portfolioId, id);
    return valuations.findAllByAssetIdOrderByValidFrom(id);
  }

  /** Returns the effective property-growth assumption for presentation and simulation inputs. */
  public BigDecimal expectedPropertyGrowth(Long portfolioId, Long id, LocalDate date) {
    return valuationPeriods(portfolioId, id).stream()
        .filter(
            period ->
                !period.getValidFrom().isAfter(date)
                    && (period.getValidTo() == null || !period.getValidTo().isBefore(date)))
        .max(java.util.Comparator.comparing(LongTermAssetValuationPeriodEntity::getValidFrom))
        .map(LongTermAssetValuationPeriodEntity::getExpectedAnnualGrowthRate)
        .orElse(null);
  }

  @Transactional(readOnly = true)
  public List<LongTermAssetBondRatePeriodEntity> bondRatePeriods(Long portfolioId, Long id) {
    owned(portfolioId, id);
    return bondRates.findAllByAssetIdOrderByValidFrom(id);
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
          rentalContracts.findAllByAssetIdOrderByStartDate(a.getId()).stream()
              .map(
                  c ->
                      new RentalContractModel(
                          c.getId(),
                          c.getStartDate(),
                          c.getEndDate(),
                          c.getTerminatedDate(),
                          c.getRentalTaxPaidByTenant(),
                          c.getTenantName(),
                          c.getTenantEmail(),
                          c.getTenantPhone(),
                          c.getTerms().stream()
                              .map(
                                  t ->
                                      new RentalContractModel.Term(
                                          com.smartbox.investory.longterm.api.model
                                              .CashFlowTypeModel.valueOf(t.getType().name()),
                                          t.getAmount(),
                                          com.smartbox.investory.longterm.api.model.FrequencyModel
                                              .valueOf(t.getFrequency().name()),
                                          t.isPaidByTenant()))
                              .toList()))
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
                                        == com.smartbox.investory.longterm.api.model
                                            .CashFlowTypeModel.RENT))
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
                rentalTaxPolicy(a.getPortfolioId(), effectiveDate)
                    .map(RentalTaxPolicyEntity::getRate)
                    .orElse(REAL_ESTATE_TAX_RATE));
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
      LongTermAssetBondDetailsEntity d = bonds.findById(a.getId()).orElse(null);
      if (d != null) maturity = d.getMaturityDate();
      rate = currentRate(a.getId(), effectiveDate);
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
      LongTermAssetDepositDetailsEntity d = deposits.findById(a.getId()).orElse(null);
      if (d != null) {
        maturity = d.getMaturityDate();
        rate = d.getAnnualInterestRate();
        gross = a.getCurrentValue().multiply(rate);
        tax = gross.multiply(d.getTaxRate());
      }
    } else if (a.getType() == LongTermAssetType.CASH_RESERVE) {
      rate =
          valuations.findAllByAssetIdOrderByValidFrom(a.getId()).stream()
              .filter(
                  p ->
                      LongTermAssetCalculator.applies(
                          p.getValidFrom(), p.getValidTo(), effectiveDate))
              .reduce((first, second) -> second)
              .map(LongTermAssetValuationPeriodEntity::getExpectedAnnualGrowthRate)
              .orElse(BigDecimal.ZERO);
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
            .orElseThrow(() -> new NoSuchElementException("Portfolio not found"))
            .baseCurrency();
    return AggregateSummary.of(list(portfolioId, date), base, currencyRates, date);
  }

  @Transactional(readOnly = true)
  public AggregateSummary aggregateForLongTermAssets(Long portfolioId, LocalDate date) {
    date = effectiveDate(date);
    return AggregateSummary.of(list(portfolioId, date), planningCurrency, currencyRates, date);
  }

  private LocalDate effectiveDate(LocalDate date) {
    return date != null && date.isBefore(historyStart) ? historyStart : date;
  }

  private static BigDecimal normalizeMoney(BigDecimal value) {
    BigDecimal rounded = value.setScale(3, java.math.RoundingMode.HALF_UP);
    return rounded.stripTrailingZeros().scale() <= 0
        ? rounded.setScale(0)
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
      return FinancialPresentation.wholeNumber(
          annualEconomics
              .netAnnualIncomeAfterTax()
              .divide(BigDecimal.valueOf(12), 2, java.math.RoundingMode.HALF_UP));
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

    @Deprecated
    public AggregateSummary(
        CurrencyType currency,
        BigDecimal totalCurrentValue,
        BigDecimal gross,
        BigDecimal expenses,
        BigDecimal netBeforeTax,
        BigDecimal tax,
        BigDecimal netAfterTax) {
      this(
          currency, totalCurrentValue, AnnualEconomics.of(totalCurrentValue, gross, expenses, tax));
    }

    public BigDecimal weightedGrossYield() {
      return annualEconomics.grossYield();
    }

    public BigDecimal weightedNetYield() {
      return annualEconomics.netYieldAfterTax();
    }

    /**
     * @deprecated Use {@link #annualEconomics()}.
     */
    @Deprecated
    public BigDecimal totalGrossAnnualIncome() {
      return annualEconomics.grossAnnualIncome();
    }

    /**
     * @deprecated Use {@link #annualEconomics()}.
     */
    @Deprecated
    public BigDecimal totalAnnualExpenses() {
      return annualEconomics.annualExpenses();
    }

    /**
     * @deprecated Use {@link #annualEconomics()}.
     */
    @Deprecated
    public BigDecimal totalNetAnnualIncomeBeforeTax() {
      return annualEconomics.netAnnualIncomeBeforeTax();
    }

    /**
     * @deprecated Use {@link #annualEconomics()}.
     */
    @Deprecated
    public BigDecimal estimatedAnnualTax() {
      return annualEconomics.annualTax();
    }

    /**
     * @deprecated Use {@link #annualEconomics()}.
     */
    @Deprecated
    public BigDecimal totalNetAnnualIncomeAfterTax() {
      return annualEconomics.netAnnualIncomeAfterTax();
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

    /**
     * @deprecated Use {@link #annualEconomics()}.
     */
    @Deprecated
    public BigDecimal grossAnnualIncome() {
      return annualEconomics.grossAnnualIncome();
    }

    /**
     * @deprecated Use {@link #annualEconomics()}.
     */
    @Deprecated
    public BigDecimal grossYield() {
      return annualEconomics.grossYield();
    }

    /**
     * @deprecated Use {@link #realEstatePlanning()}.
     */
    @Deprecated
    public BigDecimal totalPaymentMonthly() {
      return realEstatePlanning == null
          ? BigDecimal.ZERO
          : realEstatePlanning.totalPaymentMonthly();
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

  private BigDecimal currentRate(Long id, LocalDate date) {
    return bondRates.findAllByAssetIdOrderByValidFrom(id).stream()
        .filter(p -> LongTermAssetCalculator.applies(p.getValidFrom(), p.getValidTo(), date))
        .findFirst()
        .map(LongTermAssetBondRatePeriodEntity::getAnnualInterestRate)
        .orElse(BigDecimal.ZERO);
  }

  private static LocalDate effectiveContractEnd(RentalContractModel c) {
    if (c.endDate() == null) return c.terminatedDate();
    if (c.terminatedDate() == null) return c.endDate();
    return c.endDate().isBefore(c.terminatedDate()) ? c.endDate() : c.terminatedDate();
  }

  public record RentalPeriod(LocalDate effectiveFrom, LocalDate endDate) {}

  private LongTermAssetEntity owned(Long portfolioId, Long id) {
    return assets
        .findByIdAndPortfolioId(id, portfolioId)
        .orElseThrow(() -> new NoSuchElementException("Long-term asset not found"));
  }
}
