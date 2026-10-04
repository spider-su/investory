package com.smartbox.investory.investment.reconciliation.application;

import com.smartbox.investory.investment.api.reporting.InvestmentReconciliationApi;
import com.smartbox.investory.investment.api.reporting.model.AccountMovementReview;
import com.smartbox.investory.investment.api.reporting.model.AccountMovementReviewCommand;
import com.smartbox.investory.investment.api.reporting.model.ReconciliationReport;
import com.smartbox.investory.investment.infrastructure.persistence.reconciliation.AccountMovementReviewRepository;
import com.smartbox.investory.investment.projection.PortfolioProjectionRefreshService;
import com.smartbox.investory.investment.reconciliation.ReconciliationContext;
import com.smartbox.investory.investment.reconciliation.ReconciliationReportService;
import com.smartbox.investory.shared.time.ApplicationTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/** Adapts reconciliation reports to the public Investment API. */
@Service
@Primary
@RequiredArgsConstructor
public class InvestmentReconciliationApplicationService implements InvestmentReconciliationApi {
  private final ReconciliationReportService reconciliationReportService;
  private final PortfolioProjectionRefreshService projectionRefreshService;
  private final ApplicationTime applicationTime;
  private final AccountMovementReviewRepository accountMovementReviewRepository;

  @Override
  public ReconciliationReport loadReconciliationReport(Long portfolioId) {
    if (portfolioId == null || portfolioId <= 0) {
      throw new IllegalArgumentException("portfolioId must be positive");
    }
    return reconciliationReportService.generateReport(
        new ReconciliationContext(applicationTime.now(), applicationTime.today(), portfolioId));
  }

  @Override
  public void refreshReconciliationViews() {
    projectionRefreshService.refreshReconciliationViews();
  }

  @Override
  public List<AccountMovementReview> loadAccountMovementReviews(Long portfolioId) {
    requirePortfolioId(portfolioId);
    return accountMovementReviewRepository.findActiveByPortfolioId(portfolioId);
  }

  @Override
  public void recordAccountMovementReview(Long portfolioId, AccountMovementReviewCommand command) {
    requirePortfolioId(portfolioId);
    if (command == null
        || command.accountId() == null
        || command.accountId() <= 0
        || command.eventDate() == null
        || command.eventFingerprint() == null
        || !command.eventFingerprint().matches("[0-9a-f]{32}")
        || command.resolution() == null
        || command.rationale() == null
        || command.rationale().isBlank()
        || command.rationale().length() > 10_000
        || command.evidenceReference() == null
        || command.evidenceReference().isBlank()
        || command.evidenceReference().length() > 2_000
        || command.reviewedBy() == null
        || command.reviewedBy().isBlank()
        || command.reviewedBy().length() > 128) {
      throw new IllegalArgumentException("A complete account movement review is required");
    }
    if (!List.of("ACCOUNT_MARKET_VALUE_SPIKE", "ACCOUNT_EQUITY_SPIKE")
        .contains(command.issueCode())) {
      throw new IllegalArgumentException("Unsupported account movement issue code");
    }
    if (!accountMovementReviewRepository.appendReview(portfolioId, command)) {
      throw new IllegalStateException(
          "The account movement changed or is no longer in this portfolio. Reload and review it again.");
    }
  }

  private static void requirePortfolioId(Long portfolioId) {
    if (portfolioId == null || portfolioId <= 0) {
      throw new IllegalArgumentException("portfolioId must be positive");
    }
  }
}
