package com.smartbox.investory.investment.reporting.dashboard.service;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.api.reporting.model.Benchmark;
import com.smartbox.investory.investment.performance.model.Portfolio;
import com.smartbox.investory.shared.policy.FinancialPolicyDefaults;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DashboardPeriodViewService {
  private final DashboardPortfolioPeriodService portfolioPeriodService;
  private final DashboardBenchmarkPeriodService benchmarkPeriodService;
  private final LocalDate historyStart;
  private final Clock clock;

  public DashboardPeriodViewService() {
    this(null, Clock.systemDefaultZone());
  }

  public DashboardPeriodViewService(
      @Value("${app.history-start:" + FinancialPolicyDefaults.HISTORY_START_TEXT + "}")
          String historyStart) {
    this(LocalDate.parse(historyStart), Clock.systemDefaultZone());
  }

  public DashboardPeriodViewService(LocalDate historyStart, Clock clock) {
    this(
        new DashboardPortfolioPeriodService(),
        new DashboardBenchmarkPeriodService(),
        historyStart,
        clock);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public DashboardPeriodViewService(
      DashboardPortfolioPeriodService portfolioPeriodService,
      DashboardBenchmarkPeriodService benchmarkPeriodService,
      @Value("${app.history-start:" + FinancialPolicyDefaults.HISTORY_START_TEXT + "}")
          String historyStart) {
    this(
        portfolioPeriodService,
        benchmarkPeriodService,
        LocalDate.parse(historyStart),
        Clock.systemDefaultZone());
  }

  private DashboardPeriodViewService(
      DashboardPortfolioPeriodService portfolioPeriodService,
      DashboardBenchmarkPeriodService benchmarkPeriodService,
      LocalDate historyStart,
      Clock clock) {
    this.portfolioPeriodService = portfolioPeriodService;
    this.benchmarkPeriodService = benchmarkPeriodService;
    this.historyStart = historyStart;
    this.clock = clock;
  }

  public Portfolio filter(Portfolio source, DashboardPeriod period) {
    Portfolio portfolio = DashboardPortfolioPeriodService.copyPortfolio(source);
    portfolioPeriodService.apply(portfolio, startMonth(period));
    return portfolio;
  }

  public Benchmark filter(Benchmark source, DashboardPeriod period) {
    Benchmark benchmark = DashboardBenchmarkPeriodService.copyBenchmark(source);
    benchmarkPeriodService.apply(benchmark, startMonth(period));
    return benchmark;
  }

  private YearMonth startMonth(DashboardPeriod period) {
    // Reporting inputs are monthly. A trailing date range includes the calendar month containing
    // its start date, so a 1M selection on 15 August includes the July monthly observation.
    ZonedDateTime now = ZonedDateTime.now(clock);
    ZonedDateTime requested = period.startDate(now);
    YearMonth configured = historyStart == null ? null : YearMonth.from(historyStart);
    return requested == null
        ? configured
        : configured != null && YearMonth.from(requested).isBefore(configured)
            ? configured
            : YearMonth.from(requested);
  }
}
