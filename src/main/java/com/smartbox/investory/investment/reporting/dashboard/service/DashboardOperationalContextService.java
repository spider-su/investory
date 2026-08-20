package com.smartbox.investory.investment.reporting.dashboard.service;

import com.smartbox.investory.infrastructure.ImportBatchStatus;
import com.smartbox.investory.integration.export.yahoo.YahooExportService;
import com.smartbox.investory.investment.accounting.model.models.Portfolio;
import com.smartbox.investory.investment.accounting.model.models.PortfolioDataQuality;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatistics;
import com.smartbox.investory.investment.infrastructure.persistence.account.AccountStatisticsRepository;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportHistory;
import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportRepository;
import com.smartbox.investory.investment.reporting.dashboard.application.DashboardOperationalView;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Composes existing persisted operational facts for progressive-disclosure dashboard context. */
@Service
@Transactional(readOnly = true)
public class DashboardOperationalContextService {
  private final ImportRepository importRepository;
  private final AccountStatisticsRepository accountStatisticsRepository;
  private final YahooExportService yahooExportService;

  public DashboardOperationalContextService(
      ImportRepository importRepository, AccountStatisticsRepository accountStatisticsRepository) {
    this(importRepository, accountStatisticsRepository, null);
  }

  @Autowired
  public DashboardOperationalContextService(
      ImportRepository importRepository,
      AccountStatisticsRepository accountStatisticsRepository,
      YahooExportService yahooExportService) {
    this.importRepository = importRepository;
    this.accountStatisticsRepository = accountStatisticsRepository;
    this.yahooExportService = yahooExportService;
  }

  public DashboardOperationalView load(Portfolio portfolio) {
    PortfolioDataQuality quality =
        portfolio.getDataQuality() == null
            ? PortfolioDataQuality.unknown()
            : portfolio.getDataQuality();
    Optional<ImportHistory> latestImport =
        importRepository.findFirstByStatusOrderByFinishedAtDesc(ImportBatchStatus.COMPLETED);
    List<AccountStatistics> accounts = accountStatisticsRepository.findAll();
    ZonedDateTime latestTransaction =
        accounts.stream()
            .map(AccountStatistics::getLastActivityAt)
            .filter(java.util.Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(null);
    ImportHistory imported = latestImport.orElse(null);
    long updatedAccounts =
        quality.reconciledAccounts() > 0 ? quality.reconciledAccounts() : accounts.size();
    var importContext =
        new DashboardOperationalView.ImportContext(
            imported == null ? null : imported.getFinishedAt(),
            imported == null || imported.getRowsApplied() == null ? 0 : imported.getRowsApplied(),
            imported == null || imported.getRowsFailed() == null ? 0 : imported.getRowsFailed(),
            updatedAccounts);
    var freshness =
        new DashboardOperationalView.FreshnessContext(
            quality.latestReportingRefreshAt(),
            latestTransaction,
            quality.latestPriceDate(),
            updatedAccounts);
    var valuation = valuation(quality);
    return new DashboardOperationalView(
        importContext,
        freshness,
        new DashboardOperationalView.FxContext(
            portfolio.getBaseCurrency(),
            portfolio.getExchangeRates(),
            quality.latestFxMonth(),
            portfolio.getExchangeRates() == null ? 0 : portfolio.getExchangeRates().size() + 1),
        valuation,
        yahoo(yahooExportService));
  }

  private DashboardOperationalView.YahooContext yahoo(YahooExportService service) {
    if (service == null) return new DashboardOperationalView.YahooContext(null, false);
    YahooExportService.YahooExportStatus status = service.status();
    return new DashboardOperationalView.YahooContext(status.lastExport(), status.upToDate());
  }

  private DashboardOperationalView.ValuationContext valuation(PortfolioDataQuality quality) {
    List<String> symbols =
        quality.issues().stream()
            .map(issue -> issue.assetId() == null ? issue.accountId() : issue.assetId())
            .filter(java.util.Objects::nonNull)
            .distinct()
            .limit(3)
            .toList();
    long unreliable =
        quality.missingPriceCount() + quality.proxyPriceCount() + quality.estimatedPriceCount();
    return new DashboardOperationalView.ValuationContext(
        quality.state(),
        quality.stalePriceCount() + unreliable,
        quality.stalePriceCount(),
        unreliable,
        symbols);
  }
}
