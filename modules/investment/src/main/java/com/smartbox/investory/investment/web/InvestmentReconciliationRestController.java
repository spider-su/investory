package com.smartbox.investory.investment.web;

import com.smartbox.investory.investment.api.reporting.InvestmentReconciliationApi;
import com.smartbox.investory.investment.api.reporting.model.ReconciliationReport;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
