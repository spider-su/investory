package com.smartbox.investory.poc.accounting;

import com.smartbox.investory.accounting.AccountingBankImportService;
import java.io.IOException;
import java.time.LocalDate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AccountingBankController {
  private final AccountingBankImportService importService;

  public AccountingBankController(AccountingBankImportService importService) {
    this.importService = importService;
  }

  @PostMapping("/poc/accounting/bank/import")
  public String importBank(
      @RequestParam String month,
      @RequestParam("bankFile") MultipartFile bankFile,
      RedirectAttributes redirectAttributes) {
    try {
      AccountingBankImportService.Result result =
          importService.importFile(
              bankFile.getOriginalFilename(),
              bankFile.getContentType(),
              bankFile.getBytes(),
              LocalDate.parse(month + "-01"));
      redirectAttributes.addFlashAttribute(
          "bankImportMessage",
          "Bank file imported: "
              + result.importedRows()
              + " transaction(s), "
              + result.reviewRequiredRows()
              + " review required.");
    } catch (IOException | RuntimeException exception) {
      redirectAttributes.addFlashAttribute("bankImportError", exception.getMessage());
    }
    return "redirect:/poc/accounting?month=" + month;
  }
}
