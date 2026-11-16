package com.smartbox.investory.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.profile.api.contract.ProfileResponse;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.web.ProfileResponseMapper;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PortfolioRagControllerTest {
  @Mock AuthorizationService authorization;
  @Mock ProfileSnapshotReader profiles;
  @Mock ProfileResponseMapper mapper;
  @Mock InvestoryRagClient client;
  @Mock ExternalMarketDataService market;
  @Mock RagUserRateLimiter rateLimiter;
  @Mock PortfolioProfitabilityAnswer profitability;
  @Mock PortfolioAssistantReadModels readModels;
  private PortfolioRagController controller;
  private Authentication user;
  private RagConcurrencyLimiter concurrencyLimiter;

  @BeforeEach
  void setUp() {
    concurrencyLimiter = new RagConcurrencyLimiter(1);
    controller =
        new PortfolioRagController(
            authorization,
            profiles,
            mapper,
            client,
            rateLimiter,
            concurrencyLimiter,
            profitability,
            readModels,
            market);
    user =
        new UsernamePasswordAuthenticationToken(
            "user-a", "n/a", List.of(new SimpleGrantedAuthority("ROLE_PROFILE_USER")));
  }

  @Test
  void deniesCrossPortfolioBeforeReadingOrCallingPython() {
    when(authorization.canReadScoped(22L, user)).thenReturn(false);
    var exception =
        assertThrows(
            ResponseStatusException.class,
            () ->
                controller.ask(
                    22L, new PortfolioRagController.Question("What is my balance?"), user));
    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    verifyNoInteractions(rateLimiter, profiles, mapper, client);
  }

  @Test
  void authorizedRequestReadsProfileAndCallsPythonOnce() {
    InvestmentProfile model = mock(InvestmentProfile.class);
    ProfileResponse response = mock(ProfileResponse.class);
    when(authorization.canReadScoped(21L, user)).thenReturn(true);
    when(rateLimiter.tryAcquire("user-a")).thenReturn(true);
    when(profiles.loadProfile(21L)).thenReturn(model);
    when(mapper.map(model)).thenReturn(response);
    var expected = new InvestoryRagClient.RagAnswer("The total is ...", List.of("live:profile"));
    when(client.ask("What is my balance?", response)).thenReturn(expected);

    var actual =
        controller.ask(21L, new PortfolioRagController.Question("What is my balance?"), user);
    assertEquals(expected, actual);
    verify(authorization).canReadScoped(21L, user);
    verify(client).ask("What is my balance?", response);
  }

  @Test
  void documentationQuestionChecksMembershipWithoutReadingAnyFinancialSnapshot() {
    when(authorization.canReadScoped(21L, user)).thenReturn(true);
    when(rateLimiter.tryAcquire("user-a")).thenReturn(true);
    var expected =
        new InvestoryRagClient.RagAnswer(
            "Documented investment methodology [methodology.md]", List.of("methodology.md"));
    when(client.askDocumentation("How does Investory calculate returns?")).thenReturn(expected);
    var answer =
        controller.askDocumentation(
            21L,
            new PortfolioRagController.Question("How does Investory calculate returns?"),
            user);
    assertEquals(expected, answer);
    verify(client).askDocumentation("How does Investory calculate returns?");
    verifyNoInteractions(profiles, mapper);
  }

  @Test
  void documentationQuestionDeniesOtherPortfolioBeforeAnyDataIsRead() {
    when(authorization.canReadScoped(22L, user)).thenReturn(false);
    var denied =
        assertThrows(
            ResponseStatusException.class,
            () ->
                controller.askDocumentation(
                    22L, new PortfolioRagController.Question("Explain returns"), user));
    assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
    verifyNoInteractions(profiles, mapper, client, rateLimiter);
  }

  @Test
  void documentationQuestionSharesTheSameUserRateBudget() {
    when(authorization.canReadScoped(21L, user)).thenReturn(true);
    when(rateLimiter.tryAcquire("user-a")).thenReturn(false);
    var denied =
        assertThrows(
            ResponseStatusException.class,
            () ->
                controller.askDocumentation(
                    21L, new PortfolioRagController.Question("Explain returns"), user));
    assertEquals(HttpStatus.TOO_MANY_REQUESTS, denied.getStatusCode());
    verifyNoInteractions(profiles, mapper, client);
  }

  @Test
  void profitabilityFollowUpUsesAuthoritativeReportingNotModelOrProfileSnapshot() {
    when(authorization.canReadScoped(21L, user)).thenReturn(true);
    when(rateLimiter.tryAcquire("user-a")).thenReturn(true);
    String question = "is it profitable for all time I have it";
    when(profitability.supports(question)).thenReturn(true);
    var expected =
        new InvestoryRagClient.RagAnswer(
            "All-time investment profit +120 PLN [live:investment-performance]",
            List.of("live:investment-performance"));
    when(profitability.answer(21L)).thenReturn(expected);

    assertEquals(
        expected, controller.ask(21L, new PortfolioRagController.Question(question), user));
    verify(profitability).answer(21L);
    verifyNoInteractions(profiles, mapper, client);
  }

  @Test
  void unauthorizedProfitabilityFollowUpCannotReadReportingData() {
    when(authorization.canReadScoped(22L, user)).thenReturn(false);
    var error =
        assertThrows(
            ResponseStatusException.class,
            () ->
                controller.ask(
                    22L, new PortfolioRagController.Question("Was it profitable all time?"), user));
    assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    verifyNoInteractions(profitability, profiles, mapper, client);
  }

  @Test
  void busyProfitabilityFollowUpNeverQueriesReportingData() {
    when(authorization.canReadScoped(21L, user)).thenReturn(true);
    when(rateLimiter.tryAcquire("user-a")).thenReturn(false);
    var error =
        assertThrows(
            ResponseStatusException.class,
            () ->
                controller.ask(
                    21L, new PortfolioRagController.Question("Was it profitable all time?"), user));
    assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
    verifyNoInteractions(profitability, profiles, mapper, client);
  }

  @Test
  void mainAssetsUseReadOnlyAuthorizedModelWithoutCallingTheLlm() {
    when(authorization.canReadScoped(21L, user)).thenReturn(true);
    when(rateLimiter.tryAcquire("user-a")).thenReturn(true);
    when(readModels.classify("what are main assets?"))
        .thenReturn(PortfolioAssistantReadModels.Intent.MAIN_ASSETS);
    var result =
        new InvestoryRagClient.RagAnswer(
            "Top holdings [live:investment-structure]", List.of("live:investment-structure"));
    when(readModels.mainAssets(21L)).thenReturn(result);
    assertEquals(
        result,
        controller.ask(21L, new PortfolioRagController.Question("what are main assets?"), user));
    verifyNoInteractions(client, profiles, mapper);
  }

  @Test
  void retirementQuestionCannotReadAnUnauthorizedPortfolio() {
    when(authorization.canReadScoped(22L, user)).thenReturn(false);
    assertEquals(
        HttpStatus.FORBIDDEN,
        assertThrows(
                ResponseStatusException.class,
                () ->
                    controller.ask(
                        22L,
                        new PortfolioRagController.Question("what is my retirement plan"),
                        user))
            .getStatusCode());
    verifyNoInteractions(readModels, client, profiles, mapper);
  }

  @Test
  void spyBenchmarkAnswerSharesRateAndConcurrencyAdmission() {
    when(authorization.canReadScoped(21L, user)).thenReturn(true);
    when(rateLimiter.tryAcquire("user-a")).thenReturn(false);
    assertEquals(
        HttpStatus.TOO_MANY_REQUESTS,
        assertThrows(
                ResponseStatusException.class,
                () ->
                    controller.ask(
                        21L,
                        new PortfolioRagController.Question("What if I bought SPY instead?"),
                        user))
            .getStatusCode());
    verifyNoInteractions(readModels, client, profiles, mapper);
  }

  @Test
  void rateLimitRejectsBeforeAccessingSnapshotOrCallingPython() {
    when(authorization.canReadScoped(21L, user)).thenReturn(true);
    when(rateLimiter.tryAcquire("user-a")).thenReturn(false);
    var exception =
        assertThrows(
            ResponseStatusException.class,
            () -> controller.ask(21L, new PortfolioRagController.Question("Net worth?"), user));
    assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
    verifyNoInteractions(profiles, mapper, client);
  }

  @Test
  void invalidInputNeverCallsDependencies() {
    assertEquals(
        HttpStatus.BAD_REQUEST,
        assertThrows(
                ResponseStatusException.class,
                () -> controller.ask(0L, new PortfolioRagController.Question("Hi"), user))
            .getStatusCode());
    assertEquals(
        HttpStatus.BAD_REQUEST,
        assertThrows(
                ResponseStatusException.class,
                () -> controller.ask(1L, new PortfolioRagController.Question("  "), user))
            .getStatusCode());
    verifyNoInteractions(authorization, rateLimiter, profiles, mapper, client);
  }

  @Test
  void sanitizedUpstreamError() {
    InvestmentProfile model = mock(InvestmentProfile.class);
    ProfileResponse response = mock(ProfileResponse.class);
    when(authorization.canReadScoped(21L, user)).thenReturn(true);
    when(rateLimiter.tryAcquire("user-a")).thenReturn(true);
    when(profiles.loadProfile(21L)).thenReturn(model);
    when(mapper.map(model)).thenReturn(response);
    when(client.ask("Question", response)).thenThrow(new PythonRagClient.RagUnavailableException());
    var exception =
        assertThrows(
            ResponseStatusException.class,
            () -> controller.ask(21L, new PortfolioRagController.Question("Question"), user));
    assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
  }

  @Test
  void concurrentRequestIsRejectedBeforeReadingAnotherSnapshot() throws Exception {
    when(authorization.canReadScoped(21L, user)).thenReturn(true);
    when(rateLimiter.tryAcquire("user-a")).thenReturn(true);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var model = mock(InvestmentProfile.class);
    var response = mock(ProfileResponse.class);
    var expected = new InvestoryRagClient.RagAnswer("Current facts", List.of("live:profile"));
    when(profiles.loadProfile(21L))
        .thenAnswer(
            invocation -> {
              entered.countDown();
              if (!release.await(5, TimeUnit.SECONDS))
                throw new IllegalStateException("Test latch timed out");
              return model;
            });
    when(mapper.map(model)).thenReturn(response);
    when(client.ask("Question", response)).thenReturn(expected);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var first =
          executor.submit(
              () -> controller.ask(21L, new PortfolioRagController.Question("Question"), user));
      try {
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        var busy =
            assertThrows(
                ResponseStatusException.class,
                () -> controller.ask(21L, new PortfolioRagController.Question("Question"), user));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, busy.getStatusCode());
        verify(profiles, times(1)).loadProfile(21L);
      } finally {
        release.countDown();
      }
      assertEquals(expected, first.get(5, TimeUnit.SECONDS));
      assertEquals(
          expected, controller.ask(21L, new PortfolioRagController.Question("Question"), user));
    }
  }

  @Test
  void unsupportedScopeDoesNotReadFactsOrInvokeProvider() {
    when(authorization.canReadScoped(21L, user)).thenReturn(true);
    when(rateLimiter.tryAcquire("user-a")).thenReturn(true);
    when(readModels.classify("Future scope"))
        .thenReturn(PortfolioAssistantReadModels.Intent.UNSUPPORTED);
    assertTrue(
        controller
            .ask(21L, new PortfolioRagController.Question("Future scope"), user)
            .sources()
            .isEmpty());
    verifyNoInteractions(profiles, mapper, client, profitability);
  }

  @Test
  void snapshotFailureReleasesAdmissionForTheNextRequest() {
    when(authorization.canReadScoped(21L, user)).thenReturn(true);
    when(rateLimiter.tryAcquire("user-a")).thenReturn(true);
    var failure = new IllegalStateException("Synthetic snapshot failure");
    when(profiles.loadProfile(21L)).thenThrow(failure);
    for (int attempt = 0; attempt < 2; attempt++) {
      assertSame(
          failure,
          assertThrows(
              IllegalStateException.class,
              () -> controller.ask(21L, new PortfolioRagController.Question("Question"), user)));
    }
    verify(profiles, times(2)).loadProfile(21L);
    verifyNoInteractions(mapper, client);
  }

  @Test
  void unavailableMarketResearchReturns503AndReleasesAdmission() {
    when(authorization.canReadScoped(21L, user)).thenReturn(true);
    when(rateLimiter.tryAcquire("user-a")).thenReturn(true);
    when(readModels.largestHoldingSymbol(21L)).thenReturn("MSFT");
    when(market.lookup("GLOBAL_QUOTE", "MSFT"))
        .thenThrow(new PythonRagClient.MarketResearchUnavailableException());
    when(market.lookup("COMPANY_OVERVIEW", "MSFT"))
        .thenThrow(new PythonRagClient.MarketResearchUnavailableException());
    var direct =
        assertThrows(
            ResponseStatusException.class,
            () ->
                controller.researchMarketData(
                    21L,
                    new PortfolioRagController.MarketResearchRequest("GLOBAL_QUOTE", "MSFT"),
                    user));
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, direct.getStatusCode());
    var holding =
        assertThrows(
            ResponseStatusException.class, () -> controller.researchLargestHolding(21L, user));
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, holding.getStatusCode());
    verifyNoInteractions(profiles, mapper);
  }

  @Test
  void marketResearchRejectsOtherPortfolioBeforeExternalCall() {
    when(authorization.canReadScoped(22L, user)).thenReturn(false);
    var denied =
        assertThrows(
            ResponseStatusException.class,
            () ->
                controller.researchMarketData(
                    22L,
                    new PortfolioRagController.MarketResearchRequest("GLOBAL_QUOTE", "MSFT"),
                    user));
    assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
    verifyNoInteractions(client, readModels, profiles, mapper, rateLimiter);
  }

  @Test
  void marketResearchRejectsInvalidToolAndSymbolBeforeAuthorization() {
    for (var request :
        List.of(
            new PortfolioRagController.MarketResearchRequest("SELL", "MSFT"),
            new PortfolioRagController.MarketResearchRequest("GLOBAL_QUOTE", "MSFT;DROP"),
            new PortfolioRagController.MarketResearchRequest("COMPANY_OVERVIEW", "msft"))) {
      var error =
          assertThrows(
              ResponseStatusException.class,
              () -> controller.researchMarketData(21L, request, user));
      assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }
    verifyNoInteractions(authorization, client, readModels, profiles, mapper);
  }

  @Test
  void authorizedMarketResearchOnlySendsTickerAndTool() {
    when(authorization.canReadScoped(21L, user)).thenReturn(true);
    when(rateLimiter.tryAcquire("user-a")).thenReturn(true);
    var result =
        new InvestoryRagClient.MarketData(
            "Alpha Vantage",
            "GLOBAL_QUOTE",
            "MSFT",
            "https://www.alphavantage.co/",
            "{\"price\": \"123.45\"}");
    when(market.lookup("GLOBAL_QUOTE", "MSFT")).thenReturn(result);
    assertEquals(
        result,
        controller.researchMarketData(
            21L, new PortfolioRagController.MarketResearchRequest("GLOBAL_QUOTE", "MSFT"), user));
    verifyNoInteractions(readModels, profiles, mapper);
  }

  @Test
  void largestHoldingResearchUsesOnlyScopedPublicTicker() {
    when(authorization.canReadScoped(21L, user)).thenReturn(true);
    when(rateLimiter.tryAcquire("user-a")).thenReturn(true);
    when(readModels.largestHoldingSymbol(21L)).thenReturn("NVDA");
    var result =
        new InvestoryRagClient.MarketData(
            "Alpha Vantage",
            "COMPANY_OVERVIEW",
            "NVDA",
            "https://www.alphavantage.co/",
            "{\"symbol\": \"NVDA\"}");
    when(market.lookup("COMPANY_OVERVIEW", "NVDA")).thenReturn(result);
    assertEquals(result, controller.researchLargestHolding(21L, user));
    verify(market).lookup("COMPANY_OVERVIEW", "NVDA");
    verifyNoInteractions(profiles, mapper);
  }

  @Test
  void largestHoldingFallsBackToAnExplicitQuoteOnlyOnMissingEntitlement() {
    when(authorization.canReadScoped(21L, user)).thenReturn(true);
    when(rateLimiter.tryAcquire("user-a")).thenReturn(true);
    when(readModels.largestHoldingSymbol(21L)).thenReturn("MSFT");
    when(market.lookup("COMPANY_OVERVIEW", "MSFT"))
        .thenThrow(new PythonRagClient.MarketDataNotEntitledException());
    var quote =
        new InvestoryRagClient.MarketData(
            "Alpha Vantage", "GLOBAL_QUOTE", "MSFT", "https://www.alphavantage.co/", "quote data");
    when(market.lookup("GLOBAL_QUOTE", "MSFT")).thenReturn(quote);
    assertEquals(quote, controller.researchLargestHolding(21L, user));
    verifyNoInteractions(profiles, mapper);
  }

  @Test
  void providerTransportFailureDoesNotTriggerAnExtraLookup() {
    when(authorization.canReadScoped(21L, user)).thenReturn(true);
    when(rateLimiter.tryAcquire("user-a")).thenReturn(true);
    when(readModels.largestHoldingSymbol(21L)).thenReturn("MSFT");
    when(market.lookup("COMPANY_OVERVIEW", "MSFT"))
        .thenThrow(new PythonRagClient.RagUnavailableException());
    var error =
        assertThrows(
            ResponseStatusException.class, () -> controller.researchLargestHolding(21L, user));
    assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
    verify(market, never()).lookup("GLOBAL_QUOTE", "MSFT");
  }

  @Test
  void largestHoldingWithoutPublicTickerCannotCallProvider() {
    when(authorization.canReadScoped(21L, user)).thenReturn(true);
    when(rateLimiter.tryAcquire("user-a")).thenReturn(true);
    when(readModels.largestHoldingSymbol(21L)).thenReturn("INVALID SYMBOL");
    var error =
        assertThrows(
            ResponseStatusException.class, () -> controller.researchLargestHolding(21L, user));
    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatusCode());
    verifyNoInteractions(client, profiles, mapper);
  }

  @Test
  void marketResearchSharesTheExistingRateLimit() {
    when(authorization.canReadScoped(21L, user)).thenReturn(true);
    when(rateLimiter.tryAcquire("user-a")).thenReturn(false);
    var denied =
        assertThrows(
            ResponseStatusException.class, () -> controller.researchLargestHolding(21L, user));
    assertEquals(HttpStatus.TOO_MANY_REQUESTS, denied.getStatusCode());
    verifyNoInteractions(client, readModels, profiles, mapper);
  }
}
