package com.smartbox.investory.config;

import com.smartbox.investory.investment.api.asset.InvestmentAssetApi;
import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import com.smartbox.investory.longterm.api.LongTermAssetsApi;
import com.smartbox.investory.mcp.PortfolioResearchClient;
import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.profile.web.ProfileResponseMapper;
import com.smartbox.investory.rag.InvestoryRagClient;
import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.RetirementProjectionApi;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Composition bridge: MCP and chat read the same public domain APIs. */
@Component
@ConditionalOnExpression("${app.mcp.enabled:false} or ${investory.rag.enabled:false}")
public class PortfolioResearchMcpAdapter implements PortfolioResearchClient {
  private final InvestmentDashboardApi investment;
  private final InvestmentAssetApi assets;
  private final ProfileSnapshotReader profiles;
  private final ProfileResponseMapper mapper;
  private final RetirementPlanApi plans;
  private final LongTermAssetsApi longTerm;
  private final RetirementProjectionApi projections;
  private final ObjectProvider<InvestoryRagClient> rag;
  private final ObjectProvider<com.smartbox.investory.rag.ExternalMarketDataService> market;

  public PortfolioResearchMcpAdapter(
      InvestmentDashboardApi investment,
      InvestmentAssetApi assets,
      ProfileSnapshotReader profiles,
      ProfileResponseMapper mapper,
      RetirementPlanApi plans,
      LongTermAssetsApi longTerm,
      RetirementProjectionApi projections,
      ObjectProvider<InvestoryRagClient> rag,
      ObjectProvider<com.smartbox.investory.rag.ExternalMarketDataService> market) {
    this.investment = investment;
    this.assets = assets;
    this.profiles = profiles;
    this.mapper = mapper;
    this.plans = plans;
    this.longTerm = longTerm;
    this.projections = projections;
    this.rag = rag;
    this.market = market;
  }

  private static String decimal(BigDecimal value) {
    return value == null ? null : value.toPlainString();
  }

  private static String decimal(Double value) {
    return value == null || !Double.isFinite(value)
        ? null
        : BigDecimal.valueOf(value).toPlainString();
  }

  private InvestmentDashboardApi.DashboardPageView page(long id, DashboardPeriod period) {
    return investment.loadDashboard(
        new InvestmentDashboardApi.DashboardQuery(List.of(), false, period, id));
  }

  @Override
  public GeneralProfile generalProfile(long id) {
    var p = mapper.map(profiles.loadProfile(id));
    var income = p.incomeSummary();
    return new GeneralProfile(
        p.currency() == null ? null : p.currency().name(),
        decimal(p.totalNetWorth()),
        decimal(p.marketPortfolioValue()),
        decimal(p.longTermAssetValue()),
        decimal(p.liquidAssets()),
        decimal(p.illiquidAssets()),
        decimal(p.currentRentalIncome()),
        decimal(p.currentBondIncome()),
        income == null ? null : decimal(income.combinedNetAnnualIncome()),
        income == null ? null : decimal(income.longTermAnnualIncome()),
        income == null ? null : decimal(income.marketNetAnnualIncome()),
        decimal(p.retirementReserve()),
        decimal(p.investmentCapital()),
        Instant.now(),
        "live:profile");
  }

  @Override
  public LongTermSummary longTermAssets(long id, int limit) {
    if (limit < 1 || limit > 20) throw new IllegalArgumentException();
    var view = longTerm.overview(id, LocalDate.now());
    if (view == null) throw new IllegalStateException("Long-term overview unavailable");
    var items =
        view.groups().stream()
            .flatMap(
                group ->
                    group.assets() == null
                        ? java.util.stream.Stream.empty()
                        : group.assets().stream())
            .limit(limit)
            .map(
                asset ->
                    new LongTermAsset(
                        asset.name(),
                        asset.type() == null ? null : asset.type().name(),
                        asset.currency() == null ? null : asset.currency().name(),
                        decimal(asset.currentValue()),
                        asset.maturityDate() == null ? null : asset.maturityDate().toString(),
                        decimal(asset.currentAnnualRate()),
                        decimal(asset.monthlyRentalIncome()),
                        asset.integrityWarning()))
            .toList();
    var economics = view.economics();
    return new LongTermSummary(
        view.currency() == null ? null : view.currency().name(),
        decimal(view.totalValue()),
        decimal(view.investmentValue()),
        decimal(view.personalAssetValue()),
        economics == null ? null : decimal(economics.netAnnualIncomeAfterTax()),
        economics == null ? null : decimal(economics.monthlyNetIncomeAfterTax()),
        items,
        Instant.now(),
        "live:longterm");
  }

