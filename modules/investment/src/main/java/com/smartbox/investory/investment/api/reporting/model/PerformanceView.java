package com.smartbox.investory.investment.api.reporting.model;

import java.util.List;

public record PerformanceView(
    BenchmarkView benchmark,
    PerformanceSummary summary,
    List<InstrumentPerformance> topGainers,
    List<InstrumentPerformance> topLosers,
    String kpiStart) {

  public PerformanceView {
    summary =
        summary == null
            ? new PerformanceSummary(
                0.0,
                null,
                null,
                0.0,
                0.0,
                0.0,
                "—",
                0.0,
                "—",
                0.0,
                ReturnMetric.unavailable(ReturnMetric.Status.INSUFFICIENT_DATA, "No performance"),
                ReturnMetric.unavailable(ReturnMetric.Status.INSUFFICIENT_DATA, "No performance"),
                new PerformanceAttribution(
                    java.math.BigDecimal.ZERO,
                    null,
                    java.math.BigDecimal.ZERO,
                    java.math.BigDecimal.ZERO,
                    null,
                    java.math.BigDecimal.ZERO,
                    java.math.BigDecimal.ZERO,
                    java.math.BigDecimal.ZERO,
                    java.math.BigDecimal.ZERO,
                    true,
                    false),
                ReturnMetric.unavailable(ReturnMetric.Status.INSUFFICIENT_DATA, "No performance"),
                ReturnMetric.unavailable(ReturnMetric.Status.INSUFFICIENT_DATA, "No performance"),
                null)
            : summary;
    topGainers =
        com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(topGainers);
    topLosers = com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(topLosers);
    kpiStart = kpiStart == null || kpiStart.isBlank() ? "2026-01" : kpiStart;
  }
}
