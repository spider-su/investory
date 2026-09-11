package com.smartbox.investory.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Bounded, external-target, read-only load smoke test for one page. */
@DisplayName("Long-Term Assets read-only stress")
class LongTermAssetsReadOnlyStress {
  private static final String DEFAULT_BASE_URL =
      "https://investory-61359240267.europe-central2.run.app";
  private static final String PATH = "/portfolios/2/long-term-assets";
  private static final int MAX_USERS = 20;
  private static final Path RESULTS = Path.of("target", "ui-test-results", "stress");

  @Test
  void longTermAssetsIsReadOnlyUnderBoundedLoad() throws Exception {
    Config config = Config.fromEnvironment();
    StressMetrics metrics = new StressMetrics(config.users * config.iterations);
    ExecutorService executor = Executors.newFixedThreadPool(config.users);
    List<Future<Void>> futures = new ArrayList<>();
    for (int user = 0; user < config.users; user++) {
      int userId = user + 1;
      futures.add(executor.submit((Callable<Void>) () -> runUser(config, metrics, userId)));
    }
    executor.shutdown();
    for (Future<Void> future : futures) {
      try {
        future.get();
      } catch (ExecutionException failure) {
        metrics.scenarioFailure(failure.getCause());
      }
    }
    writeResults(config, metrics);
    System.out.println(metrics.summary(config));

    assertThat(metrics.unexpectedWrites()).as("unexpected write requests").isZero();
    assertThat(metrics.http5xx()).as("HTTP 5xx responses").isZero();
    assertThat(metrics.criticalFailures()).as("critical HTTP failures").isZero();
    assertThat(metrics.pageErrors()).as("browser page errors").isZero();
    assertThat(metrics.consoleErrors()).as("JavaScript console errors").isZero();
    assertThat(metrics.failedScenarios()).isZero();
    assertThat(metrics.repeated429()).as("repeated or sustained HTTP 429 responses").isFalse();
  }

