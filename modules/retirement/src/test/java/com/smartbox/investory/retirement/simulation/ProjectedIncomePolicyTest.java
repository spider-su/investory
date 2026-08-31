package com.smartbox.investory.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.retirement.api.model.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Projected Income Policy")
class ProjectedIncomePolicyTest {
  @DisplayName("source Is The Safe Default")
  @Test
  void sourceIsTheSafeDefault() {
    assertThat(ProjectedIncomePolicy.SOURCE.rentalIncomeMode())
        .isEqualTo(ProjectedIncomePolicy.IncomeMode.SOURCE);
    assertThat(ProjectedIncomePolicy.SOURCE.bondCashIncomeMode())
        .isEqualTo(ProjectedIncomePolicy.IncomeMode.SOURCE);
  }

  @DisplayName("manual Values Are Explicit And Non Negative")
  @Test
  void manualValuesAreExplicitAndNonNegative() {
    var policy =
        new ProjectedIncomePolicy(
            ProjectedIncomePolicy.IncomeMode.MANUAL,
            new BigDecimal("200000"),
            ProjectedIncomePolicy.IncomeMode.MANUAL,
            new BigDecimal("30000"));
    assertThat(policy.manualRentalIncome()).isEqualByComparingTo("200000");
    assertThat(policy.manualBondCashIncome()).isEqualByComparingTo("30000");
  }
}
