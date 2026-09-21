package com.smartbox.investory.ui.accounting;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Native Ryczalt MVC adapter. Accounting conclusions stay in native read models. */
@Controller
@Profile("!legacy-accounting-compat")
public class RyczaltAccountingPageController {
  private static final String BASE = "/profiles/{profileId}/accounting";
  private final RyczaltWebAccountingClient client;

  public RyczaltAccountingPageController(RyczaltWebAccountingClient client) {
    this.client = client;
  }

  @GetMapping(BASE)
  public String page(
      @PathVariable long profileId,
      @RequestParam(required = false) YearMonth month,
      Model model,
      HttpServletRequest request) {
    var periods = client.periods(profileId);
    var selected = month == null ? YearMonth.now() : month;
    var reference = client.reference(profileId, selected);
    RyczaltWebAccountingClient.Period period;
    List<RyczaltWebAccountingClient.Invoice> invoices;
    List<RyczaltWebAccountingClient.Transaction> transactions;
    List<RyczaltWebAccountingClient.Obligation> obligations;
    List<RyczaltWebAccountingClient.Issue> issues;
    if (periods.stream().noneMatch(value -> value.month().equals(selected))) {
      period = emptyPeriod(selected);
      invoices = List.of();
      transactions = List.of();
      obligations = List.of();
      issues = List.of();
    } else {
      period = client.period(profileId, selected);
      invoices = client.invoices(profileId, selected);
      transactions = client.transactions(profileId, selected);
      obligations = client.obligations(profileId, selected);
      issues = client.issues(profileId, selected);
    }
    model.addAttribute("profileId", profileId);
    model.addAttribute("selectedMonth", selected);
    model.addAttribute("periods", periods);
    model.addAttribute("period", period);
    model.addAttribute("reference", reference);
    model.addAttribute("invoices", invoices);
    model.addAttribute("transactions", transactions);
    model.addAttribute("obligations", obligations);
    model.addAttribute("issues", issues);
    model.addAttribute("canWrite", canWrite(request));
    return "accounting/ryczalt";
  }

  private static RyczaltWebAccountingClient.Period emptyPeriod(YearMonth month) {
    return new RyczaltWebAccountingClient.Period(
        month,
        "OPEN",
        List.of(),
        new RyczaltWebAccountingClient.Summary(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
        new RyczaltWebAccountingClient.Audit(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO),
        new RyczaltWebAccountingClient.Documents(0, 0),
        new RyczaltWebAccountingClient.Settlement(
            0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true),
        new RyczaltWebAccountingClient.Reconciliation(0, 0, 0, 0),
        new RyczaltWebAccountingClient.Completeness("INCOMPLETE", 0),
        List.of());
  }

  @GetMapping(BASE + "/counterparties")
  public String counterparties(
      @PathVariable long profileId, Model model, HttpServletRequest request) {
    model.addAttribute("profileId", profileId);
    model.addAttribute("counterparties", client.counterparties(profileId));
    model.addAttribute("canWrite", canWrite(request));
    return "accounting/ryczalt-counterparties";
  }

  @GetMapping(BASE + "/counterparties/{counterpartyId}")
  public String counterparty(
      @PathVariable long profileId,
      @PathVariable long counterpartyId,
      Model model,
      HttpServletRequest request) {
    model.addAttribute("profileId", profileId);
    model.addAttribute("counterparty", client.counterparty(profileId, counterpartyId));
    model.addAttribute("rules", client.rules(profileId, counterpartyId));
    model.addAttribute("canWrite", canWrite(request));
    return "accounting/ryczalt-counterparty";
  }

  @PostMapping(BASE + "/counterparties/{counterpartyId}/alias")
  public String alias(
      @PathVariable long profileId,
      @PathVariable long counterpartyId,
      @RequestParam(required = false) String alias,
      RedirectAttributes redirect) {
    client.alias(profileId, counterpartyId, alias);
    redirect.addFlashAttribute("accountingMessage", "Alias saved.");
    return redirect(profileId, counterpartyId);
  }

  @PostMapping(BASE + "/actions/settle")
  public String settle(@PathVariable long profileId, YearMonth month, RedirectAttributes redirect) {
    return command(profileId, month, redirect, () -> client.settle(profileId, month), "settle");
  }

  @PostMapping(BASE + "/actions/freeze")
  public String freeze(
      @PathVariable long profileId,
      YearMonth month,
      @RequestParam String reason,
      RedirectAttributes redirect) {
    return command(
        profileId, month, redirect, () -> client.freeze(profileId, month, reason), "freeze");
  }

  @PostMapping(BASE + "/actions/reopen")
  public String reopen(
      @PathVariable long profileId,
      YearMonth month,
      @RequestParam String reason,
      RedirectAttributes redirect) {
    return command(
        profileId, month, redirect, () -> client.reopen(profileId, month, reason), "reopen");
  }

  private String command(
      long profileId,
      YearMonth month,
      RedirectAttributes redirect,
      Runnable operation,
      String name) {
    try {
      operation.run();
      redirect.addFlashAttribute("accountingMessage", "Ryczalt action completed: " + name + ".");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("accountingError", "Ryczalt action failed.");
    }
    return "redirect:/profiles/" + profileId + "/accounting?month=" + month;
  }

  private static String redirect(long profileId, long counterpartyId) {
    return "redirect:/profiles/" + profileId + "/accounting/counterparties/" + counterpartyId;
  }

  private static boolean canWrite(HttpServletRequest request) {
    return request.isUserInRole("ADMIN") || request.isUserInRole("PROFILE_OWNER");
  }
}
