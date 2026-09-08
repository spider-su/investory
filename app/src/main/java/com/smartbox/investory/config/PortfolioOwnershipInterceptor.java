package com.smartbox.investory.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/** Enforces the portfolio-user boundary before any portfolio HTTP handler runs. */
@Component
@RequiredArgsConstructor
public class PortfolioOwnershipInterceptor implements HandlerInterceptor {
  private final ObjectProvider<AuthorizationService> authorizations;

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    Authentication authentication =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) return true;
    AuthorizationService authorization = authorizations.getIfAvailable();
    if (authorization == null) return true;
    if (isIntegrationConfiguration(request)) {
      return rejectUnless(response, authorization.canManageIntegrations(authentication));
    }
    Long portfolioId = portfolioId(request);
    if (portfolioId == null) return true;
    boolean allowed =
        isRead(request)
            ? authorization.canRead(portfolioId, authentication)
            : authorization.canWrite(portfolioId, authentication);
    return rejectUnless(response, allowed);
  }

  @Override
  public void postHandle(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      ModelAndView modelAndView) {
    if (modelAndView == null) return;
    AuthorizationService authorization = authorizations.getIfAvailable();
    if (authorization == null) return;
    Authentication authentication =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    Long profileId = portfolioId(request);
    boolean admin = authorization.canAdminister(authentication);
    boolean owner = profileId != null && authorization.canWrite(profileId, authentication);
    modelAndView.addObject("canEdit", admin || owner);
    modelAndView.addObject("canImport", admin || owner);
    modelAndView.addObject("canManageProfile", admin || owner);
    modelAndView.addObject(
        "canManageIntegrations", authorization.canManageIntegrations(authentication));
  }

  private static boolean rejectUnless(HttpServletResponse response, boolean allowed) {
    if (allowed) return true;
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    return false;
  }

  private static boolean isRead(HttpServletRequest request) {
    return "GET".equalsIgnoreCase(request.getMethod())
        || "HEAD".equalsIgnoreCase(request.getMethod())
        || "OPTIONS".equalsIgnoreCase(request.getMethod());
  }

  private static boolean isIntegrationConfiguration(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/settings/integrations")
        || path.startsWith("/api/v1/admin/integrations");
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
