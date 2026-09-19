package com.smartbox.investory.accounting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartbox.investory.accounting.api.AccountingUserApi;
import com.smartbox.investory.ryczalt.application.RyczaltAccountingApi;
import com.smartbox.investory.ryczalt.application.bank.RyczaltBankApi;
import com.smartbox.investory.ryczalt.application.ksef.RyczaltKsefApi;
import com.smartbox.investory.ryczalt.application.ksef.RyczaltKsefSyncResult;
import com.smartbox.investory.ryczalt.application.query.RyczaltPaymentHistoryReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodListItem;
import com.smartbox.investory.ryczalt.domain.ObligationStatus;
import com.smartbox.investory.ryczalt.domain.ObligationType;
import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import com.smartbox.investory.ryczalt.integration.ksef.KsefSyncMode;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LegacyAccountingApiBridgeTest {
  private final AccountingUserApi legacy = Mockito.mock(AccountingUserApi.class);
  private final RyczaltAccountingApi ryczalt = Mockito.mock(RyczaltAccountingApi.class);
  private final RyczaltBankApi ryczaltBank = Mockito.mock(RyczaltBankApi.class);
  private final RyczaltKsefApi ryczaltKsef = Mockito.mock(RyczaltKsefApi.class);
  private final LegacyAccountingApiBridge bridge =
      new LegacyAccountingApiBridge(legacy, ryczalt, ryczaltBank, ryczaltKsef);

  @Test
  void routesBankImportToNativeAcquisition() {
    byte[] content = "csv".getBytes();
    when(ryczaltBank.importBank(7, content, "bank.csv", "text/csv"))
        .thenReturn(
            new com.smartbox.investory.ryczalt.application.bank.RyczaltBankImportResult(1, 1, 0));

    bridge.importBank(7, "bank.csv", "text/csv", content, YearMonth.of(2026, 1));

    verify(ryczaltBank).importBank(7, content, "bank.csv", "text/csv");
    verify(legacy, Mockito.never())
        .importBank(Mockito.anyLong(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
  }

  @Test
  void routesSyncKsefToNativePurchasesAndSales() {
    var month = YearMonth.of(2026, 1);
    when(ryczaltKsef.sync(7, month, Set.of(KsefSyncMode.PURCHASES, KsefSyncMode.SALES)))
        .thenReturn(new RyczaltKsefSyncResult(2, 2, 0, 0, 0));

    var result = bridge.syncKsef(7, month);

    assertThat(result.imported()).isEqualTo(2);
    verify(ryczaltKsef).sync(7, month, Set.of(KsefSyncMode.PURCHASES, KsefSyncMode.SALES));
    verify(legacy, Mockito.never()).syncKsef(Mockito.anyLong(), Mockito.any());
  }

  @Test
  void routesSyncKsefSellerToNativeSales() {
    var month = YearMonth.of(2026, 1);
    when(ryczaltKsef.sync(7, month, Set.of(KsefSyncMode.SALES)))
        .thenReturn(new RyczaltKsefSyncResult(1, 1, 0, 0, 0));

    bridge.syncKsefSeller(7, month);

    verify(ryczaltKsef).sync(7, month, Set.of(KsefSyncMode.SALES));
  }

  @Test
  void routesReimportKsefToNativeReimport() {
    var month = YearMonth.of(2026, 1);
    when(ryczaltKsef.reimport(7, month)).thenReturn(new RyczaltKsefSyncResult(1, 0, 0, 1, 0));

    var result = bridge.reimportKsef(7, month);

    assertThat(result.imported()).isEqualTo(1);
    verify(ryczaltKsef).reimport(7, month);
  }

  @Test
  void keepsThirdPartyKsefOnLegacy() {
    var month = YearMonth.of(2026, 1);
    when(legacy.syncKsefThirdParty(7, month))
        .thenReturn(new AccountingUserApi.KsefSyncResult("NOT_SUPPORTED", 0, 0, 0, 0, 0, ""));

    bridge.syncKsefThirdParty(7, month);

    verify(legacy).syncKsefThirdParty(7, month);
    verifyNoInteractions(ryczaltKsef);
  }

  @Test
  void delegatesHistoricalSettlementToLegacyApi() {
    var month = YearMonth.of(2026, 1);
    when(ryczalt.hasPeriod(7, month)).thenReturn(false);

    bridge.settle(7, month);

    verify(legacy).settle(7, month);
  }

  @Test
  void usesNativeSettlementForRyczaltBackedPeriod() {
    var month = YearMonth.of(2026, 1);
    when(ryczalt.hasPeriod(7, month)).thenReturn(true);

    bridge.settle(7, month);

    verify(ryczalt).settle(7, month);
    verifyNoInteractions(legacy);
  }

  @Test
  void fallsBackWhenNoNativePeriodsExist() {
    when(ryczalt.periods(7)).thenReturn(List.of());

    bridge.months(7);

    verify(legacy).months(7);
  }

  @Test
  void mergesMonthsAndNativeWinsForDuplicateMonth() {
    var november = YearMonth.of(2025, 11);
    var january = YearMonth.of(2026, 1);
    var february = YearMonth.of(2026, 2);
    when(legacy.months(7))
        .thenReturn(
            List.of(
                new AccountingUserApi.MonthRef(november, "November 2025", "OPEN", "Open"),
                new AccountingUserApi.MonthRef(january, "January 2026", "LOCKED", "Locked")));
    when(ryczalt.periods(7))
        .thenReturn(
            List.of(
                new RyczaltPeriodListItem(january, PeriodStatus.FROZEN),
                new RyczaltPeriodListItem(february, PeriodStatus.OPEN)));

    var result = bridge.months(7);

    assertThat(result)
        .extracting(AccountingUserApi.MonthRef::month)
        .containsExactly(november, january, february);
    assertThat(result.get(1).lifecycle()).isEqualTo("FROZEN");
  }

  @Test
  void returnsNativeOnlyMonthsWhenLegacyHasNone() {
    var month = YearMonth.of(2026, 2);
    when(legacy.months(7)).thenReturn(List.of());
    when(ryczalt.periods(7))
        .thenReturn(List.of(new RyczaltPeriodListItem(month, PeriodStatus.OPEN)));

    assertThat(bridge.months(7))
        .extracting(AccountingUserApi.MonthRef::month)
        .containsExactly(month);
  }

  @Test
  void mergesPaymentHistoryAndNativeWinsForOverlappingPeriodAndType() {
    var january = YearMonth.of(2026, 1);
    var february = YearMonth.of(2026, 2);
    when(legacy.paymentHistory(7, january, february, null))
        .thenReturn(List.of(payment("RYCZALT", january, "100"), payment("VAT", february, "200")));
    when(ryczalt.periods(7))
        .thenReturn(List.of(new RyczaltPeriodListItem(january, PeriodStatus.FROZEN)));
    when(ryczalt.paymentHistory(7, january, february, null))
        .thenReturn(List.of(nativePayment(ObligationType.RYCZALT, january, "150")));

    var result = bridge.paymentHistory(7, january, february, null);

    assertThat(result)
        .extracting(AccountingUserApi.PaymentHistoryView::type)
        .containsExactly("RYCZALT", "VAT");
    assertThat(result.get(0).amount()).isEqualByComparingTo("150");
    assertThat(result.get(1).amount()).isEqualByComparingTo("200");
  }

  @Test
  void keepsLegacyOnlyPaymentHistory() {
    var month = YearMonth.of(2025, 12);
    var expected = payment("VAT", month, "200");
    when(legacy.paymentHistory(7, month, month, null)).thenReturn(List.of(expected));
    when(ryczalt.periods(7)).thenReturn(List.of());

    assertThat(bridge.paymentHistory(7, month, month, null)).containsExactly(expected);
  }

  @Test
  void returnsNativeOnlyPaymentHistory() {
    var month = YearMonth.of(2026, 1);
    when(legacy.paymentHistory(7, month, month, null)).thenReturn(List.of());
    when(ryczalt.periods(7))
        .thenReturn(List.of(new RyczaltPeriodListItem(month, PeriodStatus.OPEN)));
    var nativeResult = nativePayment(ObligationType.VAT, month, "200");
    when(ryczalt.paymentHistory(7, month, month, null)).thenReturn(List.of(nativeResult));

    assertThat(bridge.paymentHistory(7, month, month, null).get(0).amount())
        .isEqualByComparingTo("200");
  }

  private AccountingUserApi.PaymentHistoryView payment(
      String type, YearMonth period, String amount) {
    var value = new BigDecimal(amount);
    return new AccountingUserApi.PaymentHistoryView(
        type, period, value, BigDecimal.ZERO, value, null, null, "OPEN");
  }

  private RyczaltPaymentHistoryReadModel nativePayment(
      ObligationType type, YearMonth period, String amount) {
    var value = new BigDecimal(amount);
    return new RyczaltPaymentHistoryReadModel(
        type, period, value, BigDecimal.ZERO, value, null, null, ObligationStatus.OPEN);
  }
}
