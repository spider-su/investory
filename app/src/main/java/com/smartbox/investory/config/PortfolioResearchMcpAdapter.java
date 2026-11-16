package com.smartbox.investory.config;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import com.smartbox.investory.mcp.PortfolioResearchClient;
import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.profile.web.ProfileResponseMapper;
import com.smartbox.investory.rag.InvestoryRagClient;
import com.smartbox.investory.retirement.api.RetirementPlanApi;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Composition bridge: MCP and chat read the same public domain APIs. */
@Component
@ConditionalOnProperty(prefix = "app.mcp", name = "enabled", havingValue = "true")
public class PortfolioResearchMcpAdapter implements PortfolioResearchClient {
  private final InvestmentDashboardApi investment;
  private final ProfileSnapshotReader profiles;
  private final ProfileResponseMapper mapper;
  private final RetirementPlanApi plans;
  private final ObjectProvider<InvestoryRagClient> rag;
  private final ObjectProvider<com.smartbox.investory.rag.ExternalMarketDataService> market;

  public PortfolioResearchMcpAdapter(
      InvestmentDashboardApi investment,
      ProfileSnapshotReader profiles,
      ProfileResponseMapper mapper,
      RetirementPlanApi plans,
      ObjectProvider<InvestoryRagClient> rag,
      ObjectProvider<com.smartbox.investory.rag.ExternalMarketDataService> market) {
    this.investment = investment;
    this.profiles = profiles;
    this.mapper = mapper;
    this.plans = plans;
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
        value.data(),
        c.status(),
        c.fetchedAt(),
        c.asOf(),
        c.expiresAt(),
        c.remainingBudget(),
        c.warnings());
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
