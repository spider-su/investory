package com.smartbox.investory.ui.investment;

import com.smartbox.investory.investment.api.reporting.InvestmentReconciliationApi;
import com.smartbox.investory.investment.api.reporting.model.AccountMovementReview;
import com.smartbox.investory.investment.api.reporting.model.AccountMovementReviewCommand;
import com.smartbox.investory.investment.api.reporting.model.ReconciliationReport;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class InProcessInvestmentReconciliationClient implements InvestmentReconciliationClient {
  private final InvestmentReconciliationApi reconciliation;

  public InProcessInvestmentReconciliationClient(InvestmentReconciliationApi reconciliation) {
    this.reconciliation = reconciliation;
  }

  @Override
  public ReconciliationReport loadReconciliationReport(Long portfolioId) {
    return reconciliation.loadReconciliationReport(portfolioId);
  }

  @Override
  public void refreshReconciliationViews(Long portfolioId) {
    reconciliation.refreshReconciliationViews();
  }

  @Override
  public List<AccountMovementReview> loadAccountMovementReviews(Long portfolioId) {
    return reconciliation.loadAccountMovementReviews(portfolioId);
  }

  @Override
  public void recordAccountMovementReview(Long portfolioId, AccountMovementReviewCommand command) {
    reconciliation.recordAccountMovementReview(portfolioId, command);
  }
}
