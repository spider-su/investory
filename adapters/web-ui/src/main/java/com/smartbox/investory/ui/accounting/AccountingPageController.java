package com.smartbox.investory.ui.accounting;

import java.time.YearMonth;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AccountingPageController {
  private final AccountingRestClient client;

  public AccountingPageController(AccountingRestClient client) {
    this.client = client;
  }

  @GetMapping("/accounting")
  public String page(
      @RequestParam(defaultValue = "1") long profileId,
      @RequestParam(required = false) YearMonth month,
      Model model) {
    var months = client.months(profileId);
    YearMonth selected =
        month != null
            ? month
            : (months.isEmpty() ? YearMonth.now() : months.get(months.size() - 1).month());
    model.addAttribute("profileId", profileId);
    model.addAttribute("months", months);
    model.addAttribute("selectedMonth", selected);
    model.addAttribute("overview", client.overview(profileId, selected));
    model.addAttribute("documents", client.documents(profileId, selected));
    model.addAttribute("bankTransactions", client.bankTransactions(profileId, selected));
    model.addAttribute("payments", client.payments(profileId, selected));
    model.addAttribute("filings", client.filings(profileId, selected));
    model.addAttribute("reconciliation", client.reconciliation(profileId, selected));
    return "accounting/accounting";
  }
}
