package com.smartbox.investory.ui.investment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class ReconciliationController {

  private final InvestmentReconciliationClient reconciliation;

  @GetMapping("/dashboard/reconciliation")
  public String reconciliation(Model model, @RequestParam Long portfolioId) {
    var report = reconciliation.loadReconciliationReport(portfolioId);
    model.addAttribute("report", report);
    model.addAttribute("portfolioId", portfolioId);
    return "reconciliation";
  }
}
