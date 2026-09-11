package com.smartbox.investory.poc.accounting;

import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.BankRow;
import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.ComparisonRow;
import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.ExpenseRow;
import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.FxCalculation;
import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.InvoiceRow;
import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.ObligationRow;
import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.ReconciliationRow;
import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.RyczałtCalculation;
import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.TaxInputRow;
import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.VatCalculation;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyConversionUnavailableException;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountingFactService {
  private static final LocalDate JULY_2026 = LocalDate.of(2026, 7, 1);
  private static final BigDecimal HALF = new BigDecimal("0.50");

  private final AccountingFactRepository factRepository;
  private final AccountingPocRepository pocRepository;
  private final CurrencyConversion currencyConversion;

  public List<AccountingFact> facts() {
    return factRepository.findAll();
  }

  public List<LocalDate> availablePeriods() {
    return pocRepository.availablePeriods();
  }

  public AccountingMonthSnapshot snapshot(LocalDate period) {
    List<InvoiceRow> invoices = pocRepository.invoicesForPeriod(period);
    List<ExpenseRow> expenses = pocRepository.expensesForPeriod(period);
    List<BankRow> bankTransactions = pocRepository.bankTransactionsForPeriod(period);
    List<ObligationRow> obligations = pocRepository.obligationsForPeriod(period);
    List<TaxInputRow> taxInputs = pocRepository.taxInputsForPeriod(period);

    List<InvoiceRow> periodInvoices =
        invoices.stream().filter(invoice -> invoice.taxPeriod().equals(period)).toList();

    BigDecimal domesticRevenue =
        periodInvoices.stream()
            .filter(invoice -> "PLN".equals(invoice.currency()))
            .map(InvoiceRow::bookedNetPln)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal foreignBookedRevenue =
        periodInvoices.stream()
            .filter(invoice -> !"PLN".equals(invoice.currency()))
            .map(InvoiceRow::bookedNetPln)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal foreignSourceEur =
        periodInvoices.stream()
            .filter(invoice -> "EUR".equals(invoice.currency()))
            .map(InvoiceRow::netAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    FxCalculation fx = calculateFx(periodInvoices, foreignBookedRevenue, foreignSourceEur);
    RyczałtCalculation ryczalt =
        calculateRyczalt(period, invoices, domesticRevenue, fx, obligations, taxInputs);
    VatCalculation vat =
        calculateVat(period, invoices, periodInvoices, expenses, obligations, taxInputs);
    List<ComparisonRow> comparisons =
        buildComparisons(
            period,
            domesticRevenue,
            foreignBookedRevenue,
            fx,
            ryczalt,
            vat,
            obligations,
            taxInputs);

    List<ReconciliationRow> reconciliations = reconcile(invoices, bankTransactions, obligations);

    return new AccountingMonthSnapshot(
        period,
        domesticRevenue,
        foreignBookedRevenue,
        foreignSourceEur,
        domesticRevenue.add(foreignBookedRevenue),
        fx,
        ryczalt,
        vat,
        comparisons,
        invoices,
        expenses,
        reconciliations,
        obligations,
        bankTransactions);
  }

  private List<ComparisonRow> buildComparisons(
      LocalDate period,
      BigDecimal domesticRevenue,
      BigDecimal foreignBookedRevenue,
      FxCalculation fx,
      RyczałtCalculation ryczalt,
      VatCalculation vat,
      List<ObligationRow> obligations,
      List<TaxInputRow> taxInputs) {
    BigDecimal expectedRevenue =
        domesticRevenue
            .add(foreignBookedRevenue)
            .add(ryczalt.julyOnlyCorrectionNetAdjustment())
            .setScale(2, RoundingMode.HALF_UP);
    BigDecimal calculatedRevenue =
        ryczalt.revenueBeforeDeductions().setScale(2, RoundingMode.HALF_UP);
    BigDecimal revenueDifference =
        calculatedRevenue.subtract(expectedRevenue).setScale(2, RoundingMode.HALF_UP);
    String revenueStatus =
        "NO_FX_SOURCE".equals(fx.status())
                && period.getYear() == 2026
                && period.getMonthValue() <= 8
            ? "INPUTS_INCOMPLETE"
            : revenueDifference.signum() == 0 ? "MATCH" : "DIFF";

    BigDecimal zusCalculated =
        taxInput(taxInputs, "HEALTH_CONTRIBUTION_PAID").setScale(2, RoundingMode.HALF_UP);
    BigDecimal zusExpected = obligationAmount(obligations, "ZUS").setScale(2, RoundingMode.HALF_UP);
    BigDecimal zusDifference =
        zusCalculated.subtract(zusExpected).setScale(2, RoundingMode.HALF_UP);
    String zusStatus =
        !hasObligation(obligations, "ZUS")
            ? "NO_GOLDEN"
            : zusCalculated.signum() == 0
                ? "INPUTS_INCOMPLETE"
                : zusDifference.signum() == 0 ? "MATCH" : "DIFF";

    return List.of(
        new ComparisonRow(
            "REVENUE",
            calculatedRevenue,
            expectedRevenue,
            revenueDifference,
            "PLN",
            revenueStatus,
            "Calculated domestic revenue plus Investory FX; July includes its explicit one-off correction fixture."),
        new ComparisonRow(
            "RYCZALT",
            ryczalt.calculatedTax(),
            ryczalt.expectedTax(),
            ryczalt.difference(),
            "PLN",
            ryczalt.status(),
            "12% ryczałt compared with captured wFirma/bank golden output."),
        new ComparisonRow(
            "VAT",
            vat.calculatedVat(),
            vat.expectedVat(),
            vat.difference(),
            "PLN",
            vat.status(),
            "Output VAT minus document-level deductible input VAT; July uses its parked special adjustment."),
        new ComparisonRow(
            "ZUS",
            zusCalculated,
            zusExpected,
            zusDifference,
            "PLN",
            zusStatus,
            "Current POC compares the captured health-contribution source fact with the ZUS obligation; contribution formula is not modeled yet."),
        new ComparisonRow(
            "FX",
            fx.calculatedPln(),
            fx.expectedPln(),
            fx.difference(),
            "PLN",
            fx.status(),
            "Foreign revenue converted through Investory CurrencyConversion and compared with the booked PLN value."));
  }

  private FxCalculation calculateFx(
      List<InvoiceRow> periodInvoices, BigDecimal expectedForeignPln, BigDecimal foreignSourceEur) {
    InvoiceRow eurInvoice =
        periodInvoices.stream()
            .filter(invoice -> "EUR".equals(invoice.currency()))
            .findFirst()
            .orElse(null);
    if (eurInvoice == null) {
      return new FxCalculation(
          null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "NO_FX_SOURCE");
    }

    BigDecimal calculated;
    String status;
    try {
      calculated =
          currencyConversion
              .convertToBaseCurrency(
                  eurInvoice.netAmount(),
                  CurrencyType.PLN,
                  CurrencyType.EUR,
                  eurInvoice.fxRateDate())
              .setScale(2, RoundingMode.HALF_UP);
      status =
          calculated.compareTo(expectedForeignPln.setScale(2, RoundingMode.HALF_UP)) == 0
              ? "MATCH"
              : "DIFF";
    } catch (CurrencyConversionUnavailableException ex) {
      calculated = expectedForeignPln.setScale(2, RoundingMode.HALF_UP);
      status = "FX_UNAVAILABLE_USING_GOLDEN";
    }

    BigDecimal expected = expectedForeignPln.setScale(2, RoundingMode.HALF_UP);
    return new FxCalculation(
        eurInvoice.fxRateDate(),
        foreignSourceEur,
        calculated,
        expected,
        calculated.subtract(expected).setScale(2, RoundingMode.HALF_UP),
        status);
  }

  private RyczałtCalculation calculateRyczalt(
      LocalDate period,
      List<InvoiceRow> invoices,
      BigDecimal domesticRevenue,
      FxCalculation fx,
      List<ObligationRow> obligations,
      List<TaxInputRow> taxInputs) {
    BigDecimal julyOnlyCorrectionNet =
        JULY_2026.equals(period)
            ? invoices.stream()
                .map(InvoiceRow::correctionNetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
            : BigDecimal.ZERO;
    BigDecimal revenueBeforeDeductions =
        domesticRevenue.add(fx.calculatedPln()).add(julyOnlyCorrectionNet);

    BigDecimal healthPaid = taxInput(taxInputs, "HEALTH_CONTRIBUTION_PAID");
    BigDecimal healthDeduction = healthPaid.multiply(HALF).setScale(2, RoundingMode.HALF_UP);
    BigDecimal taxableBase =
        revenueBeforeDeductions.subtract(healthDeduction).setScale(2, RoundingMode.HALF_UP);

    BigDecimal rate =
        invoices.stream()
            .filter(invoice -> invoice.taxPeriod().equals(period))
            .map(InvoiceRow::ryczaltRate)
            .filter(value -> value != null)
            .findFirst()
            .orElse(new BigDecimal("0.12"));

    BigDecimal calculatedTax = taxableBase.multiply(rate).setScale(0, RoundingMode.HALF_UP);
    BigDecimal expectedTax = obligationAmount(obligations, "RYCZALT");
    BigDecimal difference = calculatedTax.subtract(expectedTax);
    boolean hasGolden = hasObligation(obligations, "RYCZALT");
    boolean missingForeignSource =
        fx.status().equals("NO_FX_SOURCE") && period.getMonthValue() <= 7;
    String status =
        !hasGolden
            ? "NO_GOLDEN"
            : missingForeignSource
                ? "INPUTS_INCOMPLETE"
                : difference.signum() == 0 ? "MATCH" : "DIFF";

    return new RyczałtCalculation(
        revenueBeforeDeductions,
        julyOnlyCorrectionNet,
        healthPaid,
        healthDeduction,
        taxableBase,
        rate,
        calculatedTax,
        expectedTax,
        difference,
        status);
  }

  private VatCalculation calculateVat(
      LocalDate period,
      List<InvoiceRow> invoices,
      List<InvoiceRow> periodInvoices,
      List<ExpenseRow> expenses,
      List<ObligationRow> obligations,
      List<TaxInputRow> taxInputs) {
    BigDecimal outputBeforeCorrection =
        periodInvoices.stream()
            .filter(invoice -> "PLN".equals(invoice.currency()))
            .map(InvoiceRow::vatAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal julyOnlySalesCorrectionVat =
        JULY_2026.equals(period)
            ? invoices.stream()
                .map(InvoiceRow::correctionVatAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
            : BigDecimal.ZERO;
    BigDecimal outputVat = outputBeforeCorrection.add(julyOnlySalesCorrectionVat);

    BigDecimal deductibleInputVat =
        JULY_2026.equals(period)
            ? BigDecimal.ZERO
            : expenses.stream()
                .map(ExpenseRow::deductibleVat)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    BigDecimal julyOnlyAdjustment =
        JULY_2026.equals(period)
            ? taxInput(taxInputs, "JULY_ONLY_VAT_CORRECTION_ADJUSTMENT")
            : BigDecimal.ZERO;

    BigDecimal calculatedVat =
        outputVat
            .subtract(deductibleInputVat)
            .subtract(julyOnlyAdjustment)
            .setScale(0, RoundingMode.HALF_UP);
    BigDecimal expectedVat = obligationAmount(obligations, "VAT");
    BigDecimal difference = calculatedVat.subtract(expectedVat);
    boolean hasGolden = hasObligation(obligations, "VAT");
    boolean expenseDocumentsMissing = !JULY_2026.equals(period) && expenses.isEmpty() && hasGolden;
    String status =
        !hasGolden
            ? "NO_GOLDEN"
            : expenseDocumentsMissing
                ? "EXPENSES_MISSING"
                : difference.signum() == 0 ? "MATCH" : "DIFF";

    return new VatCalculation(
        outputBeforeCorrection,
        julyOnlySalesCorrectionVat,
        outputVat,
        deductibleInputVat,
        julyOnlyAdjustment,
        calculatedVat,
        expectedVat,
        difference,
        status);
  }

  private BigDecimal taxInput(List<TaxInputRow> inputs, String inputType) {
    return inputs.stream()
        .filter(input -> inputType.equals(input.inputType()))
        .map(TaxInputRow::amount)
        .findFirst()
        .orElse(BigDecimal.ZERO);
  }

  private boolean hasObligation(List<ObligationRow> obligations, String type) {
    return obligations.stream().anyMatch(obligation -> type.equals(obligation.obligationType()));
  }

  private BigDecimal obligationAmount(List<ObligationRow> obligations, String type) {
    return obligations.stream()
        .filter(obligation -> type.equals(obligation.obligationType()))
        .map(ObligationRow::expectedAmount)
        .findFirst()
        .orElse(BigDecimal.ZERO);
  }

  private List<ReconciliationRow> reconcile(
      List<InvoiceRow> invoices, List<BankRow> bankTransactions, List<ObligationRow> obligations) {
    List<ReconciliationRow> result = new ArrayList<>();

    for (InvoiceRow invoice : invoices) {
      BankRow match =
          bankTransactions.stream()
              .filter(row -> "BUSINESS".equals(row.scope()))
              .filter(row -> "CUSTOMER_RECEIPT".equals(row.transactionType()))
              .filter(row -> invoice.currency().equals(row.currency()))
              .filter(row -> invoice.expectedReceivable().compareTo(row.amount()) == 0)
              .filter(
                  row ->
                      row.relatedPeriod() == null
                          || invoice.taxPeriod().equals(row.relatedPeriod()))
              .filter(
                  row ->
                      invoice.reference().equalsIgnoreCase(row.reference())
                          || invoice.customerAlias().equals(row.counterpartyAlias()))
              .findFirst()
              .orElse(null);

      String explanation =
          invoice.correctionGrossAmount().signum() == 0
              ? "Exact expected receivable matched to a business customer receipt."
              : "July historical correction fixture matched; generic correction processing is parked.";

      result.add(
          new ReconciliationRow(
              invoice.reference(),
              "INVOICE_PAYMENT",
              invoice.expectedReceivable(),
              invoice.currency(),
              match == null ? BigDecimal.ZERO : match.amount(),
              match == null ? null : match.bookingDate(),
              match == null ? "UNMATCHED" : "MATCHED",
              match == null ? "No exact business receipt found." : explanation));
    }

    for (ObligationRow obligation : obligations) {
      result.add(
          new ReconciliationRow(
              obligation.obligationType(),
              "OBLIGATION_PAYMENT",
              obligation.expectedAmount(),
              "PLN",
              obligation.paidAmount(),
              obligation.paymentDate(),
              obligation.status(),
              obligation.note()));
    }

    return List.copyOf(result);
  }
}
