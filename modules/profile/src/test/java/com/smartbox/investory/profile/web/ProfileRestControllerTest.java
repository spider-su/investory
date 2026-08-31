package com.smartbox.investory.profile.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.profile.api.ProfileSummaryReader;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation;
import com.smartbox.investory.profile.api.model.ProfileAssetProjection;
import com.smartbox.investory.profile.api.model.ProfileIncomeSummary;
import com.smartbox.investory.shared.currency.CurrencyType;
import jakarta.validation.constraints.Positive;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@DisplayName("Profile REST contract")
class ProfileRestControllerTest {
  @Mock private ProfileSummaryReader profiles;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.standaloneSetup(new ProfileRestController(profiles)).build();
  }

  @Test
  void returnsCanonicalProfileAndMapsPortfolioId() throws Exception {
    when(profiles.loadSummary(7L))
        .thenReturn(com.smartbox.investory.profile.api.model.ProfileSummary.from(profile(7L)));

    mvc.perform(get("/api/v1/portfolios/7/profile"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.*", hasSize(14)))
        .andExpect(jsonPath("$.portfolioId").value(7))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.marketPortfolioValue").value(100))
        .andExpect(jsonPath("$.longTermAssetValue").value(0))
        .andExpect(jsonPath("$.totalNetWorth").value(100))
        .andExpect(jsonPath("$.liquidAssets").value(100))
        .andExpect(jsonPath("$.illiquidAssets").value(0))
        .andExpect(jsonPath("$.allocations").isArray())
        .andExpect(jsonPath("$.currentRentalIncome").value(0))
        .andExpect(jsonPath("$.currentBondIncome").value(0))
        .andExpect(jsonPath("$.retirementReserve").value(0))
        .andExpect(jsonPath("$.investmentCapital").value(100))
        .andExpect(jsonPath("$.income.marketIncomeYtd").value(0))
        .andExpect(jsonPath("$.income.combinedAnnualIncome").value(0))
        .andExpect(jsonPath("$.allocationReconciliation.shortTerm.authoritativeValue").value(0))
        .andExpect(jsonPath("$.allocationReconciliation.balanced").value(true))
        .andExpect(jsonPath("$.longTermPlanningState").doesNotExist())
        .andExpect(jsonPath("$.longTermAssets").doesNotExist())
        .andExpect(jsonPath("$..rentalContracts").doesNotExist())
        .andExpect(jsonPath("$..tenantName").doesNotExist())
        .andExpect(jsonPath("$..tenantEmail").doesNotExist())
        .andExpect(jsonPath("$..tenantPhone").doesNotExist());

    verify(profiles).loadSummary(7L);
  }

  @Test
  void rejectsNonPositivePortfolioId() throws Exception {
    Method method = ProfileRestController.class.getDeclaredMethod("profile", Long.class);
    assertThat(method.getParameters()[0].isAnnotationPresent(Positive.class)).isTrue();
  }

  @Test
  void doesNotExposeRedundantPartialProfileEndpoints() throws Exception {
    mvc.perform(get("/api/v1/profile").param("portfolioId", "7")).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/portfolios/7/profile/allocation")).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/portfolios/7/profile/long-term-assets"))
        .andExpect(status().isNotFound());
  }

  private static InvestmentProfile profile(Long portfolioId) {
    return new InvestmentProfile(
        portfolioId,
        CurrencyType.USD,
        new BigDecimal("100"),
        BigDecimal.ZERO,
        new BigDecimal("100"),
        new BigDecimal("100"),
        BigDecimal.ZERO,
        List.of(),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        ProfileAssetProjection.EMPTY,
        BigDecimal.ZERO,
        new BigDecimal("100"),
        new ProfileIncomeSummary(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO),
        ProfileAllocationReconciliation.EMPTY);
  }
}
