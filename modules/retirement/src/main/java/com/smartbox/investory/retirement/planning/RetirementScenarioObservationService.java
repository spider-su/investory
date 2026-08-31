package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.investment.api.reporting.TrailingPortfolioReturnReader;
import com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshotReader;
import com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel;
import com.smartbox.investory.retirement.api.RetirementScenarioObservationApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.ScenarioObservation;
import com.smartbox.investory.retirement.api.model.ScenarioObservationAvailability;
import com.smartbox.investory.shared.currency.CurrencyConversionUnavailableException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Computes factual observations for scenario comparison; it never changes simulation inputs. */
@Slf4j
@Service
public class RetirementScenarioObservationService implements RetirementScenarioObservationApi {
  private static final MathContext ROOT_CONTEXT = new MathContext(20, RoundingMode.HALF_UP);

  private final LongTermAssetAnnualSnapshotReader historicalLongTerm;
  private final TrailingPortfolioReturnReader performance;
  private final Clock clock;

  public RetirementScenarioObservationService(
      LongTermAssetAnnualSnapshotReader historicalLongTerm,
      TrailingPortfolioReturnReader performance,
      Clock clock) {
    this.historicalLongTerm = historicalLongTerm;
    this.performance = performance;
    this.clock = clock;
  }

  @Override
  public Map<String, ScenarioObservation> load(Long portfolioId, PlanningTimeline timeline) {
    LocalDate today = LocalDate.now(clock);
    Map<String, ScenarioObservation> result = new LinkedHashMap<>();
    result.put("Inflation", unavailable());
    result.put("Spending growth", spendingGrowth(timeline));
    LongTermAssetAnnualSnapshotModel current = safeHistoricalSnapshot(portfolioId, today.getYear());
    LongTermAssetAnnualSnapshotModel prior =
        safeHistoricalSnapshot(portfolioId, today.getYear() - 1);
    result.put(
        "Rental growth",
        rentalGrowth(
            current == null ? null : current.rentalIncome(),
            prior == null ? null : prior.rentalIncome(),
            today));
    result.put("Bond return", unavailable());

    YearMonth to = YearMonth.from(today).minusMonths(1);
    BigDecimal actual = performance.returnPercentage(portfolioId, to.minusMonths(11), to);
    result.put(
        "Equity return",
        actual == null
            ? unavailable()
            : new ScenarioObservation(
                actual.movePointLeft(2),
                "Observed annualized",
                "trailing 12 months",
                ScenarioObservationAvailability.AVAILABLE));
    return Map.copyOf(result);
  }

  private LongTermAssetAnnualSnapshotModel safeHistoricalSnapshot(Long portfolioId, int year) {
    try {
      return historicalLongTerm.historicalAnnualSnapshot(portfolioId, year);
    } catch (CurrencyConversionUnavailableException failure) {
      log.warn(
          "Historical long-term scenario observation unavailable for portfolio {} and year {}",
          portfolioId,
          year,
          failure);
      return null;
    }
  }

  private ScenarioObservation spendingGrowth(PlanningTimeline timeline) {
    if (timeline == null) return unavailable();
    var years =
        timeline.years().stream()
            .filter(row -> row.past() != null && row.past().status().name().equals("CLOSED"))
            .map(PlanningTimelineYear::past)
            .map(
                year -> {
                  BigDecimal core = value(year.values(), PlanningMetric.CORE_SPENDING);
                  BigDecimal discretionary =
                      value(year.values(), PlanningMetric.DISCRETIONARY_SPENDING);
                  return core == null || discretionary == null
                      ? null
                      : new AnnualSpending(year.year(), core.add(discretionary));
                })
            .filter(java.util.Objects::nonNull)
            .sorted(java.util.Comparator.comparingInt(AnnualSpending::year))
            .toList();
    if (years.size() < 2) return insufficient("closed years");
    AnnualSpending first = years.get(years.size() - 2);
    AnnualSpending last = years.getLast();
    if (first.amount().signum() <= 0 || last.amount().signum() <= 0)
      return insufficient("closed years");
    int elapsedYears = last.year() - first.year();
    BigDecimal ratio = last.amount().divide(first.amount(), ROOT_CONTEXT);
    BigDecimal rate = elapsedYears > 1 ? nthRoot(ratio, elapsedYears) : ratio;
    return available(
        rate.subtract(BigDecimal.ONE),
        "Observed annualized",
        "closed years " + first.year() + "–" + last.year());
  }

  private static BigDecimal value(
      Map<PlanningMetric, PlanningMetricValue> values, PlanningMetric metric) {
    PlanningMetricValue value = values == null ? null : values.get(metric);
    return value == null || !value.available() ? null : value.value();
  }

  private ScenarioObservation rentalGrowth(BigDecimal current, BigDecimal prior, LocalDate date) {
    if (current == null || prior == null || prior.signum() == 0)
      return insufficient("year over year");
    return available(
        current.divide(prior, ROOT_CONTEXT).subtract(BigDecimal.ONE),
        "Annual net rent run rate",
        "as of " + date + " vs prior year end");
  }

  private static ScenarioObservation available(BigDecimal value, String label, String period) {
    return new ScenarioObservation(value, label, period, ScenarioObservationAvailability.AVAILABLE);
  }

  private static ScenarioObservation insufficient(String period) {
    return new ScenarioObservation(
        null, "Observed annualized", period, ScenarioObservationAvailability.INSUFFICIENT_HISTORY);
  }

  private static ScenarioObservation unavailable() {
    return new ScenarioObservation(null, null, null, ScenarioObservationAvailability.UNAVAILABLE);
  }

  private static BigDecimal nthRoot(BigDecimal value, int degree) {
    BigDecimal estimate = BigDecimal.ONE;
    BigDecimal n = BigDecimal.valueOf(degree);
    for (int i = 0; i < 30; i++) {
      BigDecimal numerator =
          n.subtract(BigDecimal.ONE)
              .multiply(estimate)
              .add(value.divide(power(estimate, degree - 1), ROOT_CONTEXT));
      BigDecimal next = numerator.divide(n, ROOT_CONTEXT);
      if (next.subtract(estimate).abs().compareTo(new BigDecimal("1E-18")) < 0) return next;
      estimate = next;
    }
    return estimate;
  }

  private static BigDecimal power(BigDecimal value, int exponent) {
    BigDecimal result = BigDecimal.ONE;
    for (int i = 0; i < exponent; i++) result = result.multiply(value, ROOT_CONTEXT);
    return result;
  }

  private record AnnualSpending(int year, BigDecimal amount) {}
}
