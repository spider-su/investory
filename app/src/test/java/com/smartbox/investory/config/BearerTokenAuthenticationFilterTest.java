package com.smartbox.investory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

class BearerTokenAuthenticationFilterTest {
  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void authenticatesFromConfiguredHttpOnlySessionCookie() throws Exception {
    var tokens = new TokenAuthenticationService("a".repeat(32), Duration.ofHours(1));
    var user = User.withUsername("web-user").password("ignored").roles("USER").build();
    UserDetailsService users = username -> user;
    var filter = new BearerTokenAuthenticationFilter(tokens, users, "investory_web_session");
    var request = new MockHttpServletRequest();
    request.setCookies(new jakarta.servlet.http.Cookie("investory_web_session", tokens.issue(user)));
    var response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("web-user");
    verify(chain).doFilter(request, response);
  }
}
