package com.smartbox.investory.poc.accounting;

import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.BankRow;
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

  public AccountingMonthSnapshot july2026() {
    return snapshot(JULY_2026);
  }

  AccountingMonthSnapshot snapshot(LocalDate period) {
    List<InvoiceRow> invoices = pocRepository.invoicesForPeriod(period);
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
    VatCalculation vat = calculateVat(invoices, periodInvoices, obligations, taxInputs);

    List<ReconciliationRow> reconciliations =
        reconcile(invoices, bankTransactions, obligations);

    return new AccountingMonthSnapshot(
        period,
        domesticRevenue,
        foreignBookedRevenue,
        foreignSourceEur,
        domesticRevenue.add(foreignBookedRevenue),
        fx,
        ryczalt,
        vat,
        invoices,
        reconciliations,
        obligations,
        bankTransactions);
  }

  private FxCalculation calculateFx(
      List<InvoiceRow> periodInvoices,
      BigDecimal expectedForeignPln,
      BigDecimal foreignSourceEur) {
    InvoiceRow eurInvoice =
        periodInvoices.stream()
            .filter(invoice -> "EUR".equals(invoice.currency()))
            .findFirst()
            .orElse(null);
    if (eurInvoice == null) {
      return new FxCalculation(
          null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "NO_FX");
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
    BigDecimal correctionNet =
        invoices.stream()
            .map(InvoiceRow::correctionNetAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal revenueBeforeDeductions =
        domesticRevenue.add(fx.calculatedPln()).add(correctionNet);

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

    return new RyczałtCalculation(
        revenueBeforeDeductions,
        correctionNet,
        healthPaid,
        healthDeduction,
        taxableBase,
        rate,
        calculatedTax,
        expectedTax,
        difference,
        difference.signum() == 0 ? "MATCH" : "DIFF");
  }

  private VatCalculation calculateVat(
      List<InvoiceRow> invoices,
      List<InvoiceRow> periodInvoices,
      List<ObligationRow> obligations,
      List<TaxInputRow> taxInputs) {
    BigDecimal outputBeforeCorrections =
        periodInvoices.stream()
            .filter(invoice -> "PLN".equals(invoice.currency()))
            .map(InvoiceRow::vatAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal correctionVat =
        invoices.stream()
            .map(InvoiceRow::correctionVatAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal outputVat = outputBeforeCorrections.add(correctionVat);
    BigDecimal deductibleInputVat = taxInput(taxInputs, "DEDUCTIBLE_INPUT_VAT");
    BigDecimal calculatedVat =
        outputVat.subtract(deductibleInputVat).setScale(0, RoundingMode.HALF_UP);
    BigDecimal expectedVat = obligationAmount(obligations, "VAT");
    BigDecimal difference = calculatedVat.subtract(expectedVat);

    return new VatCalculation(
        outputBeforeCorrections,
        correctionVat,
        outputVat,
        deductibleInputVat,
        calculatedVat,
        expectedVat,
        difference,
        difference.signum() == 0 ? "MATCH" : "DIFF");
  }

  private BigDecimal taxInput(List<TaxInputRow> inputs, String inputType) {
    return inputs.stream()
        .filter(input -> inputType.equals(input.inputType()))
        .map(TaxInputRow::amount)
        .findFirst()
        .orElse(BigDecimal.ZERO);
  }

  private BigDecimal obligationAmount(List<ObligationRow> obligations, String type) {
    return obligations.stream()
        .filter(obligation -> type.equals(obligation.obligationType()))
        .map(ObligationRow::expectedAmount)
        .findFirst()
        .orElse(BigDecimal.ZERO);
  }

  private List<ReconciliationRow> reconcile(
      List<InvoiceRow> invoices,
      List<BankRow> bankTransactions,
      List<ObligationRow> obligations) {
    List<ReconciliationRow> result = new ArrayList<>();

    for (InvoiceRow invoice : invoices) {
      BankRow match =
          bankTransactions.stream()
              .filter(row -> "BUSINESS".equals(row.scope()))
              .filter(row -> "CUSTOMER_RECEIPT".equals(row.transactionType()))
              .filter(row -> invoice.currency().equals(row.currency()))
              .filter(row -> invoice.expectedReceivable().compareTo(row.amount()) == 0)
              .filter(
                  row -> row.relatedPeriod() == null || invoice.taxPeriod().equals(row.relatedPeriod()))
              .filter(
                  row ->
                      invoice.reference().equalsIgnoreCase(row.reference())
                          || invoice.customerAlias().equals(row.counterpartyAlias()))
              .findFirst()
              .orElse(null);

      String explanation =
          invoice.correctionGrossAmount().signum() == 0
              ? "Exact expected receivable matched to a business customer receipt."
              : "Exact corrected receivable matched; original invoice remains preserved.";

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
