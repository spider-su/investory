package com.smartbox.investory.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

  @Bean
  CacheManager cacheManager() {
    CaffeineCacheManager manager = new CaffeineCacheManager("portfolioCalculation", "benchmark");
    manager.setCaffeine(
        Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterAccess(java.time.Duration.ofHours(6))
            .recordStats());
    return manager;
  }

  @Bean("investmentCalculationKeyGenerator")
  KeyGenerator investmentCalculationKeyGenerator() {
    return (target, method, params) -> {
      if (params.length == 0 || params[0] == null) {
        return "all";
      }
      if (params[0] instanceof Collection<?> values) {
        return values.stream()
            .filter(Objects::nonNull)
            .map(String::valueOf)
            .distinct()
            .sorted()
            .collect(Collectors.joining(",", "accounts:", ""));
      }
      return method.getName() + ":" + params[0];
    };
  }
}
