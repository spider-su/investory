package com.smartbox.investory.accounting;

import com.smartbox.investory.accounting.AccountingMonthSnapshot.BankRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.ComparisonRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.ExpenseRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.FxCalculation;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.InvoiceRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.ObligationRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.ReconciliationRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.RyczaltCalculation;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.TaxInputRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.VatCalculation;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.ZusCalculation;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyConversionUnavailableException;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountingFactService {
  private static final LocalDate JULY_2026 = LocalDate.of(2026, 7, 1);
  private static final BigDecimal HALF = new BigDecimal("0.50");
  private static final AccountingDueDatePolicy DUE_DATE_POLICY = new AccountingDueDatePolicy();

  private final AccountingFactRepository factRepository;
  private final AccountingPocRepository pocRepository;
  private final CurrencyConversion currencyConversion;

  public List<AccountingFact> facts() {
    return factRepository.findAll();
  }

  public List<LocalDate> availablePeriods() {
    return pocRepository.availablePeriods();
  }

  public AccountingProfile accountingProfile() {
    AccountingProfile profile = pocRepository.accountingProfile();
    return profile == null ? AccountingProfile.defaultProfile() : profile;
  }

  public void updateHasUop(boolean hasUop) {
    pocRepository.updateHasUop(hasUop);
  }

  public AccountingMonthSnapshot snapshot(LocalDate period) {
    List<InvoiceRow> invoices = pocRepository.invoicesForPeriod(period);
    List<InvoiceRow> correctionSources =
        JULY_2026.equals(period)
            ? pocRepository.invoicesForPeriod(period.minusMonths(1))
            : List.of();
    List<ExpenseRow> expenses = pocRepository.expensesForPeriod(period);
    List<BankRow> bankTransactions = pocRepository.bankTransactionsForPeriod(period);
    List<ObligationRow> obligations = pocRepository.obligationsForPeriod(period);
    List<TaxInputRow> taxInputs = pocRepository.taxInputsForPeriod(period);

    BigDecimal domesticRevenue =
        invoices.stream()
            .filter(invoice -> "PLN".equals(invoice.currency()))
            .map(InvoiceRow::bookedNetPln)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal foreignBookedRevenue =
        invoices.stream()
            .filter(invoice -> !"PLN".equals(invoice.currency()))
            .map(InvoiceRow::bookedNetPln)
            .filter(value -> value != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal foreignSourceEur =
        invoices.stream()
            .filter(invoice -> "EUR".equals(invoice.currency()))
            .map(InvoiceRow::netAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    FxCalculation fx = calculateFx(invoices, foreignBookedRevenue, foreignSourceEur);
    RyczaltCalculation ryczalt =
        calculateRyczalt(
            period, invoices, correctionSources, domesticRevenue, fx, obligations, taxInputs);
    VatCalculation vat = calculateVat(period, invoices, correctionSources, expenses, obligations);
    AccountingProfile profile = accountingProfile();
    HealthContributionBasis basis = pocRepository.healthContributionBasis(period);
    if (basis == null) basis = profile.healthContributionBasis();
    ZusCalculation zus = calculateZus(profile, basis, taxInputs);
    List<ComparisonRow> comparisons =
        buildComparisons(
            period,
            domesticRevenue,
            foreignBookedRevenue,
            fx,
            ryczalt,
            vat,
            zus,
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
        zus,
        comparisons,
        invoices,
        expenses,
        reconciliations,
        obligations,
        bankTransactions);
  }

  private ZusCalculation calculateZus(
      AccountingProfile profile,
      HealthContributionBasis healthContributionBasis,
      List<TaxInputRow> taxInputs) {
    BigDecimal healthZus = healthContribution(taxInputs, healthContributionBasis);
    BigDecimal compulsorySocialZus = taxInput(taxInputs, "JDG_COMPULSORY_SOCIAL_ZUS");
    BigDecimal socialZus = profile.hasUop() ? BigDecimal.ZERO : compulsorySocialZus;
    String socialZusReasonCode =
        profile.hasUop()
            ? ZusCalculation.UOP_PRIMARY_INSURANCE
            : ZusCalculation.JDG_PRIMARY_INSURANCE;
    return new ZusCalculation(
        socialZus.setScale(2, RoundingMode.HALF_UP),
        healthZus.setScale(2, RoundingMode.HALF_UP),
        socialZus.add(healthZus).setScale(2, RoundingMode.HALF_UP),
        profile.hasUop(),
        socialZusReasonCode);
  }

  private BigDecimal healthContribution(
      List<TaxInputRow> taxInputs, HealthContributionBasis healthContributionBasis) {
    if (healthContributionBasis == HealthContributionBasis.HIGH) {
      return taxInput(taxInputs, "HEALTH_CONTRIBUTION_PAID");
    }
    return taxInput(taxInputs, "HEALTH_CONTRIBUTION_" + healthContributionBasis.name());
  }

  private List<ComparisonRow> buildComparisons(
      LocalDate period,
      BigDecimal domesticRevenue,
      BigDecimal foreignBookedRevenue,
      FxCalculation fx,
      RyczaltCalculation ryczalt,
      VatCalculation vat,
      ZusCalculation zus,
      List<ObligationRow> obligations,
      List<TaxInputRow> taxInputs) {
    BigDecimal sourceDerivedExpectedRevenue =
        domesticRevenue
            .add(foreignBookedRevenue)
            .add(ryczalt.julyOnlyCorrectionNetAdjustment())
            .setScale(2, RoundingMode.HALF_UP);
    BigDecimal explicitRevenueGolden = taxInput(taxInputs, "EXPECTED_REVENUE_PLN");
    BigDecimal expectedRevenue =
        explicitRevenueGolden.signum() == 0
            ? sourceDerivedExpectedRevenue
            : explicitRevenueGolden.setScale(2, RoundingMode.HALF_UP);
    BigDecimal calculatedRevenue =
        ryczalt.revenueBeforeDeductions().setScale(2, RoundingMode.HALF_UP);
    BigDecimal revenueDifference =
        calculatedRevenue.subtract(expectedRevenue).setScale(2, RoundingMode.HALF_UP);
    String revenueStatus =
        revenueDifference.signum() == 0
            ? "MATCH"
            : fx.status().startsWith("FX_UNAVAILABLE") || "NO_FX_SOURCE".equals(fx.status())
                ? "INPUTS_INCOMPLETE"
                : "DIFF";

    BigDecimal zusCalculated = zus.totalZus().setScale(2, RoundingMode.HALF_UP);
    BigDecimal zusExpected = obligationAmount(obligations, "ZUS").setScale(2, RoundingMode.HALF_UP);
    BigDecimal zusDifference =
        zusCalculated.subtract(zusExpected).setScale(2, RoundingMode.HALF_UP);
    boolean hasZusGolden = hasObligation(obligations, "ZUS");
    String zusStatus =
        !hasZusGolden
            ? "NO_GOLDEN"
            : zusCalculated.signum() == 0
                ? "INPUTS_INCOMPLETE"
                : zusDifference.signum() == 0
                    ? "MATCH"
                    : zus.hasUop() ? "DIFF" : "HISTORICAL_PROFILE_DIFF";
    String zusComparisonNote =
        !hasZusGolden
            ? "No captured historical ZUS obligation is available for this month."
            : zus.hasUop()
                ? "Captured 2026 ZUS golden is the historical health-only obligation under the qualifying-UoP profile; cash reconciliation is checked separately."
                : "Captured 2026 ZUS golden is health-only under the historical qualifying-UoP profile. The current profile disables UoP, so calculated total JDG ZUS also includes compulsory social ZUS; this difference is expected and is not a reconstruction failure.";

    return List.of(
        new ComparisonRow(
            "REVENUE",
            calculatedRevenue,
            expectedRevenue,
            revenueDifference,
            "PLN",
            revenueStatus,
            "Calculated from accounting-period sales and Investory FX; expected value comes from wFirma monthly revenue when captured."),
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
            "Declaration semantics: sales VAT and deductible purchase VAT are rounded independently to whole PLN before subtraction; July sales correction is represented separately."),
        new ComparisonRow(
            "ZUS", zusCalculated, zusExpected, zusDifference, "PLN", zusStatus, zusComparisonNote),
        new ComparisonRow(
            "FX",
            fx.calculatedPln(),
            fx.expectedPln(),
            fx.difference(),
            "PLN",
            fx.status(),
            fxNote(fx)));
  }

  private String fxNote(FxCalculation fx) {
    String note =
        "Foreign revenue converted through Investory CurrencyConversion and compared with the booked PLN value.";
    if (fx.unavailableInvoiceReferences().isEmpty()) return note;
    return note
        + " Unavailable invoice FX source(s): "
        + String.join(", ", fx.unavailableInvoiceReferences())
        + ". Booked PLN values are used only as an explicit fallback.";
  }

  private FxCalculation calculateFx(
      List<InvoiceRow> invoices, BigDecimal expectedForeignPln, BigDecimal foreignSourceEur) {
    List<InvoiceRow> eurInvoices =
        invoices.stream().filter(invoice -> "EUR".equals(invoice.currency())).toList();
    if (eurInvoices.isEmpty()) {
      return new FxCalculation(
          null,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          BigDecimal.ZERO,
          "NO_FX_SOURCE",
          List.of());
    }

    BigDecimal calculated = BigDecimal.ZERO;
    boolean bookedFallback = false;
    List<String> unavailableReferences = new ArrayList<>();
    for (InvoiceRow invoice : eurInvoices) {
      try {
        calculated =
            calculated.add(
                currencyConversion.convertToBaseCurrency(
                    invoice.netAmount(), CurrencyType.PLN, CurrencyType.EUR, invoice.fxRateDate()));
      } catch (CurrencyConversionUnavailableException ex) {
        unavailableReferences.add(invoice.reference());
        if (invoice.bookedNetPln() != null) {
          calculated = calculated.add(invoice.bookedNetPln());
          bookedFallback = true;
        }
      }
    }
    calculated = calculated.setScale(2, RoundingMode.HALF_UP);

    BigDecimal expected = expectedForeignPln.setScale(2, RoundingMode.HALF_UP);
    String status =
        !unavailableReferences.isEmpty()
            ? bookedFallback ? "FX_UNAVAILABLE_USING_BOOKED_FALLBACK" : "FX_UNAVAILABLE"
            : calculated.compareTo(expected) == 0 ? "MATCH" : "DIFF";
    return new FxCalculation(
        eurInvoices.getFirst().fxRateDate(),
        foreignSourceEur,
        calculated,
        expected,
        calculated.subtract(expected).setScale(2, RoundingMode.HALF_UP),
        status,
        List.copyOf(unavailableReferences));
  }

  private RyczaltCalculation calculateRyczalt(
      LocalDate period,
      List<InvoiceRow> invoices,
      List<InvoiceRow> correctionSources,
      BigDecimal domesticRevenue,
      FxCalculation fx,
      List<ObligationRow> obligations,
      List<TaxInputRow> taxInputs) {
    BigDecimal julyOnlyCorrectionNet =
        JULY_2026.equals(period)
            ? correctionSources.stream()
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
            .map(InvoiceRow::ryczaltRate)
            .filter(value -> value != null)
            .findFirst()
            .orElse(new BigDecimal("0.12"));

    BigDecimal calculatedTax = taxableBase.multiply(rate).setScale(0, RoundingMode.HALF_UP);
    BigDecimal expectedTax = obligationAmount(obligations, "RYCZALT");
    BigDecimal difference = calculatedTax.subtract(expectedTax);
    boolean hasGolden = hasObligation(obligations, "RYCZALT");
    BigDecimal revenueGolden = taxInput(taxInputs, "EXPECTED_REVENUE_PLN");
    boolean missingForeignSource =
        fx.status().equals("NO_FX_SOURCE")
            && revenueGolden.signum() != 0
            && revenueGolden.compareTo(domesticRevenue) != 0;
    String status =
        !hasGolden
            ? "NO_GOLDEN"
            : missingForeignSource
                ? "INPUTS_INCOMPLETE"
                : difference.signum() == 0 ? "MATCH" : "DIFF";

    return new RyczaltCalculation(
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
      List<InvoiceRow> correctionSources,
      List<ExpenseRow> expenses,
      List<ObligationRow> obligations) {
    BigDecimal outputBeforeCorrection =
        invoices.stream()
            .filter(invoice -> "PLN".equals(invoice.currency()))
            .map(InvoiceRow::vatAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal julyOnlySalesCorrectionVat =
        JULY_2026.equals(period)
            ? correctionSources.stream()
                .map(InvoiceRow::correctionVatAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
            : BigDecimal.ZERO;
    BigDecimal outputVat = outputBeforeCorrection.add(julyOnlySalesCorrectionVat);

    BigDecimal deductibleInputVat =
        expenses.stream()
            .map(ExpenseRow::deductibleVat)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

    BigDecimal calculatedVat =
        outputVat
            .setScale(0, RoundingMode.HALF_UP)
            .subtract(deductibleInputVat.setScale(0, RoundingMode.HALF_UP));
    BigDecimal expectedVat = obligationAmount(obligations, "VAT");
    BigDecimal difference = calculatedVat.subtract(expectedVat);
    boolean hasGolden = hasObligation(obligations, "VAT");
    boolean expenseDocumentsMissing = expenses.isEmpty() && hasGolden;
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
        BigDecimal.ZERO,
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
    Set<Long> usedBankTransactionIds = new HashSet<>();

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
              .filter(row -> usedBankTransactionIds.add(row.id()))
              .findFirst()
              .orElse(null);

      String explanation =
          invoice.correctionGrossAmount().signum() == 0
              ? "Exact expected receivable matched to a business customer receipt."
              : "Corrected receivable matched; the correction remains a July-specific historical fixture.";

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
      List<BankRow> payments =
          bankTransactions.stream()
              .filter(row -> "BUSINESS".equals(row.scope()))
              .filter(
                  row ->
                      obligationTransactionType(obligation.obligationType())
                          .equals(row.transactionType()))
              .filter(
                  row ->
                      row.relatedPeriod() == null
                          || obligation.taxPeriod() == null
                          || obligation.taxPeriod().equals(row.relatedPeriod()))
              .toList();
      BigDecimal paidAmount =
          payments.stream().map(row -> row.amount().abs()).reduce(BigDecimal.ZERO, BigDecimal::add);
      LocalDate paymentDate =
          payments.stream().map(BankRow::bookingDate).max(LocalDate::compareTo).orElse(null);
      String status = obligation.status();
      String explanation = obligation.note();
      if (!"REPORTING_ONLY".equals(status)) {
        AccountingPaymentStatus paymentStatus =
            DUE_DATE_POLICY.paymentStatus(obligation.expectedAmount(), paidAmount);
        status = paymentStatus.name();
        if (paymentStatus != AccountingPaymentStatus.PAID) {
          BigDecimal cashDifference =
              paidAmount.subtract(obligation.expectedAmount()).setScale(2, RoundingMode.HALF_UP);
          explanation =
              obligation.note() + " Cash difference: " + cashDifference.toPlainString() + " PLN.";
        }
      }
      if ("REPORTING_ONLY".equals(status)) {
        paidAmount = BigDecimal.ZERO;
        paymentDate = null;
      }
      result.add(
          new ReconciliationRow(
              obligation.obligationType(),
              "OBLIGATION_PAYMENT",
              obligation.expectedAmount(),
              "PLN",
              paidAmount,
              paymentDate,
              status,
              explanation));
    }

    return List.copyOf(result);
  }

  private String obligationTransactionType(String obligationType) {
    return switch (obligationType) {
      case "RYCZALT" -> "RYCZALT_PAYMENT";
      case "VAT" -> "VAT_PAYMENT";
      case "ZUS" -> "ZUS_PAYMENT";
      default -> "__NO_ACCOUNTING_PAYMENT__";
    };
  }
}
