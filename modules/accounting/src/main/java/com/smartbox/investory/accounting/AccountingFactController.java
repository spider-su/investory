package com.smartbox.investory.accounting;

import com.smartbox.investory.accounting.AccountingInvoiceRecognitionService.RecognizedInvoice;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AccountingFactController {
  private final AccountingFactService service;
  private final AccountingInvoiceRecognitionService invoiceRecognitionService;
  private final AccountingInvoiceIngestionService invoiceIngestionService;
  private final AccountingSourceEvidenceService sourceEvidenceService;
  private final AccountingJdgExporter exporter;
  private final AccountingFilingService filingService;

  public AccountingFactController(
      AccountingFactService service,
      AccountingInvoiceRecognitionService recognition,
      AccountingInvoiceIngestionService ingestion,
      AccountingJdgExporter exporter) {
    this(service, recognition, ingestion, exporter, null, null);
  }

  public AccountingFactController(
      AccountingFactService service,
      AccountingInvoiceRecognitionService recognition,
      AccountingInvoiceIngestionService ingestion,
      AccountingJdgExporter exporter,
      AccountingSourceEvidenceService sourceEvidenceService) {
    this(service, recognition, ingestion, exporter, sourceEvidenceService, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public AccountingFactController(
      AccountingFactService service,
      AccountingInvoiceRecognitionService recognition,
      AccountingInvoiceIngestionService ingestion,
      AccountingJdgExporter exporter,
      AccountingSourceEvidenceService sourceEvidenceService,
      AccountingFilingService filingService) {
    this.service = service;
    this.invoiceRecognitionService = recognition;
    this.invoiceIngestionService = ingestion;
    this.sourceEvidenceService = sourceEvidenceService;
    this.exporter = exporter;
    this.filingService = filingService;
  }

  @GetMapping("/poc/accounting")
  public String facts(@RequestParam(required = false) String month, Model model) {
    populateModel(month, model);
    return "poc/accounting-facts";
  }

  @PostMapping("/poc/accounting/profile")
  public String updateProfile(
      @RequestParam String month,
      @RequestParam(defaultValue = "false") boolean hasUop,
      RedirectAttributes redirectAttributes) {
    LocalDate selected = parseMonth(month);
    service.updateHasUop(hasUop);
    redirectAttributes.addFlashAttribute(
        "accountingProfileMessage",
        hasUop
            ? "UoP enabled. JDG social ZUS is not charged; health ZUS remains applicable."
            : "UoP disabled. Normal JDG social and health ZUS apply.");
    return "redirect:/poc/accounting?month=" + formatMonth(selected);
  }

  @GetMapping("/poc/accounting/export")
  public ResponseEntity<byte[]> exportJdg() {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=jdg-accounting-2026.csv")
        .contentType(MediaType.parseMediaType("text/csv"))
        .body(exporter.exportCsv());
  }

  @GetMapping("/poc/accounting/jpk")
  public ResponseEntity<byte[]> exportJpk(@RequestParam String month) {
    byte[] xml = filingService.jpk(parseMonth(month));
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=jpk-v7m-" + month + ".xml")
        .contentType(MediaType.APPLICATION_XML)
        .body(xml);
  }

  @PostMapping("/poc/accounting/confirm")
  public String confirm(@RequestParam String month, RedirectAttributes redirectAttributes) {
    try {
      filingService.confirm(parseMonth(month));
      redirectAttributes.addFlashAttribute("filingMessage", "Month confirmed for filing output.");
    } catch (RuntimeException exception) {
      redirectAttributes.addFlashAttribute("filingError", exception.getMessage());
    }
    return "redirect:/poc/accounting?month=" + month;
  }

  @PostMapping("/poc/accounting/invoice/recognize")
  public String recognizeInvoice(
      @RequestParam String month, @RequestParam("invoice") MultipartFile invoice, Model model) {
    LocalDate selected = populateModel(month, model);
    AccountingInvoiceForm form = new AccountingInvoiceForm();
    form.setMonth(formatMonth(selected));
    long sourceId = 0;
    try {
      sourceId =
          sourceEvidenceService == null
              ? 0
              : sourceEvidenceService.receiveUpload(
                  invoice.getOriginalFilename(), invoice.getContentType(), invoice.getBytes());
      RecognizedInvoice recognized =
          invoiceRecognitionService.recognize(
              invoice.getOriginalFilename(), invoice.getContentType(), invoice.getBytes());
      copyRecognized(recognized, form);
      form.setSourceIdentity(Long.toString(sourceId));
      if (sourceId != 0)
        sourceEvidenceService.status(sourceId, AccountingSourceStatus.PARSED, null);
      model.addAttribute("invoiceDraft", form);
      model.addAttribute(
          "recognitionMessage",
          "Invoice fields and document direction were extracted. Review the type, counterparty and dates before saving.");
    } catch (IOException | RuntimeException exception) {
      if (sourceId != 0) {
        sourceEvidenceService.status(
            sourceId, AccountingSourceStatus.FAILED, exception.getMessage());
      }
      model.addAttribute("invoiceDraft", form);
      model.addAttribute("recognitionError", exception.getMessage());
    }
    return "poc/accounting-facts";
  }

  @PostMapping("/poc/accounting/invoice")
  public String saveInvoice(
      @ModelAttribute AccountingInvoiceForm invoiceDraft, RedirectAttributes redirectAttributes) {
    String month = invoiceDraft.getMonth();
    try {
      LocalDate taxPeriod = parseMonth(month);
      String documentType = invoiceDraft.getDocumentType().trim().toUpperCase();
      boolean inserted =
          invoiceIngestionService.ingest(
              new AccountingInvoiceIngestionService.ReviewedInvoice(
                  taxPeriod,
                  documentType,
                  invoiceDraft.getIssueDate(),
                  invoiceDraft.getSaleDate(),
                  invoiceDraft.getReference(),
                  invoiceDraft.getCounterpartyAlias(),
                  invoiceDraft.getCategory(),
                  invoiceDraft.getCurrency(),
                  invoiceDraft.getNetAmount(),
                  invoiceDraft.getVatAmount(),
                  invoiceDraft.getGrossAmount(),
                  invoiceDraft.getVatDeductionRatio(),
                  "AI_EXTRACTED_REVIEWED",
                  buildReviewedNote(invoiceDraft),
                  invoiceDraft.getSourceIdentity(),
                  invoiceDraft.getCounterpartyTaxIdentifier(),
                  invoiceDraft.getCounterpartyCountry(),
                  invoiceDraft.getKsefNumber(),
                  invoiceDraft.getFilingEvidence() == null
                      ? null
                      : new AccountingFilingEvidence(
                          invoiceDraft.getFilingEvidence(), invoiceDraft.getKsefNumber())));
      if (invoiceDraft.getSourceIdentity() != null && !invoiceDraft.getSourceIdentity().isBlank()) {
        sourceEvidenceService.status(
            Long.parseLong(invoiceDraft.getSourceIdentity()),
            inserted ? AccountingSourceStatus.IMPORTED : AccountingSourceStatus.IMPORTED,
            null);
      }
      redirectAttributes.addFlashAttribute(
          "invoiceSaved",
          inserted
              ? "SALES_INVOICE".equals(documentType)
                  ? "Sales invoice saved."
                  : "Purchase invoice saved."
              : "Invoice was already imported; no duplicate was created.");
    } catch (RuntimeException exception) {
      redirectAttributes.addFlashAttribute("invoiceSaveError", exception.getMessage());
    }
    return "redirect:/poc/accounting?month=" + month;
  }

  private LocalDate populateModel(String month, Model model) {
    List<LocalDate> periods = service.availablePeriods();
    LocalDate selected = resolveSelectedPeriod(month, periods);
    int selectedIndex = periods.indexOf(selected);

    model.addAttribute("periods", periods);
    model.addAttribute("selectedPeriod", selected);
    model.addAttribute("previousPeriod", selectedIndex > 0 ? periods.get(selectedIndex - 1) : null);
    model.addAttribute(
        "nextPeriod",
        selectedIndex >= 0 && selectedIndex < periods.size() - 1
            ? periods.get(selectedIndex + 1)
            : null);
    AccountingMonthSnapshot snapshot = service.snapshot(selected);
    model.addAttribute("hasUop", snapshot.zus().hasUop());
    model.addAttribute("snapshot", snapshot);
    model.addAttribute("facts", service.facts());
    if (sourceEvidenceService != null) {
      model.addAttribute("sourceOutcomes", sourceEvidenceService.outcomes(selected));
      model.addAttribute("bankSourceOutcomes", sourceEvidenceService.bankOutcomes(selected));
    }
    model.addAttribute("bankProcessedRows", snapshot.bankTransactions().size());
    model.addAttribute(
        "bankReviewRows",
        snapshot.bankTransactions().stream()
            .filter(transaction -> "UNKNOWN".equals(transaction.transactionType()))
            .count());
    if (filingService != null) {
      AccountingFilingService.FilingResult filing = filingService.filing(selected);
      model.addAttribute("filing", filing);
      try {
        model.addAttribute("paymentInstructions", filingService.paymentInstructions(selected));
      } catch (RuntimeException exception) {
        model.addAttribute("paymentInstructionError", exception.getMessage());
      }
    }
    return selected;
  }

  private void copyRecognized(RecognizedInvoice recognized, AccountingInvoiceForm form) {
    form.setDocumentType(recognized.documentType());
    form.setIssueDate(recognized.issueDate());
    form.setSaleDate(recognized.saleDate());
    form.setDueDate(recognized.dueDate());
    form.setReference(recognized.reference());
    form.setCounterpartyAlias(counterparty(recognized));
    form.setCounterpartyTaxIdentifier(
        "SALES_INVOICE".equals(recognized.documentType())
            ? recognized.buyerNip()
            : recognized.sellerNip());
    form.setCategory(recognized.category());
    form.setCurrency(recognized.currency());
    form.setNetAmount(recognized.netAmount());
    form.setVatAmount(recognized.vatAmount());
    form.setGrossAmount(recognized.grossAmount());
    form.setVatDeductionRatio(
        "SALES_INVOICE".equals(recognized.documentType())
            ? BigDecimal.ZERO
            : defaultDeductionRatio(recognized.category()));
    form.setNote(recognized.note());
  }

  private String counterparty(RecognizedInvoice recognized) {
    if ("SALES_INVOICE".equals(recognized.documentType())) {
      return firstNonBlank(recognized.buyer(), recognized.seller());
    }
    return firstNonBlank(recognized.seller(), recognized.buyer());
  }

  private BigDecimal defaultDeductionRatio(String category) {
    return "VEHICLE_FUEL".equals(category) ? new BigDecimal("0.50") : BigDecimal.ONE;
  }

  private String buildReviewedNote(AccountingInvoiceForm form) {
    StringBuilder note = new StringBuilder();
    if (form.getNote() != null && !form.getNote().isBlank()) {
      note.append(form.getNote().trim()).append(' ');
    }
    if (form.getDueDate() != null) {
      note.append("Due date ").append(form.getDueDate()).append(". ");
    }
    note.append("Uploaded document recognized by AI and reviewed before persistence.");
    if (form.getSourceIdentity() != null && !form.getSourceIdentity().isBlank()) {
      note.append(" Source evidence ID ").append(form.getSourceIdentity()).append('.');
    }
    return note.toString();
  }

  private String firstNonBlank(String first, String second) {
    return first != null && !first.isBlank() ? first : second;
  }

  private LocalDate parseMonth(String month) {
    if (month == null || !month.matches("\\d{4}-\\d{2}")) {
      throw new IllegalArgumentException("Valid accounting month is required");
    }
    return LocalDate.parse(month + "-01");
  }

  private String formatMonth(LocalDate period) {
    return "%04d-%02d".formatted(period.getYear(), period.getMonthValue());
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
