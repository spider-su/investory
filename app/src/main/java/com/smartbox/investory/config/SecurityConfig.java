package com.smartbox.investory.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      @Value("${app.security.read-authentication-required:true}")
          boolean readAuthenticationRequired,
      // CSRF is intentionally disabled during the Accounting POC. Re-enable it after the POC
      // through APP_SECURITY_CSRF_PROTECTION_REQUIRED or environment-specific configuration.
      @Value("${app.security.csrf-protection-required:false}") boolean csrfProtectionRequired,
      @Value("${app.security.legacy-accounting-write-enabled:false}")
          boolean legacyAccountingWriteEnabled) {
    var authorization =
        http.csrf(
                csrf -> {
                  if (csrfProtectionRequired) {
                    csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(
                            "/api/**",
                            "/profiles/*/accounting/documents/recognize",
                            "/profiles/*/accounting/documents/save");
                  } else {
                    csrf.disable();
                  }
                })
            .sessionManagement(
                session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(
                auth ->
                    auth.requestMatchers(
                            "/",
                            "/error",
                            "/favicon.svg",
                            "/css/**",
                            "/js/**",
                            "/actuator/health",
                            "/actuator/health/readiness",
                            "/actuator/health/liveness",
                            "/swagger-ui.html",
                            "/swagger-ui/**",
                            "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers("/settings/**", "/api/v1/admin/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/api/v1/investment/maintenance/**")
                        .hasRole("ADMIN")
                        .requestMatchers(
                            HttpMethod.POST, "/api/v1/portfolios/*/investment/imports/**")
                        .hasAnyRole("ADMIN", "PROFILE_OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/**")
                        .hasRole("ADMIN"));

    authorization.authorizeHttpRequests(
        auth -> {
          var legacyAccounting = auth.requestMatchers(HttpMethod.POST, "/poc/accounting/**");
          if (legacyAccountingWriteEnabled) legacyAccounting.authenticated();
          else legacyAccounting.denyAll();
        });

    if (readAuthenticationRequired) {
      authorization.authorizeHttpRequests(
          auth -> auth.requestMatchers(HttpMethod.GET, "/**").authenticated());
    } else {
      authorization.authorizeHttpRequests(
          auth -> auth.requestMatchers(HttpMethod.GET, "/**").permitAll());
    }

    authorization
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .httpBasic(Customizer.withDefaults())
        .formLogin(AbstractHttpConfigurer::disable);

    return authorization.build();
  }

  @Bean
  public UserDetailsService userDetailsService(
      @Value("${app.security.admin-username:admin}") String adminUsername,
      @Value("${app.security.admin-password:change-me-admin}") String adminPassword,
      @Value("${app.security.user-username:user}") String userUsername,
      @Value("${app.security.user-password:change-me-user}") String userPassword,
      PasswordEncoder passwordEncoder,
      ObjectProvider<JdbcTemplate> jdbcTemplates,
      Environment environment) {
    boolean allowConfiguredFallback =
        environment.acceptsProfiles(Profiles.of("local", "test", "test-fast"));
    UserDetailsService configuredFallback =
        username -> {
          if (adminUsername.equals(username)) {
            return User.withUsername(adminUsername)
                .password(passwordEncoder.encode(adminPassword))
                .roles("ADMIN", "USER")
                .build();
          }
          if (userUsername.equals(username)) {
            return User.withUsername(userUsername)
                .password(passwordEncoder.encode(userPassword))
                .roles("USER")
                .build();
          }
          throw new org.springframework.security.core.userdetails.UsernameNotFoundException(
              username);
        };
    return username -> {
      JdbcTemplate jdbc = jdbcTemplates.getIfAvailable();
      if (jdbc == null)
        return fallbackOrReject(username, configuredFallback, allowConfiguredFallback);
      try {
        return jdbc.queryForObject(
            "SELECT username, password_hash, role, active FROM investory.app_users WHERE username = ?",
            (rs, row) -> {
              if (!rs.getBoolean("active") || rs.getString("password_hash") == null) {
                throw new org.springframework.security.core.userdetails.UsernameNotFoundException(
                    username);
              }
              String role = rs.getString("role");
              return User.withUsername(rs.getString("username"))
                  .password(rs.getString("password_hash"))
                  .roles(role == null || role.isBlank() ? "USER" : role)
                  .build();
            },
            username);
      } catch (org.springframework.dao.EmptyResultDataAccessException e) {
        return fallbackOrReject(username, configuredFallback, allowConfiguredFallback);
      }
    };
  }

  private org.springframework.security.core.userdetails.UserDetails fallbackOrReject(
      String username, UserDetailsService configuredFallback, boolean allowConfiguredFallback) {
    if (allowConfiguredFallback) return configuredFallback.loadUserByUsername(username);
    throw new org.springframework.security.core.userdetails.UsernameNotFoundException(username);
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
