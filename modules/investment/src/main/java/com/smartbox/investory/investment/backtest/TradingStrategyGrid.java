package com.smartbox.investory.investment.backtest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** The deliberately bounded 2026 comparison grid requested for the initial evaluation. */
public final class TradingStrategyGrid {
  private static final List<BigDecimal> HARD_STOPS = decimals("3,5,7,8,10,12,15,20");
  private static final List<BigDecimal> RESCUE_LIMITS = decimals("3,5,8,10");
  private static final List<BigDecimal> TRAIL_ACTIVATIONS = decimals("5,10,15");
  private static final List<BigDecimal> TRAIL_DISTANCES = decimals("3,5,7,10,12,15");
  private static final List<BigDecimal> COMBINED_STOPS = decimals("5,7,8,10,12,15");

  private TradingStrategyGrid() {}

  public record Scenario(String name, TradingStrategyBacktester.Rules rules) {}

  public static List<Scenario> scenarios() {
    List<Scenario> result = new ArrayList<>();
    result.add(new Scenario("ACTUAL", TradingStrategyBacktester.Rules.actual()));
    result.add(new Scenario("NO_DAY_TRADING", rules(null, null, null, null, true)));
    HARD_STOPS.forEach(
        loss ->
            result.add(
                new Scenario("HARD_STOP_" + pct(loss), rules(loss, null, null, null, false))));
    RESCUE_LIMITS.forEach(
        loss ->
            result.add(
                new Scenario("NO_RESCUE_" + pct(loss), rules(null, loss, null, null, false))));
    for (BigDecimal activation : TRAIL_ACTIVATIONS)
      for (BigDecimal distance : TRAIL_DISTANCES)
        result.add(
            new Scenario(
                "TRAIL_" + pct(activation) + "_" + pct(distance),
                rules(null, null, activation, distance, false)));
    COMBINED_STOPS.forEach(
        loss ->
            result.add(
                new Scenario(
                    "NO_DAY_HARD_STOP_" + pct(loss), rules(loss, null, null, null, true))));
    RESCUE_LIMITS.forEach(
        loss ->
            result.add(
                new Scenario(
                    "NO_DAY_NO_RESCUE_" + pct(loss), rules(null, loss, null, null, true))));
    // Three predeclared combinations cover rescue control, trailing protection, and both controls.
    result.add(
        new Scenario("NO_DAY_RESCUE_5_HARD_STOP_8", rules(dec("8"), dec("5"), null, null, true)));
    result.add(new Scenario("NO_DAY_TRAIL_10_7", rules(null, null, dec("10"), dec("7"), true)));
    result.add(
        new Scenario(
            "NO_DAY_RESCUE_5_TRAIL_10_7", rules(null, dec("5"), dec("10"), dec("7"), true)));
    return List.copyOf(result);
  }

  public static List<ScenarioResult> run(
      TradingStrategyBacktester simulator,
      List<TradingStrategyBacktester.Execution> executions,
      java.util.Map<TradingStrategyBacktester.CampaignKey, List<TradingStrategyBacktester.DailyBar>>
          bars,
      LocalDate horizon) {
    LocalDate start =
        executions.stream()
            .map(e -> e.time().toLocalDate())
            .min(LocalDate::compareTo)
            .orElse(horizon);
    return run(simulator, executions, bars, start, horizon);
  }

  public static List<ScenarioResult> run(
      TradingStrategyBacktester simulator,
      List<TradingStrategyBacktester.Execution> executions,
      java.util.Map<TradingStrategyBacktester.CampaignKey, List<TradingStrategyBacktester.DailyBar>>
          bars,
      LocalDate simulationStart,
      LocalDate horizon) {
    return scenarios().stream()
        .map(
            s ->
                new ScenarioResult(
                    s, simulator.run(executions, bars, s.rules(), simulationStart, horizon)))
        .toList();
  }

  public record ScenarioResult(Scenario scenario, TradingStrategyBacktester.Result result) {}

  private static TradingStrategyBacktester.Rules rules(
      BigDecimal hard, BigDecimal rescue, BigDecimal activation, BigDecimal trail, boolean noDay) {
    return new TradingStrategyBacktester.Rules(
        fraction(hard), fraction(rescue), fraction(activation), fraction(trail), noDay);
  }

  private static BigDecimal fraction(BigDecimal percent) {
    return percent == null ? null : percent.movePointLeft(2);
  }

  private static List<BigDecimal> decimals(String commaSeparated) {
    return java.util.Arrays.stream(commaSeparated.split(","))
        .map(TradingStrategyGrid::dec)
        .toList();
  }

  private static BigDecimal dec(String value) {
    return new BigDecimal(value);
  }

  private static String pct(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString().replace('.', '_');
  }
}
