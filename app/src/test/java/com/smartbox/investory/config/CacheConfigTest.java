package com.smartbox.investory.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.investment.reporting.BenchmarkService;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Cache Config")
class CacheConfigTest {

  @DisplayName("provides Caches Used By Investment Calculations")
  @Test
  void providesCachesUsedByInvestmentCalculations() {
    var cacheManager = new CacheConfig().cacheManager();

    assertThat(cacheManager.getCache("portfolioCalculation")).isNotNull();
    assertThat(cacheManager.getCache("benchmark")).isNotNull();
  }

  @Test
  void createsDistinctScopedBenchmarkKeys() throws Exception {
    Method method =
        BenchmarkService.class.getMethod("calculate", Long.class, java.util.Collection.class);
    var generator = new CacheConfig().investmentCalculationKeyGenerator();

    assertThat(generator.generate(new BenchmarkServiceTarget(), method, 7L, List.of(12L, 11L)))
        .isEqualTo("portfolio:7:accounts:11,12");
    assertThat(generator.generate(new BenchmarkServiceTarget(), method, 8L, List.of(12L, 11L)))
        .isEqualTo("portfolio:8:accounts:11,12");
  }

  private static final class BenchmarkServiceTarget {}
}
