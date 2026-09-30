package com.smartbox.investory.config;

import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
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

}