  @Override
  public RetirementSimulation retirementSimulation(long id) {
    var selected = plans.resolvePlanId(id, null);
    if (selected.isEmpty()) {
      return new RetirementSimulation(
          false,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          List.of(),
          Instant.now(),
          "live:retirement-simulation",
          "No selected saved plan");
    }
    var details = plans.details(id, selected.get());
    var projection = projections.load(id, selected.get());
    var assumptions = projection.assumptions();
    var currency = mapper.map(profiles.loadProfile(id)).currency();
    var scenarios =
        Arrays.stream(com.smartbox.investory.retirement.api.model.SimulationScenario.values())
            .map(
                scenario -> {
                  var summary = projection.summaries().get(scenario);
                  if (summary == null) return null;
                  return new SimulationScenarioSummary(
                      scenario.name(),
                      summary.failed(),
                      summary.firstFailureAge(),
                      summary.firstFailureYear(),
                      decimal(summary.finalNetWorth()),
                      decimal(summary.finalLiquidAssets()),
                      decimal(summary.finalIlliquidAssets()),
                      decimal(summary.minimumLiquidAssets()),
                      decimal(summary.lifetimeRequiredPortfolioFunding()),
                      decimal(summary.lifetimeActualWithdrawals()),
                      decimal(summary.totalUnfundedAmount()),
                      decimal(summary.firstYearPassiveIncomeCoverage()));
                })
            .filter(java.util.Objects::nonNull)
            .toList();
    return new RetirementSimulation(
        true,
        details == null ? null : details.name(),
        currency == null ? null : currency.name(),
        assumptions.currentAge(),
        assumptions.retirementAge(),
        assumptions.endAge(),
        decimal(assumptions.annualLivingExpenses()),
        decimal(assumptions.annualDiscretionaryExpenses()),
        decimal(assumptions.inflationRate()),
        decimal(assumptions.equityReturnRate()),
        decimal(assumptions.fixedIncomeReturnRate()),
        scenarios,
        Instant.now(),
        "live:retirement-simulation",
        "Saved-plan projection only; nominal scenarios are model estimates, not guaranteed outcomes.");
  }

  @Override
  public RetirementSimulation retirementWhatIf(
      long id,
      String selectedPlan,
      BigDecimal annualLivingExpenses,
      BigDecimal annualDiscretionaryExpenses) {
    if (selectedPlan == null
        || selectedPlan.isBlank()
        || annualLivingExpenses == null
        || annualLivingExpenses.signum() < 0
        || annualDiscretionaryExpenses == null
        || annualDiscretionaryExpenses.signum() < 0) {
      throw new IllegalArgumentException("Invalid retirement what-if inputs");
    }
    var resolvedId = plans.resolvePlanId(id, null);
    if (resolvedId.isEmpty()) throw new IllegalArgumentException("No selected retirement plan");
    var details = plans.details(id, resolvedId.get());
    if (details == null || details.assumptions() == null || !selectedPlan.equals(details.name())) {
      throw new IllegalArgumentException("Retirement plan has changed");
    }
    var assumptions =
        details.assumptions().toBuilder()
            .annualLivingExpenses(annualLivingExpenses)
            .annualDiscretionaryExpenses(annualDiscretionaryExpenses)
            .build();
    // The public projection use case is read-only; it does not update saved plan assumptions.
    var projection = projections.project(profiles.loadProfile(id), assumptions, details.baseline());
    var currency = mapper.map(profiles.loadProfile(id)).currency();
    var scenarios =
        Arrays.stream(com.smartbox.investory.retirement.api.model.SimulationScenario.values())
            .map(
                scenario -> {
                  var summary = projection.summaries().get(scenario);
                  if (summary == null) return null;
                  return new SimulationScenarioSummary(
                      scenario.name(),
                      summary.failed(),
                      summary.firstFailureAge(),
                      summary.firstFailureYear(),
                      decimal(summary.finalNetWorth()),
                      decimal(summary.finalLiquidAssets()),
                      decimal(summary.finalIlliquidAssets()),
                      decimal(summary.minimumLiquidAssets()),
                      decimal(summary.lifetimeRequiredPortfolioFunding()),
                      decimal(summary.lifetimeActualWithdrawals()),
                      decimal(summary.totalUnfundedAmount()),
                      decimal(summary.firstYearPassiveIncomeCoverage()));
                })
            .filter(java.util.Objects::nonNull)
            .toList();
    return new RetirementSimulation(
        true,
        details.name(),
        currency == null ? null : currency.name(),
        assumptions.currentAge(),
        assumptions.retirementAge(),
        assumptions.endAge(),
        decimal(assumptions.annualLivingExpenses()),
        decimal(assumptions.annualDiscretionaryExpenses()),
        decimal(assumptions.inflationRate()),
        decimal(assumptions.equityReturnRate()),
        decimal(assumptions.fixedIncomeReturnRate()),
        scenarios,
        Instant.now(),
        "live:retirement-what-if",
        "Read-only calculation using an isolated assumptions copy; saved plan unchanged.");
  }

