package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountingProfileResolverTest {
  private final AccountingProfileResolver resolver = new AccountingProfileResolver();
  private final List<BusinessActivityPeriod> jdg =
      List.of(new BusinessActivityPeriod(LocalDate.of(2023, 1, 1), null));
  private final List<EmploymentInsurancePeriod> uop =
      List.of(
          new EmploymentInsurancePeriod(
              LocalDate.of(2022, 1, 1), LocalDate.of(2024, 6, 30), true),
          new EmploymentInsurancePeriod(LocalDate.of(2025, 1, 1), null, true));
  private final List<AccountingTaxProfilePeriod> tax =
      List.of(
          new AccountingTaxProfilePeriod(
              LocalDate.of(2023, 1, 1), null, true, new BigDecimal("0.12"), true, true, "JDG", false));

  @Test
  void resolvesOverlappingActivityAndUopAndPreservesGap() {
    assertThat(resolver.resolve(LocalDate.of(2023, 6, 1), jdg, uop, tax).qualifyingUop()).isTrue();
    assertThat(resolver.resolve(LocalDate.of(2024, 12, 1), jdg, uop, tax).qualifyingUop()).isFalse();
    assertThat(resolver.resolve(LocalDate.of(2025, 6, 1), jdg, uop, tax).qualifyingUop()).isTrue();
  }

  @Test
  void resolvesTaxApplicabilityForTheRequestedPeriod() {
    var result = resolver.resolve(LocalDate.of(2025, 6, 1), jdg, uop, tax);

    assertThat(result.jdgActive()).isTrue();
    assertThat(result.ryczaltRate()).isEqualByComparingTo("0.12");
    assertThat(result.vatRegistered()).isTrue();
    assertThat(result.vatEuRegistered()).isTrue();
  }
}
