package com.smartbox.investory.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.application.ProfileQueryService;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorLongTermFacts;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorTestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

/** Verifies the single-transaction whole-wealth composition boundary. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class InvestmentProfileCompositionIT extends FastDatabaseTest {
  @Autowired private ProfileQueryService profiles;
  @Autowired private MockMvc mvc;

  @Test
  void returnsOneCanonicalProfileSnapshotAndDoesNotExposeAnotherPortfolio() throws Exception {
    InvestmentProfile profile = profiles.loadProfile(HappyInvestorTestData.PORTFOLIO_ID);
    assertThat(profile.portfolioId()).isEqualTo(HappyInvestorTestData.PORTFOLIO_ID);
    assertThat(profile.longTermAssetValue())
        .isEqualByComparingTo(HappyInvestorLongTermFacts.LONG_TERM_TOTAL);
    assertThat(profile.totalNetWorth())
        .isEqualByComparingTo(
            profile.marketPortfolioValue().add(HappyInvestorLongTermFacts.LONG_TERM_TOTAL));
    assertThat(profile.allocations()).isNotNull();
    assertThat(profile.longTermPlanningState()).isNotNull();

    mvc.perform(
            get("/api/v1/portfolios/" + HappyInvestorTestData.PORTFOLIO_ID + "/profile")
                .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
        .andExpect(status().isOk());
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> profiles.loadProfile(999999L))
        .isInstanceOf(RuntimeException.class);
  }
}
