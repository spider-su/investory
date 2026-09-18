package com.smartbox.investory.ui.accounting;

import com.smartbox.investory.accounting.api.AccountingStagingApi;
import com.smartbox.investory.accounting.api.AccountingUserApi.IssueView;
import com.smartbox.investory.accounting.api.AccountingUserApi.ReconciliationView;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.presentation.FinancialPresentation;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AccountingPageController {
  private static final String BASE = "/profiles/{profileId}/accounting";
  private final AccountingRestClient client;
  private final CurrencyConversion currencyConversion;

  public AccountingPageController(AccountingRestClient client) {
    this(client, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public AccountingPageController(
      AccountingRestClient client, CurrencyConversion currencyConversion) {
    this.client = client;
    this.currencyConversion = currencyConversion;
  }

  @GetMapping("/accounting")
  public String legacyPage(
      @RequestParam(defaultValue = "1") long profileId,
      @RequestParam(required = false) YearMonth month) {
    return redirect(profileId, month);
  }

  @GetMapping(BASE)
  public String page(
      @PathVariable("profileId") long profileId,
      @RequestParam(required = false) YearMonth month,
      Model model,
      jakarta.servlet.http.HttpServletRequest request) {
    YearMonth currentMonth = YearMonth.now();
    var months = new ArrayList<>(client.months(profileId));
    if (months.stream().noneMatch(period -> period.month().equals(currentMonth))) {
      months.add(
          new AccountingRestClient.MonthRef(
              currentMonth,
              currentMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                  + " "
                  + currentMonth.getYear(),
              "OPEN",
              "Open"));
      months.sort(Comparator.comparing(AccountingRestClient.MonthRef::month));
    }
    YearMonth selected = month != null ? month : currentMonth;
    int selectedMonthIndex =
        java.util.stream.IntStream.range(0, months.size())
            .filter(index -> months.get(index).month().equals(selected))
            .findFirst()
            .orElse(-1);
    model.addAttribute(
        "previousMonth", selectedMonthIndex > 0 ? months.get(selectedMonthIndex - 1) : null);
    model.addAttribute(
        "nextMonth",
        selectedMonthIndex >= 0 && selectedMonthIndex + 1 < months.size()
            ? months.get(selectedMonthIndex + 1)
            : null);
    var overview = client.overview(profileId, selected);
    var stagingSummary = client.summary(profileId, selected);
    var stagingRows = client.rows(profileId, selected);

    boolean hasOperationalData =
        overview.summary().documents() > 0 || overview.summary().bankTransactions() > 0;
    boolean hasAcquiredData =
        hasOperationalData || overview.sources().evidenceCount() > 0 || !stagingRows.isEmpty();
    var documents =
        hasOperationalData
            ? client.documents(profileId, selected)
            : java.util.List.<AccountingRestClient.DocumentView>of();
    var reconciliation =
        hasOperationalData
            ? client.reconciliation(profileId, selected)
            : List.<ReconciliationView>of();
    var payments =
        hasOperationalData
            ? client.payments(profileId, selected)
            : java.util.List.<AccountingRestClient.PaymentView>of();
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
    model.addAttribute("profileId", profileId);
    model.addAttribute("months", months);
    model.addAttribute("selectedMonth", selected);
    model.addAttribute("overview", overview);
    model.addAttribute("stagingSummary", stagingSummary);
    model.addAttribute("stagingRows", stagingRows);
    model.addAttribute("hasAcquiredData", hasAcquiredData);
    model.addAttribute("hasOperationalData", hasOperationalData);
    model.addAttribute("hasReviewIssues", hasReviewIssues);
    model.addAttribute("documents", documents);
    model.addAttribute(
        "incomeDocuments",
        documents.stream().filter(d -> isIncomeDirection(d.direction())).toList());
    model.addAttribute(
        "costDocuments",
        documents.stream().filter(d -> !isIncomeDirection(d.direction())).toList());
    var paymentRows =
        reconciliation.stream()
            .filter(row -> "OBLIGATION_PAYMENT".equals(row.kind()))
            .filter(row -> Set.of("VAT", "RYCZALT", "ZUS").contains(row.reference()))
            .toList();
    model.addAttribute("payments", payments);
    model.addAttribute("paymentRows", paymentRows);
    addPaymentHeaderModel(model, paymentRows, "RYCZALT", "Ryczalt");
    addPaymentHeaderModel(model, paymentRows, "VAT", "Vat");
    addPaymentHeaderModel(model, paymentRows, "ZUS", "Zus");
    model.addAttribute(
        "incomeBankMatched",
        reconciliation.stream()
            .filter(row -> "INVOICE_PAYMENT".equals(row.kind()))
            .filter(row -> "MATCHED".equals(row.status()))
            .map(row -> matchedBankAmountPln(row, selected))
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    model.addAttribute(
        "costBankMatched",
        reconciliation.stream()
            .filter(row -> "EXPENSE_PAYMENT".equals(row.kind()))
            .filter(row -> "MATCHED".equals(row.status()))
            .map(row -> matchedBankAmountPln(row, selected))
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    BigDecimal paidAmount = paidAmount(paymentRows);
    BigDecimal toPayAmount = totalToPay(overview, paymentRows, selected);
    model.addAttribute("paidAmountDisplay", money(paidAmount, "PLN"));
    model.addAttribute("toPayAmountDisplay", money(toPayAmount, "PLN"));
    model.addAttribute("workspaceStatus", workspaceStatus);
    model.addAttribute("totalToPayDisplay", money(toPayAmount, "PLN"));
    model.addAttribute("ryczaltDisplay", money(overview.summary().ryczalt(), "PLN"));
    model.addAttribute("vatDisplay", money(overview.summary().vat(), "PLN"));
    model.addAttribute("zusDisplay", money(overview.summary().zus(), "PLN"));
    model.addAttribute("referenceRyczaltDisplay", money(overview.reference().ryczalt(), "PLN"));
    model.addAttribute("referenceVatDisplay", money(overview.reference().vatPayable(), "PLN"));
    model.addAttribute("referenceZusDisplay", money(overview.reference().zus(), "PLN"));
    model.addAttribute(
        "ryczaltDiffDisplay",
        diffLabel(overview.summary().ryczalt(), overview.reference().ryczalt()));
    model.addAttribute(
        "vatDiffDisplay", diffLabel(overview.summary().vat(), overview.reference().vatPayable()));
    model.addAttribute(
        "zusDiffDisplay", diffLabel(overview.summary().zus(), overview.reference().zus()));
    model.addAttribute("revenueDisplay", money(overview.summary().revenue(), "PLN"));
    var documentPresentations = new ArrayList<DocumentPresentation>();
    documents.stream()
        .map(d -> documentView(d, profileId, selected, overview.issues(), reconciliation))
        .forEach(documentPresentations::add);
    stagingRows.stream()
        .filter(row -> "INVOICE".equals(row.type()))
        .filter(row -> !row.promoted() && row.canonicalMatchId() == null)
        .map(row -> stagedDocumentView(profileId, selected, row))
        .forEach(documentPresentations::add);
    model.addAttribute(
        "incomeDocumentsView",
        documentPresentations.stream().filter(d -> isIncomeDirection(d.direction())).toList());
    model.addAttribute(
        "costDocumentsView",
        documentPresentations.stream().filter(d -> !isIncomeDirection(d.direction())).toList());
    model.addAttribute("filingStatusLabel", filingState(overview.filingSummary().lifecycle()));
    model.addAttribute(
        "jpkStatusLabel", artifactState(overview.filingSummary().jpkStatus(), "JPK"));
    model.addAttribute(
        "upoStatusLabel", artifactState(overview.filingSummary().upoStatus(), "UPO"));
    String filingState = filingState(overview.filingSummary().lifecycle());
    model.addAttribute(
        "submissionStatusLabel",
        "Filed".equals(filingState)
            ? "Submitted"
            : "Failed".equals(filingState)
                ? "Failed"
                : "Pending".equals(filingState) ? "Pending" : "Not submitted");
    model.addAttribute(
        "reviewIssues",
        overview.issues().stream()
            .filter(
                issue ->
                    issue.kind()
                        != com.smartbox.investory.accounting.api.AccountingUserApi.IssueKind.INFO)
            .map(AccountingPageController::issueView)
            .toList());
    model.addAttribute("sourcesStep", !hasAcquiredData ? "pending" : "complete");
    model.addAttribute(
        "reviewStep", hasReviewIssues ? "attention" : hasAcquiredData ? "complete" : "pending");
    model.addAttribute(
        "jpkStep", artifactDone(overview.filingSummary().jpkStatus()) ? "complete" : "pending");
    if ("Failed".equals(artifactState(overview.filingSummary().jpkStatus(), "JPK")))
      model.addAttribute("jpkStep", "attention");
    model.addAttribute(
        "fileStep",
        "Filed".equals(filingState(overview.filingSummary().lifecycle()))
            ? "complete"
            : "Failed".equals(filingState(overview.filingSummary().lifecycle()))
                ? "attention"
                : "pending");
    model.addAttribute(
        "payStep",
        !paymentRows.isEmpty()
                && paymentRows.stream().allMatch(row -> "MATCHED".equals(row.status()))
            ? "complete"
            : "pending");
    model.addAttribute(
        "sourcesStepClass", "accounting-workflow__step--" + model.getAttribute("sourcesStep"));
    model.addAttribute(
        "reviewStepClass", "accounting-workflow__step--" + model.getAttribute("reviewStep"));
    model.addAttribute(
        "jpkStepClass", "accounting-workflow__step--" + model.getAttribute("jpkStep"));
    model.addAttribute(
        "fileStepClass", "accounting-workflow__step--" + model.getAttribute("fileStep"));
    model.addAttribute(
        "payStepClass", "accounting-workflow__step--" + model.getAttribute("payStep"));
    model.addAttribute("canWrite", canWrite(request));
    return "accounting/accounting";
  }

  @GetMapping(BASE + "/counterparties")
  public String counterparties(
      @PathVariable("profileId") long profileId,
      Model model,
      jakarta.servlet.http.HttpServletRequest request) {
    model.addAttribute("profileId", profileId);
    model.addAttribute("counterparties", client.counterparties(profileId));
    model.addAttribute("canWrite", canWrite(request));
    return "accounting/counterparties";
  }

  @GetMapping(BASE + "/counterparties/{counterpartyId}")
  public String counterparty(
      @PathVariable("profileId") long profileId,
      @PathVariable long counterpartyId,
      Model model,
      jakarta.servlet.http.HttpServletRequest request) {
    var counterparty =
        client.counterparties(profileId).stream()
            .filter(item -> item.id() == counterpartyId)
            .findFirst()
            .orElseThrow(
                () ->
                    new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND));
    model.addAttribute("profileId", profileId);
    model.addAttribute("counterparty", counterparty);
    var invoices =
        client.counterpartyDocuments(profileId, counterpartyId).stream()
            .map(
                item ->
                    documentView(item.document(), profileId, item.month(), List.of(), List.of()))
            .toList();
    model.addAttribute("counterpartyInvoices", invoices);
    model.addAttribute("canWrite", canWrite(request));
    return "accounting/counterparty";
  }

  private static boolean belongsTo(
      AccountingRestClient.DocumentView document,
      com.smartbox.investory.accounting.api.AccountingUserApi.CounterpartyView counterparty) {
    if (document.counterpartyTaxIdentifier() != null
        && counterparty.taxIdentifier() != null
        && normalizeIdentifier(document.counterpartyTaxIdentifier())
            .equals(normalizeIdentifier(counterparty.taxIdentifier()))
        && equalText(document.counterpartyCountry(), counterparty.country())) return true;
    return equalText(document.counterparty(), counterparty.name())
        || equalText(document.counterparty(), counterparty.displayName());
  }

  private static String normalizeIdentifier(String value) {
    return value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
  }

  private static boolean equalText(String left, String right) {
    return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
  }

  @PostMapping(BASE + "/counterparties/{counterpartyId}/alias")
  public String updateCounterpartyAlias(
      @PathVariable("profileId") long profileId,
      @PathVariable long counterpartyId,
      @RequestParam(required = false) String alias,
      RedirectAttributes redirect) {
    try {
      client.updateCounterpartyAlias(profileId, counterpartyId, alias);
      redirect.addFlashAttribute("accountingMessage", "Counterparty alias saved.");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("accountingError", safeMessage(exception));
    }
    return "redirect:/profiles/" + profileId + "/accounting/counterparties/" + counterpartyId;
  }

  private void addPaymentHeaderModel(
      Model model, List<ReconciliationView> paymentRows, String reference, String key) {
    var payment = paymentRows.stream().filter(row -> reference.equals(row.reference())).findFirst();
    model.addAttribute(
        "bank" + key + "Display",
        payment.map(row -> money(row.matchedAmount(), "PLN")).orElse("—"));
    model.addAttribute(
        "bank" + key + "Status",
        payment.map(AccountingPageController::paymentStatusLabel).orElse("Unpaid"));
    model.addAttribute(
        "bank" + key + "StatusClass",
        payment.map(AccountingPageController::paymentStatusClass).orElse("is-unpaid"));
  }

  private BigDecimal matchedBankAmountPln(ReconciliationView row, YearMonth selected) {
    if (row.matchedAmount() == null || row.matchedAmount().signum() == 0) return BigDecimal.ZERO;
    if (currencyConversion == null
        || row.currency() == null
        || "PLN".equalsIgnoreCase(row.currency())) return row.matchedAmount();
    LocalDate rateDate = row.paymentDate() != null ? row.paymentDate() : selected.atDay(1);
    return currencyConversion.convertToBaseCurrency(
        row.matchedAmount(), CurrencyType.PLN, CurrencyType.valueOf(row.currency()), rateDate);
  }

  private BigDecimal totalToPay(
      AccountingRestClient.MonthOverview overview,
      List<ReconciliationView> paymentRows,
      YearMonth selected) {
    if (paymentRows.isEmpty())
      return overview
          .summary()
          .vat()
          .add(overview.summary().ryczalt())
          .add(overview.summary().zus());
    return paymentRows.stream()
        .map(
            row ->
                row.expectedAmount()
                    .subtract(row.matchedAmount() == null ? BigDecimal.ZERO : row.matchedAmount())
                    .max(BigDecimal.ZERO))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static BigDecimal paidAmount(List<ReconciliationView> paymentRows) {
    return paymentRows.stream()
        .map(row -> row.matchedAmount() == null ? BigDecimal.ZERO : row.matchedAmount())
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static String paymentStatusLabel(ReconciliationView row) {
    return switch (row.status()) {
      case "MATCHED" -> "✓ Paid";
      case "DIFF" -> "⚠ Difference";
      default -> "○ Unpaid";
    };
  }

  private static String paymentStatusClass(ReconciliationView row) {
    return switch (row.status()) {
      case "MATCHED" -> "is-paid";
      case "DIFF" -> "is-diff";
      default -> "is-unpaid";
    };
  }

  private static DocumentPresentation documentView(
      AccountingRestClient.DocumentView document,
      long profileId,
      YearMonth month,
      List<IssueView> issues,
      List<ReconciliationView> reconciliation) {
    boolean income = isIncomeDirection(document.direction());
    boolean review = hasDocumentReview(document.reference(), document.sourceReference(), issues);
    String issueSummary = documentIssueSummary(document, review, issues);
    String status =
        income
            ? review
                ? "To review"
                : isPaid(document.reference(), reconciliation) ? "Paid" : "Issued"
            : review || document.category() == null || document.category().isBlank()
                ? "To review"
                : "Approved";
    return new DocumentPresentation(
        document.id(),
        document.reference(),
        document.date() == null
            ? "—"
            : document.date().format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)),
        wholeMoney(document.grossAmount(), document.currency()),
        status,
        issueSummary,
        document.direction(),
        document.sourceReference(),
        safeReference(document.sourceReference()),
        review && document.sourceReference() != null,
        document.counterparty() == null || document.counterparty().isBlank()
            ? "—"
            : document.counterparty(),
        category(document.category()),
        formatDate(document.saleDate()),
        document.counterpartyTaxIdentifier(),
        document.counterpartyCountry(),
        acquisitionSource(document.acquisitionSource()),
        document.sourceName(),
        "/profiles/" + profileId + "/accounting/documents/" + document.id() + "?month=" + month);
  }

  private static DocumentPresentation stagedDocumentView(
      long profileId,
      YearMonth month,
      com.smartbox.investory.accounting.api.AccountingStagingApi.Row row) {
    boolean income =
        !"EXPENSE".equalsIgnoreCase(row.documentKind())
            && !"PURCHASE_INVOICE".equalsIgnoreCase(row.documentKind())
            && !"RECEIPT".equalsIgnoreCase(row.documentKind());
    String label = "To review";
    String issueSummary = stagedIssueSummary(row);
    String href =
        row.source() != null
            ? "/profiles/"
                + profileId
                + "/accounting/documents/review?month="
                + month
                + "&sourceReference="
                + java.net.URLEncoder.encode(row.source(), java.nio.charset.StandardCharsets.UTF_8)
            : null;
    return new DocumentPresentation(
        row.id(),
        row.reference(),
        formatDate(row.documentDate()),
        wholeMoney(row.amount(), row.currency()),
        label,
        issueSummary,
        income ? "SALES" : "PURCHASE",
        row.source(),
        safeReference(row.source()),
        href != null,
        row.counterparty() == null || row.counterparty().isBlank() ? "—" : row.counterparty(),
        category(row.category()),
        "—",
        row.counterpartyTaxIdentifier(),
        row.counterpartyCountry(),
        acquisitionSource(row.sourceType()),
        null,
        href);
  }

  private static String category(String value) {
    if (value == null || value.isBlank()) return "—";
    return switch (value.toUpperCase(Locale.ROOT)) {
      case "VEHICLE_FUEL" -> "Fuel";
      case "PRODUCT" -> "Products";
      case "ACCOUNTING_SERVICE" -> "Accounting";
      case "BUSINESS_SERVICE", "SERVICE" -> "Services";
      case "EQUIPMENT" -> "Equipment";
      case "OTHER" -> "Other";
      default -> "—";
    };
  }

  private static String documentIssueSummary(
      AccountingRestClient.DocumentView document, boolean review, List<IssueView> issues) {
    var reasons = new ArrayList<String>();
    boolean purchase = !isIncomeDirection(document.direction());
    if (purchase && (document.category() == null || document.category().isBlank())) {
      reasons.add("Category required");
    }
    if (issues != null) {
      issues.stream()
          .filter(
              issue ->
                  issue.sourceReference() != null
                      && (issue.sourceReference().equals(document.reference())
                          || issue.sourceReference().equals(document.sourceReference())))
          .map(AccountingPageController::issueReason)
          .filter(reason -> !reason.isBlank())
          .forEach(reasons::add);
    }
    if (review && reasons.isEmpty()) reasons.add("Review required");
    return String.join(" · ", reasons.stream().distinct().toList());
  }

  private static String stagedIssueSummary(AccountingStagingApi.Row row) {
    if (row.reasonCodes() == null || row.reasonCodes().isEmpty()) {
      return row.category() == null || row.category().isBlank()
          ? "Category required"
          : "Review required";
    }
    return row.reasonCodes().stream()
        .map(AccountingPageController::reasonLabel)
        .distinct()
        .collect(java.util.stream.Collectors.joining(" · "));
  }

  private static String issueReason(IssueView issue) {
    return reasonLabel(issue.code());
  }

  private static String reasonLabel(String code) {
    if (code == null) return "Review required";
    String normalized = code.toUpperCase(Locale.ROOT);
    if (normalized.startsWith("MISSING_VAT_CLASSIFICATION")) return "VAT treatment required";
    if (normalized.startsWith("MISSING_EXPLICIT_VAT_RATE")) return "VAT rate uncertain";
    if (normalized.startsWith("MISSING_JPK_EVIDENCE_CLASSIFICATION"))
      return "JPK evidence required";
    if (normalized.startsWith("MISSING_COUNTERPARTY_IDENTIFIER")) return "Counterparty ID missing";
    if (normalized.startsWith("SOURCE_REVIEW_REQUIRED")) return "Source review required";
    if (normalized.startsWith("UNSUPPORTED_VAT_RATE")) return "VAT rate unsupported";
    if (normalized.startsWith("CATEGORY")) return "Category required";
    return "Review required";
  }

  private static boolean hasDocumentReview(
      String reference, String sourceReference, List<IssueView> issues) {
    if (issues == null) return false;
    return issues.stream()
        .filter(
            issue ->
                issue.kind()
                    == com.smartbox.investory.accounting.api.AccountingUserApi.IssueKind
                        .NEEDS_ANSWER)
        .map(IssueView::sourceReference)
        .anyMatch(
            value -> value != null && (value.equals(reference) || value.equals(sourceReference)));
  }

  private static boolean isPaid(String reference, List<ReconciliationView> rows) {
    return rows != null
        && rows.stream()
            .anyMatch(
                row ->
                    "INVOICE_PAYMENT".equals(row.kind())
                        && "MATCHED".equals(row.status())
                        && reference.equalsIgnoreCase(row.reference()));
  }

  private static String acquisitionSource(String source) {
    if (source == null || source.isBlank()) return null;
    return switch (source.toUpperCase(Locale.ROOT)) {
      case "KSEF" -> "KSeF";
      case "UPLOAD", "FILE", "FILE_IMPORT" -> "File import";
      case "MANUAL" -> "Manual";
      case "BANK" -> "Bank";
      default -> source;
    };
  }

  private static String wholeMoney(BigDecimal value, String currency) {
    if (value == null) return "—";
    String code =
        currency == null || currency.isBlank() ? "PLN" : currency.toUpperCase(Locale.ROOT);
    return FinancialPresentation.moneyWhole(value, code, Locale.forLanguageTag("pl-PL"));
  }

  private static String money(BigDecimal value, String currency) {
    if (value == null) return "—";
    NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("pl-PL"));
    format.setMinimumFractionDigits(0);
    format.setMaximumFractionDigits(2);
    String amount = format.format(value);
    return currency == null || "PLN".equalsIgnoreCase(currency)
        ? amount + " zł"
        : amount + " " + currency;
  }

  private static String diffLabel(BigDecimal calculated, BigDecimal reference) {
    if (calculated == null || reference == null) return "Diff vs reference: —";
    BigDecimal diff = calculated.subtract(reference);
    String sign = diff.signum() > 0 ? "+" : "";
    return "Diff vs reference: " + sign + money(diff, "PLN");
  }

  private static String formatDate(java.time.LocalDate date) {
    return date == null ? "—" : date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH));
  }

  private static boolean isIncomeDirection(String direction) {
    return "SALE".equalsIgnoreCase(direction) || "SALES".equalsIgnoreCase(direction);
  }

  private static String safeReference(String reference) {
    return reference != null && reference.matches("(?i)[a-f0-9]{64}")
        ? "Source document"
        : reference;
  }

  private static String filingState(String value) {
    if (value == null) return "Not started";
    return switch (value.toUpperCase(Locale.ROOT)) {
      case "OPEN" -> "Open";
      case "FILED", "PAID", "SETTLED", "LOCKED", "CLOSED" -> "Filed";
      case "CONFIRMED", "READY_FOR_REVIEW" -> "Ready to file";
      case "SOURCES_INCOMPLETE" -> "Sources incomplete";
      case "ISSUES" -> "Review needed";
      case "FAILED" -> "Failed";
      case "PENDING", "SUBMITTING" -> "Pending";
      default -> "In progress";
    };
  }

  private static String artifactState(String value, String name) {
    if (value == null
        || value.isBlank()
        || "MISSING".equalsIgnoreCase(value)
        || "NOT_GENERATED".equalsIgnoreCase(value)) return name + " not generated";
    return switch (value.toUpperCase(Locale.ROOT)) {
      case "PENDING", "SUBMITTING" -> "Pending";
      case "FAILED", "ERROR", "INVALID", "REJECTED" -> "Failed";
      case "COMPLETED",
          "GENERATED",
          "VALID",
          "SUBMITTED",
          "RECEIVED",
          "AVAILABLE",
          "ACCEPTED",
          "POSTED" ->
          "Available";
      default -> "Status unavailable";
    };
  }

  private static boolean artifactDone(String value) {
    return value != null
        && ("GENERATED".equalsIgnoreCase(value)
            || "VALID".equalsIgnoreCase(value)
            || "SUBMITTED".equalsIgnoreCase(value)
            || "COMPLETED".equalsIgnoreCase(value)
            || "AVAILABLE".equalsIgnoreCase(value));
  }

  record DocumentPresentation(
      long id,
      String reference,
      String date,
      String amount,
      String status,
      String issueSummary,
      String direction,
      String sourceReference,
      String sourceReferenceDisplay,
      boolean canReview,
      String counterparty,
      String category,
      String saleDate,
      String counterpartyTaxIdentifier,
      String counterpartyCountry,
      String acquisitionSource,
      String sourceName,
      String href) {}

  private static IssuePresentation issueView(IssueView issue) {
    String title =
        switch (issue.code() == null ? "" : issue.code().toUpperCase(Locale.ROOT)) {
          case "MISSING_JPK_EVIDENCE_CLASSIFICATION" -> "JPK evidence required";
          case "SOURCE_REVIEW_REQUIRED" -> "Tax treatment needs review";
          case "SOURCE_PARSED" -> "Document ready for review";
          default ->
              issue.title() == null ? "Accounting review needed" : issue.title().replace('_', ' ');
        };
    String message = issue.message();
    if ("MISSING_JPK_EVIDENCE_CLASSIFICATION".equalsIgnoreCase(issue.code()))
      message = "Choose the JPK evidence type for this document.";
    if ("SOURCE_REVIEW_REQUIRED".equalsIgnoreCase(issue.code()))
      message = "Confirm the tax treatment and document details.";
    String referenceLabel = issue.sourceReference();
    if (referenceLabel != null && referenceLabel.matches("(?i)[a-f0-9]{64}"))
      referenceLabel = "Source document";
    return new IssuePresentation(
        title,
        message,
        referenceLabel,
        issue.sourceReference(),
        issue.kind()
                == com.smartbox.investory.accounting.api.AccountingUserApi.IssueKind.NEEDS_ANSWER
            && issue.sourceReference() != null);
  }

  record IssuePresentation(
      String title,
      String message,
      String sourceLabel,
      String sourceReference,
      boolean canReview) {}

  @PostMapping(BASE + "/actions/confirm")
  public String confirm(
      @PathVariable("profileId") long profileId, YearMonth month, RedirectAttributes redirect) {
    return action("confirm", profileId, month, redirect, () -> client.confirm(profileId, month));
  }

  @PostMapping(BASE + "/actions/file")
  public String file(
      @PathVariable("profileId") long profileId, YearMonth month, RedirectAttributes redirect) {
    return action("file", profileId, month, redirect, () -> client.file(profileId, month));
  }

  @PostMapping(BASE + "/actions/settle")
  public String settle(
      @PathVariable("profileId") long profileId, YearMonth month, RedirectAttributes redirect) {
    return action("settle", profileId, month, redirect, () -> client.settle(profileId, month));
  }

  @PostMapping(BASE + "/actions/lock")
  public String lock(
      @PathVariable("profileId") long profileId, YearMonth month, RedirectAttributes redirect) {
    return action("lock", profileId, month, redirect, () -> client.lock(profileId, month));
  }

  @PostMapping(BASE + "/actions/reopen")
  public String reopen(
      @PathVariable("profileId") long profileId,
      YearMonth month,
      @RequestParam String reason,
      RedirectAttributes redirect) {
    if (reason == null || reason.isBlank()) {
      redirect.addFlashAttribute("accountingError", "Cannot reopen this month without a reason.");
    } else {
      return action(
          "reopen", profileId, month, redirect, () -> client.reopen(profileId, month, reason));
    }
    return redirect(profileId, month);
  }

  @PostMapping(BASE + "/documents/recognize")
  public String recognize(
      @PathVariable("profileId") long profileId,
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

  @GetMapping(BASE + "/documents/review")
  public String reviewSource(
      @PathVariable("profileId") long profileId,
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

  @GetMapping(BASE + "/documents/{documentId}")
  public String documentDetails(
      @PathVariable("profileId") long profileId,
      @PathVariable long documentId,
      @RequestParam YearMonth month,
      Model model,
      jakarta.servlet.http.HttpServletRequest request) {
    var document =
        client.documents(profileId, month).stream()
            .filter(candidate -> candidate.id() == documentId)
            .findFirst()
            .orElseThrow(
                () ->
                    new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND));
    var overview = client.overview(profileId, month);
    var reconciliation = client.reconciliation(profileId, month);
    model.addAttribute("profileId", profileId);
    model.addAttribute("selectedMonth", month);
    model.addAttribute(
        "document", documentView(document, profileId, month, overview.issues(), reconciliation));
    model.addAttribute("canWrite", canWrite(request));
    return "accounting/document";
  }

  @PostMapping(BASE + "/documents/save")
  public String saveReviewed(
      @PathVariable("profileId") long profileId,
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

  @PostMapping(BASE + "/bank/import")
  public String importBank(
      @PathVariable("profileId") long profileId,
      YearMonth month,
      MultipartFile file,
      RedirectAttributes redirect) {
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

  @PostMapping(BASE + "/ksef/sync")
  public String syncKsef(
      @PathVariable("profileId") long profileId, YearMonth month, RedirectAttributes redirect) {
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

  @PostMapping(BASE + "/ksef/sync-seller")
  public String syncKsefSeller(
      @PathVariable("profileId") long profileId, YearMonth month, RedirectAttributes redirect) {
    try {
      var result = client.syncKsefSeller(profileId, month);
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

  @PostMapping(BASE + "/ksef/sync-third-party")
  public String syncKsefThirdParty(
      @PathVariable("profileId") long profileId, YearMonth month, RedirectAttributes redirect) {
    try {
      var result = client.syncKsefThirdParty(profileId, month);
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

  @PostMapping(BASE + "/staging/reconcile")
  public String reconcile(
      @PathVariable("profileId") long profileId, YearMonth month, RedirectAttributes redirect) {
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

  @PostMapping(BASE + "/staging/promote")
  public String promote(
      @PathVariable("profileId") long profileId, YearMonth month, RedirectAttributes redirect) {
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

  @PostMapping(BASE + "/filings/jpk/generate")
  public String generateJpk(
      @PathVariable("profileId") long profileId, YearMonth month, RedirectAttributes redirect) {
    try {
      client.generateJpk(profileId, month);
      redirect.addFlashAttribute("accountingMessage", "JPK_V7M(3) generated and validated.");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("accountingError", safeMessage(exception));
    }
    return redirect(profileId, month);
  }

  @GetMapping(BASE + "/filings/jpk")
  public org.springframework.http.ResponseEntity<byte[]> downloadJpk(
      @PathVariable("profileId") long profileId, YearMonth month) {
    return org.springframework.http.ResponseEntity.ok()
        .contentType(org.springframework.http.MediaType.APPLICATION_XML)
        .header(
            org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"JPK_V7M_" + month + ".xml\"")
        .body(client.downloadJpk(profileId, month));
  }

  @PostMapping(BASE + "/filings/confirmations")
  public String recordUpo(
      @PathVariable("profileId") long profileId,
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
    String location = "redirect:/profiles/" + profileId + "/accounting";
    return month == null ? location : location + "?month=" + month;
  }

  private boolean canWrite(jakarta.servlet.http.HttpServletRequest request) {
    return request.isUserInRole("ADMIN") || request.isUserInRole("PROFILE_OWNER");
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
