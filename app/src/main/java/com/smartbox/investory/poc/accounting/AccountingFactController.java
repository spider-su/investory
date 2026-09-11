package com.smartbox.investory.poc.accounting;

import com.smartbox.investory.poc.accounting.AccountingExpenseNormalizer.ExpenseImportCandidate;
import com.smartbox.investory.poc.accounting.AccountingExpenseNormalizer.NormalizedExpense;
import com.smartbox.investory.poc.accounting.AccountingInvoiceRecognitionService.RecognizedInvoice;
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
    AccountingExpenseForm form = new AccountingExpenseForm();
    form.setMonth(formatMonth(selected));
    try {
      RecognizedInvoice recognized =
          invoiceRecognitionService.recognize(
              invoice.getOriginalFilename(), invoice.getContentType(), invoice.getBytes());
      copyRecognized(recognized, form);
      model.addAttribute("expenseDraft", form);
      model.addAttribute(
          "recognitionMessage",
          "Invoice fields were extracted from the uploaded document. Review them before saving.");
    } catch (IOException | RuntimeException exception) {
      model.addAttribute("expenseDraft", form);
      model.addAttribute("recognitionError", exception.getMessage());
    }
    return "poc/accounting-facts";
  }

  @PostMapping("/poc/accounting/expense")
  public String saveExpense(
      @ModelAttribute AccountingExpenseForm expenseDraft, RedirectAttributes redirectAttributes) {
    String month = expenseDraft.getMonth();
    try {
      LocalDate taxPeriod = parseMonth(month);
      validateRequired(expenseDraft);
      BigDecimal deductionRatio =
          expenseDraft.getVatDeductionRatio() == null
              ? defaultDeductionRatio(expenseDraft.getCategory())
              : expenseDraft.getVatDeductionRatio();
      NormalizedExpense normalized =
          expenseNormalizer.normalize(
              new ExpenseImportCandidate(
                  expenseDraft.getCategory(),
                  expenseDraft.getGrossAmount(),
                  expenseDraft.getNetAmount(),
                  expenseDraft.getVatAmount(),
                  deductionRatio));

      repository.insertExpense(
          taxPeriod,
          expenseDraft.getInvoiceDate(),
          expenseDraft.getReference().trim(),
          expenseDraft.getSupplierAlias().trim(),
          expenseDraft.getCategory().trim(),
          expenseDraft.getCurrency().trim().toUpperCase(),
          normalized.netAmount(),
          normalized.vatAmount(),
          normalized.grossAmount(),
          normalized.vatDeductionRatio(),
          "AI_EXTRACTED_REVIEWED",
          buildReviewedNote(expenseDraft.getNote()));
      redirectAttributes.addFlashAttribute("expenseSaved", "Expense invoice saved.");
    } catch (RuntimeException exception) {
      redirectAttributes.addFlashAttribute("expenseSaveError", exception.getMessage());
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
    model.addAttribute("snapshot", service.snapshot(selected));
    model.addAttribute("facts", service.facts());
    return selected;
  }

  private void copyRecognized(RecognizedInvoice recognized, AccountingExpenseForm form) {
    form.setInvoiceDate(recognized.invoiceDate());
    form.setReference(recognized.reference());
    form.setSupplierAlias(recognized.supplier());
    form.setCategory(recognized.category());
    form.setCurrency(recognized.currency());
    form.setNetAmount(recognized.netAmount());
    form.setVatAmount(recognized.vatAmount());
    form.setGrossAmount(recognized.grossAmount());
    form.setVatDeductionRatio(defaultDeductionRatio(recognized.category()));
    form.setNote(recognized.note());
  }

  private void validateRequired(AccountingExpenseForm form) {
    if (form.getReference() == null || form.getReference().isBlank()) {
      throw new IllegalArgumentException("Invoice reference is required");
    }
    if (form.getSupplierAlias() == null || form.getSupplierAlias().isBlank()) {
      throw new IllegalArgumentException("Supplier is required");
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
  }

  private BigDecimal defaultDeductionRatio(String category) {
    return "VEHICLE_FUEL".equals(category) ? new BigDecimal("0.50") : BigDecimal.ONE;
  }

  private String buildReviewedNote(String note) {
    String base = note == null || note.isBlank() ? "" : note.trim() + " ";
    return base
        + "Uploaded invoice recognized by AI and reviewed in the accounting form before persistence.";
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
