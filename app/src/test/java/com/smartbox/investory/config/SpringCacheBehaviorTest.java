package com.smartbox.investory.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class SpringCacheBehaviorTest {

  @Test
  void repeatedCallsHitCacheAndDifferentAccountSetsUseDifferentEntries() {
    try (var context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
      var calculator = context.getBean(TestCalculator.class);

      assertThat(calculator.calculate(java.util.List.of(2L, 1L))).isEqualTo("calculation-1");
      assertThat(calculator.calculate(java.util.List.of(1L, 2L))).isEqualTo("calculation-1");
      assertThat(calculator.calculate(java.util.List.of(3L))).isEqualTo("calculation-2");
      assertThat(calculator.calls()).isEqualTo(2);
    }
  }

  @Configuration
  @EnableCaching
  static class TestConfiguration {
    @Bean
    CaffeineCacheManager cacheManager() {
      var manager = new CaffeineCacheManager("testCalculation");
      manager.setCaffeine(Caffeine.newBuilder());
      return manager;
    }

    @Bean
    TestCalculator testCalculator() {
      return new TestCalculator();
    }
  }

  public static class TestCalculator {
    private final AtomicInteger calls = new AtomicInteger();

    @Cacheable(cacheNames = "testCalculation", key = "#root.target.key(#accountIds)")
    public String calculate(Collection<Long> accountIds) {
      return "calculation-" + calls.incrementAndGet();
    }

    public String key(Collection<Long> accountIds) {
      return accountIds.stream()
          .sorted()
          .distinct()
          .map(String::valueOf)
          .reduce((a, b) -> a + "," + b)
          .orElse("all");
    }

    public int calls() {
      return calls.get();
    }
  }
}
