package com.smartbox.investory.ui.retirement;

import com.smartbox.investory.investment.reporting.PerformanceResult;
import com.smartbox.investory.investment.reporting.PortfolioPerformanceQuery;
import com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshotReader;
import com.smartbox.investory.longterm.application.service.LongTermAssetsFacade;
import com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.retirement.planning.PlanningMetric;
import com.smartbox.investory.retirement.planning.PlanningTimeline;
import com.smartbox.investory.retirement.planning.PlanningTimelineYear;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Aggregates optional factual values for presentation; never feeds simulation inputs. */
@Service
public class ScenarioObservationService {
  private final LongTermAssetAnnualSnapshotReader longTerm;
  private final LongTermAssetsFacade longTermAssets;
  private final PortfolioPerformanceQuery performance;
  private final Clock clock;

  public ScenarioObservationService(LongTermAssetAnnualSnapshotReader longTerm,
                                    LongTermAssetsFacade longTermAssets,
                                    PortfolioPerformanceQuery performance, Clock clock) {
    this.longTerm = longTerm;
    this.longTermAssets = longTermAssets;
    this.performance = performance;
    this.clock = clock;
  }

  public Map<String, ScenarioObservation> load(Long portfolioId, PlanningTimeline timeline) {
    LocalDate today = LocalDate.now(clock);
    Map<String, ScenarioObservation> result = new LinkedHashMap<>();
    result.put("Inflation", ScenarioObservation.unavailable());
    result.put("Spending growth", spendingGrowth(timeline));

    LongTermAssetAnnualSnapshotModel current = longTerm.currentAnnualSnapshot(portfolioId, today);
    LongTermAssetAnnualSnapshotModel prior = longTerm.historicalAnnualSnapshot(portfolioId, today.getYear() - 1);
    result.put("Rental growth", growth(current.rentalIncome(), prior.rentalIncome()));
    result.put("Bond return", averageBondYield(portfolioId, today));

    YearMonth to = YearMonth.from(today).minusMonths(1);
    PerformanceResult actual = performance.forMonths(to.minusMonths(11), to);
    BigDecimal returnRate = actual.returnPercentage() == null ? null : actual.returnPercentage().movePointLeft(2);
    result.put("Equity return", returnRate == null
        ? ScenarioObservation.unavailable()
        : new ScenarioObservation(returnRate, "Observed annualized", "trailing 12 months",
            ScenarioAssumptionView.Availability.AVAILABLE));
    return result;
  }

  private ScenarioObservation averageBondYield(Long portfolioId, LocalDate date) {
    var yields = longTermAssets.list(portfolioId, date).stream()
        .filter(asset -> asset.type() == LongTermAssetType.BOND)
        .map(com.smartbox.investory.longterm.application.model.LongTermAssetSummary::currentAnnualRate)
        .filter(java.util.Objects::nonNull)
        .toList();
    if (yields.isEmpty()) return ScenarioObservation.unavailable();
    BigDecimal average = yields.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
        .divide(BigDecimal.valueOf(yields.size()), 12, java.math.RoundingMode.HALF_UP);
    return new ScenarioObservation(average, "Current average yield", "as of " + date,
        ScenarioAssumptionView.Availability.AVAILABLE);
  }

  private ScenarioObservation spendingGrowth(PlanningTimeline timeline) {
    if (timeline == null) return ScenarioObservation.unavailable();
    var years = timeline.years().stream()
        .filter(row -> row.past() != null && row.past().status().name().equals("CLOSED"))
        .map(PlanningTimelineYear::past)
        .map(year -> {
          BigDecimal core = value(year.values(), PlanningMetric.CORE_SPENDING);
          BigDecimal discretionary = value(year.values(), PlanningMetric.DISCRETIONARY_SPENDING);
          return core == null || discretionary == null ? null
              : new AnnualSpending(year.year(), core.add(discretionary));
        })
        .filter(java.util.Objects::nonNull)
        .sorted(java.util.Comparator.comparingInt(AnnualSpending::year))
        .toList();
    if (years.size() < 2) {
      return new ScenarioObservation(null, "Observed annualized", "closed years",
          ScenarioAssumptionView.Availability.INSUFFICIENT_HISTORY);
    }
    AnnualSpending first = years.get(years.size() - 2);
    AnnualSpending last = years.getLast();
    if (first.amount().signum() <= 0 || last.amount().signum() <= 0) {
      return new ScenarioObservation(null, "Observed annualized", "closed years",
          ScenarioAssumptionView.Availability.INSUFFICIENT_HISTORY);
    }
    int elapsedYears = last.year() - first.year();
    BigDecimal rate = last.amount().divide(first.amount(), 12, java.math.RoundingMode.HALF_UP);
    if (elapsedYears > 1) rate = BigDecimal.valueOf(Math.pow(rate.doubleValue(), 1.0 / elapsedYears));
    return new ScenarioObservation(rate.subtract(BigDecimal.ONE), "Observed annualized",
        "closed years " + first.year() + "–" + last.year(), ScenarioAssumptionView.Availability.AVAILABLE);
  }

  private static BigDecimal value(Map<PlanningMetric, com.smartbox.investory.retirement.planning.PlanningMetricValue> values,
      PlanningMetric metric) {
    var value = values == null ? null : values.get(metric);
    return value == null || !value.available() ? null : value.value();
  }

  private record AnnualSpending(int year, BigDecimal amount) {}

  private ScenarioObservation growth(BigDecimal current, BigDecimal prior) {
    if (current == null || prior == null || prior.signum() == 0)
      return new ScenarioObservation(null, "Observed annualized", "year over year",
          ScenarioAssumptionView.Availability.INSUFFICIENT_HISTORY);
    return new ScenarioObservation(current.divide(prior, 12, java.math.RoundingMode.HALF_UP).subtract(BigDecimal.ONE),
        "Observed annualized", "year over year", ScenarioAssumptionView.Availability.AVAILABLE);
  }
}
