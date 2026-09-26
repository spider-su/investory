package com.smartbox.investory.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/** Local-only convenience authentication for the seeded development profile. */
@Configuration(proxyBeanMethods = false)
@Profile("local")
public class LocalDevelopmentSecurityConfig {

  @Bean
  @ConditionalOnProperty(
      prefix = "app.security.local.auto-login",
      name = "enabled",
      havingValue = "true")
  public LocalDevelopmentAuthenticationFilter localDevelopmentAuthenticationFilter(
      JdbcTemplate jdbc,
      UserDetailsService users,
      @org.springframework.beans.factory.annotation.Value("${app.security.local.profile-id:1}")
          long profileId) {
    return new LocalDevelopmentAuthenticationFilter(jdbc, users, profileId);
  }

  static final class LocalDevelopmentAuthenticationFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbc;
    private final UserDetailsService users;
    private final long profileId;

    LocalDevelopmentAuthenticationFilter(
        JdbcTemplate jdbc, UserDetailsService users, long profileId) {
      this.jdbc = jdbc;
      this.users = users;
      this.profileId = profileId;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
      if (SecurityContextHolder.getContext().getAuthentication() == null) {
        String username = findProfileOwner();
        UserDetails user = users.loadUserByUsername(username);
        var authentication =
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
      }
      filterChain.doFilter(request, response);
    }

    private String findProfileOwner() {
      return jdbc.queryForObject(
          "SELECT u.username "
              + "FROM investory.profile_memberships pm "
              + "JOIN investory.app_users u ON u.id = pm.user_id "
              + "WHERE pm.profile_id = ? AND pm.role = 'OWNER' AND u.active "
              + "ORDER BY pm.created_at, u.id "
              + "LIMIT 1",
          String.class,
          profileId);
    }
  }
}
