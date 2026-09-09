package com.smartbox.investory.retirement.planning;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.api.model.Liquidity;
import com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation;
import com.smartbox.investory.profile.api.model.ProfileAssetProjection;
import com.smartbox.investory.profile.api.model.ProfileIncomeSummary;
import com.smartbox.investory.profile.api.model.ProjectedLongTermAsset;
import com.smartbox.investory.retirement.api.model.PlanningBaseline;
import com.smartbox.investory.retirement.planning.application.PlanningProfileBaseline;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.projection.ProjectionSource;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanningProfileBaselineTest {
  @Test
  void preservesProfileLongTermStateWhenLegacyBaselineHasNoSerializedState() {
    var asset =
        new ProjectedLongTermAsset(
            1L,
            "Bond",
            EconomicBucket.FIXED_INCOME,
            CurrencyType.PLN,
            bd("800"),
            Liquidity.LIQUID,
            List.of(),
            List.of(),
            null);
    var state =
        new ProfileAssetProjection(List.of(asset), BigDecimal.ZERO, 2025, ProjectionSource.ACTUAL);
    var profile =
        new InvestmentProfile(
            1L,
            CurrencyType.PLN,
            bd("900"),
            bd("800"),
            bd("1700"),
            bd("100"),
            bd("800"),
            List.of(),
            bd("100"),
            bd("20"),
            state,
            bd("100"),
            bd("800"),
            new ProfileIncomeSummary(null, null, null, bd("120"), null, null, null),
            ProfileAllocationReconciliation.EMPTY);
    var baseline = new PlanningBaseline(2025, bd("100"), bd("800"), bd("800"), bd("100"), bd("20"));

    var applied = PlanningProfileBaseline.apply(profile, baseline);

    assertThat(applied.longTermPlanningState().assets()).containsExactly(asset);
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
