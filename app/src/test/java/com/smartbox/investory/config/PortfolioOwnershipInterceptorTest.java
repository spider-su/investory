package com.smartbox.investory.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

  @ParameterizedTest
  @ValueSource(strings = {"ask", "ask-docs"})
  void readOnlyRagPostUsesStrictScopedReadInsteadOfWrite(String route) {
    var request = new MockHttpServletRequest("POST", "/api/v1/portfolios/17/rag/" + route);
    var response = new MockHttpServletResponse();
    when(authorization.canReadScoped(17L, authentication)).thenReturn(true);
    assertTrue(interceptor.preHandle(request, response, new Object()));
    verify(authorization).canReadScoped(17L, authentication);
    verify(authorization, never()).canWrite(17L, authentication);
  }

  @ParameterizedTest
  @ValueSource(strings = {"ask", "ask-docs"})
  void readOnlyRagPostRejectsUnrelatedPortfolio(String route) {
    var request = new MockHttpServletRequest("POST", "/api/v1/portfolios/99/rag/" + route);
    var response = new MockHttpServletResponse();
    assertFalse(interceptor.preHandle(request, response, new Object()));
    assertEquals(403, response.getStatus());
    verify(authorization).canReadScoped(99L, authentication);
    verify(authorization, never()).canWrite(99L, authentication);
  }

  @Test
  void otherRagPostStillRequiresWriteAccess() {
    var request = new MockHttpServletRequest("POST", "/api/v1/portfolios/17/rag/ask-docs/reset");
    var response = new MockHttpServletResponse();
    assertFalse(interceptor.preHandle(request, response, new Object()));
    assertEquals(403, response.getStatus());
    verify(authorization).canWrite(17L, authentication);
    verify(authorization, never()).canReadScoped(17L, authentication);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }
}
