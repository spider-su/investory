package com.smartbox.investory.ryczalt.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.ryczalt.domain.ObligationStatus;
import com.smartbox.investory.ryczalt.domain.ObligationType;
import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import com.smartbox.investory.ryczalt.persistence.RyczaltCalculationJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltInvoiceJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltObligationEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltObligationJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPaymentMatchJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltTransactionJpaRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RyczaltAccountingQueryServiceTest {
  private final RyczaltPeriodJpaRepository periods = mock(RyczaltPeriodJpaRepository.class);
  private final RyczaltInvoiceJpaRepository invoices = mock(RyczaltInvoiceJpaRepository.class);
  private final RyczaltTransactionJpaRepository transactions =
      mock(RyczaltTransactionJpaRepository.class);
  private final RyczaltObligationJpaRepository obligations =
      mock(RyczaltObligationJpaRepository.class);
  private final RyczaltPaymentMatchJpaRepository matches =
      mock(RyczaltPaymentMatchJpaRepository.class);
  private final RyczaltCalculationJpaRepository calculations =
      mock(RyczaltCalculationJpaRepository.class);
  private RyczaltAccountingQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new RyczaltAccountingQueryService(
            periods, invoices, transactions, obligations, matches, calculations);
  }

  @Test
  void missingPeriodIsExplicitAndProfileScoped() {
    when(periods.findByProfileIdAndYearAndMonth(7L, 2026, 1)).thenReturn(Optional.empty());

    assertThrows(
        RyczaltPeriodNotFoundException.class, () -> service.getPeriod(7L, YearMonth.of(2026, 1)));
    verify(periods).findByProfileIdAndYearAndMonth(7L, 2026, 1);
  }

  @Test
  void periodListIsProfileScopedAndNewestFirstAsReturnedByRepository() {
    RyczaltPeriodEntity newer = new RyczaltPeriodEntity(7L, 2026, 2, PeriodStatus.OPEN);
    RyczaltPeriodEntity older = new RyczaltPeriodEntity(7L, 2026, 1, PeriodStatus.FROZEN);
    when(periods.findByProfileIdOrderByYearDescMonthDesc(7L)).thenReturn(List.of(newer, older));

    assertEquals(
        List.of(
            new RyczaltPeriodListItem(YearMonth.of(2026, 2), PeriodStatus.OPEN),
            new RyczaltPeriodListItem(YearMonth.of(2026, 1), PeriodStatus.FROZEN)),
        service.listPeriods(7L));
    verify(periods).findByProfileIdOrderByYearDescMonthDesc(7L);
  }

  @Test
  void obligationReadUsesPersistedMatchesAndDoesNotMutate() {
    RyczaltPeriodEntity period = mock(RyczaltPeriodEntity.class);
    RyczaltObligationEntity obligation = mock(RyczaltObligationEntity.class);
    when(period.getProfileId()).thenReturn(7L);
    when(period.id()).thenReturn(11L);
    when(period.getYear()).thenReturn(2026);
    when(period.getMonth()).thenReturn(1);
    when(period.getStatus()).thenReturn(PeriodStatus.FROZEN);
    when(obligations.findByProfileIdAndPeriodIdOrderByTypeAsc(7L, 11L))
        .thenReturn(List.of(obligation));
    when(obligation.id()).thenReturn(91L);
    when(obligation.getType()).thenReturn(ObligationType.ZUS);
    when(obligation.getAmount()).thenReturn(new BigDecimal("100.00"));
    when(obligation.getCurrency()).thenReturn(CurrencyType.PLN);
    when(obligation.getDueDate()).thenReturn(LocalDate.of(2026, 2, 15));
    when(matches.allocatedForObligation(7L, 91L)).thenReturn(new BigDecimal("40.00"));
    when(periods.findByProfileIdAndYearAndMonth(7L, 2026, 1)).thenReturn(Optional.of(period));
    when(calculations.findByProfileIdAndPeriodId(7L, 11L)).thenReturn(List.of());

    RyczaltObligationReadModel result =
        service.getObligations(7L, YearMonth.of(2026, 1)).getFirst();

    assertEquals(new BigDecimal("40.00"), result.paidAmount());
    assertEquals(new BigDecimal("60.00"), result.outstandingAmount());
    assertEquals(ObligationStatus.PARTIALLY_PAID, result.status());
    verify(matches).allocatedForObligation(7L, 91L);
  }
}