  private static Void runUser(Config config, StressMetrics metrics, int userId) {
    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright
                .chromium()
                .launch(
                    new BrowserType.LaunchOptions()
                        .setHeadless(true)
                        .setExecutablePath(
                            Path.of(
                                System.getenv()
                                    .getOrDefault(
                                        "STRESS_BROWSER_PATH",
                                        ".playwright/chromium-1234/chrome-linux64/chrome"))));
        BrowserContext context =
            browser.newContext(
                new Browser.NewContextOptions()
                    .setHttpCredentials(config.username, config.password)
                    .setViewportSize(1440, 1000))) {
      context.setDefaultTimeout(Duration.ofSeconds(30).toMillis());
      context.setDefaultNavigationTimeout(Duration.ofSeconds(45).toMillis());
      for (int iteration = 1; iteration <= config.iterations; iteration++) {
        runScenario(config, metrics, context, userId, iteration);
        if (iteration < config.iterations)
          sleep(config.delayMs + ThreadLocalRandom.current().nextLong(config.jitterMs + 1));
      }
    }
    return null;
  }

  private static void runScenario(
      Config config, StressMetrics metrics, BrowserContext context, int userId, int iteration) {
    metrics.scenarioStarted();
    Page page = context.newPage();
    AtomicReference<String> action = new AtomicReference<>("open Long-Term Assets page");
    List<String> errors = new ArrayList<>();
    page.onRequest(
        request -> {
          metrics.requestStarted();
          if (List.of("POST", "PUT", "PATCH", "DELETE").contains(request.method())) {
            String violation = request.method() + " " + request.url() + " [" + action.get() + "]";
            metrics.unexpectedWrite(violation);
            throw new IllegalStateException("Unexpected write request: " + violation);
          }
        });
    page.onResponse(response -> recordResponse(metrics, response, config.baseUrl));
    page.onRequestFailed(request -> metrics.failedRequest());
    page.onConsoleMessage(
        message -> {
          if ("error".equals(message.type())) {
            metrics.consoleError(message.text());
            errors.add("console: " + message.text());
          }
        });
    page.onPageError(
        error -> {
          metrics.pageError(error);
          errors.add("page: " + error);
        });
    try {
      long initialStart = System.nanoTime();
      Response response = page.navigate(config.baseUrl + PATH);
      metrics.initialDuration(System.nanoTime() - initialStart);
      assertThat(response).as("Long-Term Assets navigation response").isNotNull();
      assertThat(response.status()).isEqualTo(200);
      assertRendered(page);
      // The page has no safe sorting/filtering controls. Expanding native details panels is safe.
      action.set("expand asset group details");
      expandReadOnlyDetails(page);
      assertLayoutAndAssetData(page);
      action.set("reload Long-Term Assets page");
      long reloadStart = System.nanoTime();
      Response reload = page.reload();
      metrics.reloadDuration(System.nanoTime() - reloadStart);
      assertThat(reload).isNotNull();
      assertThat(reload.status()).isEqualTo(200);
      assertRendered(page);
      action.set("expand asset group details after reload");
      expandReadOnlyDetails(page);
      assertLayoutAndAssetData(page);
      assertThat(errors).as("browser errors for user %d iteration %d", userId, iteration).isEmpty();
      metrics.scenarioPassed();
    } catch (RuntimeException | AssertionError failure) {
      metrics.scenarioFailure(failure);
      saveFailure(page, context, userId, iteration);
      throw failure;
    } finally {
      page.close();
    }
  }

  private static void assertRendered(Page page) {
    assertThat(page.title()).containsIgnoringCase("Long-term assets");
    page.locator("main").waitFor();
    page.locator(".iv-long-term-assets-sections").waitFor();
    page.locator("table.long-term-assets-table").first().waitFor();
    assertThat(page.locator("main").isVisible()).isTrue();
    assertThat(page.locator(".iv-long-term-assets-sections").isVisible()).isTrue();
    assertThat(page.locator("table.long-term-assets-table").count()).isGreaterThan(0);
    assertThat(page.locator(".iv-collapsed-summary").count()).isGreaterThan(0);
    assertThat(page.locator(".iv-collapsed-summary").first().isVisible()).isTrue();
    assertThat(page.locator("body").textContent())
        .doesNotContain("Whitelabel Error Page", "Internal Server Error", "Exception:");
  }

  private static void expandReadOnlyDetails(Page page) {
    Locator summaries = page.locator(".iv-planning-section__details summary");
    for (int index = 0; index < summaries.count(); index++) {
      Locator summary = summaries.nth(index);
      if (!"true".equals(summary.locator("xpath=..").getAttribute("open"))) summary.click();
    }
  }

  private static void assertLayoutAndAssetData(Page page) {
    assertThat(page.locator(".iv-planning-section").count()).isGreaterThan(0);
    for (String sectionId :
        new String[] {"real-estate", "bonds", "cash-reserves", "personal-assets"}) {
      Locator section = page.locator("#" + sectionId);
      assertThat(section.isVisible()).as("visible %s asset card", sectionId).isTrue();
      assertThat(section.locator("h2").textContent()).isNotBlank();
      assertThat(section.locator("table.long-term-assets-table").count())
          .as("tables in %s asset card", sectionId)
          .isGreaterThan(0);
      assertThat(section.locator("tbody tr").count())
          .as("asset data in %s asset card", sectionId)
          .isGreaterThan(0);
      assertThat(section.boundingBox()).as("layout box for %s asset card", sectionId).isNotNull();
    }
    assertThat(page.locator(".iv-portfolio-structure--long-term").isVisible()).isTrue();
    assertThat(page.locator(".iv-long-term-allocation__legend").isVisible()).isTrue();
    assertThat(page.evaluate("document.documentElement.scrollWidth <= window.innerWidth + 1"))
        .isEqualTo(true);
  }

  private static void recordResponse(StressMetrics metrics, Response response, String baseUrl) {
    metrics.response(response.status());
    if (response.url().startsWith(baseUrl)
        && response.status() >= 400
        && response.status() != 401
        && response.status() != 429)
      metrics.criticalFailure("HTTP " + response.status() + " " + response.url());
  }

  private static void saveFailure(Page page, BrowserContext context, int userId, int iteration) {
    try {
      Files.createDirectories(RESULTS);
      String name = "long-term-assets-user-" + userId + "-iteration-" + iteration;
      page.screenshot(new Page.ScreenshotOptions().setPath(RESULTS.resolve(name + ".png")));
      Files.writeString(RESULTS.resolve(name + ".html"), page.content());
    } catch (IOException ignored) {
      // The assertion failure is the primary result; artifact writing is best effort.
    }
  }

  private static void writeResults(Config config, StressMetrics metrics) throws IOException {
    Files.createDirectories(RESULTS);
    Files.writeString(RESULTS.resolve("long-term-assets-readonly.json"), metrics.json(config));
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Stress test interrupted", interrupted);
    }
  }

  record Config(
      String baseUrl,
      int users,
      int iterations,
      long delayMs,
      long jitterMs,
      String username,
      String password) {
    static Config fromEnvironment() {
      String profile = value("STRESS_PROFILE", "low").toLowerCase(Locale.ROOT);
      int profileUsers =
          switch (profile) {
            case "smoke" -> 1;
            case "low" -> 5;
            case "medium" -> 10;
            case "high" -> 20;
            default -> throw new IllegalArgumentException("Unknown STRESS_PROFILE: " + profile);
          };
      int users = integer("STRESS_USERS", profileUsers);
      int iterations = integer("STRESS_ITERATIONS", 3);
      if (users < 1 || users > MAX_USERS)
        throw new IllegalArgumentException("STRESS_USERS must be 1..20");
      if (iterations < 1) throw new IllegalArgumentException("STRESS_ITERATIONS must be positive");
      return new Config(
          value("BASE_URL", DEFAULT_BASE_URL).replaceAll("/$", ""),
          users,
          iterations,
          Long.parseLong(value("STRESS_DELAY_MS", "300")),
          Long.parseLong(value("STRESS_JITTER_MS", "200")),
          value("STRESS_USERNAME", "admin"),
          value("STRESS_PASSWORD", "change-me-admin"));
    }

    private static String value(String key, String fallback) {
      return System.getenv().getOrDefault(key, fallback);
    }

    private static int integer(String key, int fallback) {
      return Integer.parseInt(value(key, Integer.toString(fallback)));
    }
  }
}
