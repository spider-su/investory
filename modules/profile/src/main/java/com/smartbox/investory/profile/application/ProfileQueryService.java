package com.smartbox.investory.profile.application;

import com.smartbox.investory.investment.api.portfolio.BrokerageAssetClassificationReader;
import com.smartbox.investory.investment.api.portfolio.BrokerageIncomeSnapshot;
import com.smartbox.investory.investment.api.portfolio.BrokeragePortfolioReader;
import com.smartbox.investory.investment.api.portfolio.SharedBrokeragePortfolioSnapshot;
import com.smartbox.investory.longterm.api.LongTermAssetProfileSummaryReader;
import com.smartbox.investory.longterm.api.LongTermAssetProjectionReader;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileSummaryModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileSummarySnapshotModel;
import com.smartbox.investory.profile.api.ProfileComposition;
import com.smartbox.investory.profile.api.ProfilePlanningReader;
import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.profile.api.ProfileSummaryReader;
import com.smartbox.investory.profile.api.model.AssetHorizon;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation;
import com.smartbox.investory.profile.api.model.ProfileIncomeSummary;
import com.smartbox.investory.profile.api.model.ProfilePlanning;
import com.smartbox.investory.profile.api.model.ProfileSummary;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileQueryService
    implements ProfileSummaryReader, ProfilePlanningReader, ProfileSnapshotReader {
  private final BrokeragePortfolioReader brokeragePortfolioReadService;
  private final LongTermAssetProfileSummaryReader longTermSummary;
  private final LongTermAssetProjectionReader longTermProjections;
  private final CurrencyConversion currencyRates;
  private final Clock clock;
  private final ProfileAllocationCalculator allocationCalculator;
  private final ProfileIncomeCalculator incomeCalculator;
  private final ProfileLiquidityCalculator liquidityCalculator;
  private final ProfilePlanningCalculator planningCalculator;

  @Autowired
  public ProfileQueryService(
      BrokeragePortfolioReader brokeragePortfolioReadService,
      LongTermAssetProfileSummaryReader longTermSummary,
      LongTermAssetProjectionReader longTermProjections,
      BrokerageAssetClassificationReader brokerageAssetClassificationReader,
      CurrencyConversion currencyRates,
      Clock clock) {
    this.brokeragePortfolioReadService = brokeragePortfolioReadService;
    this.longTermSummary = longTermSummary;
    this.longTermProjections = longTermProjections;
    this.currencyRates = currencyRates;
    this.clock = clock;
    this.allocationCalculator = new ProfileAllocationCalculator(brokerageAssetClassificationReader);
    this.incomeCalculator = new ProfileIncomeCalculator(currencyRates);
    this.liquidityCalculator = new ProfileLiquidityCalculator(allocationCalculator, currencyRates);
    this.planningCalculator = new ProfilePlanningCalculator(allocationCalculator);
  }

  @Override
  @Transactional(readOnly = true)
  public ProfileSummary loadSummary(Long portfolioId) {
    return buildSummary(portfolioId);
  }

  @Override
  @Transactional(readOnly = true)
  public ProfilePlanning loadPlanning(Long portfolioId) {
    LocalDate date = LocalDate.now(clock);
    return new ProfilePlanning(
        planningCalculator.state(longTermProjections.projectionInputs(portfolioId, date), date));
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public InvestmentProfile loadProfile(Long portfolioId) {
    return ProfileComposition.load(this, this, portfolioId);
  }

  private ProfileSummary buildSummary(Long portfolioId) {
    LocalDate date = LocalDate.now(clock);
    SharedBrokeragePortfolioSnapshot market =
        brokeragePortfolioReadService.currentSnapshot(portfolioId);
    CurrencyType base = market.baseCurrency();
    LongTermAssetProfileSummarySnapshotModel longTermSnapshot =
        longTermSummary.summary(portfolioId, date);
    LongTermAssetProfileSummaryModel longTerm = longTermSnapshot.summary();
    var longTermAssetRows = longTermSnapshot.assets();
    BigDecimal marketCash = toBase(market.cash(), market.baseCurrency(), base, date);
    Map<ProfileAllocationCalculator.AllocationKey, BigDecimal> values =
        allocationCalculator.values(
            market,
            longTermAssetRows,
            marketCash,
            (value, source) -> toBase(value, source, base, date));
    BigDecimal marketValue = toBase(market.balance(), market.baseCurrency(), base, date);
    BigDecimal longTermValue =
        toBase(longTerm.totalCurrentValue(), longTerm.currency(), base, date);
    ProfileAllocationReconciliation reconciliation =
        new ProfileAllocationReconciliation(
            allocationCalculator.reconciliation(values, AssetHorizon.SHORT_TERM, marketValue),
            allocationCalculator.reconciliation(values, AssetHorizon.LONG_TERM, longTermValue));
    BigDecimal total = marketValue.add(longTermValue);
    BrokerageIncomeSnapshot incomeSnapshot =
        brokeragePortfolioReadService.incomeForMonths(
            portfolioId, YearMonth.of(date.getYear(), 1), YearMonth.from(date));
    CurrencyType incomeCurrency =
        incomeSnapshot == null || incomeSnapshot.baseCurrency() == null
            ? market.baseCurrency()
            : incomeSnapshot.baseCurrency();
    BigDecimal marketIncome =
        incomeSnapshot == null
            ? toBase(market.dividends(), market.baseCurrency(), base, date)
                .add(toBase(market.interest(), market.baseCurrency(), base, date))
            : toBase(incomeSnapshot.netIncome(), incomeCurrency, base, date);
    BigDecimal longTermIncome =
        toBase(longTerm.netAnnualIncomeAfterTax(), longTerm.currency(), base, date);
    ProfileIncomeSummary income =
        incomeCalculator.calculate(
            marketIncome,
            incomeSnapshot,
            incomeCurrency,
            marketValue,
            longTermIncome,
            longTermValue,
            total,
            base,
            date);
    var liquidity =
        liquidityCalculator.calculate(
            values, longTermAssetRows, marketCash, marketValue, base, date);
    return new ProfileSummary(
        portfolioId,
        base,
        marketValue,
        longTermValue,
        total,
        liquidity.liquid(),
        liquidity.illiquid(),
        allocationCalculator.allocations(values),
        toBase(longTermSnapshot.annualSnapshot().rentalIncome(), longTerm.currency(), base, date),
        toBase(longTermSnapshot.annualSnapshot().bondIncome(), longTerm.currency(), base, date),
        liquidity.reserve(),
        liquidity.investmentCapital(),
        income,
        reconciliation);
  }

  private BigDecimal toBase(
      BigDecimal value, CurrencyType source, CurrencyType target, java.time.LocalDate date) {
    return value == null || source == target
        ? value
        : currencyRates.convertToBaseCurrency(value, target, source, date);
  }
}
