package com.smartbox.investory.poc.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.ComparisonRow;
import com.smartbox.investory.shared.currency.CurrencyConversion;
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

  private static final LocalDate FEBRUARY = LocalDate.of(2026, 2, 1);
  private static final LocalDate JULY = LocalDate.of(2026, 7, 1);

  @Autowired private AccountingFactService service;
  @MockitoBean private CurrencyConversion currencyConversion;

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
    assertThat(snapshot.ryczalt().julyOnlyCorrectionNetAdjustment()).isZero();
    assertThat(snapshot.vat().julyOnlySalesCorrectionVat()).isZero();
    assertThat(snapshot.vat().julyOnlyVatCorrectionAdjustment()).isZero();
  }

  @Test
  void julySpecialMonthReconstructsExactGoldenResultsAndExcludesPrivateAndInternalCashFlows() {
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
    assertThat(snapshot.vat().julyOnlyVatCorrectionAdjustment()).isEqualByComparingTo("146.00");

    assertThat(snapshot.bankTransactions())
        .anyMatch(row -> "EXCLUDED_INTERNAL".equals(row.scope()))
        .anyMatch(row -> "EXCLUDED_PRIVATE".equals(row.scope()));

    assertThat(snapshot.reconciliations())
        .noneMatch(row -> "Transfer of funds".equals(row.reference()))
        .noneMatch(row -> "26M07 PPE rental".equals(row.reference()));

    assertThat(snapshot.reconciliations())
        .filteredOn(row -> "INVOICE_PAYMENT".equals(row.kind()))
        .allMatch(row -> "MATCHED".equals(row.status()));
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
