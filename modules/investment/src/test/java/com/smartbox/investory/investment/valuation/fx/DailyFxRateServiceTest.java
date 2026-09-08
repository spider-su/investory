package com.smartbox.investory.investment.valuation.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.port.fx.FxRateProvider.FxHistoryQuote;
import com.smartbox.investory.investment.valuation.fx.persistence.DailyFxRateEntity;
import com.smartbox.investory.investment.valuation.fx.persistence.DailyFxRateRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyFxRateServiceTest {
  @Mock private DailyFxRateRepository repository;

  @Test
  void materializesAllDirectedPairsAndCarriesForwardMissingCalendarDays() {
    LocalDate observed = LocalDate.of(2024, 9, 2);
    LocalDate preceding = observed.minusDays(1);
    LocalDate following = observed.plusDays(1);
    when(repository.findByRateDateAndBaseAndToCurrency(any(), any(), any()))
        .thenReturn(Optional.empty());

    new DailyFxRateService(repository)
        .replaceRange(
            List.of(
                new FxHistoryQuote(
                    CurrencyType.USD, CurrencyType.PLN, new BigDecimal("4.0"), observed, observed),
                new FxHistoryQuote(
                    CurrencyType.USD, CurrencyType.EUR, new BigDecimal("0.8"), observed, observed)),
            preceding,
            following);

    ArgumentCaptor<DailyFxRateEntity> captor = ArgumentCaptor.forClass(DailyFxRateEntity.class);
    verify(repository, org.mockito.Mockito.times(18)).save(captor.capture());
    assertEquals(
        6, captor.getAllValues().stream().filter(r -> r.getRateDate().equals(preceding)).count());
    assertEquals(
        6, captor.getAllValues().stream().filter(r -> r.getRateDate().equals(observed)).count());
    assertEquals(
        6, captor.getAllValues().stream().filter(r -> r.getRateDate().equals(following)).count());
    assertEquals(
        "CARRY_FORWARD",
        captor.getAllValues().stream()
            .filter(r -> r.getRateDate().equals(preceding))
            .findFirst()
            .orElseThrow()
            .getMethod());
    assertEquals(
        "CARRY_FORWARD",
        captor.getAllValues().stream()
            .filter(r -> r.getRateDate().equals(following))
            .findFirst()
            .orElseThrow()
            .getMethod());
  }

  @Test
  void startsInitialLoadAtSeptemberFirstAndUsesShortOverlapAfterwards() {
    DailyFxRateService service = new DailyFxRateService(repository);
    assertEquals(LocalDate.of(2024, 9, 1), service.defaultStart());
    when(repository.count()).thenReturn(0L, 18L);
    assertEquals(LocalDate.of(2024, 9, 1), service.refreshStart(LocalDate.of(2024, 9, 3)));
    assertEquals(LocalDate.of(2026, 9, 1), service.refreshStart(LocalDate.of(2026, 9, 8)));
  }
}
