package com.smartbox.investory.ui.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.api.model.ProfileIncomeSummary;
import com.smartbox.investory.retirement.api.RetirementAnnualCostsApi;
import com.smartbox.investory.retirement.api.model.AnnualCostView;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.ui.profile.ProfileClient;
import com.smartbox.investory.ui.profile.RetirementProfileClient;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FinancialLandscapePageAssemblerTest {
  private final ProfileClient profiles = mock(ProfileClient.class);
  private final RetirementProfileClient retirementProfile = mock(RetirementProfileClient.class);
  private final AnnualCostsClient annualCosts = mock(AnnualCostsClient.class);
  private final FinancialLandscapePageAssembler assembler =
      new FinancialLandscapePageAssembler(profiles, retirementProfile, annualCosts);

  @BeforeEach
  void setup() {
    InvestmentProfile profile = mock(InvestmentProfile.class);
    when(profile.portfolioId()).thenReturn(7L);
    when(profile.currency()).thenReturn(CurrencyType.PLN);
    ProfileIncomeSummary summary = mock(ProfileIncomeSummary.class);
    when(summary.combinedNetAnnualIncome()).thenReturn(new BigDecimal("252300"));
    when(summary.marketNetAnnualIncome()).thenReturn(new BigDecimal("62300"));
    when(summary.longTermAnnualIncome()).thenReturn(new BigDecimal("190000"));
    when(profile.incomeSummary()).thenReturn(summary);
    when(profile.liquidAssets()).thenReturn(new BigDecimal("120000"));
    when(profile.illiquidAssets()).thenReturn(new BigDecimal("850000"));
    when(profiles.loadProfile(7L)).thenReturn(profile);
    when(retirementProfile.currentYearAnnualCost(7L, CurrencyType.PLN))
        .thenReturn(
            new AnnualCostView(true, new BigDecimal("255800"), CurrencyType.PLN, 2026, 11L));
  }

  @Test
  void usesProfileIncomeAndCurrentYearCostsForEveryTotal() {
    when(annualCosts.load(7L, 11L, 2026))
        .thenReturn(
            new RetirementAnnualCostsApi.AnnualCosts(
                11L,
                2026,
                "Plan",
                List.of(
                    new RetirementAnnualCostsApi.Group(1L, "Housing", new BigDecimal("6000"), 0)),
                new BigDecimal("6000"),
                new BigDecimal("72000"),
                BigDecimal.ZERO,
                null,
                null,
                BigDecimal.ZERO));

    var page = assembler.assemble(7L);

    assertThat(page.portfolioId()).isEqualTo(7L);
    assertThat(page.currency()).isEqualTo("PLN");
    assertThat(page.year()).isEqualTo(2026);
    assertThat(page.annualIncome()).isEqualByComparingTo("252300");
    assertThat(page.annualCashIncome()).isEqualByComparingTo("190000");
    assertThat(page.annualMarketResult()).isEqualByComparingTo("62300");
    assertThat(page.annualExpenses()).isEqualByComparingTo("255800");
    assertThat(page.annualGap()).isEqualByComparingTo("3500");
    assertThat(page.liquidAssets()).isEqualByComparingTo("120000");
    assertThat(page.illiquidAssets()).isEqualByComparingTo("850000");
    assertThat(page.expenseGroups()).hasSize(1);
    assertThat(page.annualCashIncome().add(page.annualMarketResult()))
        .isEqualByComparingTo(page.annualIncome());
    verify(profiles).loadProfile(7L);
    verify(retirementProfile).currentYearAnnualCost(7L, CurrencyType.PLN);
    verify(annualCosts).load(7L, 11L, 2026);
  }

  @Test
  void compactDoesNotLoadUnusedExpenseGroups() {
    var page = assembler.assemble(7L, false);

    assertThat(page.annualIncome()).isEqualByComparingTo("252300");
    assertThat(page.annualExpenses()).isEqualByComparingTo("255800");
    assertThat(page.expenseGroups()).isEmpty();
    assertThat(page.asAttributes(true))
        .containsOnlyKeys(
            "annualIncome",
            "annualCashIncome",
            "annualMarketResult",
            "annualExpenses",
            "annualGap");
    verifyNoInteractions(annualCosts);
  }

  @Test
  void keepsProfileIncomeButNoBalanceWhenPlanCostsUnavailable() {
    when(retirementProfile.currentYearAnnualCost(7L, CurrencyType.PLN))
        .thenReturn(AnnualCostView.unavailable(CurrencyType.PLN, 2026));

    var page = assembler.assemble(7L);

    assertThat(page.year()).isEqualTo(2026);
    assertThat(page.annualIncome()).isEqualByComparingTo("252300");
    assertThat(page.annualCashIncome()).isEqualByComparingTo("190000");
    assertThat(page.annualExpenses()).isNull();
    assertThat(page.annualGap()).isNull();
    assertThat(page.expenseGroups()).isEmpty();
    verifyNoInteractions(annualCosts);
  }

  @Test
  void derivesPositiveSurplusFromSameProfileTotals() {
    when(retirementProfile.currentYearAnnualCost(7L, CurrencyType.PLN))
        .thenReturn(
            new AnnualCostView(true, new BigDecimal("180000"), CurrencyType.PLN, 2026, null));

    var page = assembler.assemble(7L);

    assertThat(page.annualExpenses()).isEqualByComparingTo("180000");
    assertThat(page.annualGap()).isEqualByComparingTo("0");
    verifyNoInteractions(annualCosts);
  }
}
