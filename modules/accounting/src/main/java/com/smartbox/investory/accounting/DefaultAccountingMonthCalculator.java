package com.smartbox.investory.accounting;

import com.smartbox.investory.accounting.AccountingCalculationResult.CalculatedObligation;
import com.smartbox.investory.accounting.AccountingCalculationResult.FxCalculation.Conversion;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.ExpenseRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.InvoiceRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.TaxInputRow;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyConversionUnavailableException;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Deterministic calculation engine. This class never loads repositories or compares goldens. */
@Service
public class DefaultAccountingMonthCalculator implements AccountingMonthCalculator {
  private static final BigDecimal HALF = new BigDecimal("0.50");
  private final CurrencyConversion currencyConversion;

  public DefaultAccountingMonthCalculator(CurrencyConversion currencyConversion) {
    this.currencyConversion = currencyConversion;
  }

  @Override
  public AccountingCalculationResult calculate(AccountingCalculationInput input) {
    List<AccountingIssue> issues = new ArrayList<>();
    AccountingCalculationResult.FxCalculation fx = calculateFx(input, issues);
    BigDecimal domestic =
        input.invoices().stream()
            .filter(i -> "PLN".equals(i.currency()))
            .map(InvoiceRow::netAmount)
            .filter(v -> v != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal revenue =
        domestic.add(fx.convertedRevenuePln()).add(input.adjustments().revenueNetPln());
    var paidContributions = input.periodContext().yearToDate().paidContributions();
    BigDecimal paidSocial =
        paidContributions.stream()
            .filter(p -> "SOCIAL".equals(p.contributionType()))
            .filter(p -> !p.paymentDate().isAfter(input.period()))
            .map(PaidContribution::paidAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal health =
        paidContributions.stream()
            .filter(p -> "HEALTH".equals(p.contributionType()))
            .filter(p -> !p.paymentDate().isAfter(input.period()))
            .map(PaidContribution::paidAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (health.signum() == 0) {
      health = required(input.taxInputs(), "HEALTH_CONTRIBUTION_PAID", issues);
    }
    BigDecimal socialDeduction = paidSocial;
    BigDecimal healthDeduction =
        health.signum() == 0
            ? BigDecimal.ZERO
            : health.multiply(HALF).setScale(2, RoundingMode.HALF_UP);
    BigDecimal taxable =
        revenue.subtract(socialDeduction).subtract(healthDeduction).setScale(2, RoundingMode.HALF_UP);
    Map<BigDecimal, BigDecimal> buckets = new LinkedHashMap<>();
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
    if (buckets.keySet().stream().anyMatch(rate -> rate.compareTo(new BigDecimal("0.12")) != 0)) {
      issues.add(
          issue("UNSUPPORTED_RYCZALT_RATE", null, "Only the 12% ryczalt rate is supported."));
    }
    if (input.adjustments().revenueNetPln().signum() != 0) {
      buckets.merge(new BigDecimal("0.12"), input.adjustments().revenueNetPln(), BigDecimal::add);
    }
    BigDecimal tax =
        buckets.entrySet().stream()
            .map(e -> e.getValue().multiply(e.getKey()))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .subtract(socialDeduction.multiply(new BigDecimal("0.12")))
            .subtract(healthDeduction.multiply(new BigDecimal("0.12")))
            .setScale(0, RoundingMode.HALF_UP);
    BigDecimal outputVat =
        input.invoices().stream()
            .filter(i -> "PLN".equals(i.currency()))
            .map(InvoiceRow::vatAmount)
            .filter(v -> v != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .add(input.adjustments().salesVat());
    BigDecimal deductible =
        input.expenses().stream()
            .map(ExpenseRow::deductibleVat)
            .filter(v -> v != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    BigDecimal calculatedVat =
        outputVat
            .setScale(0, RoundingMode.HALF_UP)
            .subtract(deductible.setScale(0, RoundingMode.HALF_UP));
    boolean qualifyingUop = input.periodContext().qualifyingUop();
    BigDecimal social =
        qualifyingUop
            ? BigDecimal.ZERO
            : required(input.taxInputs(), "JDG_COMPULSORY_SOCIAL_ZUS", issues);
    BigDecimal totalZus = social.add(health).setScale(2, RoundingMode.HALF_UP);
    var zus =
        new AccountingCalculationResult.ZusCalculation(
            social.setScale(2, RoundingMode.HALF_UP),
            health.setScale(2, RoundingMode.HALF_UP),
            totalZus,
            qualifyingUop,
            qualifyingUop ? "UOP_PRIMARY_INSURANCE" : "JDG_PRIMARY_INSURANCE");
    var ryczalt =
        new AccountingCalculationResult.RyczaltCalculation(
            revenue, socialDeduction, health, healthDeduction, taxable, buckets, tax);
    var vat =
        new AccountingCalculationResult.VatCalculation(
            outputVat.subtract(input.adjustments().salesVat()),
            input.adjustments().salesVat(),
            outputVat,
            deductible,
            calculatedVat);
    return new AccountingCalculationResult(
        input.period(),
        new AccountingCalculationResult.RevenueCalculation(domestic, fx.convertedRevenuePln()),
        fx,
        ryczalt,
        vat,
        zus,
        List.of(
            new CalculatedObligation("RYCZALT", tax, input.period()),
            new CalculatedObligation("VAT", calculatedVat, input.period()),
            new CalculatedObligation("ZUS", totalZus, input.period())),
        List.copyOf(issues));
  }

  private AccountingCalculationResult.FxCalculation calculateFx(
      AccountingCalculationInput input, List<AccountingIssue> issues) {
    List<Conversion> entries = new ArrayList<>();
    List<String> unavailable = new ArrayList<>();
    for (InvoiceRow invoice : input.invoices()) {
      if ("PLN".equals(invoice.currency())) continue;
      try {
        CurrencyType source = CurrencyType.valueOf(invoice.currency());
        BigDecimal converted =
            currencyConversion
                .convertToBaseCurrency(
                    invoice.netAmount(), CurrencyType.PLN, source, invoice.fxRateDate())
                .setScale(2, RoundingMode.HALF_UP);
        entries.add(
            new Conversion(
                invoice.reference(), invoice.currency(), invoice.netAmount(), converted));
      } catch (IllegalArgumentException | CurrencyConversionUnavailableException ex) {
        unavailable.add(invoice.reference());
        issues.add(
            issue(
                "MISSING_FX",
                invoice.reference(),
                "FX conversion is unavailable for " + invoice.currency() + " revenue."));
      }
    }
    BigDecimal total =
        entries.stream().map(Conversion::convertedPln).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new AccountingCalculationResult.FxCalculation(
        List.copyOf(entries), total, List.copyOf(unavailable));
  }

  private BigDecimal required(List<TaxInputRow> inputs, String type, List<AccountingIssue> issues) {
    return inputs.stream()
        .filter(i -> type.equals(i.inputType()))
        .map(TaxInputRow::amount)
        .findFirst()
        .orElseGet(
            () -> {
              issues.add(issue("MISSING_" + type, null, type + " input is required."));
              return BigDecimal.ZERO;
            });
  }

  private AccountingIssue issue(String type, String reference, String message) {
    return new AccountingIssue(type, "INCOMPLETE", reference, message);
  }
}
