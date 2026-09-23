package com.smartbox.investory.ryczalt.calculation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.ryczalt.calculation.zus.ZusCalculationInput;
import com.smartbox.investory.ryczalt.domain.ApprovalMethod;
import com.smartbox.investory.ryczalt.domain.ApprovalStatus;
import com.smartbox.investory.ryczalt.domain.PaymentVerificationPolicy;
import com.smartbox.investory.ryczalt.persistence.InvoiceDirection;
import com.smartbox.investory.ryczalt.persistence.RyczaltInvoiceEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltInvoiceJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltNativeMonthInputJpaRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativeMonthInputAggregatorTest {
  private final RyczaltInvoiceJpaRepository invoices = mock(RyczaltInvoiceJpaRepository.class);
  private final RyczaltNativeMonthInputJpaRepository monthInputs =
      mock(RyczaltNativeMonthInputJpaRepository.class);
  private final NativeMonthInputAggregator aggregator =
      new NativeMonthInputAggregator(invoices, monthInputs);

  @Test
  void groupsApprovedIncomeAndCostVatIntoNormalizedFacts() {
    RyczaltInvoiceEntity income =
        invoice(InvoiceDirection.INCOME, new BigDecimal("1000"), new BigDecimal("230"));
    income.applyDecision(
        null,
        "SERVICE",
        "DOMESTIC",
        BigDecimal.ONE,
        new BigDecimal("0.12"),
        PaymentVerificationPolicy.NOT_REQUIRED,
        ApprovalStatus.APPROVED,
        ApprovalMethod.MANUAL);
    RyczaltInvoiceEntity cost =
        invoice(InvoiceDirection.COST, new BigDecimal("500"), new BigDecimal("115"));
    cost.applyDecision(
        null,
        "OFFICE",
        "DOMESTIC",
        BigDecimal.ONE,
        null,
        PaymentVerificationPolicy.NOT_REQUIRED,
        ApprovalStatus.APPROVED,
        ApprovalMethod.MANUAL);
    when(invoices.findByProfileIdAndPeriodIdOrderByAccountingDateAscIdAsc(7L, 10L))
        .thenReturn(List.of(income, cost));

    var result =
        aggregator.aggregate(
            7L,
            10L,
            YearMonth.of(2026, 9),
            new ZusCalculationInput(true, false, "JDG", false, BigDecimal.ZERO, null),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO);

    assertEquals(new BigDecimal("1000"), result.revenueByRate().get(new BigDecimal("0.12")));
    assertEquals(new BigDecimal("230"), result.vat().outputVatBeforeCorrections());
    assertEquals(new BigDecimal("115"), result.vat().deductibleInputVat());
  }

  @Test
  void blocksCalculationWhenApprovedIncomeHasNoRate() {
    RyczaltInvoiceEntity income =
        invoice(InvoiceDirection.INCOME, new BigDecimal("1000"), new BigDecimal("230"));
    income.applyDecision(
        null,
        "SERVICE",
        "DOMESTIC",
        BigDecimal.ONE,
        null,
        PaymentVerificationPolicy.NOT_REQUIRED,
        ApprovalStatus.APPROVED,
        ApprovalMethod.MANUAL);
    when(invoices.findByProfileIdAndPeriodIdOrderByAccountingDateAscIdAsc(7L, 10L))
        .thenReturn(List.of(income));

    assertThrows(
        IllegalStateException.class,
        () ->
            aggregator.aggregate(
                7L,
                10L,
                YearMonth.of(2026, 9),
                new ZusCalculationInput(true, false, "JDG", false, BigDecimal.ZERO, null),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO));
  }

  @Test
  void acceptsForeignCurrencyIncomeWhenPlnBookedAmountIsAvailable() {
    RyczaltInvoiceEntity income =
        new RyczaltInvoiceEntity(
            mock(com.smartbox.investory.ryczalt.persistence.RyczaltPeriodEntity.class),
            7L,
            InvoiceDirection.INCOME,
            "EUR-1",
            LocalDate.of(2025, 1, 31),
            LocalDate.of(2025, 1, 31),
            new BigDecimal("1000"),
            BigDecimal.ZERO,
            new BigDecimal("1000"),
            CurrencyType.EUR,
            new BigDecimal("4300"),
            new BigDecimal("0.12"),
            null);
    income.applyDecision(
        null,
        "SERVICE",
        "EU_ZERO",
        BigDecimal.ONE,
        new BigDecimal("0.12"),
        PaymentVerificationPolicy.NOT_REQUIRED,
        ApprovalStatus.APPROVED,
        ApprovalMethod.MANUAL);
    when(invoices.findByProfileIdAndPeriodIdOrderByAccountingDateAscIdAsc(7L, 10L))
        .thenReturn(List.of(income));

    var result =
        aggregator.aggregate(
            7L,
            10L,
            YearMonth.of(2025, 1),
            new ZusCalculationInput(true, false, "JDG", false, BigDecimal.ZERO, null),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO);

    assertEquals(new BigDecimal("4300"), result.revenueByRate().get(new BigDecimal("0.12")));
  }

  private RyczaltInvoiceEntity invoice(InvoiceDirection direction, BigDecimal net, BigDecimal vat) {
    return new RyczaltInvoiceEntity(
        mock(com.smartbox.investory.ryczalt.persistence.RyczaltPeriodEntity.class),
        7L,
        direction,
        "INV-1",
        LocalDate.of(2026, 9, 1),
        LocalDate.of(2026, 9, 1),
        net,
        vat,
        net.add(vat),
        CurrencyType.PLN,
        net,
        null,
        direction == InvoiceDirection.COST ? vat : null);
  }
}
