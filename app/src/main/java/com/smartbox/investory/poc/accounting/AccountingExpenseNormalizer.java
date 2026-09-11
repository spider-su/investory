package com.smartbox.investory.poc.accounting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class AccountingExpenseNormalizer {
  private static final BigDecimal FULL = new BigDecimal("1.00");
  private static final BigDecimal HALF = new BigDecimal("0.50");
  private static final BigDecimal VAT_23 = new BigDecimal("0.23");
  private static final BigDecimal VAT_08 = new BigDecimal("0.08");
  private static final BigDecimal ONE = BigDecimal.ONE;
  private static final BigDecimal CENT = new BigDecimal("0.01");

  public NormalizedExpense normalize(ExpenseImportCandidate candidate) {
    BigDecimal gross = money(candidate.grossAmount());

    BigDecimal net;
    BigDecimal vat;
    BigDecimal vatRate;
    String sourceQuality;

    if (candidate.sourceNetAmount() != null || candidate.sourceVatAmount() != null) {
      if (candidate.sourceNetAmount() == null || candidate.sourceVatAmount() == null) {
        throw new IllegalArgumentException("Source net and VAT must be supplied together");
      }
      net = money(candidate.sourceNetAmount());
      vat = money(candidate.sourceVatAmount());
      if (net.add(vat).subtract(gross).abs().compareTo(CENT) > 0) {
        throw new IllegalArgumentException("Source net + VAT does not reconcile to gross amount");
      }
      vatRate = null;
      sourceQuality = "SOURCE_DOCUMENT";
    } else {
      vatRate = defaultVatRate(candidate.category());
      net = gross.divide(ONE.add(vatRate), 2, RoundingMode.HALF_UP);
      vat = gross.subtract(net).setScale(2, RoundingMode.HALF_UP);
      sourceQuality = vatRate.compareTo(VAT_08) == 0 ? "DERIVED_RULE_8" : "DERIVED_RULE_23";
    }

    BigDecimal deductionRatio =
        candidate.vatDeductionRatio() == null
            ? defaultDeductionRatio(candidate.category())
            : candidate.vatDeductionRatio().setScale(2, RoundingMode.HALF_UP);
    validateDeductionRatio(deductionRatio);

    return new NormalizedExpense(
        net,
        vat,
        gross,
        vatRate,
        deductionRatio,
        vat.multiply(deductionRatio).setScale(2, RoundingMode.HALF_UP),
        sourceQuality);
  }

  private BigDecimal defaultVatRate(String category) {
    return switch (category) {
      case "VEHICLE_FUEL", "PRODUCT" -> VAT_08;
      case "ACCOUNTING_SERVICE", "BUSINESS_SERVICE", "SERVICE" -> VAT_23;
      default ->
          throw new IllegalArgumentException(
              "No default VAT rate for category "
                  + category
                  + "; source net/VAT is required instead of guessing");
    };
  }

  private BigDecimal defaultDeductionRatio(String category) {
    return "VEHICLE_FUEL".equals(category) ? HALF : FULL;
  }

  private void validateDeductionRatio(BigDecimal ratio) {
    if (ratio.compareTo(BigDecimal.ZERO) != 0
        && ratio.compareTo(HALF) != 0
        && ratio.compareTo(FULL) != 0) {
      throw new IllegalArgumentException("VAT deduction ratio must be 0%, 50% or 100%");
    }
  }

  private BigDecimal money(BigDecimal value) {
    if (value == null) {
      throw new IllegalArgumentException("Gross amount is required");
    }
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  public record ExpenseImportCandidate(
      String category,
      BigDecimal grossAmount,
      BigDecimal sourceNetAmount,
      BigDecimal sourceVatAmount,
      BigDecimal vatDeductionRatio) {}

  public record NormalizedExpense(
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal grossAmount,
      BigDecimal derivedVatRate,
      BigDecimal vatDeductionRatio,
      BigDecimal deductibleVat,
      String sourceQuality) {}
}
