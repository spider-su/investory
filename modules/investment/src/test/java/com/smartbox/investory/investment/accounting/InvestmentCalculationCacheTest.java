package com.smartbox.investory.investment.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.springframework.cache.caffeine.CaffeineCacheManager;

class InvestmentCalculationCacheTest {

  @Test
  void invalidationClearsPortfolioAndBenchmarkEntries() {
    CaffeineCacheManager manager = new CaffeineCacheManager("portfolioCalculation", "benchmark");
    manager.setCaffeine(Caffeine.newBuilder());
    InvestmentCalculationCache cache = new InvestmentCalculationCache(manager);
    manager.getCache("portfolioCalculation").put("all", "old-portfolio");
    manager.getCache("benchmark").put("all", "old-benchmark");

    cache.invalidate();

    assertThat(manager.getCache("portfolioCalculation").get("all")).isNull();
    assertThat(manager.getCache("benchmark").get("all")).isNull();
  }
}
