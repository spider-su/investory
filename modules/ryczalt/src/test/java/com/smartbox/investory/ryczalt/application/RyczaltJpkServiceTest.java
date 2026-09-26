package com.smartbox.investory.ryczalt.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.ryczalt.application.port.RyczaltProfileReader;
import com.smartbox.investory.ryczalt.application.query.RyczaltInvoiceReadModel;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodReadModel;
import com.smartbox.investory.ryczalt.domain.ApprovalStatus;
import com.smartbox.investory.ryczalt.domain.InvoicePaymentStatus;
import com.smartbox.investory.ryczalt.domain.PaymentVerificationPolicy;
import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import com.smartbox.investory.ryczalt.persistence.InvoiceDirection;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RyczaltJpkServiceTest {
  private final RyczaltAccountingApi accounting = mock(RyczaltAccountingApi.class);
  private final RyczaltProfileReader profiles = mock(RyczaltProfileReader.class);
  private final RyczaltJpkService service =
      new RyczaltJpkService(
          accounting, profiles, Clock.fixed(Instant.parse("2026-09-26T10:15:30Z"), ZoneOffset.UTC));

  @Test
  void generatesNativeJpkUsingTaxpayerProfileAndBookedPln() {
    YearMonth month = YearMonth.of(2026, 8);
    when(profiles.read(7L))
        .thenReturn(
            new RyczaltProfile(
                7L,
                true,
                "1234567890",
                "Alex & Co",
                "1215",
                "alex@example.test",
                "VAT-ACCOUNT",
                "RYCZALT-ACCOUNT",
                "ZUS-ACCOUNT",
                "Alex",
                "Owner",
                LocalDate.of(1980, 1, 2)));
    when(accounting.period(7L, month)).thenReturn(completePeriod(month));
    when(accounting.invoices(7L, month))
        .thenReturn(
            List.of(
                new RyczaltInvoiceReadModel(
                    11L,
                    InvoiceDirection.INCOME,
                    "FV/8",
                    LocalDate.of(2026, 8, 20),
                    LocalDate.of(2026, 8, 20),
                    new BigDecimal("1000"),
                    new BigDecimal("230"),
                    new BigDecimal("1230"),
                    CurrencyType.EUR,
                    new BigDecimal("4300.50"),
                    new BigDecimal("0.12"),
                    null,
                    null,
                    new RyczaltInvoiceReadModel.CounterpartyView(
                        3L, "Client & Sons", null, "9876543210"),
                    ApprovalStatus.APPROVED,
                    null,
                    PaymentVerificationPolicy.NOT_REQUIRED,
                    InvoicePaymentStatus.NOT_REQUIRED,
                    "KSEF",
                    "invoice-11")));

    var document = service.generate(7L, month);
    String xml = new String(document.content(), java.nio.charset.StandardCharsets.UTF_8);

    assertEquals("JPK_V7M_7_2026-08.xml", document.filename());
    assertTrue(xml.contains("<etd:NIP>1234567890</etd:NIP>"));
    assertTrue(xml.contains("<PelnaNazwa>Alex &amp; Co</PelnaNazwa>"));
    assertTrue(xml.contains("<K_19>4300.50</K_19>"));
    assertTrue(xml.contains("<NazwaKontrahenta>Client &amp; Sons</NazwaKontrahenta>"));
    assertTrue(xml.contains("<Rok>2026</Rok><Miesiac>8</Miesiac>"));
  }

  @Test
  void rejectsIncompletePeriod() {
    YearMonth month = YearMonth.of(2026, 8);
    when(profiles.read(7L)).thenReturn(mock(RyczaltProfile.class));
    when(accounting.period(7L, month))
        .thenReturn(
            new RyczaltPeriodReadModel(
                month,
                PeriodStatus.OPEN,
                List.of(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                0,
                RyczaltPeriodReadModel.ObligationTotals.empty(),
                new RyczaltPeriodReadModel.Completeness("INCOMPLETE", 1),
                Set.of()));

    assertThrows(IllegalStateException.class, () -> service.generate(7L, month));
  }

  private RyczaltPeriodReadModel completePeriod(YearMonth month) {
    return new RyczaltPeriodReadModel(
        month,
        PeriodStatus.OPEN,
        List.of(),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        1,
        0,
        RyczaltPeriodReadModel.ObligationTotals.empty(),
        new RyczaltPeriodReadModel.Completeness("COMPLETE", 0),
        Set.of());
  }
}
