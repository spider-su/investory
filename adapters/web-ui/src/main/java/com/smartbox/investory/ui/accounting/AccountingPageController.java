package com.smartbox.investory.ui.accounting;

import java.time.YearMonth;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
      Model model,
      jakarta.servlet.http.HttpServletRequest request) {
    var months = client.months(profileId);
    YearMonth selected =
        month != null
            ? month
            : (months.isEmpty() ? YearMonth.now() : months.get(months.size() - 1).month());
    model.addAttribute("profileId", profileId);
    model.addAttribute("months", months);
    model.addAttribute("selectedMonth", selected);
    model.addAttribute("overview", client.overview(profileId, selected));
    model.addAttribute("canWrite", canWrite(request));
    return "accounting/accounting";
  }

  @PostMapping("/accounting/actions/confirm")
  public String confirm(long profileId, YearMonth month, RedirectAttributes redirect) {
    return action("confirm", profileId, month, redirect, () -> client.confirm(profileId, month));
  }

  @PostMapping("/accounting/actions/file")
  public String file(long profileId, YearMonth month, RedirectAttributes redirect) {
    return action("file", profileId, month, redirect, () -> client.file(profileId, month));
  }

  @PostMapping("/accounting/actions/settle")
  public String settle(long profileId, YearMonth month, RedirectAttributes redirect) {
    return action("settle", profileId, month, redirect, () -> client.settle(profileId, month));
  }

  @PostMapping("/accounting/actions/lock")
  public String lock(long profileId, YearMonth month, RedirectAttributes redirect) {
    return action("lock", profileId, month, redirect, () -> client.lock(profileId, month));
  }

  @PostMapping("/accounting/actions/reopen")
  public String reopen(
      long profileId, YearMonth month, @RequestParam String reason, RedirectAttributes redirect) {
    if (reason == null || reason.isBlank()) {
      redirect.addFlashAttribute("accountingError", "Cannot reopen this month without a reason.");
    } else {
      return action(
          "reopen", profileId, month, redirect, () -> client.reopen(profileId, month, reason));
    }
    return redirect(profileId, month);
  }

  @PostMapping("/accounting/documents/recognize")
  public String recognize(
      long profileId,
      YearMonth month,
      MultipartFile file,
      RedirectAttributes redirect,
      Model model,
      jakarta.servlet.http.HttpServletRequest request) {
    try {
      var candidate =
          client.recognize(
              profileId, file.getOriginalFilename(), file.getContentType(), file.getBytes());
      model.addAttribute("profileId", profileId);
      model.addAttribute("selectedMonth", month);
      model.addAttribute("candidate", candidate);
      model.addAttribute("canWrite", canWrite(request));
      return "accounting/review";
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("accountingError", safeMessage(exception));
      return redirect(profileId, month);
    } catch (java.io.IOException exception) {
      redirect.addFlashAttribute("accountingError", "Cannot read the uploaded document.");
      return redirect(profileId, month);
    }
  }

  @PostMapping("/accounting/documents/save")
  public String saveReviewed(
      long profileId,
      YearMonth month,
      AccountingRestClient.ReviewedDocument document,
      RedirectAttributes redirect) {
    try {
      client.saveReviewed(profileId, document);
      redirect.addFlashAttribute("accountingMessage", "Document imported.");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("accountingError", safeMessage(exception));
    }
    return redirect(profileId, month);
  }

  @PostMapping("/accounting/bank/import")
  public String importBank(
      long profileId, YearMonth month, MultipartFile file, RedirectAttributes redirect) {
    try {
      client.importBank(
          profileId, file.getOriginalFilename(), file.getContentType(), file.getBytes(), month);
      redirect.addFlashAttribute("accountingMessage", "Bank file imported.");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("accountingError", safeMessage(exception));
    } catch (java.io.IOException exception) {
      redirect.addFlashAttribute("accountingError", "Cannot read the bank file.");
    }
    return redirect(profileId, month);
  }

  private String action(
      String name,
      long profileId,
      YearMonth month,
      RedirectAttributes redirect,
      Runnable operation) {
    try {
      operation.run();
      redirect.addFlashAttribute("accountingMessage", "Accounting action completed: " + name + ".");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("accountingError", safeMessage(exception));
    }
    return redirect(profileId, month);
  }

  private String redirect(long profileId, YearMonth month) {
    return "redirect:/accounting?profileId=" + profileId + "&month=" + month;
  }

  private boolean canWrite(jakarta.servlet.http.HttpServletRequest request) {
    return request.isUserInRole("ADMIN") || request.isUserInRole("PROFILE_OWNER");
  }

  private String safeMessage(RuntimeException exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) return "Accounting action failed.";
    return message.replaceAll("(?i)password|secret|token|sql", "[redacted]");
  }
}
