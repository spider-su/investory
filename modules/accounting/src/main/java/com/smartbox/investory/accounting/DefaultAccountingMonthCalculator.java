package com.smartbox.investory.accounting;

import com.smartbox.investory.accounting.AccountingCalculationResult.CalculatedObligation;
import com.smartbox.investory.accounting.AccountingCalculationResult.FxCalculation.Conversion;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.InvoiceRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.TaxInputRow;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyConversionUnavailableException;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** Deterministic orchestration. Tax formulas live in their focused calculators. */
@Service
public class DefaultAccountingMonthCalculator implements AccountingMonthCalculator {
  private final CurrencyConversion currencyConversion;
  private final RyczaltCalculator ryczaltCalculator = new RyczaltCalculator();
  private final VatCalculator vatCalculator = new VatCalculator();

  public DefaultAccountingMonthCalculator(CurrencyConversion currencyConversion) {
    this.currencyConversion = currencyConversion;
  }

  @Override
  public AccountingCalculationResult calculate(AccountingCalculationInput input) {
    List<AccountingIssue> issues = new ArrayList<>();
    AccountingCalculationResult.FxCalculation fx = calculateFx(input, issues);
    var ryczalt = ryczaltCalculator.calculate(input, fx, issues);
    var vat = vatCalculator.calculate(input, issues);
    var zus = calculateZus(input, issues);
    BigDecimal domestic =
        input.invoices().stream()
            .filter(i -> "PLN".equals(i.currency()))
            .map(InvoiceRow::netAmount)
            .filter(v -> v != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new AccountingCalculationResult(
        input.period(),
        new AccountingCalculationResult.RevenueCalculation(domestic, fx.convertedRevenuePln()),
        fx,
        ryczalt,
        vat,
        zus,
        List.of(
            new CalculatedObligation("RYCZALT", ryczalt.calculatedTax(), input.period()),
            new CalculatedObligation("VAT", vat.calculatedVat(), input.period()),
            new CalculatedObligation("ZUS", zus.totalZus(), input.period())),
        List.copyOf(issues));
  }

  private AccountingCalculationResult.ZusCalculation calculateZus(
      AccountingCalculationInput input, List<AccountingIssue> issues) {
    ZusCalculationInput calculated = input.periodContext().zusCalculationInput();
    boolean current = input.calculationMode() == AccountingCalculationMode.CURRENT_CALCULATION;
    boolean active = input.periodContext().jdgActive();
    boolean uop = input.periodContext().qualifyingUop();
    BigDecimal social;
    BigDecimal health;
    if (calculated != null) {
      social = calculated.socialAmount() == null ? BigDecimal.ZERO : calculated.socialAmount();
      health = calculated.healthAmount() == null ? BigDecimal.ZERO : calculated.healthAmount();
    } else if (current) {
      social = missingCurrentZusAmount("social", issues);
      health = missingCurrentZusAmount("health", issues);
    } else {
      social =
          !active || uop
              ? BigDecimal.ZERO
              : required(input.taxInputs(), "JDG_COMPULSORY_SOCIAL_ZUS", issues);
      health =
          !active
              ? BigDecimal.ZERO
              : required(input.taxInputs(), "HEALTH_CONTRIBUTION_PAID", issues);
    }
    social = social.setScale(2, RoundingMode.HALF_UP);
    health = health.setScale(2, RoundingMode.HALF_UP);
    return new AccountingCalculationResult.ZusCalculation(
        social,
        health,
        social.add(health).setScale(2, RoundingMode.HALF_UP),
        uop,
        calculated != null && calculated.socialReasonCode() != null
            ? calculated.socialReasonCode()
            : uop ? "UOP_PRIMARY_INSURANCE" : "JDG_PRIMARY_INSURANCE");
  }

  private BigDecimal missingCurrentZusAmount(String contribution, List<AccountingIssue> issues) {
    issues.add(
        new AccountingIssue(
            "MISSING_ZUS_RULE_INPUT",
            "INCOMPLETE",
            null,
            "Current calculation requires the effective 2026 ZUS rule input for "
                + contribution
                + "."));
    return BigDecimal.ZERO;
  }

  private BigDecimal required(List<TaxInputRow> inputs, String type, List<AccountingIssue> issues) {
    return inputs.stream()
        .filter(i -> type.equals(i.inputType()))
        .map(TaxInputRow::amount)
        .findFirst()
        .orElseGet(
            () -> {
              issues.add(
                  new AccountingIssue(
                      "MISSING_" + type, "INCOMPLETE", null, type + " input is required."));
              return BigDecimal.ZERO;
            });
  }

  private AccountingCalculationResult.FxCalculation calculateFx(
      AccountingCalculationInput input, List<AccountingIssue> issues) {
    List<Conversion> entries = new ArrayList<>();
    List<String> unavailable = new ArrayList<>();
    for (InvoiceRow invoice : input.invoices()) {
      if ("PLN".equals(invoice.currency())) continue;
      if (input.calculationMode() != AccountingCalculationMode.CURRENT_CALCULATION
          && invoice.bookedNetPln() != null) {
        entries.add(
            new Conversion(
                invoice.reference(),
                invoice.currency(),
                invoice.netAmount(),
                invoice.bookedNetPln()));
        continue;
      }
      try {
        CurrencyType source = CurrencyType.valueOf(invoice.currency());
        BigDecimal converted =
            currencyConversion.convertToBaseCurrency(
                invoice.netAmount(),
                CurrencyType.PLN,
                source,
                invoice.fxRateDate() != null
                    ? invoice.fxRateDate()
                    : AccountingDateRules.priorBusinessDay(
                        invoice.saleDate() != null ? invoice.saleDate() : invoice.issueDate()));
        if (converted == null) throw new CurrencyConversionUnavailableException("No FX result");
        converted = converted.setScale(2, RoundingMode.HALF_UP);
        entries.add(
            new Conversion(
                invoice.reference(), invoice.currency(), invoice.netAmount(), converted));
      } catch (IllegalArgumentException | CurrencyConversionUnavailableException ex) {
        unavailable.add(invoice.reference());
        issues.add(
            new AccountingIssue(
                "MISSING_FX",
                "BLOCKING",
                invoice.reference(),
                "FX conversion is unavailable for " + invoice.currency() + " revenue."));
      }
    }
    BigDecimal total =
        entries.stream().map(Conversion::convertedPln).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new AccountingCalculationResult.FxCalculation(
        List.copyOf(entries), total, List.copyOf(unavailable));
  }
}
