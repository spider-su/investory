package com.smartbox.investory.ui.marketradar;

import com.smartbox.investory.marketradar.api.StrategistOutlookApi.Outlook;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Presentation-only coordinates. Individual dots avoid interpolation across absent snapshots. */
public record OutlookPlot(List<Dot> dots, Double min, Double max, LocalDate first, LocalDate last) {
  public record Dot(double x, double y, double lowY, double highY, String label) {}

  private record Value(LocalDate date, Double value, Double low, Double high) {}

  public static OutlookPlot consensus(Outlook outlook) {
    return plot(
        Arrays.stream(outlook.consensusTrend())
            .map(
                p ->
                    new Value(
                        p.asOf(), p.medianTargetLevel(), p.minTargetLevel(), p.maxTargetLevel()))
            .toList());
  }

  public static OutlookPlot dispersion(Outlook outlook) {
    return plot(
        Arrays.stream(outlook.consensusTrend())
            .map(
                p ->
                    new Value(
                        p.asOf(),
                        p.dispersionPercentagePoints(),
                        p.dispersionPercentagePoints(),
                        p.dispersionPercentagePoints()))
            .toList());
  }

  public static OutlookPlot vix(Outlook outlook) {
    return plot(
        Arrays.stream(outlook.vix().trend())
            .map(p -> new Value(p.date(), p.level(), p.level(), p.level()))
            .toList());
  }

  private static OutlookPlot plot(List<Value> input) {
    List<Value> values =
        input.stream()
            .filter(p -> p.value() != null)
            .sorted(Comparator.comparing(Value::date))
            .toList();
    if (values.isEmpty()) return new OutlookPlot(List.of(), null, null, null, null);
    double min =
        values.stream().mapToDouble(p -> p.low() == null ? p.value() : p.low()).min().orElseThrow();
    double max =
        values.stream()
            .mapToDouble(p -> p.high() == null ? p.value() : p.high())
            .max()
            .orElseThrow();
    LocalDate first = values.getFirst().date(), last = values.getLast().date();
    double days = Math.max(1, ChronoUnit.DAYS.between(first, last));
    double lower = max == min ? Math.max(0, min - .5) : min;
    double upper = max == min ? max + .5 : max;
    double span = upper - lower;
    List<Dot> dots =
        values.stream()
            .map(
                p ->
                    new Dot(
                        40 + ChronoUnit.DAYS.between(first, p.date()) / days * 410,
                        125 - (p.value() - lower) / span * 105,
                        125 - ((p.low() == null ? p.value() : p.low()) - lower) / span * 105,
                        125 - ((p.high() == null ? p.value() : p.high()) - lower) / span * 105,
                        p.date() + ": " + p.value() + " (range " + p.low() + "–" + p.high() + ")"))
            .toList();
    return new OutlookPlot(dots, lower, upper, first, last);
  }
}
