package com.smartbox.investory.marketradar.application;

import com.smartbox.investory.marketradar.api.StrategistOutlookApi;
import com.smartbox.investory.marketradar.api.StrategistOutlookApi.*;
import com.smartbox.investory.marketradar.domain.DailyMarketBar;
import com.smartbox.investory.marketradar.port.HistoricalMarketDataPort;
import com.smartbox.investory.marketradar.port.StrategistForecastStore;
import com.smartbox.investory.marketradar.port.StrategistForecastStore.ForecastObservation;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class StrategistOutlookService implements StrategistOutlookApi {
  private static final int STALE_DAYS = 120;
  private final StrategistForecastStore store;
  private final HistoricalMarketDataPort market;

  public StrategistOutlookService(StrategistForecastStore store, HistoricalMarketDataPort market) {
    this.store = store;
    this.market = market;
  }

  @Override
  public Outlook outlook(LocalDate targetDate, LocalDate asOf) {
    if (targetDate == null || asOf == null)
      throw new IllegalArgumentException("targetDate and asOf are required");
    List<ForecastObservation> all = store.observations(targetDate);
    List<ForecastObservation> eligible =
        all.stream()
            .filter(
                o ->
                    o.status().equals("ELIGIBLE")
                        && !utcDate(o.forecast().publishedAt()).isAfter(asOf)
                        && !utcDate(o.forecast().observedAt()).isAfter(asOf))
            .toList();
    Map<String, ForecastObservation> latest = new TreeMap<>();
    eligible.forEach(
        o -> latest.merge(o.forecast().institutionKey(), o, (a, b) -> compare(a, b) >= 0 ? a : b));
    int stale = (int) latest.values().stream().filter(o -> age(o, asOf) > STALE_DAYS).count();
    List<ForecastObservation> current =
        latest.values().stream().filter(o -> age(o, asOf) <= STALE_DAYS).toList();

    LocalDate targetBaselineDate = LocalDate.of(targetDate.getYear() - 1, 12, 31);
    LocalDate ytdBaselineDate = LocalDate.of(asOf.getYear() - 1, 12, 31);
    LocalDate dataFrom =
        targetBaselineDate.isBefore(ytdBaselineDate)
            ? targetBaselineDate.minusDays(400)
            : ytdBaselineDate.minusDays(400);
    LocalDate dataThrough = asOf.isAfter(targetDate.plusDays(10)) ? asOf : targetDate.plusDays(10);
    List<DailyMarketBar> bars =
        market.dailyBars("^GSPC", dataFrom, dataThrough).stream()
            .filter(b -> !b.date().isAfter(asOf))
            .sorted(Comparator.comparing(DailyMarketBar::date))
            .toList();
    DailyMarketBar targetBaseline = lastOnOrBefore(bars, targetBaselineDate);
    DailyMarketBar ytdBaseline = lastOnOrBefore(bars, ytdBaselineDate);
    DailyMarketBar currentBar = bars.isEmpty() ? null : bars.get(bars.size() - 1);
    Double ytd =
        currentBar == null || ytdBaseline == null
            ? null
            : pct(currentBar.close(), ytdBaseline.close());
    boolean calendarYearEnd = targetDate.getMonthValue() == 12 && targetDate.getDayOfMonth() == 31;
    List<Double> annual =
        current.stream()
            .map(
                o ->
                    !calendarYearEnd || targetBaseline == null
                        ? null
                        : pct(o.forecast().targetLevel().doubleValue(), targetBaseline.close()))
            .filter(Objects::nonNull)
            .sorted()
            .toList();
    List<Double> remaining =
        current.stream()
            .map(
                o ->
                    currentBar == null
                        ? null
                        : pct(o.forecast().targetLevel().doubleValue(), currentBar.close()))
            .filter(Objects::nonNull)
            .sorted()
            .toList();
    Double median = median(annual), remainingMedian = median(remaining);
    Double min = annual.isEmpty() ? null : annual.get(0),
        max = annual.isEmpty() ? null : annual.get(annual.size() - 1);
    Double prior = calendarYearEnd ? medianAt(all, asOf.minusDays(30), targetBaseline) : null;
    Double revision = median == null || prior == null ? null : median - prior;

    ListRow[] rows =
        current.stream()
            .map(
                o ->
                    row(
                        o,
                        calendarYearEnd ? annualReturn(o, targetBaseline) : null,
                        remainingReturn(o, currentBar),
                        revisionLabel(eligible, o),
                        null,
                        null,
                        null))
            .toArray(ListRow[]::new);
    ListRow[] history =
        eligible.stream()
            .sorted(Comparator.comparing(o -> o.forecast().publishedAt()))
            .map(
                o ->
                    scoredRow(
                        o,
                        calendarYearEnd ? annualReturn(o, targetBaseline) : null,
                        remainingReturn(o, currentBar),
                        bars,
                        asOf))
            .toArray(ListRow[]::new);
    Accuracy accuracy = accuracy(all, bars, asOf);
    VixContext vix =
        new VixContext(
            null,
            null,
            null,
            null,
            null,
            "Unavailable: provider history is not verified for VIX",
            "^VIX (not ingested)");
    return new Outlook(
        targetDate,
        asOf,
        ytd,
        median,
        remainingMedian,
        min == null || max == null ? null : max - min,
        min,
        max,
        annual.size() >= 4 ? percentile(annual, .25) : null,
        annual.size() >= 4 ? percentile(annual, .75) : null,
        prior,
        revision,
        current.size(),
        stale,
        eligible.stream()
            .map(o -> utcDate(o.forecast().publishedAt()))
            .min(LocalDate::compareTo)
            .orElse(null),
        eligible.stream()
            .map(o -> utcDate(o.forecast().publishedAt()))
            .max(LocalDate::compareTo)
            .orElse(null),
        targetBaseline == null || currentBar == null
            ? "S&P 500 price-index history unavailable"
            : "S&P 500 price index; provider quote dates",
        rows,
        history,
        accuracy,
        vix);
  }

  private static ListRow row(
      ForecastObservation o,
      Double annual,
      Double remaining,
      String revision,
      Double error,
      Double errorPercent,
      LocalDate outcome) {
    var f = o.forecast();
    return new ListRow(
        f.institutionName(),
        f.strategist(),
        utcDate(f.publishedAt()),
        f.targetDate(),
        f.targetLevel().doubleValue(),
        annual,
        remaining,
        f.sourceTitle(),
        f.sourceUrl(),
        o.quality(),
        revision,
        error,
        errorPercent,
        outcome);
  }

  private static ListRow scoredRow(
      ForecastObservation o,
      Double annual,
      Double remaining,
      List<DailyMarketBar> bars,
      LocalDate asOf) {
    LocalDate target = o.forecast().targetDate();
    if (target.isAfter(asOf)) return row(o, annual, remaining, "", null, null, null);
    DailyMarketBar actual =
        bars.stream()
            .filter(b -> !b.date().isBefore(target))
            .min(Comparator.comparing(DailyMarketBar::date))
            .orElse(null);
    if (actual == null) return row(o, annual, remaining, "", null, null, null);
    double error = o.forecast().targetLevel().doubleValue() - actual.close();
    return row(o, annual, remaining, "", error, error / actual.close() * 100, actual.date());
  }

  private static Double annualReturn(ForecastObservation o, DailyMarketBar b) {
    return b == null ? null : pct(o.forecast().targetLevel().doubleValue(), b.close());
  }

  private static Double remainingReturn(ForecastObservation o, DailyMarketBar b) {
    return b == null ? null : pct(o.forecast().targetLevel().doubleValue(), b.close());
  }

  private static int compare(ForecastObservation a, ForecastObservation b) {
    int c = a.forecast().publishedAt().compareTo(b.forecast().publishedAt());
    if (c == 0) c = a.forecast().observedAt().compareTo(b.forecast().observedAt());
    return c == 0 ? Long.compare(a.id(), b.id()) : c;
  }

  private static long age(ForecastObservation o, LocalDate d) {
    return ChronoUnit.DAYS.between(utcDate(o.forecast().publishedAt()), d);
  }

  private static DailyMarketBar lastOnOrBefore(List<DailyMarketBar> bars, LocalDate d) {
    return bars.stream().filter(b -> !b.date().isAfter(d)).reduce((a, b) -> b).orElse(null);
  }

  private static Double medianAt(
      List<ForecastObservation> all, LocalDate date, DailyMarketBar baseline) {
    if (baseline == null) return null;
    Map<String, ForecastObservation> selected = new HashMap<>();
    all.stream()
        .filter(
            o ->
                o.status().equals("ELIGIBLE")
                    && !utcDate(o.forecast().publishedAt()).isAfter(date)
                    && !utcDate(o.forecast().observedAt()).isAfter(date)
                    && age(o, date) <= STALE_DAYS)
        .forEach(
            o ->
                selected.merge(
                    o.forecast().institutionKey(), o, (a, b) -> compare(a, b) >= 0 ? a : b));
    return median(
        selected.values().stream()
            .map(o -> pct(o.forecast().targetLevel().doubleValue(), baseline.close()))
            .sorted()
            .toList());
  }

  private static String revisionLabel(List<ForecastObservation> all, ForecastObservation o) {
    return all.stream()
        .filter(
            x ->
                x.forecast().institutionKey().equals(o.forecast().institutionKey())
                    && compare(x, o) < 0)
        .max(StrategistOutlookService::compare)
        .map(
            x ->
                o.forecast().targetLevel().compareTo(x.forecast().targetLevel()) > 0
                    ? "Raised"
                    : o.forecast().targetLevel().compareTo(x.forecast().targetLevel()) < 0
                        ? "Lowered"
                        : "Unchanged")
        .orElse("Initial");
  }

  private static Accuracy accuracy(
      List<ForecastObservation> all, List<DailyMarketBar> bars, LocalDate asOf) {
    List<Double> errors = new ArrayList<>(), percentages = new ArrayList<>();
    for (ForecastObservation o : all) {
      if (!o.status().equals("ELIGIBLE") || o.forecast().targetDate().isAfter(asOf)) continue;
      LocalDate target = o.forecast().targetDate();
      DailyMarketBar actual =
          bars.stream()
              .filter(b -> !b.date().isBefore(target))
              .min(Comparator.comparing(DailyMarketBar::date))
              .orElse(null);
      if (actual == null) continue;
      double error = o.forecast().targetLevel().doubleValue() - actual.close();
      errors.add(Math.abs(error));
      percentages.add(Math.abs(error / actual.close() * 100));
    }
    return new Accuracy(
        errors.size(),
        errors.isEmpty()
            ? null
            : errors.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),
        percentages.isEmpty()
            ? null
            : percentages.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),
        errors.size() < 5
            ? "Limited sample; no forecaster ranking (minimum 5 scored forecasts)"
            : "Descriptive historical sample");
  }

  private static Double pct(double a, double b) {
    return b == 0 ? null : (a / b - 1) * 100;
  }

  private static LocalDate utcDate(java.time.Instant instant) {
    return instant.atZone(java.time.ZoneOffset.UTC).toLocalDate();
  }

  private static Double median(List<Double> x) {
    if (x.isEmpty()) return null;
    int n = x.size();
    return n % 2 == 1 ? x.get(n / 2) : (x.get(n / 2 - 1) + x.get(n / 2)) / 2;
  }

  private static Double percentile(List<Double> x, double p) {
    if (x.isEmpty()) return null;
    double k = (x.size() - 1) * p;
    int f = (int) k, c = (int) Math.ceil(k);
    return x.get(f) + (k - f) * (x.get(c) - x.get(f));
  }
}
