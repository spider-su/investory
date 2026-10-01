package com.smartbox.investory.profile.application;

import com.smartbox.investory.investment.api.portfolio.BrokerageAssetClassificationReader;
import com.smartbox.investory.investment.api.portfolio.BrokerageIncomeSnapshot;
import com.smartbox.investory.investment.api.portfolio.BrokeragePortfolioReader;
import com.smartbox.investory.investment.api.portfolio.SharedBrokeragePortfolioSnapshot;
import com.smartbox.investory.investment.api.reporting.InvestmentIncomeSummaryReader;
import com.smartbox.investory.investment.api.reporting.InvestmentIncomeSummaryReader.InvestmentIncomeSummary;
import com.smartbox.investory.longterm.api.LongTermAssetProfileReader;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileSnapshotModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileSummaryModel;
import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.profile.api.model.AssetHorizon;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation;
import com.smartbox.investory.profile.api.model.ProfileIncomeSummary;
import com.smartbox.investory.shared.assets.AssetEconomicCategory;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileQueryService implements ProfileSnapshotReader {
  private final BrokeragePortfolioReader brokeragePortfolioReadService;
  private final LongTermAssetProfileReader longTermAssets;
  private final ProfileCurrencyNormalizer currencyNormalizer;
  private final Clock clock;
  private final ProfileAllocationCalculator allocationCalculator;
  private final ProfileIncomeCalculator incomeCalculator;
  private final ProfileLiquidityCalculator liquidityCalculator;
  private final ProfilePlanningCalculator planningCalculator;
  private final InvestmentIncomeSummaryReader investmentIncome;
  private final PortfolioContextReader portfolioContexts;

  @org.springframework.beans.factory.annotation.Autowired
  public ProfileQueryService(
      BrokeragePortfolioReader brokeragePortfolioReadService,
      LongTermAssetProfileReader longTermAssets,
      BrokerageAssetClassificationReader brokerageAssetClassificationReader,
      CurrencyConversion currencyRates,
      Clock clock,
      InvestmentIncomeSummaryReader investmentIncome,
      PortfolioContextReader portfolioContexts) {
    this.brokeragePortfolioReadService = brokeragePortfolioReadService;
    this.longTermAssets = longTermAssets;
    this.clock = clock;
    this.allocationCalculator = new ProfileAllocationCalculator(brokerageAssetClassificationReader);
    this.currencyNormalizer = new ProfileCurrencyNormalizer(currencyRates);
    this.incomeCalculator = new ProfileIncomeCalculator(currencyNormalizer);
    this.liquidityCalculator = new ProfileLiquidityCalculator(currencyNormalizer);
    this.planningCalculator =
        new ProfilePlanningCalculator(allocationCalculator, currencyNormalizer);
    this.investmentIncome = investmentIncome;
    this.portfolioContexts = portfolioContexts;
  }

  public ProfileQueryService(
      BrokeragePortfolioReader brokeragePortfolioReadService,
      LongTermAssetProfileReader longTermAssets,
      BrokerageAssetClassificationReader brokerageAssetClassificationReader,
      CurrencyConversion currencyRates,
      Clock clock) {
    this(
        brokeragePortfolioReadService,
        longTermAssets,
        brokerageAssetClassificationReader,
        currencyRates,
        clock,
        null,
        null);
  }

  @Override
  @Transactional(
      readOnly = true,
      isolation = Isolation.REPEATABLE_READ,
      propagation = Propagation.REQUIRES_NEW)
  public InvestmentProfile loadProfile(Long portfolioId) {
    if (portfolioId == null || portfolioId <= 0) {
      throw new IllegalArgumentException("portfolioId must be positive");
    }
    LocalDate date = LocalDate.now(clock);
    var longTermSnapshot = longTermAssets.snapshot(portfolioId, date);
    return buildProfile(portfolioId, longTermSnapshot, date);
  }

  private InvestmentProfile buildProfile(
      Long portfolioId, LongTermAssetProfileSnapshotModel longTermSnapshot, LocalDate date) {
    SharedBrokeragePortfolioSnapshot market =
        brokeragePortfolioReadService.currentSnapshot(portfolioId);
    CurrencyType base = market.baseCurrency();
    CurrencyType display =
        portfolioContexts == null
            ? base
            : portfolioContexts
                .findById(portfolioId)
                .map(context -> context.localCurrency())
                .orElse(base);
    LongTermAssetProfileSummaryModel longTerm = longTermSnapshot.summary();
    var longTermAssetRows = longTermSnapshot.assets();
    BigDecimal marketCash = currencyNormalizer.toBase(market.cash(), base, display, date);
    Map<ProfileAllocationCalculator.AllocationKey, BigDecimal> values =
        allocationCalculator.values(
            market,
            longTermAssetRows,
            marketCash,
            (value, source) -> currencyNormalizer.toBase(value, source, display, date));
    BigDecimal marketValue = currencyNormalizer.toBase(market.balance(), base, display, date);
    BigDecimal longTermValue = longTerm.totalCurrentValue();
    BigDecimal longTermInvestmentValue =
        longTermAssetRows.stream()
            .filter(asset -> asset.category() != AssetEconomicCategory.PERSONAL_ASSET)
            .map(
                asset ->
                    currencyNormalizer.toBase(
                        asset.currentValue(), asset.currency(), display, date))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    ProfileAllocationReconciliation reconciliation =
        new ProfileAllocationReconciliation(
            allocationCalculator.reconciliation(values, AssetHorizon.SHORT_TERM, marketValue),
            allocationCalculator.reconciliation(values, AssetHorizon.LONG_TERM, longTermValue));
    BigDecimal total = marketValue.add(longTermValue);
    BigDecimal totalInvestmentValue = marketValue.add(longTermInvestmentValue);
    BrokerageIncomeSnapshot incomeSnapshot =
        brokeragePortfolioReadService.incomeForMonths(
            portfolioId, YearMonth.of(date.getYear(), 1), YearMonth.from(date));
    CurrencyType incomeCurrency =
        incomeSnapshot == null || incomeSnapshot.baseCurrency() == null
            ? market.baseCurrency()
            : incomeSnapshot.baseCurrency();
    BigDecimal marketIncome =
        incomeSnapshot == null
            ? currencyNormalizer.toBase(
                market.dividends().add(market.interest()), base, display, date)
            : currencyNormalizer.toBase(incomeSnapshot.netIncome(), incomeCurrency, display, date);
    BigDecimal longTermIncome = longTerm.netAnnualIncomeAfterTax();
    ProfileIncomeSummary income =
        incomeCalculator.calculate(
            marketIncome,
            incomeSnapshot,
            incomeCurrency,
            marketValue,
            longTermIncome,
            longTermInvestmentValue,
            totalInvestmentValue,
            display,
            date);
    if (investmentIncome != null) {
      var summary = investmentIncome.load(portfolioId);
      if (summary.available()) {
        CurrencyType investmentCurrency = summary.currency() == null ? base : summary.currency();
        var displaySummary =
            new InvestmentIncomeSummary(
                summary.available(),
                display,
                currencyNormalizer.toBase(
                    summary.investmentBase(), investmentCurrency, display, date),
                currencyNormalizer.toBase(
                    summary.expectedAnnualInvestmentResult(), investmentCurrency, display, date),
                summary.expectedAnnualReturn(),
                currencyNormalizer.toBase(
                    summary.investmentResultYtd(), investmentCurrency, display, date),
                currencyNormalizer.toBase(
                    summary.expectedInvestmentResultYtd(), investmentCurrency, display, date),
                summary.expectationProgress());
        income =
            incomeCalculator.calculate(
                displaySummary, longTermIncome, longTermInvestmentValue, totalInvestmentValue);
      }
    }
    var liquidity =
        liquidityCalculator.calculate(
            values, longTermAssetRows, marketCash, marketValue, display, date);
    return new InvestmentProfile(
        portfolioId,
        display,
        marketValue,
        longTermValue,
        total,
        liquidity.liquid(),
        liquidity.illiquid(),
        allocationCalculator.allocations(values),
        longTermSnapshot.annualSnapshot().rentalIncome(),
        longTermSnapshot.annualSnapshot().bondIncome(),
        planningCalculator.state(longTermSnapshot.projectionInputs(), display, date),
        liquidity.reserve(),
        liquidity.investmentCapital(),
        income,
        reconciliation);
  }
}
