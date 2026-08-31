package com.smartbox.investory.config;

import static org.assertj.core.api.Assertions.assertThat;

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
}
