package com.smartbox.investory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides the Jackson 2 mapper still used by the integrations module. */
@Configuration
public class JacksonConfig {
  @Bean
  public ObjectMapper legacyObjectMapper() {
    return new ObjectMapper();
  }
}
