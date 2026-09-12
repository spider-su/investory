package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class InvoiceValidator {
  private static final BigDecimal TOLERANCE = new BigDecimal("0.02");

  List<String> validate(AccountingInvoiceRecognitionService.RecognizedInvoice invoice) {
    List<String> issues = new ArrayList<>();
    if (blank(invoice.reference())) issues.add("missing invoice number");
    if (invoice.issueDate() == null) issues.add("missing issue date");
    if (blank(invoice.seller()) || blank(invoice.sellerNip())) issues.add("missing seller identity/NIP");
    if (blank(invoice.currency())) issues.add("missing currency");
    if (invoice.netAmount() == null) issues.add("missing net amount");
    if (invoice.vatAmount() == null) issues.add("missing VAT amount");
    if (invoice.grossAmount() == null) issues.add("missing gross amount");
    if (invoice.netAmount() != null && invoice.vatAmount() != null && invoice.grossAmount() != null
        && invoice.netAmount().add(invoice.vatAmount()).subtract(invoice.grossAmount()).abs().compareTo(TOLERANCE) > 0) {
      issues.add("net + VAT does not equal gross");
    }
    if ("UNKNOWN".equals(invoice.documentType())) issues.add("invoice direction requires review");
    return List.copyOf(issues);
  }

  AccountingInvoiceRecognitionService.RecognizedInvoice resolveDirection(
      AccountingInvoiceRecognitionService.RecognizedInvoice invoice, String ownNip) {
    if ("CREDIT_NOTE".equals(invoice.documentType()) || ownNip == null || ownNip.isBlank()) return invoice;
    String own = digits(ownNip);
    String type = own != null && own.equals(digits(invoice.sellerNip())) ? "SALES_INVOICE"
        : own != null && own.equals(digits(invoice.buyerNip())) ? "PURCHASE_INVOICE" : "UNKNOWN";
    return new AccountingInvoiceRecognitionService.RecognizedInvoice(
        type, invoice.issueDate(), invoice.saleDate(), invoice.dueDate(), invoice.reference(),
        invoice.seller(), invoice.buyer(), invoice.category(), invoice.currency(), invoice.netAmount(),
        invoice.vatAmount(), invoice.grossAmount(), invoice.note(), invoice.sellerNip(), invoice.buyerNip(), invoice.evidence());
  }

  private String digits(String value) {
    if (value == null) return null;
    String result = value.replaceAll("\\D", "");
    return result.length() == 10 ? result : null;
  }

  private boolean blank(String value) { return value == null || value.isBlank(); }
}
