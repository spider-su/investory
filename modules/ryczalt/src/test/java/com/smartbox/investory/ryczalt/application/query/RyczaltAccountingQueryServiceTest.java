package com.smartbox.investory.ryczalt.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.ryczalt.domain.ObligationStatus;
import com.smartbox.investory.ryczalt.domain.ObligationType;
import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import com.smartbox.investory.ryczalt.persistence.CalculationStatus;
import com.smartbox.investory.ryczalt.persistence.CalculationType;
import com.smartbox.investory.ryczalt.persistence.InvoiceDirection;
import com.smartbox.investory.ryczalt.persistence.RyczaltCalculationEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltCalculationJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltInvoiceEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltInvoiceJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltObligationEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltObligationJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPaymentMatchEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltPaymentMatchJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltSourceReferenceEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltSourceReferenceJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltTransactionEntity;
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
  private final RyczaltSourceReferenceJpaRepository sourceReferences =
      mock(RyczaltSourceReferenceJpaRepository.class);
  private RyczaltAccountingQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new RyczaltAccountingQueryService(
            periods,
            invoices,
            transactions,
            obligations,
            matches,
            calculations,
            new com.fasterxml.jackson.databind.ObjectMapper(),
            new RyczaltInvoiceQueryService(periods, invoices, sourceReferences),
            new RyczaltPaymentQueryService(periods, obligations, matches, BigDecimal.ZERO));
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
  void periodAuditReadsNativeCalculationValues() {
    RyczaltPeriodEntity period = mock(RyczaltPeriodEntity.class);
    RyczaltCalculationEntity ryczalt =
        calculation(
            CalculationType.RYCZALT,
            "{\"revenueBeforeDeductions\":61849.12,\"taxableBase\":61102,\"calculatedTax\":7332,\"healthDeduction\":747.52,\"socialContributionDeduction\":0}");
    RyczaltCalculationEntity vat =
        calculation(
            CalculationType.VAT,
            "{\"outputVatAfterSalesCorrection\":6808,\"deductibleInputVat\":68.54,\"calculatedVat\":6739}");
    RyczaltCalculationEntity zus = calculation(CalculationType.ZUS, "{\"totalZus\":1495.04}");
    when(periods.findByProfileIdAndYearAndMonth(7L, 2026, 2)).thenReturn(Optional.of(period));
    when(period.getProfileId()).thenReturn(7L);
    when(period.id()).thenReturn(1L);
    when(period.getStatus()).thenReturn(PeriodStatus.FROZEN);
    when(invoices.findByProfileIdAndPeriodIdOrderByAccountingDateAscIdAsc(7L, 1L))
        .thenReturn(List.of());
    when(transactions.findByProfileIdAndPeriodIdOrderByBookingDateAscIdAsc(7L, 1L))
        .thenReturn(List.of());
    when(obligations.findByProfileIdAndPeriodIdOrderByTypeAsc(7L, 1L)).thenReturn(List.of());
    when(calculations.findByProfileIdAndPeriodId(7L, 1L)).thenReturn(List.of(ryczalt, vat, zus));

    var audit = service.getPeriod(7L, YearMonth.of(2026, 2)).audit();

    assertEquals(new BigDecimal("61849.12"), audit.revenue());
    assertEquals(new BigDecimal("61102"), audit.taxableBase());
    assertEquals(new BigDecimal("7332"), audit.monthlyAdvance());
    assertEquals(new BigDecimal("6808"), audit.outputVat());
    assertEquals(new BigDecimal("68.54"), audit.inputVat());
    assertEquals(new BigDecimal("6739"), audit.finalPayable());
  }

  private static RyczaltCalculationEntity calculation(CalculationType type, String result) {
    RyczaltCalculationEntity calculation = mock(RyczaltCalculationEntity.class);
    when(calculation.getType()).thenReturn(type);
    when(calculation.getStatus()).thenReturn(CalculationStatus.FROZEN);
    when(calculation.isCurrent()).thenReturn(true);
    when(calculation.getResultJson()).thenReturn(result);
    return calculation;
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

  @Test
  void paymentHistoryUsesBankBookingDateRatherThanMatchCreationDate() {
    RyczaltPeriodEntity period = mock(RyczaltPeriodEntity.class);
    RyczaltObligationEntity obligation = mock(RyczaltObligationEntity.class);
    RyczaltPaymentMatchEntity match = mock(RyczaltPaymentMatchEntity.class);
    RyczaltTransactionEntity transaction = mock(RyczaltTransactionEntity.class);
    when(period.getProfileId()).thenReturn(7L);
    when(period.getYear()).thenReturn(2026);
    when(period.getMonth()).thenReturn(1);
    when(period.id()).thenReturn(11L);
    when(obligation.id()).thenReturn(91L);
    when(obligation.getType()).thenReturn(ObligationType.ZUS);
    when(obligation.getAmount()).thenReturn(new BigDecimal("100.00"));
    when(obligation.getCurrency()).thenReturn(CurrencyType.PLN);
    when(obligation.getDueDate()).thenReturn(LocalDate.of(2026, 2, 20));
    when(matches.findByProfileIdAndObligationId(7L, 91L)).thenReturn(List.of(match));
    when(matches.allocatedForObligation(7L, 91L)).thenReturn(new BigDecimal("100.00"));
    when(match.getTransaction()).thenReturn(transaction);
    when(transaction.getBookingDate()).thenReturn(LocalDate.of(2026, 2, 3));
    when(periods.findByProfileIdOrderByYearDescMonthDesc(7L)).thenReturn(List.of(period));
    when(obligations.findByProfileIdAndPeriodIdOrderByTypeAsc(7L, 11L))
        .thenReturn(List.of(obligation));

    var history =
        service.getPaymentHistory(7L, YearMonth.of(2026, 1), YearMonth.of(2026, 1), "ZUS");

    assertEquals(LocalDate.of(2026, 2, 3), history.getFirst().paymentDate());
  }

  @Test
  void invoiceReadExposesSafeProvenanceInBulkAndKeepsProfileIsolation() {
    RyczaltPeriodEntity period = mock(RyczaltPeriodEntity.class);
    RyczaltInvoiceEntity ksefInvoice = invoice(11L);
    RyczaltInvoiceEntity uploadInvoice = invoice(12L);
    RyczaltInvoiceEntity missingSourceInvoice = invoice(13L);
    when(periods.findByProfileIdAndYearAndMonth(7L, 2026, 1)).thenReturn(Optional.of(period));
    when(period.getProfileId()).thenReturn(7L);
    when(period.id()).thenReturn(10L);
    when(invoices.findByProfileIdAndPeriodIdOrderByAccountingDateAscIdAsc(7L, 10L))
        .thenReturn(List.of(ksefInvoice, uploadInvoice, missingSourceInvoice));
    when(sourceReferences.findByProfileIdAndEntityTypeAndEntityIdIn(
            7L, "INVOICE", List.of(11L, 12L, 13L)))
        .thenReturn(
            List.of(
                new RyczaltSourceReferenceEntity(7L, "INVOICE", 11L, "KSEF", "KSEF-2026-001", null),
                new RyczaltSourceReferenceEntity(
                    7L, "INVOICE", 12L, "UPLOAD", "sha256-content-hash", null),
                new RyczaltSourceReferenceEntity(
                    7L, "INVOICE", 99L, "KSEF", "UNRELATED-INVOICE", null)));

    List<RyczaltInvoiceReadModel> result =
        serviceWithSources().getInvoices(7L, YearMonth.of(2026, 1));

    assertEquals("KSEF", result.get(0).sourceType());
    assertEquals("KSEF-2026-001", result.get(0).sourceReference());
    assertEquals("UPLOAD", result.get(1).sourceType());
    assertEquals(null, result.get(1).sourceReference());
    assertEquals(null, result.get(2).sourceType());
    assertEquals(null, result.get(2).sourceReference());
    verify(sourceReferences)
        .findByProfileIdAndEntityTypeAndEntityIdIn(7L, "INVOICE", List.of(11L, 12L, 13L));
  }

  private RyczaltAccountingQueryService serviceWithSources() {
    return new RyczaltAccountingQueryService(
        periods,
        invoices,
        transactions,
        obligations,
        matches,
        calculations,
        sourceReferences,
        new com.fasterxml.jackson.databind.ObjectMapper(),
        new RyczaltInvoiceQueryService(periods, invoices, sourceReferences),
        new RyczaltPaymentQueryService(periods, obligations, matches, BigDecimal.ZERO));
  }

  private static RyczaltInvoiceEntity invoice(long id) {
    RyczaltInvoiceEntity invoice = mock(RyczaltInvoiceEntity.class);
    when(invoice.getId()).thenReturn(id);
    when(invoice.getDirection()).thenReturn(InvoiceDirection.INCOME);
    when(invoice.getReference()).thenReturn("DOCUMENT-" + id);
    when(invoice.getIssueDate()).thenReturn(LocalDate.of(2026, 1, 10));
    when(invoice.getAccountingDate()).thenReturn(LocalDate.of(2026, 1, 10));
    when(invoice.getNetAmount()).thenReturn(BigDecimal.TEN);
    when(invoice.getVatAmount()).thenReturn(BigDecimal.ONE);
    when(invoice.getGrossAmount()).thenReturn(BigDecimal.valueOf(11));
    when(invoice.getCurrency()).thenReturn(CurrencyType.PLN);
    when(invoice.getApprovalStatus())
        .thenReturn(com.smartbox.investory.ryczalt.domain.ApprovalStatus.NEEDS_REVIEW);
    when(invoice.getPaymentVerificationPolicy())
        .thenReturn(com.smartbox.investory.ryczalt.domain.PaymentVerificationPolicy.REQUIRED);
    when(invoice.getPaymentStatus()).thenReturn("UNMATCHED");
    return invoice;
  }
}
