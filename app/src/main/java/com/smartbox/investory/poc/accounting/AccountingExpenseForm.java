package com.smartbox.investory.poc.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

@Data
public class AccountingExpenseForm {
  private String month;
  private LocalDate invoiceDate;
  private String reference;
  private String supplierAlias;
  private String category;
  private String currency = "PLN";
  private BigDecimal netAmount;
  private BigDecimal vatAmount;
  private BigDecimal grossAmount;
  private BigDecimal vatDeductionRatio;
  private String note;
}
