package com.smartbox.investory.investment.web;

import com.smartbox.investory.investment.api.reporting.InvestmentReconciliationApi;
import com.smartbox.investory.investment.api.reporting.model.ReconciliationReport;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST and in-process Java facade for Investment reconciliation reports. */
@RestController
@Validated
@RequestMapping("/api/v1/investment/reconciliation")
@RequiredArgsConstructor
public class InvestmentReconciliationRestController {
  private final InvestmentReconciliationApi reconciliation;

  @GetMapping
  public ReconciliationReport report(@RequestParam @Positive Long portfolioId) {
    return reconciliation.loadReconciliationReport(portfolioId);
  }
}
