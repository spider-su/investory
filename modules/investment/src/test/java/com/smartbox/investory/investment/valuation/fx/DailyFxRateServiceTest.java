package com.smartbox.investory.investment.valuation.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.port.fx.FxRateProvider.FxHistoryQuote;
import com.smartbox.investory.investment.valuation.fx.persistence.CurrencyRateEntity;
import com.smartbox.investory.investment.valuation.fx.persistence.CurrencyRateRepository;
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
  @Mock private CurrencyRateRepository repository;

  @Test
  void storesOnlyObservedDatesAndAllDirectedPairs() {
    LocalDate observed = LocalDate.of(2024, 9, 2);
    LocalDate preceding = observed.minusDays(1);
    LocalDate following = observed.plusDays(1);
    when(repository.findByRateDateAndBaseAndToCurrencyAndPurpose(any(), any(), any(), any()))
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

    ArgumentCaptor<CurrencyRateEntity> captor = ArgumentCaptor.forClass(CurrencyRateEntity.class);
    verify(repository, org.mockito.Mockito.times(6)).save(captor.capture());
    assertEquals(
        6, captor.getAllValues().stream().filter(r -> r.getRateDate().equals(observed)).count());
    assertEquals(
        0, captor.getAllValues().stream().filter(r -> r.getRateDate().equals(preceding)).count());
    assertEquals(
        0, captor.getAllValues().stream().filter(r -> r.getRateDate().equals(following)).count());
    assertEquals("VALUATION", captor.getAllValues().getFirst().getPurpose());
    assertEquals("OBSERVED", captor.getAllValues().getFirst().getMethod());
  }

  @Test
  void startsInitialLoadAtSeptemberFirstAndUsesShortOverlapAfterwards() {
    DailyFxRateService service = new DailyFxRateService(repository);
    assertEquals(LocalDate.of(2024, 9, 1), service.defaultStart());
    when(repository.countByPurpose("VALUATION")).thenReturn(0L, 18L);
    assertEquals(LocalDate.of(2024, 9, 1), service.refreshStart(LocalDate.of(2024, 9, 3)));
    assertEquals(LocalDate.of(2026, 9, 1), service.refreshStart(LocalDate.of(2026, 9, 8)));
  }
}
