package com.smartbox.investory.investment.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.infrastructure.persistence.benchmark.BenchmarkMonthlyCloseEntity;
import com.smartbox.investory.investment.infrastructure.persistence.benchmark.BenchmarkMonthlyCloseRepository;
import com.smartbox.investory.investment.port.market.MarketDataProvider;
import com.smartbox.investory.shared.time.ClockApplicationTime;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BenchmarkMarketDataServiceTest {
  private final BenchmarkMonthlyCloseRepository repository = org.mockito.Mockito.mock();
  private final MarketDataProvider marketDataProvider = org.mockito.Mockito.mock();
  private final ClockApplicationTime time =
      new ClockApplicationTime(
          Clock.fixed(Instant.parse("2026-10-01T20:00:00Z"), ZoneOffset.UTC),
          ZoneId.of("Europe/Warsaw"));

  @Test
  void marketPriceRefreshPersistsCurrentMonthBenchmarkCloseOncePerDay() {
    TreeMap<String, Double> fetched = new TreeMap<>();
    fetched.put("2026-10", 701.25);
    when(marketDataProvider.fetchMonthlyCloses("SPY", 120)).thenReturn(fetched);
    when(repository.findBySymbolOrderByMonthDateAsc("SPY")).thenReturn(List.of());
    BenchmarkMarketDataService service =
        new BenchmarkMarketDataService(repository, marketDataProvider, time);

    service.refreshMonthlyCloses();
    service.refreshMonthlyCloses();

    ArgumentCaptor<List<BenchmarkMonthlyCloseEntity>> rows = ArgumentCaptor.forClass(List.class);
    verify(repository).saveAll(rows.capture());
    assertThat(rows.getValue())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.getSymbol()).isEqualTo("SPY");
              assertThat(row.getMonthDate()).isEqualTo(java.time.LocalDate.of(2026, 10, 1));
              assertThat(row.getClosePrice()).isEqualByComparingTo(new BigDecimal("701.25"));
            });
    verify(marketDataProvider, times(1)).fetchMonthlyCloses("SPY", 120);
  }
}
