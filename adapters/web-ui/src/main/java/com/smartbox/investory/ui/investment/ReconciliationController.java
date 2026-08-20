package com.smartbox.investory.ui.investment;

import com.smartbox.investory.investment.reconciliation.ReconciliationMode;
import com.smartbox.investory.investment.reconciliation.ReconciliationReportService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class ReconciliationController {

  private final ReconciliationReportService reconciliationReportService;

  @GetMapping("/dashboard/reconciliation")
  public String reconciliation(
      Model model,
      @RequestParam(defaultValue = "QUICK") ReconciliationMode mode,
      @RequestParam(required = false) LocalDate asOf) {
    var report =
        mode == ReconciliationMode.QUICK && asOf == null
            ? reconciliationReportService.load()
            : reconciliationReportService.load(
                new com.smartbox.investory.investment.reconciliation.ReconciliationContext(
                    mode, java.time.Instant.now(), asOf == null ? LocalDate.now() : asOf));
    model.addAttribute("report", report);
    return "reconciliation";
  }
}
