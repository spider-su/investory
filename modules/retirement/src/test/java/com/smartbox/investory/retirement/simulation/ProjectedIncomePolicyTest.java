package com.smartbox.investory.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProjectedIncomePolicyTest {
  @Test
  void sourceIsTheSafeDefault() {
    assertThat(ProjectedIncomePolicy.SOURCE.rentalIncomeMode())
        .isEqualTo(ProjectedIncomePolicy.IncomeMode.SOURCE);
    assertThat(ProjectedIncomePolicy.SOURCE.bondCashIncomeMode())
        .isEqualTo(ProjectedIncomePolicy.IncomeMode.SOURCE);
  }

  @Test
  void manualValuesAreExplicitAndNonNegative() {
    var policy = new ProjectedIncomePolicy(ProjectedIncomePolicy.IncomeMode.MANUAL,
        new BigDecimal("200000"), ProjectedIncomePolicy.IncomeMode.MANUAL,
        new BigDecimal("30000"));
    assertThat(policy.manualRentalIncome()).isEqualByComparingTo("200000");
    assertThat(policy.manualBondCashIncome()).isEqualByComparingTo("30000");
  }
}
