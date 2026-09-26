package com.smartbox.investory.ui.accounting;

import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** MVC routes for operational Ryczalt accounting commands. */
@Controller
@RequestMapping("/profiles/{profileId}/accounting")
public class RyczaltAccountingActionController {
  private final RyczaltWebAccountingClient client;

  public RyczaltAccountingActionController(RyczaltWebAccountingClient client) {
    this.client = client;
  }

  @PostMapping("/invoices/{invoiceId}/manual-paid")
  public String manualPaid(
      @PathVariable long profileId,
      @PathVariable long invoiceId,
      @RequestParam LocalDate paidDate,
      @RequestParam(required = false) String note,
      @RequestParam(required = false) YearMonth month,
      RedirectAttributes redirect) {
    client.manualPaid(profileId, invoiceId, paidDate, note);
    redirect.addFlashAttribute("accountingMessage", "Invoice marked paid manually.");
    return RyczaltAccountingWebSupport.accountingRedirect(profileId, month);
  }

  @PostMapping("/invoices/{invoiceId}/manual-unpaid")
  public String manualUnpaid(
      @PathVariable long profileId,
      @PathVariable long invoiceId,
      @RequestParam(required = false) YearMonth month,
      RedirectAttributes redirect) {
    client.manualUnpaid(profileId, invoiceId);
    redirect.addFlashAttribute("accountingMessage", "Invoice marked unpaid.");
    return RyczaltAccountingWebSupport.accountingRedirect(profileId, month);
  }

  @PostMapping("/obligations/{obligationId}/manual-paid")
  public String manualObligationPaid(
      @PathVariable long profileId,
      @PathVariable long obligationId,
      @RequestParam YearMonth month,
      @RequestParam LocalDate paidDate,
      @RequestParam(required = false) String note,
      RedirectAttributes redirect) {
    client.manualObligationPaid(profileId, month, obligationId, paidDate, note);
    redirect.addFlashAttribute("accountingMessage", "Obligation marked paid manually.");
    return RyczaltAccountingWebSupport.accountingRedirect(profileId, month);
  }

  @PostMapping("/obligations/{obligationId}/manual-unpaid")
  public String manualObligationUnpaid(
      @PathVariable long profileId,
      @PathVariable long obligationId,
      @RequestParam YearMonth month,
      RedirectAttributes redirect) {
    client.manualObligationUnpaid(profileId, month, obligationId);
    redirect.addFlashAttribute("accountingMessage", "Obligation marked unpaid.");
    return RyczaltAccountingWebSupport.accountingRedirect(profileId, month);
  }

  @PostMapping("/bank/import")
  public String importBank(
      @PathVariable long profileId, @RequestParam MultipartFile file, RedirectAttributes redirect) {
    try {
      var result =
          client.importBank(
              profileId, file.getOriginalFilename(), file.getContentType(), file.getBytes());
      redirect.addFlashAttribute(
          "accountingMessage", "Bank import complete: " + result.imported() + " imported.");
    } catch (Exception exception) {
      RyczaltAccountingWebSupport.logFailure("bank-import", profileId, null, exception);
      redirect.addFlashAttribute(
          "accountingError",
          RyczaltAccountingWebSupport.userMessage(exception, "Bank import failed."));
    }
    return "redirect:/profiles/" + profileId + "/accounting";
  }

  @PostMapping("/ksef/sync")
  public String syncKsef(
      @PathVariable long profileId, @RequestParam YearMonth month, RedirectAttributes redirect) {
    try {
      var result = client.syncKsef(profileId, month);
      redirect.addFlashAttribute(
          "accountingMessage", "KSeF sync complete: " + result.imported() + " imported.");
    } catch (Exception exception) {
      RyczaltAccountingWebSupport.logFailure("ksef-sync", profileId, month, exception);
      redirect.addFlashAttribute(
          "accountingError",
          RyczaltAccountingWebSupport.userMessage(exception, "KSeF sync failed."));
    }
    return RyczaltAccountingWebSupport.accountingRedirect(profileId, month);
  }

  @PostMapping("/actions/freeze")
  public String freeze(
      @PathVariable long profileId,
      YearMonth month,
      @RequestParam String reason,
      RedirectAttributes redirect) {
    return command(
        profileId, month, redirect, () -> client.freeze(profileId, month, reason), "freeze");
  }

  @PostMapping("/actions/reopen")
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
      RyczaltAccountingWebSupport.logFailure(name, profileId, month, exception);
      redirect.addFlashAttribute(
          "accountingError",
          RyczaltAccountingWebSupport.userMessage(exception, "Ryczalt action failed."));
    }
    return RyczaltAccountingWebSupport.accountingRedirect(profileId, month);
  }
}
