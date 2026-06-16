package com.example.demo.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;

/**
 * Test-only helper that re-wires Spring Security into the auto-configured
 * {@code MockMvc} bean.
 *
 * <p>Spring Boot 4.x no longer attaches {@code SecurityMockMvcConfigurers.springSecurity()}
 * to the {@code MockMvc} produced by {@code @WebMvcTest} automatically, which
 * means {@code @WithMockUser} stops populating the request's {@code SecurityContext}
 * and every secured endpoint comes back as {@code 401 Unauthorized}.
 *
 * <p>Importing this config from a {@code @WebMvcTest} re-enables the previous
 * behaviour without forcing each test to build {@code MockMvc} by hand.
 */
@TestConfiguration
public class MockMvcSecurityTestConfig {

    @Bean
    MockMvcBuilderCustomizer mockMvcSpringSecurityCustomizer() {
        return builder -> builder.apply(SecurityMockMvcConfigurers.springSecurity());
    }
}

