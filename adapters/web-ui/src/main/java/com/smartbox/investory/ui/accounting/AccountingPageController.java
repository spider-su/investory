package com.smartbox.investory.ui.accounting;

import java.math.BigDecimal;
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
    var overview = client.overview(profileId, selected);
    var stagingSummary = client.summary(profileId, selected);
    var stagingRows = client.rows(profileId, selected);

    boolean hasOperationalData =
        overview.summary().documents() > 0 || overview.summary().bankTransactions() > 0;
    boolean hasAcquiredData =
        hasOperationalData || overview.sources().evidenceCount() > 0 || !stagingRows.isEmpty();
    boolean hasReviewIssues =
        overview.sources().reviewRequired() > 0
            || overview.sources().failed() > 0
            || stagingSummary.blockingCount() > 0;
    String workspaceStatus =
        !hasAcquiredData
            ? "Waiting for data"
            : hasReviewIssues
                ? "Review needed"
                : overview.filingSummary().ready() && stagingRows.isEmpty()
                    ? "Ready to file"
                    : "In progress";
    String workspaceNextAction =
        !hasAcquiredData
            ? "Add source data"
            : hasReviewIssues
                ? "Review issues"
                : stagingSummary.readyToPromote() > 0
                    ? "Promote ready data"
                    : !stagingRows.isEmpty()
                        ? "Reconcile staged data"
                        : overview.nextActionLabel();

    int referenceHeadlineMatchCount = 0;
    if (hasOperationalData && overview.reference().available()) {
      if (same(overview.summary().revenue(), overview.reference().revenue())) {
        referenceHeadlineMatchCount++;
      }
      if (same(overview.summary().vat(), overview.reference().vatPayable())) {
        referenceHeadlineMatchCount++;
      }
      if (same(overview.summary().ryczalt(), overview.reference().ryczalt())) {
        referenceHeadlineMatchCount++;
      }
      if (same(overview.summary().zus(), overview.reference().zus())) {
        referenceHeadlineMatchCount++;
      }
    }

    model.addAttribute("profileId", profileId);
    model.addAttribute("months", months);
    model.addAttribute("selectedMonth", selected);
    model.addAttribute("overview", overview);
    model.addAttribute("stagingSummary", stagingSummary);
    model.addAttribute("stagingRows", stagingRows);
    model.addAttribute("hasAcquiredData", hasAcquiredData);
    model.addAttribute("hasOperationalData", hasOperationalData);
    model.addAttribute("hasReviewIssues", hasReviewIssues);
    model.addAttribute("workspaceStatus", workspaceStatus);
    model.addAttribute("workspaceNextAction", workspaceNextAction);
    model.addAttribute("referenceHeadlineMatchCount", referenceHeadlineMatchCount);
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
      validateUpload(file, "application/pdf", "image/jpeg", "image/png", "image/webp");
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

  @GetMapping("/accounting/documents/review")
  public String reviewSource(
      long profileId,
      YearMonth month,
      @RequestParam String sourceReference,
      Model model,
      jakarta.servlet.http.HttpServletRequest request,
      RedirectAttributes redirect) {
    try {
      var candidate = client.reviewSource(profileId, sourceReference);
      model.addAttribute("profileId", profileId);
      model.addAttribute("selectedMonth", month);
      model.addAttribute("candidate", candidate);
      model.addAttribute("canWrite", canWrite(request));
      return "accounting/review";
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("accountingError", safeMessage(exception));
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
      redirect.addFlashAttribute("accountingMessage", "Document staged for reconciliation.");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("accountingError", safeMessage(exception));
    }
    return redirect(profileId, month);
  }

  @PostMapping("/accounting/bank/import")
  public String importBank(
      long profileId, YearMonth month, MultipartFile file, RedirectAttributes redirect) {
    try {
      validateUpload(file, "text/csv", "application/csv", "application/vnd.ms-excel");
      client.importBank(
          profileId, file.getOriginalFilename(), file.getContentType(), file.getBytes(), month);
      redirect.addFlashAttribute(
          "accountingMessage",
          "Bank statement imported. Transactions were routed to their accounting months.");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("accountingError", safeMessage(exception));
    } catch (java.io.IOException exception) {
      redirect.addFlashAttribute("accountingError", "Cannot read the bank file.");
    }
    return redirect(profileId, month);
  }

  @PostMapping("/accounting/ksef/sync")
  public String syncKsef(long profileId, YearMonth month, RedirectAttributes redirect) {
    try {
      var result = client.syncKsef(profileId, month);
      if ("NOT_CONFIGURED".equals(result.status())) {
        redirect.addFlashAttribute("accountingError", "KSeF is not configured.");
      } else if (result.failed() > 0) {
        redirect.addFlashAttribute("accountingError", result.message());
      } else if (result.reviewRequired() > 0) {
        redirect.addFlashAttribute("accountingWarning", result.message());
      } else {
        redirect.addFlashAttribute("accountingMessage", result.message());
      }
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("accountingError", safeMessage(exception));
    }
    return redirect(profileId, month);
  }

  @PostMapping("/accounting/staging/reconcile")
  public String reconcile(long profileId, YearMonth month, RedirectAttributes redirect) {
    try {
      var summary = client.reconcile(profileId, month);
      redirect.addFlashAttribute(
          "accountingMessage",
          "Reconciliation completed: " + summary.readyToPromote() + " row(s) ready to promote.");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("accountingError", safeMessage(exception));
    }
    return redirect(profileId, month);
  }

  @PostMapping("/accounting/staging/promote")
  public String promote(long profileId, YearMonth month, RedirectAttributes redirect) {
    try {
      var promotion = client.promote(profileId, month);
      redirect.addFlashAttribute(
          "accountingMessage",
          "Promoted "
              + promotion.invoices()
              + " document(s) and "
              + promotion.bankTransactions()
              + " bank transaction(s).");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("accountingError", safeMessage(exception));
    }
    return redirect(profileId, month);
  }

  @PostMapping("/accounting/filings/jpk/generate")
  public String generateJpk(long profileId, YearMonth month, RedirectAttributes redirect) {
    try {
      client.generateJpk(profileId, month);
      redirect.addFlashAttribute("accountingMessage", "JPK_V7M(3) generated and validated.");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("accountingError", safeMessage(exception));
    }
    return redirect(profileId, month);
  }

  @GetMapping("/accounting/filings/jpk")
  public org.springframework.http.ResponseEntity<byte[]> downloadJpk(
      long profileId, YearMonth month) {
    return org.springframework.http.ResponseEntity.ok()
        .contentType(org.springframework.http.MediaType.APPLICATION_XML)
        .header(
            org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"JPK_V7M_" + month + ".xml\"")
        .body(client.downloadJpk(profileId, month));
  }

  @PostMapping("/accounting/filings/confirmations")
  public String recordUpo(
      long profileId,
      YearMonth month,
      @RequestParam String externalReference,
      RedirectAttributes redirect) {
    try {
      client.recordConfirmation(
          profileId,
          new AccountingRestClient.ConfirmationInput(
              month,
              "JPK_V7M",
              "JPK_UPO",
              "ACCEPTED",
              externalReference,
              java.time.Instant.now(),
              null,
              null));
      redirect.addFlashAttribute("accountingMessage", "Accepted UPO recorded.");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("accountingError", safeMessage(exception));
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

  private boolean same(BigDecimal actual, BigDecimal expected) {
    return actual != null && expected != null && actual.compareTo(expected) == 0;
  }

  private String safeMessage(RuntimeException exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) return "Accounting action failed.";
    if (exception instanceof IllegalArgumentException) return message;
    return switch (exception) {
      case org.springframework.web.client.RestClientException ignored ->
          "Accounting service is temporarily unavailable.";
      case org.springframework.web.server.ResponseStatusException status
          when status.getStatusCode().is4xxClientError() ->
          "Accounting request needs attention.";
      default -> "Accounting action failed.";
    };
  }

  private void validateUpload(MultipartFile file, String... contentTypes) {
    if (file == null || file.isEmpty())
      throw new IllegalArgumentException("Uploaded file is empty");
    if (file.getSize() > 12L * 1024 * 1024)
      throw new IllegalArgumentException("Uploaded file exceeds the 12 MB limit");
    String contentType = file.getContentType();
    if (contentType != null
        && java.util.Arrays.stream(contentTypes).noneMatch(contentType::equalsIgnoreCase))
      throw new IllegalArgumentException("Unsupported uploaded file type");
  }
}