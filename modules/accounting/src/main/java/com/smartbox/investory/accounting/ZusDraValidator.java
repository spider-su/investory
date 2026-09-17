package com.smartbox.investory.accounting;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Validates DRA field cardinality and the cross-field invariants owned by Investory. */
public final class ZusDraValidator {
  public void validate(ZusDraDeclaration declaration) {
    List<String> issues = new ArrayList<>();
    if (!declaration.submissionNumber().matches("\\d{2}"))
      issues.add("submissionNumber must contain two digits");
    if (!declaration.submissionDeadlineCode().matches("\\d"))
      issues.add("submissionDeadlineCode must contain one digit");
    checkSize(declaration.socialSummary(), 37, "IV socialSummary", issues);
    checkSize(declaration.benefits(), 5, "V benefits", issues);
    checkSize(declaration.healthSummary(), 7, "VI healthSummary", issues);
    checkSize(declaration.fep(), 3, "VIII fep", issues);
    checkSize(declaration.monthlyHealthTaxation(), 20, "XI monthlyHealthTaxation", issues);
    checkSize(declaration.annualHealthSettlement(), 29, "XII annualHealthSettlement", issues);
    var income = declaration.income();
    if (income == null) issues.add("X income declaration is required");
    else if (blank(income.insuranceTitle())) issues.add("X.p1 insuranceTitle is required");
    else if (!income.insuranceTitle().replace(" ", "").matches("\\d{6}"))
      issues.add("X.p1 insuranceTitle must contain six digits");
    if (declaration.accidentRate().scale() > 2)
      issues.add("accidentRate must have at most two decimal places");
    if (declaration.accidentRate().setScale(2, RoundingMode.UNNECESSARY).signum() < 0)
      issues.add("accidentRate must not be negative");
    var payer = declaration.payer();
    if (blank(payer.nip()) && blank(payer.pesel())) issues.add("payer NIP or PESEL is required");
    if (blank(payer.surname())) issues.add("payer surname is required");
    if (blank(payer.firstName())) issues.add("payer firstName is required");
    if (payer.birthDate() == null) issues.add("payer birthDate is required");
    if (!issues.isEmpty()) throw new ZusDraValidationException(issues);
  }

  private void checkSize(List<String> values, int max, String name, List<String> issues) {
    if (values.size() > max) issues.add(name + " has more than " + max + " fields");
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
