package com.example.demo.controllers.ui;

import com.example.demo.services.reconciliation.ReconciliationReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class ReconciliationController {

  private final ReconciliationReportService reconciliationReportService;

  @GetMapping("/dashboard/reconciliation")
  public String reconciliation(Model model) {
    model.addAttribute("report", reconciliationReportService.load());
    return "reconciliation";
  }
}
