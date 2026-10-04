package com.smartbox.investory.investment.api.reporting;

import com.smartbox.investory.investment.api.reporting.model.AccountMovementReview;
import com.smartbox.investory.investment.api.reporting.model.AccountMovementReviewCommand;
import com.smartbox.investory.investment.api.reporting.model.ReconciliationReport;
import java.util.List;

/** UI-facing reconciliation report boundary. */
public interface InvestmentReconciliationApi {
  ReconciliationReport loadReconciliationReport(Long portfolioId);

  void refreshReconciliationViews();

  List<AccountMovementReview> loadAccountMovementReviews(Long portfolioId);

  void recordAccountMovementReview(Long portfolioId, AccountMovementReviewCommand command);
}
