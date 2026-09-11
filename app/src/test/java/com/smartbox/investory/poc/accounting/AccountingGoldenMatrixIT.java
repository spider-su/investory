package com.smartbox.investory.poc.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.poc.accounting.AccountingMonthSnapshot.ComparisonRow;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AccountingGoldenMatrixIT extends FastDatabaseTest {

  @Autowired private AccountingFactService service;

  @MockitoBean(name = "currencyRateService")
  private CurrencyRateService currencyConversion;

  @BeforeEach
  void configureCapturedFxGoldens() {
    stubFx(LocalDate.of(2026, 1, 30), "32171.23");
    stubFx(LocalDate.of(2026, 2, 27), "32249.12");
    stubFx(LocalDate.of(2026, 3, 30), "32706.52");
    stubFx(LocalDate.of(2026, 4, 29), "32481.25");
    stubFx(LocalDate.of(2026, 5, 29), "32317.08");
    stubFx(LocalDate.of(2026, 6, 29), "32750.80");
    stubFx(LocalDate.of(2026, 7, 30), "32908.87");
  }

  @Test
  void freezesKnownMonthlyComparisonMatrix() {
    Map<String, ExpectedMonth> expected = new LinkedHashMap<>();
    expected.put("2026-01", new ExpectedMonth("MATCH", "MATCH", "MATCH", "MATCH", "MATCH"));
    expected.put("2026-02", new ExpectedMonth("MATCH", "MATCH", "MATCH", "MATCH", "MATCH"));
    expected.put("2026-03", new ExpectedMonth("MATCH", "MATCH", "DIFF", "MATCH", "MATCH"));
    expected.put("2026-04", new ExpectedMonth("MATCH", "MATCH", "MATCH", "MATCH", "MATCH"));
    expected.put("2026-05", new ExpectedMonth("MATCH", "MATCH", "MATCH", "MATCH", "MATCH"));
    expected.put("2026-06", new ExpectedMonth("MATCH", "MATCH", "MATCH", "MATCH", "MATCH"));
    expected.put("2026-07", new ExpectedMonth("MATCH", "MATCH", "MATCH", "MATCH", "MATCH"));
    expected.put(
        "2026-08",
        new ExpectedMonth("MATCH", "NO_GOLDEN", "NO_GOLDEN", "NO_GOLDEN", "NO_FX_SOURCE"));

    expected.forEach(
        (month, monthExpected) -> {
          AccountingMonthSnapshot snapshot = service.snapshot(LocalDate.parse(month + "-01"));
          assertThat(status(snapshot, "REVENUE"))
              .as(month + " revenue")
              .isEqualTo(monthExpected.revenue());
          assertThat(status(snapshot, "RYCZALT"))
              .as(month + " ryczalt")
              .isEqualTo(monthExpected.ryczalt());
          assertThat(status(snapshot, "VAT")).as(month + " VAT").isEqualTo(monthExpected.vat());
          assertThat(status(snapshot, "ZUS")).as(month + " ZUS").isEqualTo(monthExpected.zus());
          assertThat(status(snapshot, "FX")).as(month + " FX").isEqualTo(monthExpected.fx());
        });
  }

  @Test
  void freezesCapturedDocumentLevelPurchaseVatTotals() {
    Map<String, String> expectedInputVat =
        Map.ofEntries(
            Map.entry("2026-01", "93.54"),
            Map.entry("2026-02", "100.55"),
            Map.entry("2026-03", "238.38"),
            Map.entry("2026-04", "120.20"),
            Map.entry("2026-05", "207.42"),
            Map.entry("2026-06", "196.10"),
            Map.entry("2026-07", "145.99"),
            Map.entry("2026-08", "0.00"));

    expectedInputVat.forEach(
        (month, expected) -> {
          AccountingMonthSnapshot snapshot = service.snapshot(LocalDate.parse(month + "-01"));
          assertThat(snapshot.vat().deductibleInputVat())
              .as(month + " document-level deductible purchase VAT")
              .isEqualByComparingTo(expected);
        });
  }

  @Test
  void keepsMarchOneZlotyVatDifferenceExplicitUntilFilingSemanticsAreProven() {
    AccountingMonthSnapshot march = service.snapshot(LocalDate.of(2026, 3, 1));
    ComparisonRow vat = comparison(march, "VAT");

    assertThat(march.vat().deductibleInputVat()).isEqualByComparingTo("238.38");
    assertThat(vat.calculated()).isEqualByComparingTo("7250");
    assertThat(vat.expected()).isEqualByComparingTo("7251");
    assertThat(vat.difference()).isEqualByComparingTo("-1");
    assertThat(vat.status()).isEqualTo("DIFF");
  }

  private void stubFx(LocalDate rateDate, String pln) {
    when(currencyConversion.convertToBaseCurrency(
            new BigDecimal("7636.0000"), CurrencyType.PLN, CurrencyType.EUR, rateDate))
        .thenReturn(new BigDecimal(pln));
  }

  private String status(AccountingMonthSnapshot snapshot, String area) {
    return comparison(snapshot, area).status();
  }

  private ComparisonRow comparison(AccountingMonthSnapshot snapshot, String area) {
    return snapshot.comparisons().stream()
        .filter(row -> area.equals(row.area()))
        .findFirst()
        .orElseThrow();
  }

  private record ExpectedMonth(String revenue, String ryczalt, String vat, String zus, String fx) {}
}
