package com.smartbox.investory.ui.investment;

import com.smartbox.investory.investment.api.reporting.model.AccountMovementReviewCommand;
import com.smartbox.investory.investment.api.reporting.model.AccountMovementReviewResolution;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class ReconciliationController {

  private final InvestmentReconciliationClient reconciliation;

  @GetMapping("/portfolios/{portfolioId}/dashboard/reconciliation")
  public String reconciliation(
      Model model,
      @PathVariable Long portfolioId,
      @RequestParam(defaultValue = "false") boolean refreshed) {
    populateModel(model, portfolioId);
    model.addAttribute("refreshed", refreshed);
    model.addAttribute("reviewed", false);
    return "reconciliation";
  }

  @PostMapping("/portfolios/{portfolioId}/dashboard/reconciliation/refresh")
  public String refresh(@PathVariable Long portfolioId) {
    reconciliation.refreshReconciliationViews(portfolioId);
    return "redirect:/portfolios/" + portfolioId + "/dashboard/reconciliation?refreshed=true";
  }

  @PostMapping("/portfolios/{portfolioId}/dashboard/reconciliation/account-movement-review")
  public String reviewAccountMovement(
      @PathVariable Long portfolioId,
      @Valid @ModelAttribute AccountMovementReviewForm form,
      BindingResult binding,
      Principal principal,
      Model model) {
    if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
      populateModel(model, portfolioId);
      model.addAttribute("reviewError", "Sign in before recording a review.");
      return "reconciliation";
    }
    if (binding.hasErrors()) {
      populateModel(model, portfolioId);
      model.addAttribute(
          "reviewError", "Enter a rationale, evidence reference, and valid decision.");
      model.addAttribute("submittedFingerprint", form.getEventFingerprint());
      return "reconciliation";
    }
    try {
      reconciliation.recordAccountMovementReview(
          portfolioId,
          new AccountMovementReviewCommand(
              form.getIssueCode(),
              form.getAccountId(),
              form.getEventDate(),
              form.getEventFingerprint(),
              form.getResolution(),
              form.getRationale(),
              form.getEvidenceReference(),
              principal.getName()));
    } catch (IllegalArgumentException | IllegalStateException exception) {
      populateModel(model, portfolioId);
      model.addAttribute("reviewError", exception.getMessage());
      model.addAttribute("submittedFingerprint", form.getEventFingerprint());
      return "reconciliation";
    }
    return "redirect:/portfolios/" + portfolioId + "/dashboard/reconciliation?reviewed=true";
  }

  private void populateModel(Model model, Long portfolioId) {
    model.addAttribute("report", reconciliation.loadReconciliationReport(portfolioId));
    model.addAttribute(
        "accountMovementReviews", reconciliation.loadAccountMovementReviews(portfolioId));
    model.addAttribute("reviewResolutions", List.of(AccountMovementReviewResolution.values()));
    model.addAttribute("portfolioId", portfolioId);
  }
}
