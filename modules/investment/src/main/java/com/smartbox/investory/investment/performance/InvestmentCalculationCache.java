package com.smartbox.investory.investment.performance;

import com.github.benmanes.caffeine.cache.Cache;
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

  public void invalidatePortfolio(Long portfolioId) {
    if (portfolioId == null) {
      invalidate();
      return;
    }
    var cache = cacheManager.getCache("portfolioCalculation");
    if (cache != null) {
      cache.evict(portfolioId);
      cache.evict("all");
    }
    evictBenchmarkPortfolio(portfolioId);
    // Unscoped aggregate results include every portfolio and must be rebuilt after any tenant
    // changes. Scoped entries for other portfolios remain warm.
    var benchmark = cacheManager.getCache("benchmark");
    if (benchmark != null) {
      benchmark.evict("all");
      if (benchmark.getNativeCache() instanceof Cache<?, ?> caffeine) {
        caffeine.asMap().keySet().stream()
            .filter(key -> key instanceof String value && value.startsWith("accounts:"))
            .toList()
            .forEach(benchmark::evict);
      }
    }
  }

  private void evictBenchmarkPortfolio(Long portfolioId) {
    var benchmark = cacheManager.getCache("benchmark");
    if (benchmark == null || !(benchmark.getNativeCache() instanceof Cache<?, ?> caffeine)) {
      return;
    }
    String prefix = "portfolio:" + portfolioId + ":";
    caffeine.asMap().keySet().stream()
        .filter(key -> key instanceof String value && value.startsWith(prefix))
        .toList()
        .forEach(benchmark::evict);
  }

  private void clear(String name) {
    var cache = cacheManager.getCache(name);
    if (cache != null) {
      cache.clear();
    }
  }
}
