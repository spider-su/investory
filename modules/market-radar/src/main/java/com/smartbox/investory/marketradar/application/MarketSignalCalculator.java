package com.smartbox.investory.marketradar.application;

import com.smartbox.investory.marketradar.domain.DailyMarketBar;
import com.smartbox.investory.marketradar.domain.RadarSnapshot;
import com.smartbox.investory.marketradar.domain.RadarState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MarketSignalCalculator {

  private static final int MIN_BARS = 61;

  public RadarSnapshot calculate(String symbol, List<DailyMarketBar> input) {
    List<DailyMarketBar> bars =
        input.stream().sorted(Comparator.comparing(DailyMarketBar::date)).toList();
    if (bars.size() < MIN_BARS) throw new IllegalArgumentException("At least 61 daily bars are required");

    int last = bars.size() - 1;
    DailyMarketBar current = bars.get(last);
    double close = current.close();
    double return20 = change(close, bars.get(last - 20).close());
    double return60 = change(close, bars.get(last - 60).close());
    double avgVolume20 =
        bars.subList(last - 20, last).stream().mapToLong(DailyMarketBar::volume).average().orElse(0);
    Double relativeVolume = avgVolume20 > 0 ? current.volume() / avgVolume20 : null;
    double sma50 =
        bars.subList(last - 49, last + 1).stream().mapToDouble(DailyMarketBar::close).average().orElse(close);
    double distanceSma50 = change(close, sma50);
    double rsi = rsi14(bars);
    List<String> reasons = new ArrayList<>();

    RadarState state = classify(return20, return60, relativeVolume, distanceSma50, rsi, reasons);
    return new RadarSnapshot(
        symbol.toUpperCase(java.util.Locale.ROOT),
        current.date(),
        state,
        close,
        return20,
        return60,
        relativeVolume,
        distanceSma50,
        rsi,
        reasons);
  }

  private RadarState classify(
      double r20, double r60, Double relVol, double distanceSma50, double rsi, List<String> reasons) {
    double volume = relVol == null ? 1.0 : relVol;
    if (r20 >= 0.25 || distanceSma50 >= 0.20 || rsi >= 80) {
      reasons.add("Price is materially extended from its recent trend");
      if (volume >= 1.5) reasons.add("Trading participation is unusually high");
      return RadarState.EXTENDED;
    }
    if (r20 >= 0.12 && volume >= 2.0) {
      reasons.add("Strong 20-day momentum");
      reasons.add("Relative volume is elevated");
      return RadarState.HOT;
    }
    if (r20 >= 0.05 && r60 >= 0.08 && volume >= 1.2 && distanceSma50 > 0) {
      reasons.add("Positive medium-term momentum");
      reasons.add("Price is above its 50-day average");
      reasons.add("Volume confirms participation");
      return RadarState.TRENDING;
    }
    if (r20 >= 0.02 && r60 > 0 && volume >= 1.5 && distanceSma50 > -0.03) {
      reasons.add("Short-term momentum is improving");
      reasons.add("Relative volume is elevated");
      return RadarState.EMERGING;
    }
    if (r20 < -0.05 && r60 > 0) {
      reasons.add("Short-term momentum weakened after a positive medium-term trend");
      return RadarState.COOLING;
    }
    return RadarState.NORMAL;
  }

  private double change(double current, double previous) {
    return previous == 0 ? 0 : current / previous - 1.0;
  }

  private double rsi14(List<DailyMarketBar> bars) {
    int last = bars.size() - 1;
    double gains = 0;
    double losses = 0;
    for (int i = last - 13; i <= last; i++) {
      double delta = bars.get(i).close() - bars.get(i - 1).close();
      if (delta >= 0) gains += delta;
      else losses -= delta;
    }
    if (losses == 0) return gains == 0 ? 50.0 : 100.0;
    double rs = gains / losses;
    return 100.0 - 100.0 / (1.0 + rs);
  }
}
