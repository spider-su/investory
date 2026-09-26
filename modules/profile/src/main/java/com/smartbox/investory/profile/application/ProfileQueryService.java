package com.smartbox.investory.profile.application;

import com.smartbox.investory.investment.api.portfolio.BrokerageAssetClassificationReader;
import com.smartbox.investory.investment.api.portfolio.BrokerageIncomeSnapshot;
import com.smartbox.investory.investment.api.portfolio.BrokeragePortfolioReader;
import com.smartbox.investory.investment.api.portfolio.SharedBrokeragePortfolioSnapshot;
import com.smartbox.investory.investment.api.reporting.InvestmentIncomeSummaryReader;
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

  @org.springframework.beans.factory.annotation.Autowired
  public ProfileQueryService(
      BrokeragePortfolioReader brokeragePortfolioReadService,
      LongTermAssetProfileReader longTermAssets,
      BrokerageAssetClassificationReader brokerageAssetClassificationReader,
      CurrencyConversion currencyRates,
      Clock clock,
      InvestmentIncomeSummaryReader investmentIncome) {
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
    LongTermAssetProfileSummaryModel longTerm = longTermSnapshot.summary();
    var longTermAssetRows = longTermSnapshot.assets();
    BigDecimal marketCash = market.cash();
    Map<ProfileAllocationCalculator.AllocationKey, BigDecimal> values =
        allocationCalculator.values(
            market,
            longTermAssetRows,
            marketCash,
            (value, source) -> currencyNormalizer.toBase(value, source, base, date));
    BigDecimal marketValue = market.balance();
    BigDecimal longTermValue =
        currencyNormalizer.toBase(longTerm.totalCurrentValue(), longTerm.currency(), base, date);
    BigDecimal longTermInvestmentValue =
        longTermAssetRows.stream()
            .filter(asset -> asset.category() != AssetEconomicCategory.PERSONAL_ASSET)
            .map(
                asset ->
                    currencyNormalizer.toBase(asset.currentValue(), asset.currency(), base, date))
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
            ? market.dividends().add(market.interest())
            : currencyNormalizer.toBase(incomeSnapshot.netIncome(), incomeCurrency, base, date);
    BigDecimal longTermIncome =
        currencyNormalizer.toBase(
            longTerm.netAnnualIncomeAfterTax(), longTerm.currency(), base, date);
    ProfileIncomeSummary income =
        incomeCalculator.calculate(
            marketIncome,
            incomeSnapshot,
            incomeCurrency,
            marketValue,
            longTermIncome,
            longTermInvestmentValue,
            totalInvestmentValue,
            base,
            date);
    if (investmentIncome != null) {
      var summary = investmentIncome.load(portfolioId);
      if (summary.available()) {
        income =
            incomeCalculator.calculate(
                summary, longTermIncome, longTermInvestmentValue, totalInvestmentValue);
      }
    }
    var liquidity =
        liquidityCalculator.calculate(
            values, longTermAssetRows, marketCash, marketValue, base, date);
    return new InvestmentProfile(
        portfolioId,
        base,
        marketValue,
        longTermValue,
        total,
        liquidity.liquid(),
        liquidity.illiquid(),
        allocationCalculator.allocations(values),
        currencyNormalizer.toBase(
            longTermSnapshot.annualSnapshot().rentalIncome(),
            longTermSnapshot.annualSnapshot().currency(),
            base,
            date),
        currencyNormalizer.toBase(
            longTermSnapshot.annualSnapshot().bondIncome(),
            longTermSnapshot.annualSnapshot().currency(),
            base,
            date),
        planningCalculator.state(longTermSnapshot.projectionInputs(), base, date),
        liquidity.reserve(),
        liquidity.investmentCapital(),
        income,
        reconciliation);
  }
}
