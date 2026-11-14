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
  @Mock RagUserRateLimiter rateLimiter;
  private PortfolioRagController controller;
  private Authentication user;
  private RagConcurrencyLimiter concurrencyLimiter;

  @BeforeEach
  void setUp() {
    concurrencyLimiter = new RagConcurrencyLimiter(1);
    controller =
        new PortfolioRagController(
            authorization, profiles, mapper, client, rateLimiter, concurrencyLimiter);
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
    when(client.askDocumentation("How does Investory calculate returns?"))
        .thenReturn(expected);
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
}
