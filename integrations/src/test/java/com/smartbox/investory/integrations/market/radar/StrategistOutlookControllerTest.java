package com.smartbox.investory.integrations.market.radar;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.marketradar.api.StrategistOutlookApi;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StrategistOutlookControllerTest {
  private final StrategistOutlookApi api = mock(StrategistOutlookApi.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new StrategistOutlookController(api, clock)).build();

  @Test
  void filtersByTargetAndAsOfAndReturnsStableSummaryFields() throws Exception {
    var body =
        new StrategistOutlookApi.Outlook(
            LocalDate.of(2026, 12, 31),
            LocalDate.of(2026, 10, 1),
            4.5,
            12.0,
            7.2,
            5.0,
            9.5,
            14.5,
            10.0,
            13.0,
            null,
            null,
            3,
            1,
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 9, 20),
            "price-index history",
            new StrategistOutlookApi.ListRow[0],
            new StrategistOutlookApi.ListRow[0],
            new StrategistOutlookApi.Accuracy(0, null, null, "Limited sample"),
            new StrategistOutlookApi.VixContext(
                null, null, null, null, null, "Unavailable", "^VIX"));
    when(api.outlook(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 10, 1))).thenReturn(body);

    mvc.perform(
            get("/api/v1/market-radar/strategist-outlook")
                .param("targetDate", "2026-12-31")
                .param("asOf", "2026-10-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.targetDate").value("2026-12-31"))
        .andExpect(jsonPath("$.actualYtdPercent").value(4.5))
        .andExpect(jsonPath("$.dispersionPercentagePoints").value(5.0))
        .andExpect(jsonPath("$.eligibleInstitutions").value(3));
    verify(api).outlook(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 10, 1));
  }

  @Test
  void defaultsAsOfToApplicationClock() throws Exception {
    when(api.outlook(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 10, 10)))
        .thenReturn(
            new StrategistOutlookApi.Outlook(
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 10, 10),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                null,
                null,
                "missing",
                new StrategistOutlookApi.ListRow[0],
                new StrategistOutlookApi.ListRow[0],
                new StrategistOutlookApi.Accuracy(0, null, null, "Limited"),
                new StrategistOutlookApi.VixContext(
                    null, null, null, null, null, "Unavailable", "^VIX")));
    mvc.perform(get("/api/v1/market-radar/strategist-outlook").param("targetDate", "2026-12-31"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.asOf").value("2026-10-10"));
  }
}
