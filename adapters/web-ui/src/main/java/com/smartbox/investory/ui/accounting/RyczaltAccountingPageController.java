package com.smartbox.investory.ui.accounting;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Native Ryczalt MVC adapter. Accounting conclusions stay in native read models. */
@Controller
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
    RyczaltWebAccountingClient.Period period;
    List<RyczaltWebAccountingClient.Invoice> invoices;
    List<RyczaltWebAccountingClient.Transaction> transactions;
    List<RyczaltWebAccountingClient.Obligation> obligations;
    List<RyczaltWebAccountingClient.Issue> issues;
    var selectedMonthInPeriods = periods.stream().anyMatch(value -> value.month().equals(selected));
    if (!selectedMonthInPeriods) {
      period = emptyPeriod(selected);
      invoices = List.of();
      transactions = List.of();
      obligations = List.of();
      issues = List.of();
    } else {
      period = client.period(profileId, selected);
      invoices = client.invoices(profileId, selected);
      var nextMonthTransactions =
          periods.stream().anyMatch(value -> value.month().equals(selected.plusMonths(1)))
              ? client.transactions(profileId, selected.plusMonths(1))
              : List.<RyczaltWebAccountingClient.Transaction>of();
      transactions =
          bankTransactionsForDisplay(
              client.transactions(profileId, selected), nextMonthTransactions);
      obligations = client.obligations(profileId, selected);
      issues = client.issues(profileId, selected);
    }
    model.addAttribute("profileId", profileId);
    model.addAttribute("selectedMonth", selected);
    model.addAttribute("previousMonth", selected.minusMonths(1));
    model.addAttribute("nextMonth", selected.plusMonths(1));
    model.addAttribute("selectedMonthInPeriods", selectedMonthInPeriods);
    model.addAttribute("periods", periods);
    model.addAttribute("period", period);
    model.addAttribute("invoices", invoices);
    model.addAttribute(
        "incomeInvoices",
        invoices.stream().filter(item -> "INCOME".equals(item.direction())).toList());
    model.addAttribute(
        "costInvoices", invoices.stream().filter(item -> "COST".equals(item.direction())).toList());
    model.addAttribute("transactions", transactions);
    model.addAttribute(
        "bankTransactionRows",
        bankTransactionRows(
            transactions.isEmpty() ? List.of() : client.counterparties(profileId), transactions));
    model.addAttribute("obligations", obligations);
    model.addAttribute("issues", issues);
    model.addAttribute("today", LocalDate.now());
    model.addAttribute("canWrite", canWrite(request));
    model.addAttribute("nativeRyczaltCompatibility", true);
    model.addAttribute("toPayAmountDisplay", whole(period.settlement().totalOutstanding()));
    model.addAttribute("paidAmountDisplay", whole(period.settlement().totalPaid()));
    model.addAttribute("payments", List.of());
    model.addAttribute(
        "workspaceStatus", "FROZEN".equals(period.status()) ? "Frozen" : "In progress");
    model.addAttribute("taxCards", taxCards(period, obligations));
    return "accounting/ryczalt";
  }

  private static List<TaxCard> taxCards(
      RyczaltWebAccountingClient.Period period,
      List<RyczaltWebAccountingClient.Obligation> obligations) {
    return List.of(
        taxCard("Ryczalt", period.summary().ryczalt(), obligations),
        taxCard("VAT", period.summary().vat(), obligations),
        taxCard("ZUS", period.summary().zus(), obligations));
  }

  private static TaxCard taxCard(
      String type, BigDecimal calculated, List<RyczaltWebAccountingClient.Obligation> obligations) {
    var bank =
        obligations.stream()
            .filter(item -> type.equalsIgnoreCase(item.type()))
            .map(RyczaltWebAccountingClient.Obligation::paid)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    var hasObligation = obligations.stream().anyMatch(item -> type.equalsIgnoreCase(item.type()));
    var outstanding =
        obligations.stream()
            .filter(item -> type.equalsIgnoreCase(item.type()))
            .map(RyczaltWebAccountingClient.Obligation::outstanding)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    var paid = hasObligation && outstanding.signum() <= 0;
    return new TaxCard(
        type,
        whole(calculated),
        whole(calculated),
        whole(bank),
        BigDecimal.ZERO.setScale(0).toPlainString(),
        difference(calculated, bank),
        paid ? "✓ Paid" : "○ Unpaid",
        paid ? "is-paid" : "is-unpaid");
  }

  private static String whole(BigDecimal value) {
    return value == null ? "—" : value.setScale(0, RoundingMode.HALF_UP).toPlainString();
  }

  static List<BankTransactionRow> bankTransactionRows(
      List<RyczaltWebAccountingClient.Counterparty> counterparties,
      List<RyczaltWebAccountingClient.Transaction> transactions) {
    Map<String, String> labels = new HashMap<>();
    counterparties.forEach(
        counterparty -> {
          String label =
              counterparty.alias() == null || counterparty.alias().isBlank()
                  ? counterparty.displayName()
                  : counterparty.alias();
          putCounterpartyLabel(labels, counterparty.legalName(), label);
          putCounterpartyLabel(labels, counterparty.alias(), label);
          putCounterpartyLabel(labels, counterparty.displayName(), label);
        });
    return transactions.stream()
        .map(
            transaction -> {
              String counterparty = transaction.counterparty();
              String label = labels.getOrDefault(normalizeCounterparty(counterparty), counterparty);
              BigDecimal amount =
                  transaction.amount() == null ? BigDecimal.ZERO : transaction.amount();
              BigDecimal matched =
                  transaction.matchedAmount() == null
                      ? BigDecimal.ZERO
                      : transaction.matchedAmount();
              String status =
                  matched.signum() == 0
                      ? "Unmatched"
                      : matched.compareTo(amount.abs()) >= 0 ? "Matched" : "Partially matched";
              return new BankTransactionRow(
                  label == null || label.isBlank() ? "—" : label,
                  transaction.bookingDate(),
                  whole(amount),
                  status,
                  transaction.description());
            })
        .toList();
  }

  static List<RyczaltWebAccountingClient.Transaction> bankTransactionsForDisplay(
      List<RyczaltWebAccountingClient.Transaction> currentMonth,
      List<RyczaltWebAccountingClient.Transaction> nextMonth) {
    return Stream.concat(
            currentMonth.stream().filter(transaction -> signum(transaction.amount()) > 0),
            nextMonth.stream().filter(transaction -> signum(transaction.amount()) < 0))
        .sorted(
            java.util.Comparator.comparing(
                    RyczaltWebAccountingClient.Transaction::bookingDate,
                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                .thenComparingLong(RyczaltWebAccountingClient.Transaction::id))
        .toList();
  }

  private static int signum(BigDecimal value) {
    return value == null ? 0 : value.signum();
  }

  private static void putCounterpartyLabel(Map<String, String> labels, String name, String label) {
    String key = normalizeCounterparty(name);
    if (!key.isEmpty() && label != null && !label.isBlank()) labels.putIfAbsent(key, label);
  }

  private static String normalizeCounterparty(String name) {
    if (name == null || name.isBlank()) return "";
    return Normalizer.normalize(name, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .replaceAll("[^\\p{Alnum}]", "")
        .toUpperCase(Locale.ROOT);
  }

  record BankTransactionRow(
      String counterparty,
      LocalDate bookingDate,
      String amount,
      String status,
      String description) {}

  private static String difference(BigDecimal calculated, BigDecimal comparison) {
    if (calculated == null || comparison == null) return null;
    var difference = calculated.subtract(comparison).setScale(0, RoundingMode.HALF_UP);
    return difference.signum() == 0
        ? null
        : (difference.signum() > 0 ? "+" : "") + difference.toPlainString();
  }

  record TaxCard(
      String type,
      String calculated,
      String reference,
      String bank,
      String referenceDiff,
      String bankDiff,
      String status,
      String statusClass) {}

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
    return "accounting/counterparties";
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

  @PostMapping(BASE + "/documents/recognize")
  public String recognize(
      @PathVariable long profileId, @RequestParam MultipartFile file, RedirectAttributes redirect) {
    try {
      var candidate =
          client.recognize(
              profileId, file.getOriginalFilename(), file.getContentType(), file.getBytes());
      redirect.addFlashAttribute("accountingMessage", "Document recognized. Review the candidate.");
      return "redirect:/profiles/"
          + profileId
          + "/accounting/documents/candidates/"
          + candidate.candidateKey();
    } catch (Exception exception) {
      redirect.addFlashAttribute("accountingError", "Document recognition failed.");
      return "redirect:/profiles/" + profileId + "/accounting";
    }
  }

  @GetMapping(BASE + "/documents/candidates/{candidateKey}")
  public String candidate(
      @PathVariable long profileId,
      @PathVariable UUID candidateKey,
      Model model,
      HttpServletRequest request) {
    model.addAttribute("profileId", profileId);
    model.addAttribute("candidate", client.candidate(profileId, candidateKey));
    model.addAttribute("counterparties", client.counterparties(profileId));
    model.addAttribute("canWrite", canWrite(request));
    return "accounting/ryczalt-candidate";
  }

  @PostMapping(BASE + "/documents/candidates/{candidateKey}")
  public String approveCandidate(
      @PathVariable long profileId,
      @PathVariable UUID candidateKey,
      @RequestParam(required = false) Long counterpartyId,
      @RequestParam(required = false) String classification,
      @RequestParam(required = false) String vatTreatment,
      @RequestParam(required = false) String vatDeductionRatio,
      @RequestParam(required = false) String ryczaltRate,
      @RequestParam(required = false) String paymentVerificationPolicy,
      @RequestParam(defaultValue = "false") boolean approve,
      @RequestParam(defaultValue = "false") boolean rememberRule,
      @RequestParam(required = false) String ruleName,
      @RequestParam(required = false) String serviceKey,
      RedirectAttributes redirect) {
    try {
      client.approveCandidate(
          profileId,
          candidateKey,
          counterpartyId,
          classification,
          vatTreatment,
          vatDeductionRatio,
          ryczaltRate,
          paymentVerificationPolicy,
          approve,
          rememberRule,
          ruleName,
          serviceKey);
      redirect.addFlashAttribute(
          "accountingMessage", approve ? "Invoice approved." : "Candidate saved for review.");
    } catch (Exception exception) {
      redirect.addFlashAttribute("accountingError", "Candidate could not be saved.");
      return "redirect:/profiles/" + profileId + "/accounting/documents/candidates/" + candidateKey;
    }
    return "redirect:/profiles/" + profileId + "/accounting";
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

  @PostMapping(BASE + "/counterparties/{counterpartyId}/rules")
  public String addRule(
      @PathVariable long profileId,
      @PathVariable long counterpartyId,
      RuleForm form,
      RedirectAttributes redirect) {
    client.addRule(profileId, counterpartyId, form.clientForm());
    redirect.addFlashAttribute("accountingMessage", "Rule added.");
    return redirect(profileId, counterpartyId);
  }

  @PostMapping(BASE + "/counterparties/{counterpartyId}/rules/{ruleId}")
  public String updateRule(
      @PathVariable long profileId,
      @PathVariable long counterpartyId,
      @PathVariable long ruleId,
      RuleForm form,
      RedirectAttributes redirect) {
    client.updateRule(profileId, counterpartyId, ruleId, form.clientForm());
    redirect.addFlashAttribute("accountingMessage", "Rule updated.");
    return redirect(profileId, counterpartyId);
  }

  @PostMapping(BASE + "/counterparties/{counterpartyId}/rules/{ruleId}/delete")
  public String deleteRule(
      @PathVariable long profileId,
      @PathVariable long counterpartyId,
      @PathVariable long ruleId,
      RedirectAttributes redirect) {
    client.deleteRule(profileId, counterpartyId, ruleId);
    redirect.addFlashAttribute("accountingMessage", "Rule deleted.");
    return redirect(profileId, counterpartyId);
  }

  @PostMapping(BASE + "/invoices/{invoiceId}/manual-paid")
  public String manualPaid(
      @PathVariable long profileId,
      @PathVariable long invoiceId,
      @RequestParam LocalDate paidDate,
      @RequestParam(required = false) String note,
      @RequestParam(required = false) YearMonth month,
      RedirectAttributes redirect) {
    client.manualPaid(profileId, invoiceId, paidDate, note);
    redirect.addFlashAttribute("accountingMessage", "Invoice marked paid manually.");
    return accountingRedirect(profileId, month);
  }

  @PostMapping(BASE + "/invoices/{invoiceId}/manual-unpaid")
  public String manualUnpaid(
      @PathVariable long profileId,
      @PathVariable long invoiceId,
      @RequestParam(required = false) YearMonth month,
      RedirectAttributes redirect) {
    client.manualUnpaid(profileId, invoiceId);
    redirect.addFlashAttribute("accountingMessage", "Invoice marked unpaid.");
    return accountingRedirect(profileId, month);
  }

  @PostMapping(BASE + "/bank/import")
  public String importBank(
      @PathVariable long profileId, @RequestParam MultipartFile file, RedirectAttributes redirect) {
    try {
      var result =
          client.importBank(
              profileId, file.getOriginalFilename(), file.getContentType(), file.getBytes());
      redirect.addFlashAttribute(
          "accountingMessage", "Bank import complete: " + result.imported() + " imported.");
    } catch (Exception exception) {
      redirect.addFlashAttribute("accountingError", "Bank import failed.");
    }
    return "redirect:/profiles/" + profileId + "/accounting";
  }

  @PostMapping(BASE + "/ksef/sync")
  public String syncKsef(
      @PathVariable long profileId, @RequestParam YearMonth month, RedirectAttributes redirect) {
    try {
      var result = client.syncKsef(profileId, month);
      redirect.addFlashAttribute(
          "accountingMessage", "KSeF sync complete: " + result.imported() + " imported.");
    } catch (Exception exception) {
      redirect.addFlashAttribute("accountingError", "KSeF sync failed.");
    }
    return accountingRedirect(profileId, month);
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

  private static String accountingRedirect(long profileId, YearMonth month) {
    return "redirect:/profiles/"
        + profileId
        + "/accounting"
        + (month == null ? "" : "?month=" + month);
  }

  public record RuleForm(
      String name,
      String sourceType,
      String documentType,
      String serviceKey,
      String classification,
      String vatTreatment,
      String vatDeductionRatio,
      String ryczaltRate,
      boolean autoApprove,
      String paymentVerificationPolicy) {
    RyczaltWebAccountingClient.RuleForm clientForm() {
      return new RyczaltWebAccountingClient.RuleForm(
          name,
          sourceType,
          documentType,
          serviceKey,
          classification,
          vatTreatment,
          vatDeductionRatio,
          ryczaltRate,
          autoApprove,
          paymentVerificationPolicy);
    }
  }

  private static boolean canWrite(HttpServletRequest request) {
    return request.isUserInRole("ADMIN") || request.isUserInRole("PROFILE_OWNER");
  }
}
