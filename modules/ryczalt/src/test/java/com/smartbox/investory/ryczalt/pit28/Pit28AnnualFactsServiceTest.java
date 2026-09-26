package com.smartbox.investory.ryczalt.pit28;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.ryczalt.application.RyczaltAccountingApi;
import com.smartbox.investory.ryczalt.application.query.RyczaltInvoiceReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodListItem;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodReadModel;
import com.smartbox.investory.ryczalt.domain.ApprovalStatus;
import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import com.smartbox.investory.ryczalt.persistence.CalculationStatus;
import com.smartbox.investory.ryczalt.persistence.InvoiceDirection;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Pit28AnnualFactsServiceTest {
  private final RyczaltAccountingApi accounting = mock(RyczaltAccountingApi.class);
  private final Pit28AnnualFactsService service = new Pit28AnnualFactsService(accounting);

  @Test
  void aggregatesPlnEurAndUsdUsingBookedPlnOnly() {
    YearMonth month = YearMonth.of(2026, 3);
    when(accounting.periods(7L))
        .thenReturn(List.of(new RyczaltPeriodListItem(month, PeriodStatus.OPEN)));
    when(accounting.period(7L, month)).thenReturn(completePeriod(month));
    when(accounting.zusFacts(7L, month))
        .thenReturn(
            new Pit28MonthlyZusFacts(
                new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("150")));
    when(accounting.invoices(7L, month))
        .thenReturn(
            List.of(
                invoice("PLN-1", CurrencyType.PLN, "1000", "1000"),
                invoice("EUR-1", CurrencyType.EUR, "200", "2000"),
                invoice("USD-1", CurrencyType.USD, "300", "3000")));

    var result = service.collect(7L, 2026);

    assertEquals(Pit28ReadinessStatus.READY, result.readiness().status());
    assertEquals(new BigDecimal("6000"), result.facts().revenuePln());
    assertEquals(new BigDecimal("2000"), result.facts().revenueByOriginalCurrency().get("EUR"));
    assertEquals(new BigDecimal("3000"), result.facts().revenueByOriginalCurrency().get("USD"));
  }

  @Test
  void unresolvedForeignCurrencyBlocksInsteadOfBecomingZero() {
    YearMonth month = YearMonth.of(2026, 3);
    when(accounting.periods(7L))
        .thenReturn(List.of(new RyczaltPeriodListItem(month, PeriodStatus.OPEN)));
    when(accounting.period(7L, month)).thenReturn(completePeriod(month));
    when(accounting.zusFacts(7L, month))
        .thenReturn(new Pit28MonthlyZusFacts(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    when(accounting.invoices(7L, month))
        .thenReturn(List.of(invoice("EUR-1", CurrencyType.EUR, "200", null)));

    var result = service.collect(7L, 2026);

    assertEquals(Pit28ReadinessStatus.BLOCKED, result.readiness().status());
    assertTrue(
        result.readiness().issues().stream().anyMatch(issue -> "MISSING_FX".equals(issue.code())));
    assertEquals(BigDecimal.ZERO, result.facts().revenuePln());
  }

  private RyczaltPeriodReadModel completePeriod(YearMonth month) {
    return new RyczaltPeriodReadModel(
        month,
        PeriodStatus.OPEN,
        List.of(
            new RyczaltPeriodReadModel.CalculationState(
                "ZUS", CalculationStatus.CURRENT, new BigDecimal("150"), "ZUS", "ZUS", null)),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new BigDecimal("150"),
        BigDecimal.ZERO,
        0,
        0,
        RyczaltPeriodReadModel.ObligationTotals.empty(),
        new RyczaltPeriodReadModel.Completeness("COMPLETE", 0),
        Set.of());
  }

  private RyczaltInvoiceReadModel invoice(
      String reference, CurrencyType currency, String net, String bookedPln) {
    return new RyczaltInvoiceReadModel(
        reference.hashCode(),
        InvoiceDirection.INCOME,
        reference,
        LocalDate.of(2026, 3, 15),
        LocalDate.of(2026, 3, 15),
        new BigDecimal(net),
        BigDecimal.ZERO,
        new BigDecimal(net),
        currency,
        bookedPln == null ? null : new BigDecimal(bookedPln),
        new BigDecimal("0.12"),
        BigDecimal.ZERO,
        null,
        null,
        null,
        null,
        ApprovalStatus.APPROVED,
        null,
        null,
        null,
        null,
        null);
  }
}
