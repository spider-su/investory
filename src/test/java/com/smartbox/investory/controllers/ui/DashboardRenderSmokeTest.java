package com.smartbox.investory.controllers.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.application.dashboard.DashboardFacade;
import com.smartbox.investory.application.dashboard.DashboardQuery;
import com.smartbox.investory.config.BuildMetadata;
import com.smartbox.investory.services.BenchmarkService;
import com.smartbox.investory.services.PlanningPresentation;
import com.smartbox.investory.services.PortfolioService;
import com.smartbox.investory.services.dashboard.DashboardPeriodFilterService;
import com.smartbox.investory.services.models.Benchmark;
import com.smartbox.investory.services.models.Portfolio;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

class DashboardRenderSmokeTest {

  @Test
  void dashboardTemplateRendersThroughThymeleaf() {
    Portfolio portfolio = new Portfolio();
    portfolio.setBalance(100.0);
    Benchmark benchmark = new Benchmark();
    PortfolioService portfolioService = mock(PortfolioService.class);
    BenchmarkService benchmarkService = mock(BenchmarkService.class);
    when(portfolioService.calculateTotalProfitLoss()).thenReturn(portfolio);
    when(benchmarkService.calculate()).thenReturn(benchmark);

    var dashboard =
        new DashboardFacade(portfolioService, benchmarkService, new DashboardPeriodFilterService())
            .loadDashboard(new DashboardQuery(List.of(), false, "1Y"));

    var webApplication = JakartaServletWebApplication.buildApplication(new MockServletContext());
    var context =
        new WebContext(
            webApplication.buildExchange(
                new MockHttpServletRequest(), new MockHttpServletResponse()),
            java.util.Locale.US,
            Map.of(
                "dashboard",
                dashboard,
                "selectedPeriod",
                dashboard.selectedPeriod(),
                "periods",
                dashboard.periods(),
                "portfolioId",
                1L,
                "yahooFinanceUrl",
                "",
                "buildMetadata",
                BuildMetadata.development(),
                "format",
                new PlanningPresentation()));

    String html = templateEngine().process("dashboard", context);

    assertThat(html).contains("Investory", "Performance", "Portfolio structure");

    int topbarStart = html.indexOf("<header class=\"iv-topbar iv-app-header-shell\">");
    int secondaryStart =
        html.indexOf("<div class=\"iv-topbar__secondary iv-app-header-shell__secondary\">");
    assertThat(topbarStart).isGreaterThanOrEqualTo(0);
    assertThat(secondaryStart).isGreaterThan(topbarStart);

    String topbar = html.substring(topbarStart, secondaryStart);
    assertThat(topbar).contains("iv-topbar-summary", "iv-topbar__meta");
    assertThat(topbar).doesNotContain("id=\"view-accounts\"", "id=\"data-quality\"");
    assertThat(topbar.indexOf("id=\"cash-flows\"")).isGreaterThanOrEqualTo(0);
    assertThat(topbar.indexOf("id=\"investment-gain\""))
        .isGreaterThan(topbar.indexOf("id=\"cash-flows\""));
    assertThat(topbar.indexOf("id=\"balance-cash\""))
        .isGreaterThan(topbar.indexOf("id=\"investment-gain\""));

    int railLeftStart = html.indexOf("id=\"iv-header-rail-left\"", secondaryStart);
    int pageNavStart = html.indexOf("class=\"iv-page-nav-slot\"", secondaryStart);
    int accountsStart = html.indexOf("id=\"view-accounts\"", secondaryStart);
    assertThat(accountsStart).isGreaterThan(railLeftStart).isLessThan(pageNavStart);
    assertThat(html.substring(accountsStart, html.indexOf("</details>", accountsStart)))
        .contains("iv-header-rail__accounts");
    assertThat(pageNavStart).isGreaterThan(secondaryStart);
    int railRightStart = html.indexOf("id=\"iv-header-rail-right\"", secondaryStart);
    int valuationStart = html.indexOf("id=\"data-quality\"", secondaryStart);
    assertThat(valuationStart).isGreaterThan(railRightStart);
    assertThat(html.substring(valuationStart, html.indexOf("</details>", valuationStart)))
        .contains("iv-data-quality-popover");
  }

  private static TemplateEngine templateEngine() {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
    resolver.setCheckExistence(true);
    SpringTemplateEngine engine = new SpringTemplateEngine();
    engine.setTemplateResolver(resolver);
    return engine;
  }
}
