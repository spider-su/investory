package com.smartbox.investory.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.*;
import com.smartbox.investory.infrastructure.JdbcStrategistForecastStoreIT;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Browser rendering acceptance, deliberately isolated from live provider/deployment claims. */
class MarketRadarOutlookUiIT {
  @Test
  @org.junit.jupiter.api.Timeout(120)
  void realSourceReplayShowsFactsDistributionAndHonestPartialState() throws Exception {
    new JdbcStrategistForecastStoreIT().renderRealSourceAcceptance("forecast_source_browser");
    Path directory = Path.of("target/ui-test-results");
    String html = Files.readString(directory.resolve("issue-112-real-source.html"));
    try (Playwright playwright = Playwright.create();
        Browser browser =
            System.getProperty("outlook.browser.ws") == null
                ? playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))
                : playwright.chromium().connect(System.getProperty("outlook.browser.ws"))) {
      Page page = browser.newPage(new Browser.NewPageOptions().setViewportSize(1440, 1100));
      page.setDefaultTimeout(20000);
      List<String> errors = new ArrayList<>();
      page.onPageError(errors::add);
      // Local rendered application HTML and checked-in assets only. No external requests.
      page.route(
          "http://outlook.test/**",
          route -> {
            String path = java.net.URI.create(route.request().url()).getPath();
            if (path.equals("/market-radar"))
              route.fulfill(new Route.FulfillOptions().setContentType("text/html").setBody(html));
            else {
              try (var stream = getClass().getResourceAsStream("/static" + path)) {
                if (stream == null) route.fulfill(new Route.FulfillOptions().setStatus(404));
                else
                  route.fulfill(
                      new Route.FulfillOptions()
                          .setContentType(
                              path.endsWith(".css") ? "text/css" : "application/javascript")
                          .setBodyBytes(stream.readAllBytes()));
              } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
              }
            }
          });
      page.navigate("http://outlook.test/market-radar");
      assertThat(page.locator("#outlook-title").innerText()).isEqualTo("S&P 500 Outlook");
      assertThat(page.locator("main").innerText())
          .contains(
              "Consensus median target: 7900.0 index points",
              "7800.0 / 8000.0 index points",
              "Eligible institutions\n2",
              "Mike Wilson",
              "Dubravko Lakos-Bujas",
              "2026-09-21",
              "2026-07-01",
              "2026-12-31",
              "Insufficient dated history to plot");
      assertThat(page.locator("svg[aria-label='Consensus target median and min/max']").count())
          .isEqualTo(1);
      assertThat(page.locator("a[href^='https://www.goldmansachs.com/']").count()).isGreaterThan(0);
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(directory.resolve("issue-112-real-source.png"))
              .setFullPage(true));
      page.setViewportSize(390, 844);
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(directory.resolve("issue-112-real-source-mobile.png"))
              .setFullPage(true));
      new com.smartbox.investory.app.MarketRadarOutlookTemplateTest()
          .rendersPopulatedPlotsQuoteDatesAndNegativeReturnUnits();
      page.setViewportSize(1440, 1100);
      page.setContent(Files.readString(directory.resolve("issue-112-synthetic.html")));
      assertThat(page.locator("svg[aria-label='VIX index points, separate scale'] circle").count())
          .isEqualTo(2);
      assertThat(page.locator("svg[aria-label='Forecast full-year return range in pp']").count())
          .isEqualTo(1);
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(directory.resolve("issue-112-synthetic-plots.png"))
              .setFullPage(true));
      assertThat(errors).isEmpty();
    }
  }
}
