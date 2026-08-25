package com.smartbox.investory.investment.reporting;

import com.smartbox.investory.investment.infrastructure.market.client.TwelveDataService;
import com.smartbox.investory.investment.infrastructure.persistence.benchmark.BenchmarkMonthlyCloseEntity;
import com.smartbox.investory.investment.infrastructure.persistence.benchmark.BenchmarkMonthlyCloseRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/** Owns provider fetching and persistence of benchmark market history. */
@Service
class BenchmarkMarketDataService {

  private static final String SYMBOL = "SPY";
  private static final int FETCH_MONTHS = 120;

  private final BenchmarkMonthlyCloseRepository repository;
  private final TwelveDataService twelveDataService;
  private LocalDate fetchAttemptedOn;

  BenchmarkMarketDataService(
      BenchmarkMonthlyCloseRepository repository, TwelveDataService twelveDataService) {
    this.repository = repository;
    this.twelveDataService = twelveDataService;
  }

  synchronized NavigableMap<String, Double> monthlyCloses(List<String> requiredLabels) {
    NavigableMap<String, Double> cached = loadCachedCloses();
    if (!hasRequiredCloses(cached, requiredLabels)
        && !LocalDate.now().equals(fetchAttemptedOn)) {
      NavigableMap<String, Double> fetched =
          twelveDataService.fetchMonthlyCloses(SYMBOL, FETCH_MONTHS);
      fetchAttemptedOn = LocalDate.now();
      if (!CollectionUtils.isEmpty(fetched)) {
        persistFetchedCloses(fetched);
        cached = loadCachedCloses();
      }
    }
    return cached;
  }

  private NavigableMap<String, Double> loadCachedCloses() {
    NavigableMap<String, Double> closes = new TreeMap<>();
    for (BenchmarkMonthlyCloseEntity row : repository.findBySymbolOrderByMonthDateAsc(SYMBOL)) {
      if (row.getMonthDate() != null && row.getClosePrice() != null) {
        closes.put(YearMonth.from(row.getMonthDate()).toString(), row.getClosePrice().doubleValue());
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
        repository.findBySymbolOrderByMonthDateAsc(SYMBOL).stream()
            .filter(row -> row.getMonthDate() != null)
            .collect(
                Collectors.toMap(
                    row -> YearMonth.from(row.getMonthDate()).toString(),
                    row -> row,
                    (first, ignored) -> first,
                    TreeMap::new));
    ZonedDateTime now = ZonedDateTime.now();
    List<BenchmarkMonthlyCloseEntity> rows = new ArrayList<>();
    fetched.forEach(
        (month, close) -> {
          if (close == null || close == 0.0) return;
          BenchmarkMonthlyCloseEntity row = existing.get(month);
          if (row == null) {
            row =
                BenchmarkMonthlyCloseEntity.builder()
                    .symbol(SYMBOL)
                    .monthDate(YearMonth.parse(month).atDay(1))
                    .build();
          }
          row.setClosePrice(close);
          row.setFetchedAt(now);
          rows.add(row);
        });
    if (!rows.isEmpty()) repository.saveAll(rows);
  }
}
