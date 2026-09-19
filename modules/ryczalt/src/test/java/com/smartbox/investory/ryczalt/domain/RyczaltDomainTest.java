package com.smartbox.investory.ryczalt.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class RyczaltDomainTest {
  @Test
  void emptyBucketHasZeroMetadata() {
    assertEquals(0, Bucket.<String>empty().meta().count());
  }

  @Test
  void bucketMetadataReflectsContentsAndItemsAreImmutable() {
    Bucket<String> bucket = Bucket.of(List.of("one", "two"));

    assertEquals(2, bucket.meta().count());
    assertThrows(UnsupportedOperationException.class, () -> bucket.items().add("three"));
  }

  @Test
  void accountingPeriodKeepsIncomeAndCostBucketsSeparate() {
    AccountingPeriod period = AccountingPeriod.empty(YearMonth.of(2026, 9));
    Invoice invoice =
        new Invoice(
            "INV-1",
            java.time.LocalDate.of(2026, 9, 1),
            java.time.LocalDate.of(2026, 9, 1),
            java.math.BigDecimal.TEN,
            java.math.BigDecimal.ZERO,
            java.math.BigDecimal.TEN,
            java.util.Currency.getInstance("PLN"));

    AccountingPeriod withInvoice =
        new AccountingPeriod(
            period.period(),
            period.status(),
            Bucket.of(List.of(invoice)),
            period.costInvoices(),
            period.transactions(),
            period.obligations());

    assertEquals(1, withInvoice.incomeInvoices().meta().count());
    assertEquals(0, withInvoice.costInvoices().meta().count());
  }

  @Test
  void lifecycleHasPreliminaryStates() {
    assertEquals(5, PeriodStatus.values().length);
    assertEquals(PeriodStatus.OPEN, AccountingPeriod.empty(YearMonth.of(2026, 9)).status());
  }
}
