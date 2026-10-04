package com.smartbox.investory.investment.web;

import com.smartbox.investory.investment.api.reporting.InvestmentReconciliationApi;
import com.smartbox.investory.investment.api.reporting.model.AccountMovementReview;
import com.smartbox.investory.investment.api.reporting.model.AccountMovementReviewCommand;
import com.smartbox.investory.investment.api.reporting.model.ReconciliationReport;
import jakarta.validation.constraints.Positive;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** REST and in-process Java facade for Investment reconciliation reports. */
@RestController
@Validated
@RequestMapping("/api/v1/portfolios/{portfolioId}/investment/reconciliation")
@RequiredArgsConstructor
public class InvestmentReconciliationRestController {
  private final InvestmentReconciliationApi reconciliation;

  @GetMapping
  public ReconciliationReport report(@PathVariable @Positive Long portfolioId) {
    if (portfolioId == null || portfolioId <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "portfolioId must be positive");
    }
    return reconciliation.loadReconciliationReport(portfolioId);
  }

  @PostMapping("/refresh")
  public void refresh(@PathVariable @Positive Long portfolioId) {
    reconciliation.refreshReconciliationViews();
  }

  @GetMapping("/account-movements")
  public List<AccountMovementReview> accountMovements(@PathVariable @Positive Long portfolioId) {
    return reconciliation.loadAccountMovementReviews(portfolioId);
  }

  @PostMapping("/account-movements/reviews")
  public void reviewAccountMovement(
      @PathVariable @Positive Long portfolioId,
      @RequestBody AccountMovementReviewCommand command,
      Principal principal) {
    if (command == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A review decision is required");
    }
    if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "A signed-in reviewer is required");
    }
    var authenticatedCommand =
        new AccountMovementReviewCommand(
            command.issueCode(),
            command.accountId(),
            command.eventDate(),
            command.eventFingerprint(),
            command.resolution(),
            command.rationale(),
            command.evidenceReference(),
            principal.getName());
    try {
      recordAccountMovementReview(portfolioId, authenticatedCommand);
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    } catch (IllegalStateException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
    }
  }

  public void recordAccountMovementReview(Long portfolioId, AccountMovementReviewCommand command) {
    reconciliation.recordAccountMovementReview(portfolioId, command);
  }
}
