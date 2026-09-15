package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.accounting.AccountingMonthSnapshot.BankRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.ComparisonRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.ExpenseRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.InvoiceRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.ObligationRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.TaxInputRow;
import com.smartbox.investory.accounting.infrastructure.persistence.AccountingFactRepository;
import com.smartbox.investory.accounting.infrastructure.persistence.AccountingPocRepository;
import com.smartbox.investory.accounting.service.AccountingFactService;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyConversionUnavailableException;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountingFactServiceTest {
  private static final LocalDate JUNE = LocalDate.of(2026, 6, 1);
  private static final LocalDate JULY = LocalDate.of(2026, 7, 1);
  private static final LocalDate JANUARY = LocalDate.of(2026, 1, 1);
  private static final LocalDate FEBRUARY = LocalDate.of(2026, 2, 1);

  @Test
  void calculatesNormalJdgSocialAndHealthZusWithoutUop() {
    AccountingMonthSnapshot snapshot =
        serviceForZus(new AccountingProfile(false)).snapshot(JANUARY);

    assertThat(snapshot.zus().socialZus()).isEqualByComparingTo("1788.29");
    assertThat(snapshot.zus().healthZus()).isEqualByComparingTo("1495.04");
    assertThat(snapshot.zus().totalZus()).isEqualByComparingTo("3283.33");
    assertThat(snapshot.zus().hasUop()).isFalse();
  }

  @Test
  void waivesOnlySocialZusWhenQualifyingUopExists() {
    AccountingMonthSnapshot snapshot = serviceForZus(new AccountingProfile(true)).snapshot(JANUARY);

    assertThat(snapshot.zus().socialZus()).isZero();
    assertThat(snapshot.zus().healthZus()).isEqualByComparingTo("1495.04");
    assertThat(snapshot.zus().totalZus()).isEqualByComparingTo("1495.04");
    assertThat(snapshot.zus().hasUop()).isTrue();
    assertThat(snapshot.zus().socialZusReason()).contains("Qualifying UoP");
  }

  @Test
  void historicalUopStartDateChangesSocialZusForEachMonth() {
    LocalDate historical = LocalDate.of(2025, 6, 1);
    AccountingFactRepository facts = mock(AccountingFactRepository.class);
    AccountingPocRepository repository = mock(AccountingPocRepository.class);
    CurrencyConversion fx = mock(CurrencyConversion.class);
    when(repository.accountingProfile()).thenReturn(new AccountingProfile(true));
    when(repository.invoicesForPeriod(historical))
        .thenReturn(
            List.of(
                invoice(
                    historical,
                    historical.plusDays(14),
                    null,
                    "SALE-2025-06",
                    "CUSTOMER",
                    "PLN",
                    "10000.00",
                    "2300.00",
                    "12300.00",
                    "0",
                    "0",
                    "0",
                    "12300.00",
                    "10000.00")));
    when(repository.invoicesForPeriod(historical.minusMonths(1))).thenReturn(List.of());
    when(repository.expensesForPeriod(historical)).thenReturn(List.of());
    when(repository.bankTransactionsForPeriod(historical)).thenReturn(List.of());
    when(repository.obligationsForPeriod(historical)).thenReturn(List.of());
    when(repository.taxInputsForPeriod(historical)).thenReturn(List.of());
    when(repository.yearToDateRevenue(historical)).thenReturn(new BigDecimal("10000.00"));
    when(repository.businessActivityPeriods(1L))
        .thenReturn(List.of(new BusinessActivityPeriod(LocalDate.of(2026, 1, 1), null)));
    when(repository.employmentPeriods(1L))
        .thenReturn(List.of(new EmploymentInsurancePeriod(LocalDate.of(2025, 8, 1), null, true)));
    when(repository.taxProfilePeriods(1L))
        .thenReturn(
            List.of(
                new AccountingTaxProfilePeriod(
                    LocalDate.of(2026, 1, 1),
                    null,
                    true,
                    new BigDecimal("0.12"),
                    true,
                    true,
                    "JDG",
                    false)));

    LocalDate uopStartMonth = LocalDate.of(2025, 8, 1);
    when(repository.invoicesForPeriod(uopStartMonth))
        .thenReturn(
            List.of(
                invoice(
                    uopStartMonth,
                    uopStartMonth.plusDays(14),
                    null,
                    "SALE-2025-08",
                    "CUSTOMER",
                    "PLN",
                    "10000.00",
                    "2300.00",
                    "12300.00",
                    "0",
                    "0",
                    "0",
                    "12300.00",
                    "10000.00")));
    when(repository.invoicesForPeriod(uopStartMonth.minusMonths(1))).thenReturn(List.of());
    when(repository.expensesForPeriod(uopStartMonth)).thenReturn(List.of());
    when(repository.bankTransactionsForPeriod(uopStartMonth)).thenReturn(List.of());
    when(repository.obligationsForPeriod(uopStartMonth)).thenReturn(List.of());
    when(repository.taxInputsForPeriod(uopStartMonth)).thenReturn(List.of());
    when(repository.yearToDateRevenue(uopStartMonth)).thenReturn(new BigDecimal("20000.00"));

    var service = new AccountingFactService(facts, repository, fx);
    AccountingMonthSnapshot beforeUop = service.snapshot(historical);
    AccountingMonthSnapshot withUop = service.snapshot(uopStartMonth);

    assertThat(beforeUop.zus().totalZus()).isEqualByComparingTo("3031.44");
    assertThat(beforeUop.zus().socialZus()).isEqualByComparingTo("1646.47");
    assertThat(beforeUop.zus().healthZus()).isEqualByComparingTo("1384.97");
    assertThat(withUop.zus().totalZus()).isEqualByComparingTo("1384.97");
    assertThat(withUop.zus().socialZus()).isZero();
    assertThat(withUop.zus().healthZus()).isEqualByComparingTo("1384.97");
  }

  @Test
  void uopDoesNotChangeVatRevenueOrMonthSelection() {
    AccountingFactService withoutUop = serviceForZus(new AccountingProfile(false));
    AccountingFactService withUop = serviceForZus(new AccountingProfile(true));

    AccountingMonthSnapshot noUopJanuary = withoutUop.snapshot(JANUARY);
    AccountingMonthSnapshot uopJanuary = withUop.snapshot(JANUARY);
    AccountingMonthSnapshot uopFebruary = withUop.snapshot(FEBRUARY);

    assertThat(uopJanuary.vat().calculatedVat())
        .isEqualByComparingTo(noUopJanuary.vat().calculatedVat());
    assertThat(uopJanuary.totalBookedRevenuePln())
        .isEqualByComparingTo(noUopJanuary.totalBookedRevenuePln());
    assertThat(uopJanuary.zus().hasUop()).isTrue();
    assertThat(uopFebruary.zus().hasUop()).isTrue();
  }

  @Test
  void calculatesOperationalMonthFromNormalizedFactsWithoutGoldenComparison() {
    AccountingMonthSnapshot snapshot =
        currentMonthService(List.of()).snapshot(LocalDate.of(2026, 9, 1));

    assertThat(snapshot.calculationMode()).isEqualTo(AccountingCalculationMode.CURRENT_CALCULATION);
    assertThat(snapshot.comparisons()).isEmpty();
    assertThat(snapshot.readiness()).isEqualTo(AccountingReadiness.READY);
    assertThat(snapshot.totalBookedRevenuePln()).isEqualByComparingTo("1000.00");
  }

  @Test
  void marksOperationalMonthIncompleteWhenRequiredInputsAreMissing() {
    AccountingFactRepository facts = mock(AccountingFactRepository.class);
    AccountingPocRepository repository = mock(AccountingPocRepository.class);
    CurrencyConversion fx = mock(CurrencyConversion.class);
    when(repository.accountingProfile()).thenReturn(new AccountingProfile(false));
    when(repository.invoicesForPeriod(LocalDate.of(2026, 9, 1))).thenReturn(List.of());
    when(repository.expensesForPeriod(LocalDate.of(2026, 9, 1))).thenReturn(List.of());
    when(repository.bankTransactionsForPeriod(LocalDate.of(2026, 9, 1))).thenReturn(List.of());
    when(repository.obligationsForPeriod(LocalDate.of(2026, 9, 1))).thenReturn(List.of());
    when(repository.taxInputsForPeriod(LocalDate.of(2026, 9, 1))).thenReturn(List.of());

    AccountingMonthSnapshot snapshot =
        new AccountingFactService(facts, repository, fx).snapshot(LocalDate.of(2026, 9, 1));

    assertThat(snapshot.readiness()).isEqualTo(AccountingReadiness.INCOMPLETE);
    assertThat(snapshot.issues())
        .extracting(AccountingIssue::type)
        .contains("MISSING_REQUIRED_INPUT", "MISSING_ZUS_RULE_INPUT");
  }

  @Test
  void sourceReviewOverridesOperationalInputReadiness() {
    AccountingIssue issue =
        new AccountingIssue(
            "SOURCE_REVIEW_REQUIRED", "REVIEW_REQUIRED", "KSEF-9", "Unknown VAT deduction");
    AccountingMonthSnapshot snapshot =
        currentMonthService(List.of(issue)).snapshot(LocalDate.of(2026, 9, 1));

    assertThat(snapshot.readiness()).isEqualTo(AccountingReadiness.REVIEW_REQUIRED);
    assertThat(snapshot.issues()).contains(issue);
  }

  @Test
  void reconstructsJulyFromSeparateSalesCorrectionAndPurchaseDocuments() {
    AccountingFactRepository factRepository = mock(AccountingFactRepository.class);
    AccountingPocRepository repository = mock(AccountingPocRepository.class);
    CurrencyConversion fx = mock(CurrencyConversion.class);
    AccountingFactService service = new AccountingFactService(factRepository, repository, fx);

    InvoiceRow juneCorrectionSource =
        invoice(
            JUNE,
            LocalDate.of(2026, 6, 30),
            null,
            "FV 4/2026",
            "CUSTOMER_PL_001",
            "PLN",
            "32560.00",
            "7488.80",
            "40048.80",
            "-150.00",
            "-34.50",
            "-184.50",
            "39864.30",
            "32560.00");
    InvoiceRow domestic =
        invoice(
            JULY,
            LocalDate.of(2026, 7, 31),
            null,
            "FV 5/2026",
            "CUSTOMER_PL_002",
            "PLN",
            "16250.00",
            "3737.50",
            "19987.50",
            "0",
            "0",
            "0",
            "19987.50",
            "16250.00");
    InvoiceRow eu =
        invoice(
            JULY,
            LocalDate.of(2026, 7, 31),
            LocalDate.of(2026, 7, 30),
            "EU-SERVICE-2026-07",
            "CUSTOMER_EU_001",
            "EUR",
            "7636.00",
            "0",
            "7636.00",
            "0",
            "0",
            "0",
            "7636.00",
            "32908.87");

    when(repository.invoicesForPeriod(JULY)).thenReturn(List.of(domestic, eu));
    when(repository.invoicesForPeriod(JUNE)).thenReturn(List.of(juneCorrectionSource));
    when(repository.expensesForPeriod(JULY))
        .thenReturn(
            List.of(
                expense("SALSOFT", "68.54", "1.00", "68.54"),
                expense("BP-1", "86.62", "0.50", "43.31"),
                expense("BP-2", "68.27", "0.50", "34.14")));
    when(repository.bankTransactionsForPeriod(JULY))
        .thenReturn(
            List.of(
                bank(
                    LocalDate.of(2026, 8, 5),
                    JULY,
                    "Service Agreements",
                    "CUSTOMER_EU_001",
                    "EUR",
                    "7636.00"),
                bank(
                    LocalDate.of(2026, 8, 13),
                    JULY,
                    "FV 5/2026",
                    "CUSTOMER_PL_002",
                    "PLN",
                    "19987.50")));
    when(repository.obligationsForPeriod(JULY))
        .thenReturn(
            List.of(
                obligation(JULY, "RYCZALT", "5791"),
                obligation(JULY, "VAT", "3557"),
                obligation(JULY, "ZUS", "1495.04")));
    when(repository.taxInputsForPeriod(JULY))
        .thenReturn(
            List.of(
                new TaxInputRow("HEALTH_CONTRIBUTION_PAID", new BigDecimal("1495.04"), "golden"),
                new TaxInputRow("EXPECTED_REVENUE_PLN", new BigDecimal("49008.87"), "wFirma")));
    when(fx.convertToBaseCurrency(
            new BigDecimal("7636.00"),
            CurrencyType.PLN,
            CurrencyType.EUR,
            LocalDate.of(2026, 7, 30)))
        .thenReturn(new BigDecimal("33058.87"));

    AccountingMonthSnapshot snapshot = service.snapshot(JULY);

    assertThat(snapshot.invoices())
        .extracting(InvoiceRow::reference)
        .containsExactly("FV 5/2026", "EU-SERVICE-2026-07");
    assertThat(snapshot.ryczalt().julyOnlyCorrectionNetAdjustment())
        .isEqualByComparingTo("-150.00");
    assertThat(snapshot.ryczalt().taxableBase()).isEqualByComparingTo("48261");
    assertThat(snapshot.ryczalt().calculatedTax()).isEqualByComparingTo("5791");
    assertThat(snapshot.vat().julyOnlySalesCorrectionVat()).isEqualByComparingTo("-34.50");
    assertThat(snapshot.vat().deductibleInputVat()).isEqualByComparingTo("145.99");
    assertThat(snapshot.vat().julyOnlyVatCorrectionAdjustment()).isZero();
    assertThat(snapshot.vat().calculatedVat()).isEqualByComparingTo("3557");
    assertThat(snapshot.comparisons())
        .filteredOn(row -> !"FX".equals(row.area()))
        .allMatch(row -> "MATCH".equals(row.status()));
    assertThat(snapshot.comparisons())
        .filteredOn(row -> "FX".equals(row.area()))
        .singleElement()
        .satisfies(row -> assertThat(row.status()).isEqualTo("DIFF"));
  }

  private AccountingFactService serviceForZus(AccountingProfile profile) {
    AccountingFactRepository factRepository = mock(AccountingFactRepository.class);
    AccountingPocRepository repository = mock(AccountingPocRepository.class);
    CurrencyConversion fx = mock(CurrencyConversion.class);
    when(repository.accountingProfile()).thenReturn(profile);
    when(repository.invoicesForPeriod(JANUARY)).thenReturn(List.of());
    when(repository.invoicesForPeriod(FEBRUARY)).thenReturn(List.of());
    when(repository.invoicesForPeriod(JANUARY.minusMonths(1))).thenReturn(List.of());
    when(repository.invoicesForPeriod(FEBRUARY.minusMonths(1))).thenReturn(List.of());
    when(repository.expensesForPeriod(JANUARY)).thenReturn(List.of());
    when(repository.expensesForPeriod(FEBRUARY)).thenReturn(List.of());
    when(repository.bankTransactionsForPeriod(JANUARY)).thenReturn(List.of());
    when(repository.bankTransactionsForPeriod(FEBRUARY)).thenReturn(List.of());
    when(repository.obligationsForPeriod(JANUARY)).thenReturn(List.of());
    when(repository.obligationsForPeriod(FEBRUARY)).thenReturn(List.of());
    when(repository.taxInputsForPeriod(JANUARY))
        .thenReturn(
            List.of(
                new TaxInputRow("HEALTH_CONTRIBUTION_PAID", new BigDecimal("1495.04"), "fixture"),
                new TaxInputRow(
                    "JDG_COMPULSORY_SOCIAL_ZUS", new BigDecimal("1788.29"), "fixture")));
    when(repository.taxInputsForPeriod(FEBRUARY))
        .thenReturn(
            List.of(
                new TaxInputRow("HEALTH_CONTRIBUTION_PAID", new BigDecimal("1495.04"), "fixture"),
                new TaxInputRow(
                    "JDG_COMPULSORY_SOCIAL_ZUS", new BigDecimal("1788.29"), "fixture")));
    return new AccountingFactService(factRepository, repository, fx);
  }

  private AccountingFactService currentMonthService(List<AccountingIssue> issues) {
    LocalDate september = LocalDate.of(2026, 9, 1);
    AccountingFactRepository factRepository = mock(AccountingFactRepository.class);
    AccountingPocRepository repository = mock(AccountingPocRepository.class);
    CurrencyConversion fx = mock(CurrencyConversion.class);
    when(repository.accountingProfile()).thenReturn(new AccountingProfile(false));
    when(repository.invoicesForPeriod(september))
        .thenReturn(
            List.of(
                invoice(
                    september,
                    september.plusDays(10),
                    null,
                    "SEP-1",
                    "CUSTOMER",
                    "PLN",
                    "1000.00",
                    "230.00",
                    "1230.00",
                    "0",
                    "0",
                    "0",
                    "1230.00",
                    "1000.00")));
    when(repository.expensesForPeriod(september))
        .thenReturn(List.of(expense("EXP-1", "23.00", "1.00", "23.00")));
    when(repository.bankTransactionsForPeriod(september)).thenReturn(List.of());
    when(repository.obligationsForPeriod(september)).thenReturn(List.of());
    when(repository.taxInputsForPeriod(september))
        .thenReturn(
            List.of(
                new TaxInputRow("HEALTH_CONTRIBUTION_PAID", new BigDecimal("100.00"), "operator"),
                new TaxInputRow(
                    "JDG_COMPULSORY_SOCIAL_ZUS", new BigDecimal("200.00"), "operator")));
    when(repository.taxProfilePeriods(1L))
        .thenReturn(
            List.of(
                new AccountingTaxProfilePeriod(
                    september, null, true, new BigDecimal("0.12"), true, true, "JDG", false)));
    when(repository.vatTransactionsForPeriod(september))
        .thenReturn(
            List.of(
                new AccountingVatTransaction(
                    september.plusDays(10),
                    "SEP-SOURCE",
                    "SEP-1",
                    AccountingVatTransaction.Direction.SALE,
                    VatTreatment.DOMESTIC_VAT,
                    "PL",
                    "PL1234567890",
                    "NIP",
                    null,
                    null,
                    null,
                    new BigDecimal("1000.00"),
                    new BigDecimal("230.00"),
                    BigDecimal.ZERO,
                    "OFF"),
                new AccountingVatTransaction(
                    september.plusDays(10),
                    "SEP-EXP-SOURCE",
                    "EXP-1",
                    AccountingVatTransaction.Direction.PURCHASE,
                    VatTreatment.DOMESTIC_PURCHASE,
                    "PL",
                    "PL0987654321",
                    "NIP",
                    null,
                    null,
                    null,
                    new BigDecimal("100.00"),
                    new BigDecimal("23.00"),
                    new BigDecimal("23.00"),
                    "OFF")));
    when(repository.sourceIssuesForPeriod(september)).thenReturn(issues);
    return new AccountingFactService(factRepository, repository, fx);
  }

  @Test
  void reconstructsCleanFebruaryVatFromExpenseDocuments() {
    LocalDate february = LocalDate.of(2026, 2, 1);
    AccountingFactRepository factRepository = mock(AccountingFactRepository.class);
    AccountingPocRepository repository = mock(AccountingPocRepository.class);
    CurrencyConversion fx = mock(CurrencyConversion.class);
    AccountingFactService service = new AccountingFactService(factRepository, repository, fx);

    InvoiceRow domestic =
        invoice(
            february,
            LocalDate.of(2026, 2, 28),
            null,
            "PDC-V1650-12",
            "CUSTOMER_PL_001",
            "PLN",
            "29600.00",
            "6808.00",
            "36408.00",
            "0",
            "0",
            "0",
            "36408.00",
            "29600.00");
    InvoiceRow eu =
        invoice(
            february,
            LocalDate.of(2026, 2, 28),
            LocalDate.of(2026, 2, 27),
            "EU-SERVICE-2026-02",
            "CUSTOMER_EU_001",
            "EUR",
            "7636.00",
            "0",
            "7636.00",
            "0",
            "0",
            "0",
            "7636.00",
            "32249.12");

    when(repository.invoicesForPeriod(february)).thenReturn(List.of(domestic, eu));
    when(repository.expensesForPeriod(february))
        .thenReturn(
            List.of(
                expense("ACCOUNTING", "68.54", "1.00", "68.54"),
                expense("FUEL", "64.01", "0.50", "32.01")));
    when(repository.bankTransactionsForPeriod(february))
        .thenReturn(
            List.of(
                bank(
                    LocalDate.of(2026, 3, 13),
                    february,
                    "PDC-V1650-12",
                    "CUSTOMER_PL_001",
                    "PLN",
                    "36408.00"),
                bank(
                    LocalDate.of(2026, 3, 5),
                    february,
                    "Service Agreements",
                    "CUSTOMER_EU_001",
                    "EUR",
                    "7636.00")));
    when(repository.obligationsForPeriod(february))
        .thenReturn(
            List.of(
                obligation(february, "RYCZALT", "7332"),
                obligation(february, "VAT", "6707"),
                obligation(february, "ZUS", "1495.04")));
    when(repository.taxInputsForPeriod(february))
        .thenReturn(
            List.of(
                new TaxInputRow("HEALTH_CONTRIBUTION_PAID", new BigDecimal("1495.04"), "fixture"),
                new TaxInputRow("EXPECTED_REVENUE_PLN", new BigDecimal("61849.12"), "wFirma")));
    when(fx.convertToBaseCurrency(
            new BigDecimal("7636.00"),
            CurrencyType.PLN,
            CurrencyType.EUR,
            LocalDate.of(2026, 2, 27)))
        .thenReturn(new BigDecimal("32249.12"));

    AccountingMonthSnapshot snapshot = service.snapshot(february);

    assertThat(snapshot.vat().deductibleInputVat()).isEqualByComparingTo("100.55");
    assertThat(snapshot.vat().calculatedVat()).isEqualByComparingTo("6707");
    assertThat(snapshot.expenses()).hasSize(2);
    assertThat(snapshot.comparisons())
        .extracting(ComparisonRow::area)
        .containsExactly("REVENUE", "RYCZALT", "VAT", "ZUS", "FX");
    assertThat(snapshot.comparisons()).allMatch(row -> "MATCH".equals(row.status()));
  }

  @Test
  void convertsEveryEurInvoiceInsteadOfOnlyTheFirstOne() {
    AccountingFactRepository factRepository = mock(AccountingFactRepository.class);
    AccountingPocRepository repository = mock(AccountingPocRepository.class);
    CurrencyConversion fx = mock(CurrencyConversion.class);
    AccountingFactService service = new AccountingFactService(factRepository, repository, fx);
    InvoiceRow first =
        invoice(
            JANUARY,
            LocalDate.of(2026, 1, 15),
            LocalDate.of(2026, 1, 14),
            "EU-1",
            "CUSTOMER_EU_001",
            "EUR",
            "100.00",
            "0",
            "100.00",
            "0",
            "0",
            "0",
            "100.00",
            "400.00");
    InvoiceRow second =
        invoice(
            JANUARY,
            LocalDate.of(2026, 1, 31),
            LocalDate.of(2026, 1, 30),
            "EU-2",
            "CUSTOMER_EU_002",
            "EUR",
            "200.00",
            "0",
            "200.00",
            "0",
            "0",
            "0",
            "200.00",
            "800.00");

    when(repository.accountingProfile()).thenReturn(new AccountingProfile(true));
    when(repository.invoicesForPeriod(JANUARY)).thenReturn(List.of(first, second));
    when(repository.invoicesForPeriod(JANUARY.minusMonths(1))).thenReturn(List.of());
    when(repository.expensesForPeriod(JANUARY)).thenReturn(List.of());
    when(repository.bankTransactionsForPeriod(JANUARY)).thenReturn(List.of());
    when(repository.obligationsForPeriod(JANUARY)).thenReturn(List.of());
    when(repository.taxInputsForPeriod(JANUARY)).thenReturn(List.of());
    when(fx.convertToBaseCurrency(
            new BigDecimal("100.00"),
            CurrencyType.PLN,
            CurrencyType.EUR,
            LocalDate.of(2026, 1, 14)))
        .thenReturn(new BigDecimal("400.00"));
    when(fx.convertToBaseCurrency(
            new BigDecimal("200.00"),
            CurrencyType.PLN,
            CurrencyType.EUR,
            LocalDate.of(2026, 1, 30)))
        .thenReturn(new BigDecimal("800.00"));

    AccountingMonthSnapshot snapshot = service.snapshot(JANUARY);

    assertThat(snapshot.fx().sourceEur()).isEqualByComparingTo("300.00");
    assertThat(snapshot.fx().calculatedPln()).isEqualByComparingTo("1200.00");
    assertThat(snapshot.fx().expectedPln()).isEqualByComparingTo("1200.00");
    assertThat(snapshot.fx().status()).isEqualTo("MATCH");
  }

  @Test
  void identifiesUnavailableEurInvoiceAndUsesOnlyItsBookedValueAsExplicitFallback() {
    AccountingFactRepository factRepository = mock(AccountingFactRepository.class);
    AccountingPocRepository repository = mock(AccountingPocRepository.class);
    CurrencyConversion fx = mock(CurrencyConversion.class);
    AccountingFactService service = new AccountingFactService(factRepository, repository, fx);
    InvoiceRow available =
        invoice(
            JANUARY,
            JANUARY.plusDays(14),
            JANUARY.plusDays(13),
            "EU-AVAILABLE",
            "EU-1",
            "EUR",
            "100.00",
            "0",
            "100.00",
            "0",
            "0",
            "0",
            "100.00",
            "400.00");
    InvoiceRow unavailable =
        invoice(
            JANUARY,
            JANUARY.plusDays(30),
            JANUARY.plusDays(29),
            "EU-UNAVAILABLE",
            "EU-2",
            "EUR",
            "200.00",
            "0",
            "200.00",
            "0",
            "0",
            "0",
            "200.00",
            "800.00");
    when(repository.accountingProfile()).thenReturn(new AccountingProfile(true));
    when(repository.invoicesForPeriod(JANUARY)).thenReturn(List.of(available, unavailable));
    when(repository.invoicesForPeriod(JANUARY.minusMonths(1))).thenReturn(List.of());
    when(repository.expensesForPeriod(JANUARY)).thenReturn(List.of());
    when(repository.obligationsForPeriod(JANUARY)).thenReturn(List.of());
    when(repository.taxInputsForPeriod(JANUARY)).thenReturn(List.of());
    when(repository.bankTransactionsForPeriod(JANUARY)).thenReturn(List.of());
    when(fx.convertToBaseCurrency(
            new BigDecimal("100.00"), CurrencyType.PLN, CurrencyType.EUR, JANUARY.plusDays(13)))
        .thenReturn(new BigDecimal("400.00"));
    when(fx.convertToBaseCurrency(
            new BigDecimal("200.00"), CurrencyType.PLN, CurrencyType.EUR, JANUARY.plusDays(29)))
        .thenThrow(new CurrencyConversionUnavailableException("rate unavailable"));

    AccountingMonthSnapshot snapshot = service.snapshot(JANUARY);

    assertThat(snapshot.fx().calculatedPln()).isEqualByComparingTo("1200.00");
    assertThat(snapshot.fx().status()).isEqualTo("FX_UNAVAILABLE_USING_BOOKED_FALLBACK");
    assertThat(snapshot.fx().unavailableInvoiceReferences()).containsExactly("EU-UNAVAILABLE");
    assertThat(snapshot.comparisons())
        .filteredOn(row -> "FX".equals(row.area()))
        .singleElement()
        .extracting(AccountingMonthSnapshot.ComparisonRow::note)
        .asString()
        .contains("EU-UNAVAILABLE", "explicit fallback");
  }

  @Test
  void doesNotReuseOneBankPaymentForTwoInvoices() {
    AccountingFactRepository factRepository = mock(AccountingFactRepository.class);
    AccountingPocRepository repository = mock(AccountingPocRepository.class);
    CurrencyConversion fx = mock(CurrencyConversion.class);
    AccountingFactService service = new AccountingFactService(factRepository, repository, fx);
    InvoiceRow first =
        invoice(
            JANUARY,
            LocalDate.of(2026, 1, 15),
            null,
            "INV-1",
            "CUSTOMER_PL_001",
            "PLN",
            "100.00",
            "23.00",
            "123.00",
            "0",
            "0",
            "0",
            "123.00",
            "100.00");
    InvoiceRow second =
        invoice(
            JANUARY,
            LocalDate.of(2026, 1, 16),
            null,
            "INV-2",
            "CUSTOMER_PL_001",
            "PLN",
            "100.00",
            "23.00",
            "123.00",
            "0",
            "0",
            "0",
            "123.00",
            "100.00");
    when(repository.accountingProfile()).thenReturn(new AccountingProfile(true));
    when(repository.invoicesForPeriod(JANUARY)).thenReturn(List.of(first, second));
    when(repository.invoicesForPeriod(JANUARY.minusMonths(1))).thenReturn(List.of());
    when(repository.expensesForPeriod(JANUARY)).thenReturn(List.of());
    when(repository.obligationsForPeriod(JANUARY)).thenReturn(List.of());
    when(repository.taxInputsForPeriod(JANUARY)).thenReturn(List.of());
    when(repository.bankTransactionsForPeriod(JANUARY))
        .thenReturn(
            List.of(
                bank(
                    LocalDate.of(2026, 2, 1),
                    JANUARY,
                    "payment",
                    "CUSTOMER_PL_001",
                    "PLN",
                    "123.00")));

    List<AccountingMonthSnapshot.ReconciliationRow> rows =
        service.snapshot(JANUARY).reconciliations();

    assertThat(rows).filteredOn(row -> "INVOICE_PAYMENT".equals(row.kind())).hasSize(2);
    assertThat(rows)
        .filteredOn(row -> "INVOICE_PAYMENT".equals(row.kind()))
        .extracting(AccountingMonthSnapshot.ReconciliationRow::status)
        .containsExactly("MATCHED", "UNMATCHED");
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
        1L,
        period,
        saleDate,
        saleDate,
        fxRateDate,
        reference,
        customer,
        "SERVICE",
        currency,
        new BigDecimal(net),
        new BigDecimal(vat),
        new BigDecimal(gross),
        new BigDecimal(correctionNet),
        new BigDecimal(correctionVat),
        new BigDecimal(correctionGross),
        new BigDecimal(expectedReceivable),
        new BigDecimal(bookedNetPln),
        new BigDecimal("0.12"),
        "fixture");
  }

  private ExpenseRow expense(String reference, String vat, String ratio, String deductible) {
    return new ExpenseRow(
        1L,
        LocalDate.of(2026, 2, 1),
        null,
        reference,
        "SUPPLIER",
        "CATEGORY",
        "PLN",
        BigDecimal.ZERO,
        new BigDecimal(vat),
        BigDecimal.ZERO,
        new BigDecimal(ratio),
        new BigDecimal(deductible),
        "SOURCE_DOCUMENT",
        "fixture");
  }

  private BankRow bank(
      LocalDate bookingDate,
      LocalDate relatedPeriod,
      String reference,
      String customer,
      String currency,
      String amount) {
    return new BankRow(
        1L,
        bookingDate,
        relatedPeriod,
        reference,
        customer,
        currency,
        new BigDecimal(amount),
        "CUSTOMER_RECEIPT",
        "BUSINESS",
        "fixture");
  }

  private ObligationRow obligation(LocalDate period, String type, String amount) {
    return new ObligationRow(
        period,
        type,
        LocalDate.of(2026, 8, 20),
        new BigDecimal(amount),
        new BigDecimal(amount),
        LocalDate.of(2026, 8, 18),
        "MATCHED",
        "golden");
  }
}
