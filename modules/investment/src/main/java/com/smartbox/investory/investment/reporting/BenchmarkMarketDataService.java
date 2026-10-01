package com.smartbox.investory.investment.reporting;

import com.smartbox.investory.investment.infrastructure.persistence.benchmark.BenchmarkMonthlyCloseEntity;
import com.smartbox.investory.investment.infrastructure.persistence.benchmark.BenchmarkMonthlyCloseRepository;
import com.smartbox.investory.investment.port.market.MarketDataProvider;
import com.smartbox.investory.shared.time.ApplicationTime;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/** Owns provider fetching and persistence of benchmark market history. */
@Service
public class BenchmarkMarketDataService {

  private static final int FETCH_MONTHS = 120;

  private final BenchmarkMonthlyCloseRepository repository;
  private final MarketDataProvider marketDataProvider;
  private final ApplicationTime applicationTime;
  private final String symbol;
  private LocalDate fetchAttemptedOn;

  public BenchmarkMarketDataService(
      BenchmarkMonthlyCloseRepository repository,
      MarketDataProvider marketDataProvider,
      ApplicationTime applicationTime) {
    this(repository, marketDataProvider, applicationTime, "SPY");
  }

  @org.springframework.beans.factory.annotation.Autowired
  public BenchmarkMarketDataService(
      BenchmarkMonthlyCloseRepository repository,
      MarketDataProvider marketDataProvider,
      ApplicationTime applicationTime,
      @Value("${app.benchmark.symbol:SPY}") String symbol) {
    this.repository = repository;
    this.marketDataProvider = marketDataProvider;
    this.applicationTime = applicationTime;
    this.symbol = symbol;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  synchronized NavigableMap<String, Double> monthlyCloses(List<String> requiredLabels) {
    NavigableMap<String, Double> cached = loadCachedCloses();
    if (!hasRequiredCloses(cached, requiredLabels)
        && !applicationTime.today().equals(fetchAttemptedOn)) {
      if (fetchAndPersist()) {
        cached = loadCachedCloses();
      }
    }
    return cached;
  }

  /** Refresh benchmark history with the market price job, including the current month-to-date. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @CacheEvict(cacheNames = "benchmark", allEntries = true)
  public synchronized void refreshMonthlyCloses() {
    fetchAndPersist();
  }

  private boolean fetchAndPersist() {
    LocalDate today = applicationTime.today();
    if (today.equals(fetchAttemptedOn)) return false;
    NavigableMap<String, Double> fetched =
        marketDataProvider.fetchMonthlyCloses(symbol, FETCH_MONTHS);
    fetchAttemptedOn = today;
    if (CollectionUtils.isEmpty(fetched)) return false;
    persistFetchedCloses(fetched);
    return true;
  }

  private NavigableMap<String, Double> loadCachedCloses() {
    NavigableMap<String, Double> closes = new TreeMap<>();
    for (BenchmarkMonthlyCloseEntity row : repository.findBySymbolOrderByMonthDateAsc(symbol)) {
      if (row.getMonthDate() != null && row.getClosePrice() != null) {
        closes.put(
            YearMonth.from(row.getMonthDate()).toString(), row.getClosePrice().doubleValue());
      }
    }
    return closes;
  }

  private static boolean hasRequiredCloses(
      NavigableMap<String, Double> closes, List<String> labels) {
    return !labels.isEmpty() && !closes.isEmpty() && labels.stream().allMatch(closes::containsKey);
  }

  private void persistFetchedCloses(NavigableMap<String, Double> fetched) {
    Map<String, BenchmarkMonthlyCloseEntity> existing =
        repository.findBySymbolOrderByMonthDateAsc(symbol).stream()
            .filter(row -> row.getMonthDate() != null)
            .collect(
                Collectors.toMap(
                    row -> YearMonth.from(row.getMonthDate()).toString(),
                    row -> row,
                    (first, ignored) -> first,
                    TreeMap::new));
    ZonedDateTime now = applicationTime.now(applicationTime.businessZone());
    List<BenchmarkMonthlyCloseEntity> rows = new ArrayList<>();
    fetched.forEach(
        (month, close) -> {
          if (close == null || close == 0.0) return;
          BenchmarkMonthlyCloseEntity row = existing.get(month);
          if (row == null) {
            row =
                BenchmarkMonthlyCloseEntity.builder()
                    .symbol(symbol)
                    .monthDate(YearMonth.parse(month).atDay(1))
                    .build();
          }
          row.setClosePrice(BigDecimal.valueOf(close));
          row.setFetchedAt(now);
          rows.add(row);
        });
    if (!rows.isEmpty()) repository.saveAll(rows);
  }
}
