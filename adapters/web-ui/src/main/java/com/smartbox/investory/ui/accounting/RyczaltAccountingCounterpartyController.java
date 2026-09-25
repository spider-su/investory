package com.smartbox.investory.ui.accounting;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** MVC routes for counterparties and their matching rules. */
@Controller
@RequestMapping("/profiles/{profileId}/accounting/counterparties")
public class RyczaltAccountingCounterpartyController {
  private final RyczaltWebAccountingClient client;

  public RyczaltAccountingCounterpartyController(RyczaltWebAccountingClient client) {
    this.client = client;
  }

  @GetMapping
  public String counterparties(
      @PathVariable long profileId, Model model, HttpServletRequest request) {
    model.addAttribute("profileId", profileId);
    model.addAttribute("counterparties", client.counterparties(profileId));
    model.addAttribute("canWrite", RyczaltAccountingWebSupport.canWrite(request));
    return "accounting/counterparties";
  }

  @GetMapping("/{counterpartyId}")
  public String counterparty(
      @PathVariable long profileId,
      @PathVariable long counterpartyId,
      Model model,
      HttpServletRequest request) {
    model.addAttribute("profileId", profileId);
    model.addAttribute("counterparty", client.counterparty(profileId, counterpartyId));
    var bills = client.invoices(profileId, counterpartyId);
    model.addAttribute("bills", bills);
    model.addAttribute("billsTotal", counterpartyInvoiceTotal(bills));
    model.addAttribute("billsCurrency", counterpartyInvoiceCurrency(bills));
    model.addAttribute("rules", client.rules(profileId, counterpartyId));
    model.addAttribute("canWrite", RyczaltAccountingWebSupport.canWrite(request));
    return "accounting/ryczalt-counterparty";
  }

  static BigDecimal counterpartyInvoiceTotal(List<RyczaltWebAccountingClient.Invoice> invoices) {
    return invoices.stream()
        .map(RyczaltWebAccountingClient.Invoice::grossAmount)
        .filter(java.util.Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  static String counterpartyInvoiceCurrency(List<RyczaltWebAccountingClient.Invoice> invoices) {
    var currencies =
        invoices.stream()
            .map(RyczaltWebAccountingClient.Invoice::currency)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    return currencies.size() == 1
        ? currencies.getFirst()
        : currencies.isEmpty() ? "" : "mixed currencies";
  }

  @PostMapping("/{counterpartyId}/alias")
  public String alias(
      @PathVariable long profileId,
      @PathVariable long counterpartyId,
      @RequestParam(required = false) String alias,
      RedirectAttributes redirect) {
    client.alias(profileId, counterpartyId, alias);
    redirect.addFlashAttribute("accountingMessage", "Alias saved.");
    return RyczaltAccountingWebSupport.counterpartyRedirect(profileId, counterpartyId);
  }

  @PostMapping("/{counterpartyId}/rules")
  public String addRule(
      @PathVariable long profileId,
      @PathVariable long counterpartyId,
      RuleForm form,
      RedirectAttributes redirect) {
    client.addRule(profileId, counterpartyId, form.clientForm());
    redirect.addFlashAttribute("accountingMessage", "Rule added.");
    return RyczaltAccountingWebSupport.counterpartyRedirect(profileId, counterpartyId);
  }

  @PostMapping("/{counterpartyId}/rules/{ruleId}")
  public String updateRule(
      @PathVariable long profileId,
      @PathVariable long counterpartyId,
      @PathVariable long ruleId,
      RuleForm form,
      RedirectAttributes redirect) {
    client.updateRule(profileId, counterpartyId, ruleId, form.clientForm());
    redirect.addFlashAttribute("accountingMessage", "Rule updated.");
    return RyczaltAccountingWebSupport.counterpartyRedirect(profileId, counterpartyId);
  }

  @PostMapping("/{counterpartyId}/rules/{ruleId}/delete")
  public String deleteRule(
      @PathVariable long profileId,
      @PathVariable long counterpartyId,
      @PathVariable long ruleId,
      RedirectAttributes redirect) {
    client.deleteRule(profileId, counterpartyId, ruleId);
    redirect.addFlashAttribute("accountingMessage", "Rule deleted.");
    return RyczaltAccountingWebSupport.counterpartyRedirect(profileId, counterpartyId);
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
}
