package com.smartbox.investory.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.smartbox.investory.marketradar.api.StrategistOutlookApi.Outlook;
import com.smartbox.investory.marketradar.application.StrategistOutlookService;
import com.smartbox.investory.marketradar.domain.*;
import com.smartbox.investory.marketradar.port.*;
import com.smartbox.investory.ui.marketradar.OutlookPlot;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

public class MarketRadarOutlookTemplateTest {
  @Test
  public void rendersPopulatedPlotsQuoteDatesAndNegativeReturnUnits() throws java.io.IOException {
    var store = mock(StrategistForecastStore.class);
    var market = mock(HistoricalMarketDataPort.class);
    LocalDate horizon = LocalDate.of(2026, 12, 31), asOf = LocalDate.of(2026, 7, 2);
    List<StrategistForecastStore.ForecastObservation> observations = new ArrayList<>();
    for (int i = 0; i < 4; i++)
      observations.add(
          new StrategistForecastStore.ForecastObservation(
              i + 1,
              new StrategistForecast(
                  "synthetic-" + i,
                  "Synthetic institution " + i,
                  "Fixture strategist",
                  Instant.parse("2026-07-01T15:00:00Z"),
                  horizon,
                  new BigDecimal(5000 + i * 200),
                  "https://example.com/fixture/" + i,
                  "Synthetic test source",
                  StrategistForecast.SourceType.PRIMARY,
                  Instant.parse("2026-07-01T16:00:00Z")),
              "VERIFIED",
              "ELIGIBLE"));
    when(store.observations(horizon)).thenReturn(observations);
    when(market.dailyBars(eq("^GSPC"), any(), any()))
        .thenReturn(
            List.of(bar("2025-12-31", 5000), bar("2026-06-30", 5000), bar("2026-07-01", 5500)));
    when(market.dailyBars(eq("^VIX"), any(), any()))
        .thenReturn(List.of(bar("2026-06-30", 20), bar("2026-07-01", 25)));
    String html = render(new StrategistOutlookService(store, market).outlook(horizon, asOf));
    assertThat(html)
        .contains(
            "2026-06-30",
            "2026-07-01",
            "6.00%",
            "-3.64%",
            "12.00 pp",
            "IQR",
            "Synthetic institution 0",
            "https://example.com/fixture/0",
            "Reference close known at publication",
            "0.00%",
            "Consensus target median and min/max",
            "VIX index points, separate scale",
            "<circle",
            "Forecast full-year return range in pp",
            "Last update age: 1 days as of 2026-07-02");
    assertThat(html).contains("5000.0 (quote 2026-06-30)");
    java.nio.file.Path artifact =
        java.nio.file.Path.of("target/ui-test-results/issue-112-synthetic.html");
    java.nio.file.Files.createDirectories(artifact.getParent());
    java.nio.file.Files.writeString(artifact, html);
  }

  @Test
  void rendersEmptyStateWithoutInventedPlots() {
    var store = mock(StrategistForecastStore.class);
    var market = mock(HistoricalMarketDataPort.class);
    when(store.observations(any())).thenReturn(List.of());
    String html =
        render(
            new StrategistOutlookService(store, market)
                .outlook(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 10, 10)));
    assertThat(html)
        .contains(
            "No current eligible forecasts",
            "Last update age: unavailable",
            "Insufficient dated history to plot",
            "Unavailable: no dated VIX history")
        .doesNotContain("<circle");
  }

  @Test
  void rendersStaleQuoteDateWithoutCurrentReturnNumbers() {
    var store = mock(StrategistForecastStore.class);
    var market = mock(HistoricalMarketDataPort.class);
    when(store.observations(any())).thenReturn(List.of());
    when(market.dailyBars(eq("^GSPC"), any(), any())).thenReturn(List.of(bar("2025-12-31", 5000)));
    String html =
        render(
            new StrategistOutlookService(store, market)
                .outlook(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 10, 1)));
    assertThat(html)
        .contains("Stale S&amp;P 500 close", "2025-12-31", "Unavailable")
        .doesNotContain("0.00%");
  }

  public static String render(Outlook outlook) {
    var resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode("HTML");
    var engine = new SpringTemplateEngine();
    engine.setTemplateResolver(resolver);
    var servlet = new MockServletContext();
    var request = new MockHttpServletRequest(servlet);
    request.setRequestURI("/market-radar");
    var response = new MockHttpServletResponse();
    var app = JakartaServletWebApplication.buildApplication(servlet);
    var context = new WebContext(app.buildExchange(request, response), Locale.US);
    context.setVariable("outlook", outlook);
    context.setVariable("consensusPlot", OutlookPlot.consensus(outlook));
    context.setVariable("dispersionPlot", OutlookPlot.dispersion(outlook));
    context.setVariable("vixPlot", OutlookPlot.vix(outlook));
    context.setVariable("signals", List.of());
    context.setVariable("states", List.of());
    context.setVariable("totalCount", 0);
    context.setVariable("interestingCount", 0);
    context.setVariable("includeNormal", false);
    return engine.process("market-radar", context);
  }

  private static DailyMarketBar bar(String date, double close) {
    return new DailyMarketBar(LocalDate.parse(date), close, close, close, close, 1);
  }
}
