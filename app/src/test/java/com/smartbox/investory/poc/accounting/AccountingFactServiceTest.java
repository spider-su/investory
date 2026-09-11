package com.smartbox.investory.poc.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.BankRow;
import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.InvoiceRow;
import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.ObligationRow;
import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.TaxInputRow;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountingFactServiceTest {
  private static final LocalDate JULY = LocalDate.of(2026, 7, 1);

  @Test
  void reconstructsJulyGoldenRyczaltVatAndFx() {
    AccountingFactRepository factRepository = mock(AccountingFactRepository.class);
    AccountingPocRepository repository = mock(AccountingPocRepository.class);
    CurrencyConversion fx = mock(CurrencyConversion.class);
    AccountingFactService service = new AccountingFactService(factRepository, repository, fx);

    InvoiceRow juneCorrection =
        invoice(
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, "FV 4/2026",
            "CUSTOMER_PL_001", "PLN", "32560.00", "7488.80", "40048.80", "-150.00",
            "-34.50", "-184.50", "39864.30", "32410.00");
    InvoiceRow domestic =
        invoice(
            JULY, LocalDate.of(2026, 7, 31), null, "FV 5/2026", "CUSTOMER_PL_002", "PLN",
            "16250.00", "3737.50", "19987.50", "0", "0", "0", "19987.50", "16250.00");
    InvoiceRow eu =
        invoice(
            JULY, LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 30), "EU-SERVICE-2026-07",
            "CUSTOMER_EU_001", "EUR", "7636.00", "0", "7636.00", "0", "0", "0", "7636.00",
            "32908.87");

    when(repository.invoicesForPeriod(JULY)).thenReturn(List.of(juneCorrection, domestic, eu));
    when(repository.bankTransactionsForPeriod(JULY))
        .thenReturn(
            List.of(
                bank(LocalDate.of(2026, 7, 16), LocalDate.of(2026, 6, 1), "FV 4/2026", "CUSTOMER_PL_001", "PLN", "39864.30"),
                bank(LocalDate.of(2026, 8, 5), JULY, "Service Agreements", "CUSTOMER_EU_001", "EUR", "7636.00"),
                bank(LocalDate.of(2026, 8, 13), JULY, "FV 5/2026", "CUSTOMER_PL_002", "PLN", "19987.50")));
    when(repository.obligationsForPeriod(JULY))
        .thenReturn(List.of(obligation("RYCZALT", "5791"), obligation("VAT", "3557"), obligation("ZUS", "1495")));
    when(repository.taxInputsForPeriod(JULY))
        .thenReturn(
            List.of(
                new TaxInputRow("HEALTH_CONTRIBUTION_PAID", new BigDecimal("1495.04"), "golden"),
                new TaxInputRow("JULY_ONLY_VAT_CORRECTION_ADJUSTMENT", new BigDecimal("146.00"), "July-only correction fixture")));
    when(fx.convertToBaseCurrency(new BigDecimal("7636.00"), CurrencyType.PLN, CurrencyType.EUR, LocalDate.of(2026, 7, 30)))
        .thenReturn(new BigDecimal("32908.8692"));

    AccountingMonthSnapshot snapshot = service.snapshot(JULY);

    assertThat(snapshot.fx().status()).isEqualTo("MATCH");
    assertThat(snapshot.fx().calculatedPln()).isEqualByComparingTo("32908.87");
    assertThat(snapshot.ryczalt().revenueBeforeDeductions()).isEqualByComparingTo("49008.87");
    assertThat(snapshot.ryczalt().julyOnlyCorrectionNetAdjustment()).isEqualByComparingTo("-150.00");
    assertThat(snapshot.ryczalt().healthDeduction()).isEqualByComparingTo("747.52");
    assertThat(snapshot.ryczalt().calculatedTax()).isEqualByComparingTo("5791");
    assertThat(snapshot.ryczalt().status()).isEqualTo("MATCH");
    assertThat(snapshot.vat().outputVatAfterSalesCorrection()).isEqualByComparingTo("3703.00");
    assertThat(snapshot.vat().julyOnlyVatCorrectionAdjustment()).isEqualByComparingTo("146.00");
    assertThat(snapshot.vat().calculatedVat()).isEqualByComparingTo("3557");
    assertThat(snapshot.vat().status()).isEqualTo("MATCH");
    assertThat(snapshot.reconciliations())
        .filteredOn(row -> "INVOICE_PAYMENT".equals(row.kind()))
        .allMatch(row -> "MATCHED".equals(row.status()));
  }

  @Test
  void reconstructsCleanFebruaryWithoutJulyCorrectionLogic() {
    LocalDate february = LocalDate.of(2026, 2, 1);
    AccountingFactRepository factRepository = mock(AccountingFactRepository.class);
    AccountingPocRepository repository = mock(AccountingPocRepository.class);
    CurrencyConversion fx = mock(CurrencyConversion.class);
    AccountingFactService service = new AccountingFactService(factRepository, repository, fx);

    InvoiceRow domestic =
        invoice(
            february, LocalDate.of(2026, 2, 28), null, "PDC-V1650-12", "CUSTOMER_PL_001", "PLN",
            "29600.00", "6808.00", "36408.00", "0", "0", "0", "36408.00", "29600.00");
    InvoiceRow eu =
        invoice(
            february, LocalDate.of(2026, 2, 28), LocalDate.of(2026, 2, 27), "EU-SERVICE-2026-02",
            "CUSTOMER_EU_001", "EUR", "7636.00", "0", "7636.00", "0", "0", "0", "7636.00",
            "32249.12");

    when(repository.invoicesForPeriod(february)).thenReturn(List.of(domestic, eu));
    when(repository.bankTransactionsForPeriod(february))
        .thenReturn(
            List.of(
                bank(LocalDate.of(2026, 3, 13), february, "PDC-V1650-12", "CUSTOMER_PL_001", "PLN", "36408.00"),
                bank(LocalDate.of(2026, 3, 5), february, "Service Agreements", "CUSTOMER_EU_001", "EUR", "7636.00")));
    when(repository.obligationsForPeriod(february))
        .thenReturn(List.of(obligation("RYCZALT", "7332"), obligation("VAT", "6707"), obligation("ZUS", "1495.04")));
    when(repository.taxInputsForPeriod(february))
        .thenReturn(
            List.of(
                new TaxInputRow("HEALTH_CONTRIBUTION_PAID", new BigDecimal("1495.04"), "fixture"),
                new TaxInputRow("DEDUCTIBLE_INPUT_VAT", new BigDecimal("101.00"), "fixture")));
    when(fx.convertToBaseCurrency(new BigDecimal("7636.00"), CurrencyType.PLN, CurrencyType.EUR, LocalDate.of(2026, 2, 27)))
        .thenReturn(new BigDecimal("32249.12"));

    AccountingMonthSnapshot snapshot = service.snapshot(february);

    assertThat(snapshot.fx().status()).isEqualTo("MATCH");
    assertThat(snapshot.ryczalt().julyOnlyCorrectionNetAdjustment()).isZero();
    assertThat(snapshot.ryczalt().calculatedTax()).isEqualByComparingTo("7332");
    assertThat(snapshot.ryczalt().status()).isEqualTo("MATCH");
    assertThat(snapshot.vat().julyOnlySalesCorrectionVat()).isZero();
    assertThat(snapshot.vat().julyOnlyVatCorrectionAdjustment()).isEqualByComparingTo("101.00");
    assertThat(snapshot.vat().calculatedVat()).isEqualByComparingTo("6707");
    assertThat(snapshot.vat().status()).isEqualTo("MATCH");
  }

  private InvoiceRow invoice(
      LocalDate period,
      LocalDate saleDate,
      LocalDate fxRateDate,
      String reference,
      String customer,
      String currency,
      String net,
      String vat,
      String gross,
      String correctionNet,
      String correctionVat,
      String correctionGross,
      String expectedReceivable,
      String bookedNetPln) {
    return new InvoiceRow(
        1L, period, saleDate, saleDate, fxRateDate, reference, customer, "SERVICE", currency,
        new BigDecimal(net), new BigDecimal(vat), new BigDecimal(gross), new BigDecimal(correctionNet),
        new BigDecimal(correctionVat), new BigDecimal(correctionGross), new BigDecimal(expectedReceivable),
        new BigDecimal(bookedNetPln), new BigDecimal("0.12"), "fixture");
  }

  private BankRow bank(
      LocalDate bookingDate,
      LocalDate relatedPeriod,
      String reference,
      String customer,
      String currency,
      String amount) {
    return new BankRow(
        1L, bookingDate, relatedPeriod, reference, customer, currency, new BigDecimal(amount),
        "CUSTOMER_RECEIPT", "BUSINESS", "fixture");
  }

  private ObligationRow obligation(String type, String amount) {
    return new ObligationRow(
        type, LocalDate.of(2026, 8, 20), new BigDecimal(amount), new BigDecimal(amount),
        LocalDate.of(2026, 8, 18), "MATCHED", "golden");
  }
}
