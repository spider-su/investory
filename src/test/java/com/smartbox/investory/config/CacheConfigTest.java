package com.smartbox.investory.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CacheConfigTest {

  @Test
  void providesCachesUsedByInvestmentCalculations() {
    var cacheManager = new CacheConfig().cacheManager();

    assertThat(cacheManager.getCache("portfolioCalculation")).isNotNull();
    assertThat(cacheManager.getCache("benchmark")).isNotNull();
  }
}
