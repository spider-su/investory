package com.smartbox.investory.ui.investment;

import com.smartbox.investory.investment.api.reporting.InvestmentReconciliationApi;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class ReconciliationController {

  private final InvestmentReconciliationApi reconciliationApi;

  @GetMapping("/dashboard/reconciliation")
  public String reconciliation(
      Model model,
      @RequestParam(defaultValue = "QUICK") String mode,
      @RequestParam(required = false) LocalDate asOf) {
    var report = reconciliationApi.load(mode, asOf);
    model.addAttribute("report", report);
    return "reconciliation";
  }
}
