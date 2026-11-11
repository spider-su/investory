package com.smartbox.investory.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.smartbox.investory.config.AuthorizationService;
import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.profile.api.contract.ProfileResponse;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.web.ProfileResponseMapper;
import java.util.List;
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
  private PortfolioRagController controller;
  private Authentication user;

  @BeforeEach
  void setUp() {
    controller = new PortfolioRagController(authorization, profiles, mapper, client);
    user = new UsernamePasswordAuthenticationToken(
        "user-a", "n/a", List.of(new SimpleGrantedAuthority("ROLE_PROFILE_USER")));
  }

  @Test
  void deniesCrossPortfolioBeforeReadingOrCallingPython() {
    when(authorization.canReadScoped(22L, user)).thenReturn(false);
    var exception = assertThrows(ResponseStatusException.class,
        () -> controller.ask(22L, new PortfolioRagController.Question("What is my balance?"), user));
    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    verifyNoInteractions(profiles, mapper, client);
  }

  @Test
  void authorizedRequestReadsProfileAndCallsPythonOnce() {
    InvestmentProfile model = mock(InvestmentProfile.class);
    ProfileResponse response = mock(ProfileResponse.class);
    when(authorization.canReadScoped(21L, user)).thenReturn(true);
    when(profiles.loadProfile(21L)).thenReturn(model);
    when(mapper.map(model)).thenReturn(response);
    var expected = new InvestoryRagClient.RagAnswer("The total is ...", List.of("live:profile"));
    when(client.ask("What is my balance?", response)).thenReturn(expected);

    var actual = controller.ask(
        21L, new PortfolioRagController.Question("What is my balance?"), user);
    assertEquals(expected, actual);
    verify(authorization).canReadScoped(21L, user);
    verify(client).ask("What is my balance?", response);
  }

  @Test
  void invalidInputNeverCallsDependencies() {
    assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
        () -> controller.ask(0L, new PortfolioRagController.Question("Hi"), user)).getStatusCode());
    assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
        () -> controller.ask(1L, new PortfolioRagController.Question("  "), user)).getStatusCode());
    verifyNoInteractions(authorization, profiles, mapper, client);
  }

  @Test
  void sanitizedUpstreamError() {
    InvestmentProfile model = mock(InvestmentProfile.class);
    ProfileResponse response = mock(ProfileResponse.class);
    when(authorization.canReadScoped(21L, user)).thenReturn(true);
    when(profiles.loadProfile(21L)).thenReturn(model);
    when(mapper.map(model)).thenReturn(response);
    when(client.ask("Question", response)).thenThrow(new PythonRagClient.RagUnavailableException());
    var exception = assertThrows(ResponseStatusException.class,
        () -> controller.ask(21L, new PortfolioRagController.Question("Question"), user));
    assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatusCode());
  }
}
