package com.smartbox.investory.poc.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.ComparisonRow;
import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.InvoiceRow;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AccountingGoldenIT extends FastDatabaseTest {

  private static final LocalDate JANUARY = LocalDate.of(2026, 1, 1);
  private static final LocalDate FEBRUARY = LocalDate.of(2026, 2, 1);
  private static final LocalDate APRIL = LocalDate.of(2026, 4, 1);
  private static final LocalDate MAY = LocalDate.of(2026, 5, 1);
  private static final LocalDate JUNE = LocalDate.of(2026, 6, 1);
  private static final LocalDate JULY = LocalDate.of(2026, 7, 1);

  @Autowired private AccountingFactService service;
  @MockitoBean private CurrencyRateService currencyConversion;

  @Test
  void januaryUsesCapturedEurSourceAndPriorBusinessDayNbpRate() {
    when(currencyConversion.convertToBaseCurrency(
            new BigDecimal("7636.0000"),
            CurrencyType.PLN,
            CurrencyType.EUR,
            LocalDate.of(2026, 1, 30)))
        .thenReturn(new BigDecimal("32171.2300"));

    AccountingMonthSnapshot snapshot = service.snapshot(JANUARY);

    assertComparison(snapshot, "REVENUE", "61771.23", "61771.23", "0.00", "MATCH");
    assertComparison(snapshot, "RYCZALT", "7329", "7329.0000", "0.0000", "MATCH");
    assertComparison(snapshot, "VAT", "6714", "6714.0000", "0.0000", "MATCH");
    assertComparison(snapshot, "ZUS", "1495.04", "1495.04", "0.00", "MATCH");
    assertComparison(snapshot, "FX", "32171.23", "32171.23", "0.00", "MATCH");
    assertThat(snapshot.foreignSourceRevenueEur()).isEqualByComparingTo("7636.0000");
    assertThat(snapshot.fx().rateDate()).isEqualTo(LocalDate.of(2026, 1, 30));
    assertThat(snapshot.invoices())
        .extracting(InvoiceRow::reference)
        .containsExactly("PDC-V1650-11", "EU-SERVICE-2026-01");
  }

  @Test
  void cleanFebruaryReconstructsExactGoldenResultsFromPersistedFixtures() {
    when(currencyConversion.convertToBaseCurrency(
            new BigDecimal("7636.0000"),
            CurrencyType.PLN,
            CurrencyType.EUR,
            LocalDate.of(2026, 2, 27)))
        .thenReturn(new BigDecimal("32249.1200"));

    AccountingMonthSnapshot snapshot = service.snapshot(FEBRUARY);

    assertComparison(snapshot, "REVENUE", "61849.12", "61849.12", "0.00", "MATCH");
    assertComparison(snapshot, "RYCZALT", "7332", "7332.0000", "0.0000", "MATCH");
    assertComparison(snapshot, "VAT", "6707", "6707.0000", "0.0000", "MATCH");
    assertComparison(snapshot, "ZUS", "1495.04", "1495.04", "0.00", "MATCH");
    assertComparison(snapshot, "FX", "32249.12", "32249.12", "0.00", "MATCH");
    assertThat(snapshot.vat().deductibleInputVat()).isEqualByComparingTo("100.55");
  }

  @Test
  void aprilUsesEightPercentBpFuelVatAndMatchesWfirmaPurchaseVat() {
    when(currencyConversion.convertToBaseCurrency(
            new BigDecimal("7636.0000"),
            CurrencyType.PLN,
            CurrencyType.EUR,
            LocalDate.of(2026, 4, 29)))
        .thenReturn(new BigDecimal("32481.2500"));

    AccountingMonthSnapshot snapshot = service.snapshot(APRIL);

    assertComparison(snapshot, "REVENUE", "63561.25", "63561.25", "0.00", "MATCH");
    assertComparison(snapshot, "RYCZALT", "7538", "7538.0000", "0.0000", "MATCH");
    assertComparison(snapshot, "VAT", "7028", "7028.0000", "0.0000", "MATCH");
    assertComparison(snapshot, "FX", "32481.25", "32481.25", "0.00", "MATCH");
    assertThat(snapshot.vat().deductibleInputVat()).isEqualByComparingTo("120.20");
  }

  @Test
  void mayUsesSourceFuelVatAndMatchesWfirmaPurchaseVat() {
    when(currencyConversion.convertToBaseCurrency(
            new BigDecimal("7636.0000"),
            CurrencyType.PLN,
            CurrencyType.EUR,
            LocalDate.of(2026, 5, 29)))
        .thenReturn(new BigDecimal("32317.0800"));

    AccountingMonthSnapshot snapshot = service.snapshot(MAY);

    assertComparison(snapshot, "REVENUE", "61917.08", "61917.08", "0.00", "MATCH");
    assertComparison(snapshot, "RYCZALT", "7340", "7340.0000", "0.0000", "MATCH");
    assertComparison(snapshot, "VAT", "6601", "6601.0000", "0.0000", "MATCH");
    assertComparison(snapshot, "FX", "32317.08", "32317.08", "0.00", "MATCH");
    assertThat(snapshot.vat().deductibleInputVat()).isEqualByComparingTo("207.42");
  }

  @Test
  void juneUsesOriginalFv4RevenueAndEightPercentFuelVat() {
    when(currencyConversion.convertToBaseCurrency(
            new BigDecimal("7636.0000"),
            CurrencyType.PLN,
            CurrencyType.EUR,
            LocalDate.of(2026, 6, 29)))
        .thenReturn(new BigDecimal("32750.8000"));

    AccountingMonthSnapshot snapshot = service.snapshot(JUNE);

    assertComparison(snapshot, "REVENUE", "65310.80", "65310.80", "0.00", "MATCH");
    assertComparison(snapshot, "RYCZALT", "7748", "7748.0000", "0.0000", "MATCH");
    assertComparison(snapshot, "VAT", "7293", "7293.0000", "0.0000", "MATCH");
    assertThat(snapshot.vat().deductibleInputVat()).isEqualByComparingTo("196.10");
    assertThat(snapshot.domesticRevenueNetPln()).isEqualByComparingTo("32560.0000");
    assertThat(snapshot.ryczalt().julyOnlyCorrectionNetAdjustment()).isZero();
    assertThat(snapshot.invoices())
        .extracting(InvoiceRow::reference)
        .containsExactly("FV 4/2026", "EU-SERVICE-2026-06");
  }

  @Test
  void julySpecialMonthUsesDocumentPurchaseVatAndExcludesPrivateAndInternalCashFlows() {
    when(currencyConversion.convertToBaseCurrency(
            new BigDecimal("7636.0000"),
            CurrencyType.PLN,
            CurrencyType.EUR,
            LocalDate.of(2026, 7, 30)))
        .thenReturn(new BigDecimal("32908.8700"));

    AccountingMonthSnapshot snapshot = service.snapshot(JULY);

    assertComparison(snapshot, "REVENUE", "49008.87", "49008.87", "0.00", "MATCH");
    assertComparison(snapshot, "RYCZALT", "5791", "5791.0000", "0.0000", "MATCH");
    assertComparison(snapshot, "VAT", "3557", "3557.0000", "0.0000", "MATCH");
    assertComparison(snapshot, "ZUS", "1495.04", "1495.04", "0.00", "MATCH");
    assertComparison(snapshot, "FX", "32908.87", "32908.87", "0.00", "MATCH");

    assertThat(snapshot.ryczalt().julyOnlyCorrectionNetAdjustment())
        .isEqualByComparingTo("-150.00");
    assertThat(snapshot.vat().julyOnlySalesCorrectionVat()).isEqualByComparingTo("-34.50");
    assertThat(snapshot.vat().deductibleInputVat()).isEqualByComparingTo("145.99");
    assertThat(snapshot.vat().julyOnlyVatCorrectionAdjustment()).isZero();

    assertThat(snapshot.invoices())
        .extracting(InvoiceRow::reference)
        .containsExactly("FV 5/2026", "EU-SERVICE-2026-07")
        .doesNotContain("FV 4/2026");

    assertThat(snapshot.bankTransactions())
        .anyMatch(row -> "EXCLUDED_INTERNAL".equals(row.scope()))
        .anyMatch(row -> "EXCLUDED_PRIVATE".equals(row.scope()));

    assertThat(snapshot.reconciliations())
        .noneMatch(row -> "Transfer of funds".equals(row.reference()))
        .noneMatch(row -> "26M07 PPE rental".equals(row.reference()))
        .noneMatch(row -> "FV 4/2026".equals(row.reference()));

    assertThat(snapshot.reconciliations())
        .filteredOn(row -> "ZUS".equals(row.reference()))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.status()).isEqualTo("DIFF");
              assertThat(row.expectedAmount()).isEqualByComparingTo("1495.04");
              assertThat(row.matchedAmount()).isEqualByComparingTo("1495.00");
            });
  }

  private void assertComparison(
      AccountingMonthSnapshot snapshot,
      String area,
      String calculated,
      String expected,
      String difference,
      String status) {
    ComparisonRow row =
        snapshot.comparisons().stream()
            .filter(candidate -> area.equals(candidate.area()))
            .findFirst()
            .orElseThrow();

    assertThat(row.calculated()).isEqualByComparingTo(calculated);
    assertThat(row.expected()).isEqualByComparingTo(expected);
    assertThat(row.difference()).isEqualByComparingTo(difference);
    assertThat(row.status()).isEqualTo(status);
  }
}
