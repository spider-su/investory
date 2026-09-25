package com.smartbox.investory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

class LocalDevelopmentSecurityConfigTest {

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void authenticatesTheActiveOwnerOfConfiguredProfile() throws Exception {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    UserDetailsService users = mock(UserDetailsService.class);
    when(jdbc.queryForObject(anyString(), eq(String.class), eq(1L))).thenReturn("sample.user");
    when(users.loadUserByUsername("sample.user"))
        .thenReturn(User.withUsername("sample.user").password("ignored").roles("USER").build());
    var filter =
        new LocalDevelopmentSecurityConfig.LocalDevelopmentAuthenticationFilter(jdbc, users, 1L);
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
        .isEqualTo("sample.user");
    verify(chain).doFilter(any(), any());
  }
}
