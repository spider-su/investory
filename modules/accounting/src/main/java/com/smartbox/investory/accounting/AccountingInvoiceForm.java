package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
public class AccountingInvoiceForm {
  private String month;
  private String sourceIdentity;
  private String documentType = "PURCHASE_INVOICE";
  private LocalDate issueDate;
  private LocalDate saleDate;
  private LocalDate dueDate;
  private String reference;
  private String counterpartyAlias;
  private String counterpartyTaxIdentifier;
  private String counterpartyCountry;
  private String ksefNumber;
  private AccountingFilingEvidence.Type filingEvidence;
  private String category;
  private String currency = "PLN";
  private BigDecimal netAmount;
  private BigDecimal vatAmount;
  private BigDecimal grossAmount;
  private BigDecimal vatDeductionRatio;
  private String note;
}
