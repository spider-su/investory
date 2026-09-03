package com.smartbox.investory.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Enforces the portfolio-user boundary before any portfolio HTTP handler runs. */
@Component
public class PortfolioOwnershipInterceptor implements HandlerInterceptor {
  private final ObjectProvider<JdbcTemplate> jdbcTemplates;

  public PortfolioOwnershipInterceptor(ObjectProvider<JdbcTemplate> jdbcTemplates) {
    this.jdbcTemplates = jdbcTemplates;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    Long portfolioId = portfolioId(request);
    if (portfolioId == null) return true;

    Authentication authentication =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) return true;
    if (authentication.getAuthorities().stream()
        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) return true;
    JdbcTemplate jdbcTemplate = jdbcTemplates.getIfAvailable();
    if (jdbcTemplate == null) return true;

    Integer owned =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM investory.portfolios p "
                + "JOIN investory.app_users u ON u.id = p.user_id "
                + "WHERE p.id = ? AND u.username = ? AND u.active",
            Integer.class,
            portfolioId,
            authentication.getName());
    if (owned == null || owned == 0) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return false;
    }
    return true;
  }

  private static Long portfolioId(HttpServletRequest request) {
    String path = request.getRequestURI();
    String marker = "/portfolios/";
    int index = path.indexOf(marker);
    if (index >= 0) {
      String value = path.substring(index + marker.length()).split("/", 2)[0];
      try {
        return Long.valueOf(value);
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    String value = request.getParameter("portfolioId");
    if (value == null) return null;
    try {
      return Long.valueOf(value);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }
}
