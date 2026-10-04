package com.smartbox.investory.ui.investment;

import com.smartbox.investory.investment.api.reporting.model.AccountMovementReview;
import com.smartbox.investory.investment.api.reporting.model.AccountMovementReviewCommand;
import com.smartbox.investory.investment.api.reporting.model.ReconciliationReport;
import java.util.List;

/** UI boundary for the in-process investment reconciliation endpoint. */
public interface InvestmentReconciliationClient {
  ReconciliationReport loadReconciliationReport(Long portfolioId);

  void refreshReconciliationViews(Long portfolioId);

  List<AccountMovementReview> loadAccountMovementReviews(Long portfolioId);

  void recordAccountMovementReview(Long portfolioId, AccountMovementReviewCommand command);
}
