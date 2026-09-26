package com.smartbox.investory.ui.accounting;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** MVC adapter for the native Ryczalt accounting overview page. */
@Controller
@RequestMapping("/profiles/{profileId}/accounting")
public class RyczaltAccountingPageController {
  private final RyczaltWebAccountingClient client;
  private final RyczaltAccountingPageViewAssembler pageAssembler;
  private final Clock clock;

  public RyczaltAccountingPageController(
      RyczaltWebAccountingClient client,
      RyczaltAccountingPageViewAssembler pageAssembler,
      Clock clock) {
    this.client = client;
    this.pageAssembler = pageAssembler;
    this.clock = clock;
  }

  @GetMapping
  public String page(
      @PathVariable long profileId,
      @RequestParam(required = false) YearMonth month,
      Model model,
      HttpServletRequest request) {
    var periods = client.periods(profileId);
    var selected = month == null ? YearMonth.now(clock) : month;
    var selectedMonthInPeriods = periods.stream().anyMatch(value -> value.month().equals(selected));
    var period =
        selectedMonthInPeriods
            ? client.period(profileId, selected)
            : RyczaltAccountingPageViewAssembler.emptyPeriod(selected);
    var invoices =
        selectedMonthInPeriods
            ? client.invoices(profileId, selected)
            : List.<RyczaltWebAccountingClient.Invoice>of();
    var currentTransactions =
        selectedMonthInPeriods
            ? client.transactions(profileId, selected)
            : List.<RyczaltWebAccountingClient.Transaction>of();
    var nextTransactions =
        selectedMonthInPeriods
                && periods.stream().anyMatch(value -> value.month().equals(selected.plusMonths(1)))
            ? client.transactions(profileId, selected.plusMonths(1))
            : List.<RyczaltWebAccountingClient.Transaction>of();
    var obligations =
        selectedMonthInPeriods
            ? client.obligations(profileId, selected)
            : List.<RyczaltWebAccountingClient.Obligation>of();
    var referenceObligations =
        selectedMonthInPeriods
            ? client.referenceObligations(profileId, selected)
            : List.<RyczaltWebAccountingClient.ReferenceObligation>of();
    var issues =
        selectedMonthInPeriods
            ? client.issues(profileId, selected)
            : List.<RyczaltWebAccountingClient.Issue>of();
    var page =
        pageAssembler.assemble(
            selected,
            periods,
            period,
            invoices,
            currentTransactions,
            nextTransactions,
            obligations,
            referenceObligations,
            issues,
            selectedMonthInPeriods ? client.counterparties(profileId) : List.of(),
            RyczaltAccountingWebSupport.canWrite(request),
            LocalDate.now(clock));

    model.addAttribute("profileId", profileId);
    model.addAttribute(
        "jpkUrl", "/api/profiles/%d/accounting/periods/%s/jpk".formatted(profileId, selected));
    model.addAttribute(
        "zusDraUrl",
        "/api/profiles/%d/accounting/periods/%s/zus-dra".formatted(profileId, selected));
    model.addAttribute("pit28Url", "/profiles/%d/accounting/pit28".formatted(profileId));
    model.addAttribute("selectedMonth", page.selectedMonth());
    model.addAttribute("previousMonth", page.previousMonth());
    model.addAttribute("nextMonth", page.nextMonth());
    model.addAttribute("selectedMonthInPeriods", page.selectedMonthInPeriods());
    model.addAttribute("periods", page.periods());
    model.addAttribute("period", page.period());
    model.addAttribute("invoices", page.invoices());
    model.addAttribute("incomeInvoices", page.incomeInvoices());
    model.addAttribute("costInvoices", page.costInvoices());
    model.addAttribute("transactions", page.transactions());
    model.addAttribute("bankTransactionRows", page.bankTransactionRows());
    model.addAttribute("obligations", page.obligations());
    model.addAttribute("issues", page.issues());
    model.addAttribute("today", page.today());
    model.addAttribute("canWrite", page.canWrite());
    model.addAttribute("toPayAmountDisplay", page.toPayAmountDisplay());
    model.addAttribute("paidAmountDisplay", page.paidAmountDisplay());
    model.addAttribute("nextDueDate", page.nextDueDate());
    model.addAttribute("taxCards", page.taxCards());
    return "accounting/ryczalt";
  }

  @GetMapping("/pit28")
  public String pit28(
      @PathVariable long profileId,
      @RequestParam(required = false) Integer year,
      Model model,
      HttpServletRequest request) {
    int selectedYear = year == null ? Year.now(clock).getValue() : year;
    model.addAttribute("profileId", profileId);
    model.addAttribute("year", selectedYear);
    model.addAttribute("previousYear", selectedYear - 1);
    model.addAttribute("nextYear", selectedYear + 1);
    model.addAttribute("pit28", client.pit28(profileId, selectedYear));
    model.addAttribute("canWrite", RyczaltAccountingWebSupport.canWrite(request));
    return "accounting/pit28";
  }
}
