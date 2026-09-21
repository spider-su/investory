package com.smartbox.investory.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public TokenAuthenticationService tokenAuthenticationService(
      @Value("${app.security.token-secret:change-me-token-secret-change-me-token-secret}")
          String secret,
      @Value("${app.security.token-lifetime:PT12H}") java.time.Duration lifetime) {
    return new TokenAuthenticationService(secret, lifetime);
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
      throws Exception {
    return configuration.getAuthenticationManager();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource(
      @Value("${app.security.mobile-api-allowed-origins:http://localhost:8081}")
          String mobileApiAllowedOrigins) {
    var configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(java.util.List.of(mobileApiAllowedOrigins.split(",")));
    configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "OPTIONS"));
    configuration.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type"));
    configuration.setAllowCredentials(true);

    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      @Value("${app.security.read-authentication-required:true}")
          boolean readAuthenticationRequired,
      @Value("${app.security.csrf-protection-required:true}") boolean csrfProtectionRequired,
      TokenAuthenticationService tokens,
      UserDetailsService users,
      @Value("${app.security.token-login-enabled:true}") boolean tokenLoginEnabled) {
    var authorization =
        http.csrf(
                csrf -> {
                  if (csrfProtectionRequired) {
                    csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/api/v1/auth/login");
                  } else {
                    csrf.disable();
                  }
                })
            .sessionManagement(
                session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(Customizer.withDefaults())
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
                        .requestMatchers(
                            tokenLoginEnabled ? "/api/v1/auth/login" : "/disabled-token-login")
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

    authorization.addFilterBefore(
        new BearerTokenAuthenticationFilter(tokens, users), BasicAuthenticationFilter.class);

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
