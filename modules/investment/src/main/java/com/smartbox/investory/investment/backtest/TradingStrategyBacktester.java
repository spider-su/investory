package com.smartbox.investory.investment.backtest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Deterministic campaign simulator. It is deliberately separate from production portfolio math. */
public final class TradingStrategyBacktester {
  private static final BigDecimal ONE = BigDecimal.ONE;
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  public enum Side {
    BUY,
    SELL
  }

  public record CampaignKey(long accountId, long assetId, String currency) {
    public CampaignKey {
      if (currency == null || currency.isBlank())
        throw new IllegalArgumentException("currency required");
    }
  }

  /** A source opening or closing execution, already normalized to the account currency. */
  public record Execution(
      CampaignKey key,
      String symbol,
      String sourceTradeId,
      Side side,
      ZonedDateTime time,
      BigDecimal quantity,
      BigDecimal price,
      BigDecimal actualProfit) {
    public Execution {
      Objects.requireNonNull(key);
      Objects.requireNonNull(side);
      Objects.requireNonNull(time);
      if (sourceTradeId == null || sourceTradeId.isBlank())
        throw new IllegalArgumentException("source trade id required");
      positive(quantity, "quantity");
      positive(price, "price");
    }
  }

  public record DailyBar(
      LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {
    public DailyBar {
      Objects.requireNonNull(date);
      positive(open, "open");
      positive(high, "high");
      positive(low, "low");
      positive(close, "close");
      if (high.compareTo(low) < 0
          || high.compareTo(open) < 0
          || high.compareTo(close) < 0
          || low.compareTo(open) > 0
          || low.compareTo(close) > 0) throw new IllegalArgumentException("inconsistent OHLC");
    }
  }

  /** Fractions use decimal form: 0.05 is five percent. Null disables a rule. */
  public record Rules(
      BigDecimal hardStopLoss,
      BigDecimal noRescueLoss,
      BigDecimal trailingActivation,
      BigDecimal trailingDistance,
      boolean suppressDayTrades) {
    public static Rules actual() {
      return new Rules(null, null, null, null, false);
    }

    public Rules {
      validateFraction(hardStopLoss, "hard stop");
      validateFraction(noRescueLoss, "no-rescue loss");
      validateFraction(trailingActivation, "trailing activation");
      validateFraction(trailingDistance, "trailing distance");
      if ((trailingActivation == null) != (trailingDistance == null))
        throw new IllegalArgumentException("trailing activation and distance must be paired");
    }

    private static void validateFraction(BigDecimal value, String label) {
      if (value != null && (value.signum() < 0 || value.compareTo(ONE) >= 0))
        throw new IllegalArgumentException(label + " must be in [0, 1)");
    }
  }

  public record Campaign(
      CampaignKey key,
      String symbol,
      ZonedDateTime firstEntry,
      ZonedDateTime finalExit,
      BigDecimal realizedProfit,
      BigDecimal maxCapitalCommitted,
      BigDecimal maxDrawdown,
      int additionsWhileProfitable,
      int additionsWhileUnderwater,
      int executionCount,
      int stopExits,
      int blockedAdditions,
      int ambiguousBars,
      int missingBars,
      List<String> sourceTradeIds,
      BigDecimal actualFinalProfit,
      BigDecimal profitLostFromInterruptedWinner,
      BigDecimal lossAvoidedFromInterruptedLoser) {
    public Campaign {
      sourceTradeIds = List.copyOf(sourceTradeIds);
    }
  }

  public record Result(
      List<Campaign> campaigns,
      BigDecimal realizedProfit,
      BigDecimal maxDrawdown,
      BigDecimal capitalEmployed,
      int executionCount,
      int stopExits,
      int blockedAdditions,
      int ambiguousBars,
      int missingBars) {
    public Result {
      campaigns = List.copyOf(campaigns);
    }

    public CampaignMetrics campaignMetrics() {
      List<Campaign> closedCampaigns =
          campaigns.stream().filter(c -> c.finalExit() != null).toList();
      List<BigDecimal> winners =
          closedCampaigns.stream()
              .map(Campaign::realizedProfit)
              .filter(v -> v.signum() > 0)
              .toList();
      List<BigDecimal> losers =
          closedCampaigns.stream()
              .map(Campaign::realizedProfit)
              .filter(v -> v.signum() < 0)
              .toList();
      BigDecimal gains = winners.stream().reduce(ZERO, BigDecimal::add);
      BigDecimal losses = losers.stream().reduce(ZERO, BigDecimal::add).abs();
      BigDecimal avgWin = average(winners), avgLoss = average(losers);
      BigDecimal profitFactor =
          losses.signum() == 0 ? null : gains.divide(losses, 8, RoundingMode.HALF_UP);
      BigDecimal payoff =
          avgLoss == null || avgLoss.signum() == 0 || avgWin == null
              ? null
              : avgWin.divide(avgLoss.abs(), 8, RoundingMode.HALF_UP);
      BigDecimal winRate =
          closedCampaigns.isEmpty()
              ? ZERO
              : BigDecimal.valueOf(winners.size())
                  .divide(BigDecimal.valueOf(closedCampaigns.size()), 8, RoundingMode.HALF_UP);
      BigDecimal expectancy =
          closedCampaigns.isEmpty()
              ? ZERO
              : closedCampaigns.stream()
                  .map(Campaign::realizedProfit)
                  .reduce(ZERO, BigDecimal::add)
                  .divide(BigDecimal.valueOf(closedCampaigns.size()), 8, RoundingMode.HALF_UP);
      List<Long> durations =
          campaigns.stream()
              .filter(c -> c.finalExit() != null)
              .map(c -> java.time.Duration.between(c.firstEntry(), c.finalExit()).toHours())
              .sorted()
              .toList();
      Long median = null;
      if (!durations.isEmpty()) {
        int m = durations.size() / 2;
        median =
            durations.size() % 2 == 1
                ? durations.get(m)
                : (durations.get(m - 1) + durations.get(m)) / 2;
      }
      return new CampaignMetrics(
          closedCampaigns.size(),
          winRate,
          avgWin,
          avgLoss,
          payoff,
          profitFactor,
          expectancy,
          median,
          winners.stream().max(BigDecimal::compareTo).orElse(null),
          losers.stream().min(BigDecimal::compareTo).orElse(null));
    }

    public int winningCampaignsStoppedEarly() {
      return (int)
          campaigns.stream()
              .filter(
                  c ->
                      c.stopExits() > 0
                          && c.actualFinalProfit() != null
                          && c.actualFinalProfit().signum() > 0)
              .count();
    }

    public int stoppedCampaignsWithoutActualOutcome() {
      return (int)
          campaigns.stream()
              .filter(c -> c.stopExits() > 0 && c.actualFinalProfit() == null)
              .count();
    }

    public BigDecimal profitLostFromInterruptedWinners() {
      return campaigns.stream()
          .map(Campaign::profitLostFromInterruptedWinner)
          .reduce(ZERO, BigDecimal::add);
    }

    public BigDecimal lossAvoidedFromInterruptedLosers() {
      return campaigns.stream()
          .map(Campaign::lossAvoidedFromInterruptedLoser)
          .reduce(ZERO, BigDecimal::add);
    }

    private static BigDecimal average(List<BigDecimal> values) {
      return values.isEmpty()
          ? null
          : values.stream()
              .reduce(ZERO, BigDecimal::add)
              .divide(BigDecimal.valueOf(values.size()), 8, RoundingMode.HALF_UP);
    }
  }

  public record CampaignMetrics(
      int campaignCount,
      BigDecimal winRate,
      BigDecimal averageWinner,
      BigDecimal averageLoser,
      BigDecimal payoffRatio,
      BigDecimal profitFactor,
      BigDecimal expectancyPerCampaign,
      Long medianHoldingHours,
      BigDecimal largestWinner,
      BigDecimal largestLoser) {}

  /**
   * Replays executions in timestamp order. Daily bars on an execution date are not consulted before
   * that execution; stops use only completed bars from earlier dates. The caller supplies bars
   * through the simulation horizon to permit stop exits after the final source execution.
   */
  public Result run(
      List<Execution> executions,
      Map<CampaignKey, List<DailyBar>> dailyBars,
      Rules rules,
      LocalDate horizon) {
    LocalDate start =
        executions.stream()
            .map(e -> e.time().toLocalDate())
            .min(LocalDate::compareTo)
            .orElse(horizon);
    return run(executions, dailyBars, rules, start, horizon);
  }

  public Result run(
      List<Execution> executions,
      Map<CampaignKey, List<DailyBar>> dailyBars,
      Rules rules,
      LocalDate simulationStart,
      LocalDate horizon) {
    Objects.requireNonNull(executions);
    Objects.requireNonNull(dailyBars);
    Objects.requireNonNull(rules);
    Objects.requireNonNull(simulationStart);
    Objects.requireNonNull(horizon);
    if (simulationStart.isAfter(horizon))
      throw new IllegalArgumentException("invalid simulation dates");
    Map<String, BigDecimal> actualLotOutcomes = new HashMap<>();
    executions.stream()
        .filter(e -> e.side() == Side.SELL && e.actualProfit() != null)
        .forEach(e -> actualLotOutcomes.put(e.sourceTradeId(), e.actualProfit()));
    Map<CampaignKey, State> states = new HashMap<>();
    Map<CampaignKey, TreeMap<LocalDate, DailyBar>> bars = normalizeBars(dailyBars);
    List<Execution> ordered =
        executions.stream()
            .filter(e -> !e.time().toLocalDate().isAfter(horizon))
            .sorted(
                Comparator.comparing(Execution::time)
                    .thenComparing(e -> e.key().accountId())
                    .thenComparing(e -> e.key().assetId()))
            .toList();
    if (rules.suppressDayTrades()) ordered = withoutDayTradeCampaigns(ordered);
    for (Execution execution : ordered) {
      State state =
          states.computeIfAbsent(
              execution.key(), k -> new State(k, execution.symbol(), simulationStart));
      state.advanceBefore(
          execution.time().toLocalDate(),
          bars.getOrDefault(execution.key(), new TreeMap<>()),
          rules);
      state.apply(execution, rules);
    }
    states
        .values()
        .forEach(s -> s.advanceThrough(horizon, bars.getOrDefault(s.key, new TreeMap<>()), rules));
    List<Campaign> campaigns =
        states.values().stream()
            .flatMap(s -> s.completed.stream())
            .map(c -> withActualOutcome(c, actualLotOutcomes))
            .sorted(
                Comparator.comparing(Campaign::firstEntry).thenComparing(c -> c.key().accountId()))
            .toList();
    BigDecimal pnl = sum(campaigns, Campaign::realizedProfit);
    BigDecimal capital = sum(campaigns, Campaign::maxCapitalCommitted);
    BigDecimal drawdown =
        campaigns.stream().map(Campaign::maxDrawdown).max(BigDecimal::compareTo).orElse(ZERO);
    return new Result(
        campaigns,
        pnl,
        drawdown,
        capital,
        campaigns.stream().mapToInt(Campaign::executionCount).sum(),
        campaigns.stream().mapToInt(Campaign::stopExits).sum(),
        campaigns.stream().mapToInt(Campaign::blockedAdditions).sum(),
        campaigns.stream().mapToInt(Campaign::ambiguousBars).sum(),
        campaigns.stream().mapToInt(Campaign::missingBars).sum());
  }

  private static Campaign withActualOutcome(
      Campaign campaign, Map<String, BigDecimal> actualLotOutcomes) {
    BigDecimal actual =
        campaign.sourceTradeIds().stream()
            .map(actualLotOutcomes::get)
            .filter(Objects::nonNull)
            .reduce(ZERO, BigDecimal::add);
    boolean allClosed = campaign.sourceTradeIds().stream().allMatch(actualLotOutcomes::containsKey);
    if (!allClosed) actual = null;
    else if (campaign.stopExits() == 0) actual = campaign.realizedProfit();
    BigDecimal winnerLoss =
        campaign.stopExits() > 0 && actual != null && actual.signum() > 0
            ? actual.max(ZERO).subtract(campaign.realizedProfit().max(ZERO)).max(ZERO)
            : ZERO;
    BigDecimal loserAvoided =
        campaign.stopExits() > 0 && actual != null && actual.signum() < 0
            ? actual.abs().subtract(campaign.realizedProfit().min(ZERO).abs()).max(ZERO)
            : ZERO;
    return new Campaign(
        campaign.key(),
        campaign.symbol(),
        campaign.firstEntry(),
        campaign.finalExit(),
        campaign.realizedProfit(),
        campaign.maxCapitalCommitted(),
        campaign.maxDrawdown(),
        campaign.additionsWhileProfitable(),
        campaign.additionsWhileUnderwater(),
        campaign.executionCount(),
        campaign.stopExits(),
        campaign.blockedAdditions(),
        campaign.ambiguousBars(),
        campaign.missingBars(),
        campaign.sourceTradeIds(),
        actual,
        winnerLoss,
        loserAvoided);
  }

  /** Retrospective comparison: removes complete actual campaigns whose exposure lasted <=24h. */
  private static List<Execution> withoutDayTradeCampaigns(List<Execution> events) {
    Map<CampaignKey, Map<String, ZonedDateTime>> openLots = new HashMap<>();
    Map<CampaignKey, ZonedDateTime> campaignStarts = new HashMap<>();
    Map<CampaignKey, List<Execution>> campaignEvents = new HashMap<>();
    List<Execution> kept = new ArrayList<>();
    for (Execution event : events) {
      Map<String, ZonedDateTime> lots =
          openLots.computeIfAbsent(event.key(), ignored -> new HashMap<>());
      if (event.side() == Side.BUY) {
        if (lots.isEmpty()) {
          campaignStarts.put(event.key(), event.time());
          campaignEvents.put(event.key(), new ArrayList<>());
        }
        lots.put(event.sourceTradeId(), event.time());
        campaignEvents.get(event.key()).add(event);
      } else {
        if (!lots.containsKey(event.sourceTradeId())) continue;
        campaignEvents.get(event.key()).add(event);
        lots.remove(event.sourceTradeId());
        if (lots.isEmpty()) {
          ZonedDateTime start = campaignStarts.remove(event.key());
          if (start == null
              || java.time.Duration.between(start, event.time())
                      .compareTo(java.time.Duration.ofHours(24))
                  > 0) kept.addAll(campaignEvents.get(event.key()));
          campaignEvents.remove(event.key());
        }
      }
    }
    // An open campaign has no actual round-trip duration, so retain it as an open position.
    campaignEvents.values().forEach(kept::addAll);
    return kept.stream().sorted(Comparator.comparing(Execution::time)).toList();
  }

  private static Map<CampaignKey, TreeMap<LocalDate, DailyBar>> normalizeBars(
      Map<CampaignKey, List<DailyBar>> input) {
    Map<CampaignKey, TreeMap<LocalDate, DailyBar>> result = new HashMap<>();
    input.forEach(
        (k, values) -> {
          TreeMap<LocalDate, DailyBar> mapped = new TreeMap<>();
          values.forEach(
              bar -> {
                if (mapped.put(bar.date(), bar) != null)
                  throw new IllegalArgumentException("duplicate daily bar " + bar.date());
              });
          result.put(k, mapped);
        });
    return result;
  }

  private static void positive(BigDecimal n, String label) {
    if (n == null || n.signum() <= 0)
      throw new IllegalArgumentException(label + " must be positive");
  }

  private static BigDecimal sum(
      List<Campaign> campaigns, java.util.function.Function<Campaign, BigDecimal> value) {
    return campaigns.stream().map(value).reduce(ZERO, BigDecimal::add);
  }

  private static final class State {
    private final CampaignKey key;
    private final String symbol;
    private final LocalDate simulationStart;
    private final List<Campaign> completed = new ArrayList<>();
    private BigDecimal quantity = ZERO, costBasis = ZERO, realized = ZERO, committed = ZERO;
    private BigDecimal highWater = ZERO, peakEquity = ZERO, maxDrawdown = ZERO;
    private final Map<String, BigDecimal> lots = new HashMap<>();
    private final java.util.Set<String> suppressedLots = new java.util.HashSet<>();
    private ZonedDateTime entry;
    private LocalDate lastBar;
    private int additionsUp, additionsDown, executions, stopExits, blocked, ambiguous, missing;
    private final java.util.Set<String> campaignLotIds = new java.util.LinkedHashSet<>();

    private State(CampaignKey key, String symbol, LocalDate simulationStart) {
      this.key = key;
      this.symbol = symbol;
      this.simulationStart = simulationStart;
    }

    private void advanceBefore(LocalDate before, TreeMap<LocalDate, DailyBar> bars, Rules rules) {
      LocalDate from =
          lastBar == null
              ? (entry == null
                  ? before
                  : entry.toLocalDate().plusDays(1).isAfter(simulationStart)
                      ? entry.toLocalDate().plusDays(1)
                      : simulationStart)
              : lastBar.plusDays(1);
      for (LocalDate d = from; d.isBefore(before); d = d.plusDays(1))
        processBar(d, bars.get(d), rules);
    }

    private void advanceThrough(LocalDate through, TreeMap<LocalDate, DailyBar> bars, Rules rules) {
      LocalDate from =
          lastBar == null
              ? (entry == null
                  ? through
                  : entry.toLocalDate().plusDays(1).isAfter(simulationStart)
                      ? entry.toLocalDate().plusDays(1)
                      : simulationStart)
              : lastBar.plusDays(1);
      for (LocalDate d = from; !d.isAfter(through); d = d.plusDays(1))
        processBar(d, bars.get(d), rules);
      if (quantity.signum() > 0) finish(null);
    }

    private void processBar(LocalDate date, DailyBar bar, Rules rules) {
      lastBar = date;
      if (quantity.signum() <= 0) return;
      if (bar == null) {
        if (isExpectedTradingDay(date)) missing++;
        return;
      }
      BigDecimal average = averageCost();
      BigDecimal stop =
          rules.hardStopLoss() == null
              ? null
              : average.multiply(ONE.subtract(rules.hardStopLoss()));
      boolean activationHit =
          rules.trailingActivation() != null
              && bar.high().compareTo(average.multiply(ONE.add(rules.trailingActivation()))) >= 0;
      BigDecimal previousHigh = highWater;
      BigDecimal nextHigh = activationHit ? highWater.max(bar.high()) : highWater;
      BigDecimal trail =
          rules.trailingDistance() == null || nextHigh.signum() == 0
              ? null
              : nextHigh.multiply(ONE.subtract(rules.trailingDistance()));
      if (trail != null) stop = stop == null ? trail : stop.max(trail);
      if (stop != null && bar.low().compareTo(stop) <= 0) {
        boolean intradayAmbiguity =
            activationHit
                && previousHigh
                        .multiply(
                            ONE.subtract(
                                rules.trailingDistance() == null ? ZERO : rules.trailingDistance()))
                        .compareTo(stop)
                    < 0;
        if (intradayAmbiguity) ambiguous++;
        BigDecimal fill = bar.open().compareTo(stop) < 0 ? bar.open() : stop;
        realized = realized.add(fill.subtract(average).multiply(quantity));
        executions++;
        stopExits++;
        suppressedLots.addAll(lots.keySet());
        lots.clear();
        finish(date.atStartOfDay(entry.getZone()));
        return;
      }
      highWater = nextHigh;
      BigDecimal mark = realized.add(bar.close().subtract(average).multiply(quantity));
      peakEquity = peakEquity.max(mark);
      maxDrawdown = maxDrawdown.max(peakEquity.subtract(mark));
    }

    private ZonedDateTime actualCloseForCampaign;
    private BigDecimal actualCampaignProfit = ZERO;

    private void apply(Execution e, Rules rules) {
      if (e.side() == Side.BUY) {
        if (quantity.signum() > 0
            && rules.noRescueLoss() != null
            && averageCost()
                    .subtract(e.price())
                    .divide(averageCost(), 12, RoundingMode.HALF_UP)
                    .compareTo(rules.noRescueLoss())
                > 0) {
          blocked++;
          suppressedLots.add(e.sourceTradeId());
          return;
        }
        if (quantity.signum() == 0) {
          entry = e.time();
          committed = ZERO;
          realized = ZERO;
          highWater = e.price();
          peakEquity = ZERO;
          maxDrawdown = ZERO;
          executions = stopExits = blocked = ambiguous = missing = additionsUp = additionsDown = 0;
          actualCampaignProfit = ZERO;
          actualCloseForCampaign = null;
          campaignLotIds.clear();
        } else if (e.price().compareTo(averageCost()) >= 0) additionsUp++;
        else additionsDown++;
        quantity = quantity.add(e.quantity());
        costBasis = costBasis.add(e.price().multiply(e.quantity()));
        lots.put(e.sourceTradeId(), e.quantity());
        campaignLotIds.add(e.sourceTradeId());
        committed = committed.max(costBasis);
        executions++;
      } else if (quantity.signum() > 0) {
        if (suppressedLots.contains(e.sourceTradeId())) return;
        BigDecimal sourceLotQuantity = lots.getOrDefault(e.sourceTradeId(), ZERO);
        if (sourceLotQuantity.signum() == 0) return;
        BigDecimal sold = quantity.min(e.quantity()).min(sourceLotQuantity);
        BigDecimal avg = averageCost();
        BigDecimal profit =
            e.actualProfit() == null
                ? e.price().subtract(avg).multiply(sold)
                : e.actualProfit().multiply(sold).divide(e.quantity(), 12, RoundingMode.HALF_UP);
        realized = realized.add(profit);
        actualCampaignProfit = actualCampaignProfit.add(profit);
        actualCloseForCampaign = e.time();
        quantity = quantity.subtract(sold);
        costBasis = avg.multiply(quantity);
        BigDecimal lotRemaining = sourceLotQuantity.subtract(sold);
        if (lotRemaining.signum() == 0) lots.remove(e.sourceTradeId());
        else lots.put(e.sourceTradeId(), lotRemaining);
        executions++;
        if (quantity.signum() == 0) finish(e.time());
      }
    }

    private BigDecimal averageCost() {
      return quantity.signum() == 0 ? ZERO : costBasis.divide(quantity, 12, RoundingMode.HALF_UP);
    }

    private void finish(ZonedDateTime exit) {
      if (entry == null) return;
      completed.add(
          new Campaign(
              key,
              symbol,
              entry,
              exit,
              realized,
              committed,
              maxDrawdown,
              additionsUp,
              additionsDown,
              executions,
              stopExits,
              blocked,
              ambiguous,
              missing,
              List.copyOf(campaignLotIds),
              realized,
              ZERO,
              ZERO));
      quantity = costBasis = ZERO;
      entry = null;
      lots.clear();
      campaignLotIds.clear();
    }

    private static boolean isExpectedTradingDay(LocalDate date) {
      return date.getDayOfWeek().getValue() <= 5;
    }
  }
}
