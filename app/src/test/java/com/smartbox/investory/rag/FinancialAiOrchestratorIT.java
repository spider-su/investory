package com.smartbox.investory.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.profile.web.ProfileResponseMapper;
import com.smartbox.investory.testsupport.FastDatabase;
import com.smartbox.investory.testsupport.WorkerDatabase;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** Real MVC/security/domain reads; only optional outbound services are stubbed. */
@SpringBootTest(
    properties = {
      "investory.rag.enabled=true",
      "investory.rag.service-token=test-internal",
      "app.security.profile-ownership-required=false",
      "investory.rag.max-requests-per-minute=100"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test-fast")
class FinancialAiOrchestratorIT {
  private static final WorkerDatabase DB = FastDatabase.scopedDatabase("financial_orchestration");
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder passwords;
  @Autowired ProfileSnapshotReader profiles;
  @Autowired ProfileResponseMapper mapper;
  @Autowired PortfolioAssistantReadModels models;
  @Autowired ObjectMapper json;
  @MockitoBean InvestoryRagClient rag;
  @MockitoBean ExternalMarketDataService market;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", DB::jdbcUrl);
    r.add("spring.datasource.username", DB::username);
    r.add("spring.datasource.password", DB::password);
  }

  @AfterAll
  static void close() {
    DB.close();
  }

  @BeforeEach
  void user() {
    jdbc.update(
        "UPDATE investory.app_users SET role='PROFILE_USER',password_hash=? WHERE username='happy.investor'",
        passwords.encode("orchestration-test"));
  }

  @Test
  void composesCanonicalFactsAndPublicDocumentationForReadOnlyUser() throws Exception {
    var profile = mapper.map(profiles.loadProfile(2L));
    var holdings = models.mainAssets(2L);
    when(rag.askDocumentation("explain internal transfers"))
        .thenReturn(
            new InvestoryRagClient.RagAnswer(
                "Internal transfers are neutral [domain/portfolio-accounting.md]",
                List.of("domain/portfolio-accounting.md")));
    var response =
        mvc.perform(
                post("/api/v1/portfolios/2/assistant/ask")
                    .with(httpBasic("happy.investor", "orchestration-test"))
                    .contentType("application/json")
                    .content(
                        "{\"question\":\"Show my net worth and holdings and explain internal transfers\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    var body = json.readTree(response.getContentAsString());
    assertThat(body.path("answer").asString())
        .contains(
            profile.totalNetWorth().toPlainString(), profile.currency().name(), holdings.answer());
    assertThat(body.path("sources").toString())
        .contains("live:profile", "domain/portfolio-accounting.md");
    verify(rag).askDocumentation("explain internal transfers");
    verify(rag, never()).ask(anyString(), any());
    verifyNoInteractions(market);
  }

  @Test
  void crossProfileAndAnonymousRequestsNeverReachOptionalSources() throws Exception {
    mvc.perform(
            post("/api/v1/portfolios/1/assistant/ask")
                .with(httpBasic("happy.investor", "orchestration-test"))
                .contentType("application/json")
                .content("{\"question\":\"Quote for ticker MSFT\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/portfolios/2/assistant/ask")
                .contentType("application/json")
                .content("{\"question\":\"Quote for ticker MSFT\"}"))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(rag, market);
  }

  @Test
  void missingMarketCoverageDoesNotRemoveVerifiedProfileAmount() throws Exception {
    var profile = mapper.map(profiles.loadProfile(2L));
    when(market.lookup("GLOBAL_QUOTE", "MSFT"))
        .thenThrow(new PythonRagClient.MarketDataUnavailableException());
    var response =
        mvc.perform(
                post("/api/v1/portfolios/2/assistant/ask")
                    .with(httpBasic("happy.investor", "orchestration-test"))
                    .contentType("application/json")
                    .content("{\"question\":\"Show my net worth and quote for ticker msft\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    var body = json.readTree(response.getContentAsString());
    assertThat(body.path("answer").asString())
        .contains(profile.totalNetWorth().toPlainString(), "unavailable");
    assertThat(body.path("sources").toString()).contains("live:profile").doesNotContain("market:");
    verify(market).lookup("GLOBAL_QUOTE", "MSFT");
    verifyNoInteractions(rag);
  }
}
