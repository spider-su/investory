package com.smartbox.investory.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import jakarta.servlet.http.Cookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {
  private final TokenAuthenticationService tokens;
  private final UserDetailsService users;
  private final String webSessionCookieName;

  public BearerTokenAuthenticationFilter(
      TokenAuthenticationService tokens, UserDetailsService users, String webSessionCookieName) {
    this.tokens = tokens;
    this.users = users;
    this.webSessionCookieName = webSessionCookieName;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    String token = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : cookieToken(request);
    if (token != null && !token.isBlank() && SecurityContextHolder.getContext().getAuthentication() == null) {
      String username = tokens.subject(token);
      if (username != null)
        try {
          var user = users.loadUserByUsername(username);
          var authentication =
              new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
          authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
          SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (RuntimeException ignored) {
          /* remain unauthenticated */
        }
    }
    chain.doFilter(request, response);
  }

  private String cookieToken(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) return null;
    for (Cookie cookie : cookies) {
      if (webSessionCookieName.equals(cookie.getName())) return cookie.getValue();
    }
    return null;
  }
}
