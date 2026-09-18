package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.accounting.AccountingMonthSnapshot.ComparisonRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.ObligationRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.ZusCalculation;
import com.smartbox.investory.accounting.infrastructure.persistence.AccountingFactRepository;
import com.smartbox.investory.accounting.infrastructure.persistence.AccountingPocRepository;
import com.smartbox.investory.accounting.service.AccountingFactService;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountingZusSemanticsTest {
  private static final LocalDate JANUARY = LocalDate.of(2026, 1, 1);

  @Test
  void exposesStableUopReasonCodeAndMatchesCapturedUopReference() {
    AccountingMonthSnapshot snapshot = snapshot(new AccountingProfile(true));

    assertThat(snapshot.zus().socialZusReasonCode())
        .isEqualTo(ZusCalculation.UOP_PRIMARY_INSURANCE);
    assertThat(snapshot.zus().socialZusReason()).contains("primary social-insurance title");
    assertThat(zusComparison(snapshot).status()).isEqualTo("MATCH");
    assertThat(zusComparison(snapshot).note()).contains("effective-dated UoP profile");
  }

  @Test
  void reportsNonUopDifferenceAgainstCapturedHealthOnlyReference() {
    AccountingMonthSnapshot snapshot = snapshot(new AccountingProfile(false));

    assertThat(snapshot.zus().socialZusReasonCode())
        .isEqualTo(ZusCalculation.JDG_PRIMARY_INSURANCE);
    assertThat(snapshot.zus().socialZus()).isEqualByComparingTo("1788.29");
    assertThat(snapshot.zus().healthZus()).isEqualByComparingTo("1495.04");
    assertThat(snapshot.zus().totalZus()).isEqualByComparingTo("3283.33");

    ComparisonRow comparison = zusComparison(snapshot);
    assertThat(comparison.status()).isEqualTo("DIFF");
    assertThat(comparison.note())
        .contains("effective-dated UoP profile")
        .contains("year-specific contribution rules");
  }

  private AccountingMonthSnapshot snapshot(AccountingProfile profile) {
    AccountingFactRepository factRepository = mock(AccountingFactRepository.class);
    AccountingPocRepository repository = mock(AccountingPocRepository.class);
    CurrencyConversion fx = mock(CurrencyConversion.class);

    when(repository.accountingProfile(1L)).thenReturn(profile);
    when(repository.invoicesForPeriod(1L, JANUARY)).thenReturn(List.of());
    when(repository.expensesForPeriod(1L, JANUARY)).thenReturn(List.of());
    when(repository.bankTransactionsForPeriod(1L, JANUARY)).thenReturn(List.of());
    when(repository.obligationsForPeriod(1L, JANUARY))
        .thenReturn(
            List.of(
                new ObligationRow(
                    "ZUS",
                    LocalDate.of(2026, 2, 20),
                    new BigDecimal("1495.04"),
                    new BigDecimal("1495.04"),
                    LocalDate.of(2026, 2, 18),
                    "MATCHED",
                    "Historical qualifying-UoP health-only golden.")));
    // ZUS must be calculated from the profile/rules. wFirma/ZUS evidence is comparison-only.
    when(repository.taxInputsForPeriod(1L, JANUARY)).thenReturn(List.of());

    return new AccountingFactService(factRepository, repository, fx).snapshot(1L, JANUARY);
  }

  private ComparisonRow zusComparison(AccountingMonthSnapshot snapshot) {
    return snapshot.comparisons().stream()
        .filter(row -> "ZUS".equals(row.area()))
        .findFirst()
        .orElseThrow();
  }
}
