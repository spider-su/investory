package com.smartbox.investory.ui.investment;

import com.smartbox.investory.investment.api.reporting.model.AccountMovementReview;
import com.smartbox.investory.investment.api.reporting.model.AccountMovementReviewCommand;
import com.smartbox.investory.investment.api.reporting.model.ReconciliationReport;
import com.smartbox.investory.investment.web.InvestmentReconciliationRestController;
import java.security.Principal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class InProcessInvestmentReconciliationClient implements InvestmentReconciliationClient {
  private final InvestmentReconciliationRestController rest;

  public InProcessInvestmentReconciliationClient(InvestmentReconciliationRestController rest) {
    this.rest = rest;
  }

  @Override
  public ReconciliationReport loadReconciliationReport(Long portfolioId) {
    return rest.report(portfolioId);
  }

  @Override
  public void refreshReconciliationViews(Long portfolioId) {
    rest.refresh(portfolioId);
  }

  @Override
  public List<AccountMovementReview> loadAccountMovementReviews(Long portfolioId) {
    return rest.accountMovements(portfolioId);
  }

  @Override
  public void recordAccountMovementReview(Long portfolioId, AccountMovementReviewCommand command) {
    Principal principal = command == null ? null : command::reviewedBy;
    rest.reviewAccountMovement(portfolioId, command, principal);
  }
}
