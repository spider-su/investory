package com.smartbox.investory.rag;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.profile.web.ProfileResponseMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class FinancialAiOrchestratorControllerTest {
  private final AuthorizationService authorization = mock(AuthorizationService.class);
  private final RagUserRateLimiter limiter = mock(RagUserRateLimiter.class);
  private final RagConcurrencyLimiter concurrency = mock(RagConcurrencyLimiter.class);
  private final ProfileSnapshotReader profiles = mock(ProfileSnapshotReader.class);
  private final ProfileResponseMapper mapper = mock(ProfileResponseMapper.class);
  private final PortfolioAssistantReadModels readModels = mock(PortfolioAssistantReadModels.class);
  private final PortfolioProfitabilityAnswer profitability = mock(PortfolioProfitabilityAnswer.class);
  private final InvestoryRagClient rag = mock(InvestoryRagClient.class);
  private final ExternalMarketDataService market = mock(ExternalMarketDataService.class);
  private final Authentication user = new UsernamePasswordAuthenticationToken("tester", "n/a");
  private FinancialAiOrchestratorController controller;

  @BeforeEach
  void setup() {
    controller = new FinancialAiOrchestratorController(
        authorization, limiter, concurrency, profiles, mapper, readModels,
        profitability, rag, market);
  }

  private void authorize() {
    when(authorization.canReadScoped(21L, user)).thenReturn(true);
    when(limiter.tryAcquire("tester")).thenReturn(true);
    when(concurrency.tryAcquire()).thenReturn(true);
  }

  @Test
  void unauthorizedPortfolioIsNotReadOrSentToProviders() {
    var error = assertThrows(ResponseStatusException.class,
        () -> controller.ask(22L, new FinancialAiOrchestratorController.Question(
            "Compare my portfolio with SPY and retirement plan"), user));
    assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    verifyNoInteractions(profiles, mapper, readModels, profitability, rag, market, limiter);
  }

  @Test
  void composesAuthoritativeBenchmarkAndRetirementSources() {
    authorize();
    String question = "Compare my portfolio with SPY and my retirement plan";
    when(readModels.spyComparison(21L)).thenReturn(new InvestoryRagClient.RagAnswer(
        "Verified SPY benchmark", List.of("live:investment-benchmark")));
    when(readModels.retirementPlan(21L)).thenReturn(new InvestoryRagClient.RagAnswer(
        "Saved retirement assumptions", List.of("live:retirement-plan")));
    var answer = controller.ask(21L, new FinancialAiOrchestratorController.Question(question), user);
    assertTrue(answer.answer().contains("Verified SPY benchmark"));
    assertTrue(answer.answer().contains("Saved retirement assumptions"));
    assertEquals(List.of("live:investment-benchmark", "live:retirement-plan"), answer.sources());
    assertEquals(List.of("JAVA_BENCHMARK", "JAVA_RETIREMENT"), answer.components());
    verifyNoInteractions(profiles, mapper, rag, market);
    verify(concurrency).release();
  }

  @Test
  void marketProviderSeesOnlyPublicSymbolAndFailedCoverageDoesNotHideJavaFacts() {
    authorize();
    when(readModels.mainAssets(21L)).thenReturn(new InvestoryRagClient.RagAnswer(
        "Scoped positions", List.of("live:investment-structure")));
    when(market.lookup("GLOBAL_QUOTE", "SPY"))
        .thenThrow(new PythonRagClient.MarketDataUnavailableException());
    var answer = controller.ask(21L, new FinancialAiOrchestratorController.Question(
        "Show my portfolio holdings and latest price of SPY"), user);
    assertTrue(answer.answer().contains("Scoped positions"));
    assertTrue(answer.answer().contains("unavailable"));
    assertEquals(List.of("live:investment-structure"), answer.sources());
    verify(market).lookup("GLOBAL_QUOTE", "SPY");
    verifyNoInteractions(profiles, mapper, rag);
  }

  @Test
  void unsupportedForecastNeverQueriesSources() {
    authorize();
    when(readModels.classify("Predict my portfolio tomorrow"))
        .thenReturn(PortfolioAssistantReadModels.Intent.UNSUPPORTED);
    var result = controller.ask(21L, new FinancialAiOrchestratorController.Question(
        "Predict my portfolio tomorrow"), user);
    assertTrue(result.sources().isEmpty());
    assertEquals(List.of("ABSTAINED"), result.components());
    verifyNoInteractions(profiles, mapper, rag, market);
  }

  @Test
  void invalidQuestionRejectedBeforeAuthorization() {
    var error = assertThrows(ResponseStatusException.class,
        () -> controller.ask(21L, new FinancialAiOrchestratorController.Question("  "), user));
    assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    verifyNoInteractions(authorization, limiter, concurrency, profiles, mapper, rag, market);
  }

  @Test
  void rateLimitStopsAllSources() {
    when(authorization.canReadScoped(21L, user)).thenReturn(true);
    var error = assertThrows(ResponseStatusException.class,
        () -> controller.ask(21L, new FinancialAiOrchestratorController.Question(
            "Compare my portfolio with SPY"), user));
    assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
    verifyNoInteractions(profiles, mapper, rag, market);
  }

  @Test
  void documentationSourceDoesNotLoadPrivateProfile() {
    authorize();
    when(rag.askDocumentation("Explain the investment methodology"))
        .thenReturn(new InvestoryRagClient.RagAnswer(
            "From docs", List.of("docs:methodology")));
    var answer = controller.ask(21L, new FinancialAiOrchestratorController.Question(
        "Explain the investment methodology"), user);
    assertEquals(List.of("docs:methodology"), answer.sources());
    verifyNoInteractions(profiles, mapper, market);
  }
}
