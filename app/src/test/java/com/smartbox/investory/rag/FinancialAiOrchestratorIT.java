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
      "investory.rag.max-requests-per-minute=100",
      "management.endpoints.web.exposure.include=health,prometheus"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test-fast")
class FinancialAiOrchestratorIT {
  private static final WorkerDatabase DB = FastDatabase.scopedDatabase("financial_orchestration");
  @Autowired MockMvc mvc;
  @Autowired io.micrometer.core.instrument.MeterRegistry metrics;
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

  @Test
  void syntheticQuestionSuiteUsesCanonicalFactsAndFixedMetricLabels() throws Exception {
    when(rag.askDocumentation("Explain internal transfers"))
        .thenReturn(
            new InvestoryRagClient.RagAnswer(
                "Internal transfers are neutral [domain/portfolio-accounting.md]",
                List.of("domain/portfolio-accounting.md")));
    when(market.lookup("GLOBAL_QUOTE", "MSFT"))
        .thenThrow(new PythonRagClient.MarketDataUnavailableException());
    try (var input = getClass().getResourceAsStream("/fixtures/assistant/questions.json")) {
      var profile = json.valueToTree(mapper.map(profiles.loadProfile(2L)));
      for (var question : json.readTree(input)) {
        String id = question.path("id").asString();
        var response =
            ask(2L, json.createObjectNode().put("question", question.path("question").asString()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        var answer = json.readTree(response.getContentAsString());
        assertThat(answer.path("components").toString())
            .as(id)
            .contains(question.path("component").asString());
        if (question.has("source")) {
          var source = question.path("source").asString();
          assertThat(answer.path("sources").toString()).as(id).contains(source);
          assertThat(answer.path("answer").asString()).as(id).contains("[" + source + "]");
        } else {
          assertThat(answer.path("sources").size()).as(id).isZero();
        }
        if (question.has("profileField")) {
          var expected = profile.path(question.path("profileField").asString()).decimalValue();
          assertThat(answer.path("answer").asString())
              .as(id)
              .contains(expected.toPlainString(), profile.path("currency").asString());
        }
        if (question.has("contains"))
          assertThat(answer.path("answer").asString())
              .as(id)
              .contains(question.path("contains").asString());
      }
    }
    verify(market, times(1)).lookup("GLOBAL_QUOTE", "MSFT");
    verify(rag, times(1)).askDocumentation("Explain internal transfers");
    verify(rag, never()).ask(anyString(), any());
    assertThat(
            metrics.find("investory.assistant.routes").tag("route", "ABSTAINED").counter().count())
        .isPositive();
    assertThat(
            metrics.find("investory.assistant.request").tag("outcome", "ANSWERED").timer().count())
        .isPositive();
    assertThat(
            metrics.getMeters().stream()
                .filter(m -> m.getId().getName().startsWith("investory.assistant"))
                .flatMap(m -> m.getId().getTags().stream())
                .map(t -> t.getKey())
                .distinct()
                .toList())
        .containsOnly("outcome", "route");
  }

  @Test
  void assetContextFollowsPeriodsAndRejectsChangedPortfolioOrRevokedMembership() throws Exception {
    var initial =
        ask(
                2L,
                json.createObjectNode()
                    .put("question", "Show realized profit for asset NVDA.US for MAX"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    var context = json.readTree(initial.getContentAsString()).path("context");
    assertThat(context.path("asset").asString()).isEqualTo("NVDA.US");
    var request = json.createObjectNode().put("question", "Compare that with YTD");
    request.set("context", context);
    var response = ask(2L, request).andExpect(status().isOk()).andReturn().getResponse();
    var answer = json.readTree(response.getContentAsString());
    assertThat(answer.path("answer").asString())
        .contains("NVDA.US realized profit/loss for MAX", "NVDA.US realized profit/loss for YTD");
    assertThat(answer.path("context").path("period").asString()).isEqualTo("YTD");
    ask(1L, request).andExpect(status().isForbidden());
    var mismatched = request.deepCopy();
    ((tools.jackson.databind.node.ObjectNode) mismatched.path("context")).put("portfolioId", 1);
    ask(2L, mismatched).andExpect(status().isBadRequest());
    // Simulate a change in authorization between turns using the same request.
    jdbc.update("UPDATE investory.app_users SET active=false WHERE username='happy.investor'");
    try {
      ask(2L, request).andExpect(status().isUnauthorized());
    } finally {
      jdbc.update("UPDATE investory.app_users SET active=true WHERE username='happy.investor'");
    }
    verifyNoInteractions(rag, market);
  }

  @Test
  void prometheusExpositionRequiresAdministrator() throws Exception {
    String path = "/actuator/prometheus";
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                .with(httpBasic("happy.investor", "orchestration-test")))
        .andExpect(status().isForbidden());
    ask(2L, json.createObjectNode().put("question", "Compare that with YTD"))
        .andExpect(status().isOk());
    var metricsResponse =
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                    .with(
                        org.springframework.security.test.web.servlet.request
                            .SecurityMockMvcRequestPostProcessors.user("synthetic-monitor")
                            .roles("ADMIN")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    assertThat(metricsResponse.getContentAsString())
        .contains("investory_assistant_request_seconds_bucket", "investory_assistant_routes_total")
        .doesNotContain("Compare that with YTD", "happy.investor");
  }

  private org.springframework.test.web.servlet.ResultActions ask(
      long id, tools.jackson.databind.JsonNode payload) throws Exception {
    return mvc.perform(
        post("/api/v1/portfolios/" + id + "/assistant/ask")
            .with(httpBasic("happy.investor", "orchestration-test"))
            .contentType("application/json")
            .content(json.writeValueAsString(payload)));
  }
}
