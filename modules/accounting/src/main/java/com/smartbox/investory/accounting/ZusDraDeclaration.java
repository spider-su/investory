package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Structured, unsigned representation of one ZUS DRA declaration.
 *
 * <p>The lists follow the numbered fields of the corresponding DRA sections. Empty values are
 * omitted from KEDU. This class is deliberately separate from the calculation snapshot: a DRA is a
 * filing document and must be reviewed before it is signed or submitted.
 */
public record ZusDraDeclaration(
    LocalDate period,
    String submissionNumber,
    String submissionDeadlineCode,
    Payer payer,
    int insuredCount,
    BigDecimal accidentRate,
    List<String> socialSummary,
    List<String> benefits,
    List<String> healthSummary,
    List<String> fep,
    List<String> monthlyHealthTaxation,
    IncomeDeclaration income,
    List<String> annualHealthSettlement,
    LocalDate declarationDate) {

  public ZusDraDeclaration {
    Objects.requireNonNull(period, "period");
    Objects.requireNonNull(payer, "payer");
    Objects.requireNonNull(submissionNumber, "submissionNumber");
    Objects.requireNonNull(submissionDeadlineCode, "submissionDeadlineCode");
    socialSummary = immutable(socialSummary);
    benefits = immutable(benefits);
    healthSummary = immutable(healthSummary);
    fep = immutable(fep);
    monthlyHealthTaxation = immutable(monthlyHealthTaxation);
    annualHealthSettlement = immutable(annualHealthSettlement);
    if (insuredCount < 0) throw new IllegalArgumentException("insuredCount must not be negative");
    if (accidentRate == null || accidentRate.signum() < 0)
      throw new IllegalArgumentException("accidentRate must not be negative");
  }

  private static List<String> immutable(List<String> values) {
    return values == null ? List.of() : List.copyOf(values);
  }

  public record Payer(
      String nip,
      String regon,
      String pesel,
      String documentType,
      String documentNumber,
      String shortName,
      String surname,
      String firstName,
      LocalDate birthDate) {}

  /** DRA section X: insurance title and contribution bases. */
  public record IncomeDeclaration(
      String insuranceTitle,
      BigDecimal socialBase,
      BigDecimal sicknessBase,
      BigDecimal accidentBase,
      BigDecimal healthBase,
      String annualBaseExceeded) {}
}
