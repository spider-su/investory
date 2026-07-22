package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Isolated security chain for local Ghostfolio frontend development.
 *
 * <p>Activate with {@code SPRING_PROFILES_ACTIVE=ghostfolio}. It does not change the application's
 * normal security chain when that profile is disabled.
 */
@Configuration
@Profile("ghostfolio")
public class GhostfolioCompatibilitySecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain ghostfolioCompatibilitySecurityFilterChain(HttpSecurity http)
            throws Exception {
        return http
                .securityMatcher(
                        "/api/v1/info",
                        "/api/v1/health",
                        "/api/v1/auth/**",
                        "/api/v1/user",
                        "/api/v1/user/**",
                        "/api/v1/account",
                        "/api/v1/account/**",
                        "/api/v1/activities",
                        "/api/v1/activities/**",
                        "/api/v1/portfolio/**",
                        "/api/v2/portfolio/**",
                        "/api/assets/**")
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(
                        session ->
                                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }
}
