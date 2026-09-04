package com.smartbox.investory.investment.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.caffeine.CaffeineCacheManager;

@DisplayName("Investment Calculation Cache")
class InvestmentCalculationCacheTest {

  @DisplayName("invalidation Clears Portfolio And Benchmark Entries")
  @Test
  void invalidationClearsPortfolioAndBenchmarkEntries() {
    CaffeineCacheManager manager = new CaffeineCacheManager("portfolioCalculation", "benchmark");
    manager.setCaffeine(Caffeine.newBuilder());
    InvestmentCalculationCache cache = new InvestmentCalculationCache(manager);
    manager.getCache("portfolioCalculation").put("all", "old-portfolio");
    manager.getCache("portfolioCalculation").put(7L, "old-a");
    manager.getCache("portfolioCalculation").put(8L, "keep-b");
    manager.getCache("benchmark").put("all", "old-benchmark");
    manager.getCache("benchmark").put("portfolio:7:all", "old-a");
    manager.getCache("benchmark").put("portfolio:8:all", "keep-b");
    manager.getCache("benchmark").put("accounts:11", "old-aggregate-selection");

    cache.invalidatePortfolio(7L);

    assertThat(manager.getCache("portfolioCalculation").get("all")).isNull();
    assertThat(manager.getCache("portfolioCalculation").get(7L)).isNull();
    assertThat(manager.getCache("portfolioCalculation").get(8L)).isNotNull();
    assertThat(manager.getCache("benchmark").get("all")).isNull();
    assertThat(manager.getCache("benchmark").get("portfolio:7:all")).isNull();
    assertThat(manager.getCache("benchmark").get("portfolio:8:all")).isNotNull();
    assertThat(manager.getCache("benchmark").get("accounts:11")).isNull();
  }
}
