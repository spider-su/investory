package com.smartbox.investory.marketradar.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.marketradar.domain.DailyMarketBar;
import com.smartbox.investory.marketradar.domain.RadarState;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketSignalCalculatorTest {

  private final MarketSignalCalculator calculator = new MarketSignalCalculator();

  @Test
  void marksStronglyExtendedSeriesAsExtended() {
    List<DailyMarketBar> bars = bars(100, 0.15, 1_000);
    DailyMarketBar last = bars.getLast();
    bars.set(\n        bars.size() - 1,\n        new DailyMarketBar(last.date(), last.open(), 160, last.low(), 160, 4_000));

    var snapshot = calculator.calculate("xyz", bars);

    assertThat(snapshot.symbol()).isEqualTo("XYZ");
    assertThat(snapshot.state()).isEqualTo(RadarState.EXTENDED);
    assertThat(snapshot.relativeVolume20d()).isGreaterThan(3.0);
    assertThat(snapshot.reasons()).isNotEmpty();
  }

  @Test
  void keepsFlatSeriesNormal() {
    var snapshot = calculator.calculate("abc", bars(100, 0, 1_000));
    assertThat(snapshot.state()).isEqualTo(RadarState.NORMAL);
  }

  private List<DailyMarketBar> bars(double start, double dailyStep, long volume) {
    List<DailyMarketBar> result = new ArrayList<>();
    for (int i = 0; i < 80; i++) {
      double close = start + i * dailyStep;
      result.add(\n          new DailyMarketBar(\n              LocalDate.of(2026, 6, 1).plusDays(i), close, close, close, close, volume));
    }
    return result;
  }
}
