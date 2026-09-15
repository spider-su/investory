package com.smartbox.investory.retirement.planning.application;

import com.smartbox.investory.investment.api.reporting.PortfolioYtdTwrReader;
import com.smartbox.investory.investment.api.reporting.model.ReturnMetric;
import com.smartbox.investory.longterm.api.BondReturnObservationReader;
import com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshotReader;
import com.smartbox.investory.longterm.api.LongTermAssetProfileReader;
import com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel;
import com.smartbox.investory.retirement.analysis.*;
import com.smartbox.investory.retirement.api.RetirementScenarioObservationApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.ScenarioObservation;
import com.smartbox.investory.retirement.api.model.ScenarioObservationAvailability;
import com.smartbox.investory.retirement.planning.input.*;
import com.smartbox.investory.retirement.planning.presentation.*;
import com.smartbox.investory.retirement.planning.projection.*;
import com.smartbox.investory.retirement.planning.reconciliation.*;
import com.smartbox.investory.retirement.planning.review.*;
import com.smartbox.investory.retirement.planning.timeline.*;
import com.smartbox.investory.retirement.preview.*;
import com.smartbox.investory.shared.currency.CurrencyConversionUnavailableException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
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
  private final LongTermAssetProfileReader currentLongTerm;
  private final BondReturnObservationReader bonds;
  private final PortfolioYtdTwrReader performance;
  private final Clock clock;

  public RetirementScenarioObservationService(
      LongTermAssetAnnualSnapshotReader historicalLongTerm,
      LongTermAssetProfileReader currentLongTerm,
      BondReturnObservationReader bonds,
      PortfolioYtdTwrReader performance,
      Clock clock) {
    this.historicalLongTerm = historicalLongTerm;
    this.currentLongTerm = currentLongTerm;
    this.bonds = bonds;
    this.performance = performance;
    this.clock = clock;
  }

  @Override
  public Map<String, ScenarioObservation> load(Long portfolioId, PlanningTimeline timeline) {
    LocalDate today = LocalDate.now(clock);
    Map<String, ScenarioObservation> result = new LinkedHashMap<>();
    result.put("Inflation", unavailable());
    result.put("Spending growth", spendingGrowth(timeline));
    int latestCompletedYear = today.getYear() - 1;
    LongTermAssetAnnualSnapshotModel current = safeCurrentSnapshot(portfolioId, today);
    LongTermAssetAnnualSnapshotModel prior =
        safeHistoricalSnapshot(portfolioId, latestCompletedYear);
    result.put(
        "Rental growth",
        rentalGrowth(
            current == null ? null : current.rentalIncome(),
            prior == null ? null : prior.rentalIncome(),
            today));
    BigDecimal bondReturn = bonds.currentWeightedEffectiveReturn(portfolioId, today);
    result.put(
        "Bond return",
        bondReturn == null
            ? unavailable()
            : available(bondReturn, "Weighted effective return", "as of " + today));

    boolean liveCurrentYear =
        timeline != null
            && timeline.years().stream()
                .anyMatch(
                    year ->
                        year.state() == PlanningTimelineState.LIVE
                            && year.year() == today.getYear());
    result.put("Equity return", equityReturn(portfolioId, today, liveCurrentYear));
    return Map.copyOf(result);
  }

  private ScenarioObservation equityReturn(
      Long portfolioId, LocalDate today, boolean liveCurrentYear) {
    if (!liveCurrentYear) return unavailable();
    ReturnMetric actual = performance.ytdTwr(portfolioId);
    if (actual == null || actual.status() == ReturnMetric.Status.CALCULATION_FAILED) {
      return unavailable();
    }
    if (actual.status() == ReturnMetric.Status.INSUFFICIENT_DATA || actual.value() == null) {
      return new ScenarioObservation(
          null,
          "Actual YTD TWR",
          today.getYear() + " YTD",
          ScenarioObservationAvailability.INSUFFICIENT_HISTORY);
    }
    return available(actual.value(), "Actual YTD TWR", today.getYear() + " YTD");
  }

  private LongTermAssetAnnualSnapshotModel safeCurrentSnapshot(Long portfolioId, LocalDate date) {
    try {
      var snapshot = currentLongTerm.snapshot(portfolioId, date);
      return snapshot == null ? null : snapshot.annualSnapshot();
    } catch (CurrencyConversionUnavailableException failure) {
      log.warn(
          "Current long-term scenario observation unavailable for portfolio {}",
          portfolioId,
          failure);
      return null;
    }
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
    var current =
        timeline.years().stream()
            .map(PlanningTimelineYear::current)
            .filter(java.util.Objects::nonNull)
            .findFirst()
            .map(this::annualSpending)
            .orElse(null);
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
    if (current != null) {
      if (years.isEmpty()) return insufficient("current year vs prior year");
      AnnualSpending prior = years.getLast();
      return growth(current, prior, "current year vs " + prior.year());
    }
    if (years.size() < 2) return insufficient("closed years");
    AnnualSpending first = years.get(years.size() - 2), last = years.getLast();
    return growth(last, first, "closed years " + first.year() + "–" + last.year());
  }

  private AnnualSpending annualSpending(CurrentPlanningYear year) {
    return year.annualizedSpending() == null
        ? null
        : new AnnualSpending(year.year(), year.annualizedSpending());
  }

  private ScenarioObservation growth(AnnualSpending current, AnnualSpending prior, String period) {
    if (current.amount().signum() <= 0 || prior.amount().signum() <= 0) return insufficient(period);
    BigDecimal ratio = current.amount().divide(prior.amount(), ROOT_CONTEXT);
    int elapsedYears = current.year() - prior.year();
    BigDecimal rate = elapsedYears > 1 ? nthRoot(ratio, elapsedYears) : ratio;
    return available(rate.subtract(BigDecimal.ONE), "Observed annualized", period);
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
