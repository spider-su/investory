package com.smartbox.investory.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.config.TokenAuthenticationService;
import com.smartbox.investory.user.application.CurrentProfileService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

class AuthRestControllerTest {
  @Test
  void webLoginSetsHttpOnlyCookieAndDoesNotReturnBearerToken() {
    var manager = mock(AuthenticationManager.class);
    var tokens = new TokenAuthenticationService("a".repeat(32), Duration.ofHours(1));
    var profiles = mock(CurrentProfileService.class);
    var user = User.withUsername("web-user").password("ignored").roles("USER").build();
    when(manager.authenticate(
            org.mockito.ArgumentMatchers.any(UsernamePasswordAuthenticationToken.class)))
        .thenReturn(
            UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
    var controller = new AuthRestController(manager, tokens, profiles);
    ReflectionTestUtils.setField(controller, "lifetime", Duration.ofHours(1));
    ReflectionTestUtils.setField(controller, "webSessionCookieName", "investory_web_session");
    ReflectionTestUtils.setField(controller, "webSessionCookieSecure", true);
    ReflectionTestUtils.setField(controller, "webSessionCookieSameSite", "Lax");
    var response = new MockHttpServletResponse();

    var result =
        controller.login(
            new AuthRestController.LoginRequest(null, "web-user", "password"), "web", response);

    assertThat(result.token()).isNull();
    assertThat(result.accessToken()).isNull();
    assertThat(response.getHeader("Set-Cookie"))
        .contains("investory_web_session=")
        .contains("HttpOnly")
        .contains("Secure")
        .contains("SameSite=Lax");
  }
}