  @Override
  public ProfileSummary profile(long id) {
    var p = mapper.map(profiles.loadProfile(id));
    return new ProfileSummary(
        p.currency() == null ? null : p.currency().name(),
        decimal(p.totalNetWorth()),
        decimal(p.marketPortfolioValue()),
        decimal(p.longTermAssetValue()),
        decimal(p.liquidAssets()),
        decimal(p.illiquidAssets()),
        Instant.now(),
        "live:profile");
  }

  @Override
  public HoldingsSummary holdings(long id, int limit) {
    if (limit < 1 || limit > 20) throw new IllegalArgumentException();
    var overview = page(id, DashboardPeriod.MAX).overview();
    var structure = overview.portfolioStructure();
    var holdings =
        structure == null
            ? List.<Holding>of()
            : structure.topHoldings().stream()
                .limit(limit)
                .map(h -> new Holding(h.symbol(), decimal(h.value()), decimal(h.weightPct())))
                .toList();
    return new HoldingsSummary(
        overview.baseCurrency() == null ? null : overview.baseCurrency().name(),
        holdings,
        Instant.now(),
        "live:investment-structure",
        "Current Investment positions; excludes separate long-term assets. Computed time is not a price valuation date.");
  }

  @Override
  public PeriodResult performance(long id, DashboardPeriod period) {
    var overview = page(id, period).overview();
    return new PeriodResult(
        period.wireValue(),
        overview.baseCurrency() == null ? null : overview.baseCurrency().name(),
        decimal(overview.totalProfit()),
        decimal(overview.totalProfit()) != null,
        Instant.now(),
        "live:investment-performance");
  }

  @Override
  public BenchmarkResult benchmark(long id, DashboardPeriod period) {
    var p = page(id, period);
    var b = p.performance() == null ? null : p.performance().benchmark();
    boolean available =
        b != null
            && b.portfolioPerformanceAvailable()
            && b.benchmarkAvailable()
            && "SPY".equals(b.symbol())
            && decimal(b.portfolioPl()) != null
            && decimal(b.benchmarkPl()) != null
            && decimal(b.portfolioReturnPct()) != null
            && decimal(b.benchmarkReturnPct()) != null;
    return new BenchmarkResult(
        period.wireValue(),
        p.overview().baseCurrency() == null ? null : p.overview().baseCurrency().name(),
        "SPY",
        available,
        available ? decimal(b.portfolioPl()) : null,
        available ? decimal(b.benchmarkPl()) : null,
        available ? decimal(b.portfolioReturnPct()) : null,
        available ? decimal(b.benchmarkReturnPct()) : null,
        Instant.now(),
        "live:investment-benchmark",
        "Existing flow-adjusted benchmark scenario, not a first-date lump-sum backtest.");
  }

