package com.smartbox.investory.ryczalt.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.ryczalt.application.port.RyczaltProfileReader;
import com.smartbox.investory.ryczalt.application.query.RyczaltPeriodReadModel;
import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RyczaltZusDraServiceTest {
  private final RyczaltAccountingApi accounting = mock(RyczaltAccountingApi.class);
  private final RyczaltProfileReader profiles = mock(RyczaltProfileReader.class);
  private final RyczaltZusDraService service =
      new RyczaltZusDraService(
          accounting, profiles, Clock.fixed(Instant.parse("2026-09-26T10:15:30Z"), ZoneOffset.UTC));

  @Test
  void generatesUnsignedZusDraDraftFromCompletedNativePeriod() {
    YearMonth month = YearMonth.of(2026, 8);
    when(profiles.read(7L))
        .thenReturn(
            new RyczaltProfile(
                7L,
                false,
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

    var document = service.generate(7L, month);
    String xml = new String(document.content(), StandardCharsets.UTF_8);

    assertEquals("ZUS_DRA_7_2026-08.xml", document.filename());
    assertTrue(xml.contains("xmlns=\"http://www.zus.pl/2026/KEDU_5_7\""));
    assertTrue(xml.contains("<p1>1234567890</p1>"));
    assertTrue(xml.contains("<p6>Alex &amp; Co</p6>"));
    assertTrue(xml.contains("<p17>3133.33</p17>"));
  }

  private RyczaltPeriodReadModel completePeriod(YearMonth month) {
    return new RyczaltPeriodReadModel(
        month,
        PeriodStatus.OPEN,
        List.of(),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new BigDecimal("3133.33"),
        BigDecimal.ZERO,
        0,
        0,
        RyczaltPeriodReadModel.ObligationTotals.empty(),
        new RyczaltPeriodReadModel.Completeness("COMPLETE", 0),
        Set.of());
  }
}
