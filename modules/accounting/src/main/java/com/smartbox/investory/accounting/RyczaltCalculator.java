package com.smartbox.investory.accounting;

import com.smartbox.investory.accounting.AccountingCalculationResult.FxCalculation.Conversion;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.InvoiceRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Calculates the supported ryczałt/PIT-28 result from normalized facts. */
final class RyczaltCalculator {
  private static final BigDecimal HALF = new BigDecimal("0.50");

  AccountingCalculationResult.RyczaltCalculation calculate(
      AccountingCalculationInput input,
      AccountingCalculationResult.FxCalculation fx,
      List<AccountingIssue> issues) {
    BigDecimal revenue =
        input.invoices().stream()
            .filter(i -> "PLN".equals(i.currency()))
            .map(InvoiceRow::netAmount)
            .filter(v -> v != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .add(fx.convertedRevenuePln())
            .add(input.adjustments().revenueNetPln());
    var periodStart = input.period().withDayOfMonth(1);
    var periodEnd = input.period().withDayOfMonth(input.period().lengthOfMonth());
    var deductions = deductionState(input, revenue, periodStart, periodEnd);
    Map<BigDecimal, BigDecimal> buckets = new TreeMap<>();
    BigDecimal effectiveRate = input.periodContext().ryczaltRate();
    if (input.calculationMode() == AccountingCalculationMode.CURRENT_CALCULATION
        && (input.periodContext().zusRegime() == null || effectiveRate == null)) {
      issues.add(
          issue("MISSING_EFFECTIVE_TAX_PROFILE", null, "No tax profile is active for the period."));
    }
    for (InvoiceRow invoice : input.invoices()) {
      if (invoice.ryczaltRate() == null) {
        issues.add(
            issue("UNSUPPORTED_RYCZALT_RATE", invoice.reference(), "Ryczalt rate is missing."));
      } else if (invoice.netAmount() != null) {
        BigDecimal amount =
            "PLN".equals(invoice.currency())
                ? invoice.netAmount()
                : fx.entries().stream()
                    .filter(e -> e.reference().equals(invoice.reference()))
                    .map(Conversion::convertedPln)
                    .findFirst()
                    .orElse(null);
        if (amount != null) buckets.merge(invoice.ryczaltRate(), amount, BigDecimal::add);
      }
    }
    if (input.adjustments().revenueNetPln().signum() != 0) {
      if (effectiveRate == null) {
        issues.add(
            issue(
                "MISSING_EFFECTIVE_TAX_PROFILE", null, "No tax profile is active for the period."));
      } else {
        buckets.merge(effectiveRate, input.adjustments().revenueNetPln(), BigDecimal::add);
      }
    }
    Map<BigDecimal, BigDecimal> taxableByRate = allocateDeductions(buckets, deductions.used());
    BigDecimal taxable =
        taxableByRate.values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(0, RoundingMode.HALF_UP);
    BigDecimal tax =
        taxableByRate.entrySet().stream()
            .map(
                entry ->
                    entry
                        .getValue()
                        .setScale(0, RoundingMode.HALF_UP)
                        .multiply(entry.getKey())
                        .setScale(0, RoundingMode.HALF_UP))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new AccountingCalculationResult.RyczaltCalculation(
        revenue,
        deductions.socialUsed(),
        health(input),
        deductions.healthUsed(),
        deductions.available(),
        deductions.used(),
        deductions.carryForward(),
        taxable,
        buckets,
        taxableByRate,
        tax);
  }

  private BigDecimal health(AccountingCalculationInput input) {
    var zus = input.periodContext().zusCalculationInput();
    return zus != null && zus.healthAmount() != null ? zus.healthAmount() : BigDecimal.ZERO;
  }

  private DeductionState deductionState(
      AccountingCalculationInput input,
      BigDecimal currentRevenue,
      java.time.LocalDate periodStart,
      java.time.LocalDate periodEnd) {
    var eligible =
        input.periodContext().yearToDate().paidContributions().stream()
            .filter(c -> c.paymentDate().getYear() == periodStart.getYear())
            .filter(c -> !c.paymentDate().isAfter(periodEnd))
            .toList();
    BigDecimal social =
        eligible.stream()
            .filter(c -> "SOCIAL".equals(c.contributionType()))
            .map(PaidContribution::deductibleAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal health =
        eligible.stream()
            .filter(c -> "HEALTH".equals(c.contributionType()))
            .map(PaidContribution::deductibleAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .multiply(HALF)
            .setScale(2, RoundingMode.HALF_UP);
    BigDecimal prior =
        eligible.stream()
            .filter(c -> c.paymentDate().isBefore(periodStart))
            .map(
                c ->
                    "HEALTH".equals(c.contributionType())
                        ? c.deductibleAmount().multiply(HALF)
                        : c.deductibleAmount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal priorRevenue =
        input
            .periodContext()
            .yearToDate()
            .taxableRyczaltRevenue()
            .subtract(currentRevenue)
            .max(BigDecimal.ZERO);
    // An explicit context checkpoint takes precedence over a lower derived prior-use estimate.
    BigDecimal previouslyUsed =
        input.periodContext().yearToDate().deductionsAlreadyConsumed().max(prior.min(priorRevenue));
    BigDecimal available = social.add(health).subtract(previouslyUsed).max(BigDecimal.ZERO);
    BigDecimal used = available.min(currentRevenue.max(BigDecimal.ZERO));
    return new DeductionState(
        available,
        used,
        available.subtract(used),
        social.min(used),
        used.subtract(social.min(used)));
  }

  private Map<BigDecimal, BigDecimal> allocateDeductions(
      Map<BigDecimal, BigDecimal> revenue, BigDecimal deductions) {
    // Numeric rate order makes rounding residue independent of invoice input order.
    Map<BigDecimal, BigDecimal> result = new TreeMap<>();
    BigDecimal total = revenue.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal remaining = deductions;
    int index = 0;
    for (var entry : revenue.entrySet()) {
      BigDecimal allocation;
      if (++index == revenue.size()) allocation = remaining;
      else if (total.signum() == 0) allocation = BigDecimal.ZERO;
      else {
        allocation = deductions.multiply(entry.getValue()).divide(total, 2, RoundingMode.HALF_UP);
        remaining = remaining.subtract(allocation);
      }
      result.put(entry.getKey(), entry.getValue().subtract(allocation).max(BigDecimal.ZERO));
    }
    return result;
  }

  private AccountingIssue issue(String type, String reference, String message) {
    return new AccountingIssue(type, "BLOCKING", reference, message);
  }

  private record DeductionState(
      BigDecimal available,
      BigDecimal used,
      BigDecimal carryForward,
      BigDecimal socialUsed,
      BigDecimal healthUsed) {}
}
