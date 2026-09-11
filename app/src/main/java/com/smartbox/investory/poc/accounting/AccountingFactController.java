package com.smartbox.investory.poc.accounting;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class AccountingFactController {
  private final AccountingFactService service;

  @GetMapping("/poc/accounting")
  public String facts(@RequestParam(required = false) String month, Model model) {
    List<LocalDate> periods = service.availablePeriods();
    LocalDate selected = resolveSelectedPeriod(month, periods);

    model.addAttribute("periods", periods);
    model.addAttribute("selectedPeriod", selected);
    model.addAttribute("snapshot", service.snapshot(selected));
    model.addAttribute("facts", service.facts());
    return "poc/accounting-facts";
  }

  private LocalDate resolveSelectedPeriod(String month, List<LocalDate> periods) {
    if (periods.isEmpty()) {
      return LocalDate.of(2026, 7, 1);
    }
    if (month != null && month.matches("\\d{4}-\\d{2}")) {
      LocalDate requested = LocalDate.parse(month + "-01");
      if (periods.contains(requested)) {
        return requested;
      }
    }
    LocalDate july = LocalDate.of(2026, 7, 1);
    return periods.contains(july) ? july : periods.get(periods.size() - 1);
  }
}
