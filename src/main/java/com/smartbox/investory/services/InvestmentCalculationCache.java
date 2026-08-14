package com.smartbox.investory.services;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InvestmentCalculationCache {
  private final CacheManager cacheManager;

  public void invalidate() {
    clear("portfolioCalculation");
    clear("benchmark");
  }

  private void clear(String name) {
    var cache = cacheManager.getCache(name);
    if (cache != null) {
      cache.clear();
    }
  }
}
