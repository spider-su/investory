package com.smartbox.investory.retirement.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileCompactPresentationTest {

  @Test
  void summaryValuesUseSimulationCompactMoneyFormat() {
    InvestmentProfile profile =
        new InvestmentProfile(
            1L,
            CurrencyType.PLN,
            new BigDecimal("10400"),
            new BigDecimal("1200000"),
            new BigDecimal("1210400"),
            new BigDecimal("10400"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("10400"),
            new BigDecimal("1200000"),
            List.of(),
            List.of());

    assertThat(profile.marketPortfolioValueCompactDisplay()).isEqualTo("10.4K");
    assertThat(profile.longTermAssetValueCompactDisplay()).isEqualTo("1.2M");
    assertThat(profile.totalNetWorthCompactDisplay()).isEqualTo("1.21M");
    assertThat(profile.historicalMarketInvestmentIncomeCompactDisplay()).isEqualTo("10.4K");
  }

  @Test
  void allocationValueUsesTheSameCompactFormat() {
    ProfileAllocation allocation =
        new ProfileAllocation(
            EconomicBucket.REAL_ESTATE,
            new BigDecimal("1200000"),
            new BigDecimal("0.75"),
            Liquidity.ILLIQUID);

    assertThat(allocation.compactValueDisplay()).isEqualTo("1.2M");
  }
}
