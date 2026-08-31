package com.smartbox.investory.investment.projection;

import com.smartbox.investory.investment.infrastructure.persistence.account.AccountDailyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Refreshes the database views used by portfolio reconciliation and reporting. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioProjectionRefreshService {
  private final AccountDailyRepository accountDailyRepository;

  public void refreshReconciliationViews() {
    refreshStage(
        "reconstructed-position-daily", accountDailyRepository::refreshReconstructedPositionDaily);
    refreshStage(
        "reconstructed-account-market-daily",
        accountDailyRepository::refreshReconstructedAccountMarketDaily);
    refreshStage("reconstructed-cash-daily", accountDailyRepository::refreshReconstructedCashDaily);
    refreshStage(
        "account-daily-reconciliation", accountDailyRepository::refreshAccountDailyReconciliation);
    refreshStage(
        "reconciliation-reporting", accountDailyRepository::refreshReconciliationReportingViews);
  }

  private void refreshStage(String stage, Runnable refresh) {
    long started = System.nanoTime();
    try {
      refresh.run();
    } finally {
      log.info(
          "IMPORT PERF reconciliation-stage={}ms stage={}",
          (System.nanoTime() - started) / 1_000_000,
          stage);
    }
  }
}
