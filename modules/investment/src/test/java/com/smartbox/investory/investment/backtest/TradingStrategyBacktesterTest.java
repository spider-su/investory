package com.smartbox.investory.investment.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TradingStrategyBacktesterTest {
  private static final TradingStrategyBacktester SUBJECT = new TradingStrategyBacktester();
  private static final TradingStrategyBacktester.CampaignKey KEY =
      new TradingStrategyBacktester.CampaignKey(1, 10, "USD");

  @Test
  void reconstructsOneCampaignAcrossAdditionalBuysAndPartialExits() {
    var result =
        run(
            List.of(
                trade("lot-1", TradingStrategyBacktester.Side.BUY, 1, "100", null),
                trade("lot-2", TradingStrategyBacktester.Side.BUY, 2, "120", null),
                trade("lot-1", TradingStrategyBacktester.Side.SELL, 3, "130", "30"),
                trade("lot-2", TradingStrategyBacktester.Side.SELL, 4, "110", "10")),
            Map.of(),
            TradingStrategyBacktester.Rules.actual(),
            4);
    assertThat(result.campaigns()).hasSize(1);
    var campaign = result.campaigns().getFirst();
    assertThat(campaign.realizedProfit()).isEqualByComparingTo("40");
    assertThat(campaign.additionsWhileProfitable()).isEqualTo(1);
    assertThat(campaign.executionCount()).isEqualTo(4);
  }

  @Test
  void countsAdditionWhileEntireCampaignIsUnderwaterAndBlocksByCampaignReturn() {
    var plain =
        run(
            List.of(
                trade("lot-1", TradingStrategyBacktester.Side.BUY, 1, "100", null),
                trade("lot-2", TradingStrategyBacktester.Side.BUY, 3, "80", null)),
            bars(bar(2, "100", "101", "89", "90")),
            TradingStrategyBacktester.Rules.actual(),
            3);
    assertThat(plain.campaigns().getFirst().additionsWhileUnderwater()).isEqualTo(1);

    var blocked =
        run(
            List.of(
                trade("lot-1", TradingStrategyBacktester.Side.BUY, 1, "100", null),
                trade("lot-2", TradingStrategyBacktester.Side.BUY, 3, "80", null),
                trade("lot-2", TradingStrategyBacktester.Side.SELL, 4, "70", "-10")),
            bars(bar(2, "100", "101", "89", "90")),
            new TradingStrategyBacktester.Rules(null, bd("0.05"), null, null, false),
            3);
    assertThat(blocked.blockedAdditions()).isEqualTo(1);
    assertThat(blocked.campaigns().getFirst().maxCapitalCommitted()).isEqualByComparingTo("100");
  }

  @Test
  void hardStopUsesStopPriceIntradayAndOpenAfterGap() {
    var stopRules = new TradingStrategyBacktester.Rules(bd("0.10"), null, null, null, false);
    var intraday =
        run(
            List.of(trade("lot-1", TradingStrategyBacktester.Side.BUY, 1, "100", null)),
            bars(bar(2, "100", "101", "85", "95")),
            stopRules,
            2);
    assertThat(intraday.realizedProfit()).isEqualByComparingTo("-10");
    assertThat(intraday.stopExits()).isEqualTo(1);

    var gap =
        run(
            List.of(trade("lot-1", TradingStrategyBacktester.Side.BUY, 1, "100", null)),
            bars(bar(2, "80", "90", "75", "85")),
            stopRules,
            2);
    assertThat(gap.realizedProfit()).isEqualByComparingTo("-20");
  }

  @Test
  void winnerDestructionUsesActualOutcomeWithoutExecutingFutureCloses() {
    var executions =
        List.of(
            trade("winner", TradingStrategyBacktester.Side.BUY, 1, "100", null),
            trade("winner", TradingStrategyBacktester.Side.SELL, 4, "150", "50"),
            trade("future-entry", TradingStrategyBacktester.Side.BUY, 4, "200", null));
    var result =
        run(
            executions,
            bars(bar(2, "100", "101", "80", "95")),
            new TradingStrategyBacktester.Rules(bd("0.10"), null, null, null, false),
            3);
    assertThat(result.campaigns()).hasSize(1);
    assertThat(result.realizedProfit()).isEqualByComparingTo("-10");
    assertThat(result.campaigns().getFirst().actualFinalProfit()).isEqualByComparingTo("50");
    assertThat(result.winningCampaignsStoppedEarly()).isEqualTo(1);
    assertThat(result.profitLostFromInterruptedWinners()).isEqualByComparingTo("50");
  }

  @Test
  void trailingActivationAndSameBarStopAreFlaggedAmbiguousConservatively() {
    var rules = new TradingStrategyBacktester.Rules(null, null, bd("0.10"), bd("0.05"), false);
    var result =
        run(
            List.of(trade("lot-1", TradingStrategyBacktester.Side.BUY, 1, "100", null)),
            bars(bar(2, "115", "120", "110", "115"), bar(3, "115", "130", "110", "120")),
            rules,
            3);
    assertThat(result.campaigns()).hasSize(1);
    assertThat(result.stopExits()).isEqualTo(1);
    assertThat(result.ambiguousBars()).isEqualTo(1);
    assertThat(result.realizedProfit()).isEqualByComparingTo("14.00");
  }

  @Test
  void stopAllowsLaterIndependentReentryAndAccountsStayCurrencyIsolated() {
    var eurKey = new TradingStrategyBacktester.CampaignKey(1, 10, "EUR");
    var input =
        List.of(
            trade("lot-1", TradingStrategyBacktester.Side.BUY, 1, "100", null),
            trade("lot-2", TradingStrategyBacktester.Side.BUY, 4, "80", null));
    var result =
        run(
            input,
            bars(bar(2, "100", "101", "80", "90"), bar(3, "90", "95", "85", "92")),
            new TradingStrategyBacktester.Rules(bd("0.10"), null, null, null, false),
            4);
    assertThat(result.campaigns()).hasSize(2);

    var executions =
        List.of(
            new TradingStrategyBacktester.Execution(
                KEY,
                "A",
                "usd-lot",
                TradingStrategyBacktester.Side.BUY,
                date(1),
                bd("1"),
                bd("10"),
                null),
            new TradingStrategyBacktester.Execution(
                eurKey,
                "A",
                "eur-lot",
                TradingStrategyBacktester.Side.BUY,
                date(1),
                bd("1"),
                bd("20"),
                null));
    var isolated =
        SUBJECT.run(
            executions,
            Map.of(),
            TradingStrategyBacktester.Rules.actual(),
            LocalDate.of(2026, 1, 1));
    assertThat(isolated.campaigns()).hasSize(2);
    assertThat(isolated.campaigns())
        .extracting(c -> c.key().currency())
        .containsExactlyInAnyOrder("USD", "EUR");
  }

  @Test
  void noDayTradingDropsOnlyActualCampaignsClosedWithinTwentyFourHours() {
    var executions =
        List.of(
            trade("day", TradingStrategyBacktester.Side.BUY, 1, "100", null),
            trade("day", TradingStrategyBacktester.Side.SELL, 1, "101", "1"),
            trade("swing", TradingStrategyBacktester.Side.BUY, 2, "100", null),
            trade("swing", TradingStrategyBacktester.Side.SELL, 4, "102", "2"));
    var result =
        run(
            executions,
            Map.of(),
            new TradingStrategyBacktester.Rules(null, null, null, null, true),
            4);
    assertThat(result.campaigns()).hasSize(1);
    assertThat(result.campaigns().getFirst().realizedProfit()).isEqualByComparingTo("2");
  }

  @Test
  void closeForBlockedLotCannotConsumeAnotherLotOrBookItsActualProfit() {
    var events =
        List.of(
            trade("kept", TradingStrategyBacktester.Side.BUY, 1, "100", null),
            trade("blocked", TradingStrategyBacktester.Side.BUY, 3, "80", null),
            trade("blocked", TradingStrategyBacktester.Side.SELL, 4, "70", "-10"),
            trade("kept", TradingStrategyBacktester.Side.SELL, 5, "105", "5"));
    var result =
        run(
            events,
            bars(bar(2, "100", "101", "89", "90")),
            new TradingStrategyBacktester.Rules(null, bd("0.05"), null, null, false),
            5);
    assertThat(result.realizedProfit()).isEqualByComparingTo("5");
    assertThat(result.campaigns()).hasSize(1);
  }

  @Test
  void missingWeekdayBarIsCountedButWeekendIsNot() {
    var result =
        run(
            List.of(trade("lot-1", TradingStrategyBacktester.Side.BUY, 2, "100", null)),
            Map.of(),
            TradingStrategyBacktester.Rules.actual(),
            5);
    assertThat(result.missingBars()).isEqualTo(1);
  }

  @Test
  void strategyGridIsBoundedAndContainsEveryRequestedNeighboringParameter() {
    var scenarios = TradingStrategyGrid.scenarios();
    assertThat(scenarios).hasSize(45);
    assertThat(scenarios)
        .extracting(TradingStrategyGrid.Scenario::name)
        .contains(
            "ACTUAL",
            "NO_DAY_TRADING",
            "HARD_STOP_3",
            "HARD_STOP_20",
            "NO_RESCUE_3",
            "NO_RESCUE_10",
            "TRAIL_5_3",
            "TRAIL_15_15",
            "NO_DAY_HARD_STOP_5",
            "NO_DAY_NO_RESCUE_10",
            "NO_DAY_TRAIL_10_7");
  }

  @Test
  void preYearPositionIsSeededAtYearStartWithoutCountingEarlierBarsAsMissing() {
    var execution =
        new TradingStrategyBacktester.Execution(
            KEY,
            "TEST",
            "carry-in",
            TradingStrategyBacktester.Side.BUY,
            LocalDate.of(2025, 12, 20).atStartOfDay(ZoneOffset.UTC),
            bd("1"),
            bd("100"),
            null);
    var result =
        SUBJECT.run(
            List.of(execution),
            bars(barOn(LocalDate.of(2026, 1, 1), "100", "101", "99", "100")),
            TradingStrategyBacktester.Rules.actual(),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 1));
    assertThat(result.missingBars()).isZero();
    assertThat(result.campaigns()).hasSize(1);
  }

  private static TradingStrategyBacktester.Result run(
      List<TradingStrategyBacktester.Execution> events,
      Map<TradingStrategyBacktester.CampaignKey, List<TradingStrategyBacktester.DailyBar>> bars,
      TradingStrategyBacktester.Rules rules,
      int lastDay) {
    return SUBJECT.run(events, bars, rules, LocalDate.of(2026, 1, lastDay));
  }

  private static TradingStrategyBacktester.Execution trade(
      String lotId, TradingStrategyBacktester.Side side, int day, String price, String pnl) {
    return new TradingStrategyBacktester.Execution(
        KEY, "TEST", lotId, side, date(day), bd("1"), bd(price), pnl == null ? null : bd(pnl));
  }

  private static java.time.ZonedDateTime date(int day) {
    return LocalDate.of(2026, 1, day).atStartOfDay(ZoneOffset.UTC);
  }

  private static BigDecimal bd(String v) {
    return new BigDecimal(v);
  }

  private static TradingStrategyBacktester.DailyBar bar(
      int day, String o, String h, String l, String c) {
    return new TradingStrategyBacktester.DailyBar(
        LocalDate.of(2026, 1, day), bd(o), bd(h), bd(l), bd(c));
  }

  private static TradingStrategyBacktester.DailyBar barOn(
      LocalDate date, String open, String high, String low, String close) {
    return new TradingStrategyBacktester.DailyBar(date, bd(open), bd(high), bd(low), bd(close));
  }

  private static Map<
          TradingStrategyBacktester.CampaignKey, List<TradingStrategyBacktester.DailyBar>>
      bars(TradingStrategyBacktester.DailyBar... bars) {
    return Map.of(KEY, List.of(bars));
  }
}
