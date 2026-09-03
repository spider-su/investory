package com.smartbox.investory.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      @Value("${app.security.read-authentication-required:true}")
          boolean readAuthenticationRequired) {
    var authorization =
        http.csrf(AbstractHttpConfigurer::disable)
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
                        .requestMatchers(HttpMethod.POST, "/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/**")
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

    return authorization.build();
  }

  @Bean
  public UserDetailsService userDetailsService(
      @Value("${app.security.admin-username:admin}") String adminUsername,
      @Value("${app.security.admin-password:change-me-admin}") String adminPassword,
      @Value("${app.security.user-username:user}") String userUsername,
      @Value("${app.security.user-password:change-me-user}") String userPassword,
      PasswordEncoder passwordEncoder,
      ObjectProvider<JdbcTemplate> jdbcTemplates) {
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
      if (jdbc == null) return configuredFallback.loadUserByUsername(username);
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
        return configuredFallback.loadUserByUsername(username);
      }
    };
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
