package com.smartbox.investory.accounting;

import com.smartbox.investory.accounting.AccountingExpenseNormalizer.ExpenseImportCandidate;
import com.smartbox.investory.accounting.AccountingExpenseNormalizer.NormalizedExpense;
import com.smartbox.investory.accounting.AccountingInvoiceRecognitionService.RecognizedInvoice;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AccountingFactController {
  private static final BigDecimal RYCZALT_RATE = new BigDecimal("0.12");

  private final AccountingFactService service;
  private final AccountingInvoiceRecognitionService invoiceRecognitionService;
  private final AccountingExpenseNormalizer expenseNormalizer;
  private final AccountingPocRepository repository;

  @GetMapping("/poc/accounting")
  public String facts(@RequestParam(required = false) String month, Model model) {
    populateModel(month, model);
    return "poc/accounting-facts";
  }

  @PostMapping("/poc/accounting/invoice/recognize")
  public String recognizeInvoice(
      @RequestParam String month, @RequestParam("invoice") MultipartFile invoice, Model model) {
    LocalDate selected = populateModel(month, model);
    AccountingInvoiceForm form = new AccountingInvoiceForm();
    form.setMonth(formatMonth(selected));
    try {
      RecognizedInvoice recognized =
          invoiceRecognitionService.recognize(
              invoice.getOriginalFilename(), invoice.getContentType(), invoice.getBytes());
      copyRecognized(recognized, form);
      model.addAttribute("invoiceDraft", form);
      model.addAttribute(
          "recognitionMessage",
          "Invoice fields and document direction were extracted. Review the type, counterparty and dates before saving.");
    } catch (IOException | RuntimeException exception) {
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
      validateRequired(invoiceDraft);
      String documentType = invoiceDraft.getDocumentType().trim().toUpperCase();
      switch (documentType) {
        case "PURCHASE_INVOICE", "RECEIPT" -> savePurchase(taxPeriod, invoiceDraft);
        case "SALES_INVOICE" -> saveSales(taxPeriod, invoiceDraft);
        case "CREDIT_NOTE" ->
            throw new IllegalArgumentException(
                "Credit-note persistence is intentionally parked; review the document without saving it yet.");
        default ->
            throw new IllegalArgumentException(
                "Choose whether this is a sales invoice or purchase invoice before saving.");
      }
      redirectAttributes.addFlashAttribute(
          "invoiceSaved",
          "SALES_INVOICE".equals(documentType)
              ? "Sales invoice saved."
              : "Purchase invoice saved.");
    } catch (RuntimeException exception) {
      redirectAttributes.addFlashAttribute("invoiceSaveError", exception.getMessage());
    }
    return "redirect:/poc/accounting?month=" + month;
  }

  private void savePurchase(LocalDate taxPeriod, AccountingInvoiceForm form) {
    BigDecimal deductionRatio =
        form.getVatDeductionRatio() == null
            ? defaultDeductionRatio(form.getCategory())
            : form.getVatDeductionRatio();
    NormalizedExpense normalized =
        expenseNormalizer.normalize(
            new ExpenseImportCandidate(
                form.getCategory(),
                form.getGrossAmount(),
                form.getNetAmount(),
                form.getVatAmount(),
                deductionRatio));

    repository.insertExpense(
        taxPeriod,
        firstNonNull(form.getIssueDate(), form.getSaleDate()),
        form.getReference().trim(),
        form.getCounterpartyAlias().trim(),
        form.getCategory().trim(),
        form.getCurrency().trim().toUpperCase(),
        normalized.netAmount(),
        normalized.vatAmount(),
        normalized.grossAmount(),
        normalized.vatDeductionRatio(),
        "AI_EXTRACTED_REVIEWED",
        buildReviewedNote(form));
  }

  private void saveSales(LocalDate taxPeriod, AccountingInvoiceForm form) {
    String currency = form.getCurrency().trim().toUpperCase();
    String invoiceKind = "PLN".equals(currency) ? "DOMESTIC_SERVICE" : "EU_SERVICE";
    BigDecimal bookedNetPln = "PLN".equals(currency) ? form.getNetAmount() : null;

    repository.insertSalesInvoice(
        taxPeriod,
        form.getIssueDate(),
        form.getSaleDate(),
        form.getReference().trim(),
        form.getCounterpartyAlias().trim(),
        invoiceKind,
        currency,
        form.getNetAmount(),
        form.getVatAmount(),
        form.getGrossAmount(),
        bookedNetPln,
        RYCZALT_RATE,
        buildReviewedNote(form));
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
    model.addAttribute("snapshot", service.snapshot(selected));
    model.addAttribute("facts", service.facts());
    return selected;
  }

  private void copyRecognized(RecognizedInvoice recognized, AccountingInvoiceForm form) {
    form.setDocumentType(recognized.documentType());
    form.setIssueDate(recognized.issueDate());
    form.setSaleDate(recognized.saleDate());
    form.setDueDate(recognized.dueDate());
    form.setReference(recognized.reference());
    form.setCounterpartyAlias(counterparty(recognized));
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

  private void validateRequired(AccountingInvoiceForm form) {
    if (form.getDocumentType() == null || form.getDocumentType().isBlank()) {
      throw new IllegalArgumentException("Document type is required");
    }
    if (form.getReference() == null || form.getReference().isBlank()) {
      throw new IllegalArgumentException("Invoice reference is required");
    }
    if (form.getCounterpartyAlias() == null || form.getCounterpartyAlias().isBlank()) {
      throw new IllegalArgumentException("Counterparty is required");
    }
    if (form.getCategory() == null || form.getCategory().isBlank()) {
      throw new IllegalArgumentException("Category is required");
    }
    if (form.getCurrency() == null || form.getCurrency().isBlank()) {
      throw new IllegalArgumentException("Currency is required");
    }
    if (form.getNetAmount() == null
        || form.getVatAmount() == null
        || form.getGrossAmount() == null) {
      throw new IllegalArgumentException("Net, VAT and gross amounts are required before saving");
    }
    if (form.getNetAmount().add(form.getVatAmount()).compareTo(form.getGrossAmount()) != 0) {
      throw new IllegalArgumentException("Net + VAT must equal gross before saving");
    }
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
    return note.toString();
  }

  private LocalDate firstNonNull(LocalDate first, LocalDate second) {
    return first != null ? first : second;
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
