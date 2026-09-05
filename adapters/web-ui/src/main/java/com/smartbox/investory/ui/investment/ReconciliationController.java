package com.smartbox.investory.ui.investment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class ReconciliationController {

  private final InvestmentReconciliationClient reconciliation;

  @GetMapping("/portfolios/{portfolioId}/dashboard/reconciliation")
  public String reconciliation(
      Model model,
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @RequestParam(defaultValue = "false") boolean refreshed) {
    var report = reconciliation.loadReconciliationReport(portfolioId);
    model.addAttribute("report", report);
    model.addAttribute("portfolioId", portfolioId);
    model.addAttribute("refreshed", refreshed);
    return "reconciliation";
  }

  @PostMapping("/portfolios/{portfolioId}/dashboard/reconciliation/refresh")
  public String refresh(@org.springframework.web.bind.annotation.PathVariable Long portfolioId) {
    reconciliation.refreshReconciliationViews();
    return "redirect:/portfolios/" + portfolioId + "/dashboard/reconciliation?refreshed=true";
  }
}
