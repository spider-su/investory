package com.smartbox.investory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class PortfolioOwnershipInterceptorTest {
  @Mock private ObjectProvider<AuthorizationService> authorizations;
  @Mock private AuthorizationService authorization;
  @Mock private Authentication authentication;

  private PortfolioOwnershipInterceptor interceptor;

  @BeforeEach
  void setUp() {
    interceptor = new PortfolioOwnershipInterceptor(authorizations);
    when(authorizations.getIfAvailable()).thenReturn(authorization);
    when(authentication.isAuthenticated()).thenReturn(true);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void profileScopedGetUsesProfileIdForReadAuthorization() {
    var request = new MockHttpServletRequest("GET", "/profiles/7/accounting");
    var response = new MockHttpServletResponse();
    when(authorization.canRead(7L, authentication)).thenReturn(false);

    assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);
    verify(authorization).canRead(7L, authentication);
  }

  @Test
  void profileScopedPostUsesProfileIdForWriteAuthorization() {
    var request = new MockHttpServletRequest("POST", "/profiles/7/accounting/staging/promote");
    var response = new MockHttpServletResponse();
    when(authorization.canWrite(7L, authentication)).thenReturn(true);

    assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    verify(authorization).canWrite(7L, authentication);
  }
}