  @Override
  public AssetSummary asset(long id, String symbol, DashboardPeriod period) {
    var asset = assets.detail(id, symbol, period);
    return new AssetSummary(
        asset.symbol(),
        asset.name(),
        asset.currency() == null ? null : asset.currency().name(),
        decimal(asset.totalQuantity()),
        decimal(asset.totalMarketValue()),
        decimal(asset.marketPrice()),
        asset.priceSource(),
        asset.priceUpdatedAt(),
        period.wireValue(),
        decimal(asset.totalRealizedProfitLoss()),
        decimal(asset.totalNetDividends()),
        "live:investment-asset");
  }

  @Override
  public CashFlows cashFlows(long id) {
    var page = page(id, DashboardPeriod.MAX);
    var cash = page.cashFlow();
    var currency = page.overview().baseCurrency();
    if (cash == null || currency == null) throw new IllegalStateException();
    return new CashFlows(
        currency.name(),
        decimal(cash.deposits()),
        decimal(cash.withdrawals()),
        decimal(cash.cash()),
        "live:investment-cash-flow",
        "since inception");
  }

  @Override
  public Freshness freshness(long id) {
    var quality = page(id, DashboardPeriod.MAX).dataQuality();
    if (quality == null) throw new IllegalStateException();
    return new Freshness(
        quality.state(),
        quality.latestPriceDate(),
        quality.latestFxMonth(),
        quality.missingPriceCount(),
        quality.stalePriceCount(),
        quality.missingFxCount(),
        "live:investment-data-quality");
  }

  @Override
  public RetirementSummary retirement(long id) {
    var names = plans.listPlans(id).stream().map(p -> p.name()).toList();
    var selected = plans.resolvePlanId(id, null);
    var details = selected.isEmpty() ? null : plans.details(id, selected.get());
    var a = details == null ? null : details.assumptions();
    return new RetirementSummary(
        a != null,
        details == null ? null : details.name(),
        a == null ? null : a.currentAge(),
        a == null ? null : a.retirementAge(),
        a == null ? null : a.endAge(),
        names,
        Instant.now(),
        "live:retirement-plan");
  }

  @Override
  public Documentation search(long id, String query, int limit) {
    var r = rag.getIfAvailable();
    if (r == null) throw new IllegalStateException();
    return new Documentation(
        r.searchDocumentation(query, limit).matches().stream()
            .map(m -> new Snippet(m.source(), m.text(), m.score()))
            .toList(),
        Instant.now());
  }

  @Override
  public MarketQuote quote(long id, String symbol) {
    var gateway = market.getIfAvailable();
    if (gateway == null) throw new IllegalStateException();
    var value = gateway.lookup("GLOBAL_QUOTE", symbol);
    var c = value.cache();
    return new MarketQuote(
        value.provider(),
        value.tool(),
        value.symbol(),
        value.source(),
        value.quote().price(),
        value.quote().currency(),
        c.status(),
        c.fetchedAt(),
        c.asOf(),
        c.expiresAt(),
        c.remainingBudget(),
        c.warnings());
  }

  @Override
  public MarketQuoteStatus quoteStatus(long id, String symbol) {
    var gateway = market.getIfAvailable();
    if (gateway == null) throw new IllegalStateException();
    var status = gateway.quoteStatus(symbol, false);
    return new MarketQuoteStatus(
        status.providerSymbol(),
        status.coverage(),
        status.cacheStatus(),
        status.symbolCooldownUntil(),
        status.providerCooldownUntil(),
        status.budgetAvailableAt(),
        status.remainingBudget(),
        status.budgetLimit());
  }

  @Override
  public Capabilities capabilities(long id) {
    var gateway = market.getIfAvailable();
    return new Capabilities(
        Arrays.stream(DashboardPeriod.values()).map(DashboardPeriod::wireValue).toList(),
        gateway != null && gateway.enabled(),
        gateway == null ? null : gateway.budget(),
        gateway == null || !gateway.enabled() ? null : gateway.remaining(),
        "rolling 24 hours; attempted lookups, including failures",
        "Computed timestamps do not imply uniform underlying price freshness.");
  }
}
