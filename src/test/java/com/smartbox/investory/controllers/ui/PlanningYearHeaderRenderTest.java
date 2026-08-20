package com.smartbox.investory.controllers.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.application.planning.PlanningYearStatus;
import com.smartbox.investory.application.simulation.SimulationScenario;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

class PlanningYearHeaderRenderTest {
  @Test
  void planningYearHeaderRendersForDraftAndClosedStates() throws Exception {
    String source = Files.readString(Path.of("src/main/resources/templates/planning-year.html"));
    int start = source.indexOf("~{fragments/app-header :: planningHeader(");
    int end = source.indexOf("}\"></div>", start) + 1;
    String headerTemplate = "<div th:replace=\"" + source.substring(start, end) + "\"></div>";

    for (PlanningYearStatus status : PlanningYearStatus.values()) {
      String rendered =
          templateEngine()
              .process(
                  headerTemplate,
                  context(status == PlanningYearStatus.CLOSED ? "Closed" : "Needs review", status));
      assertThat(rendered)
          .contains(
              "Planning year", status == PlanningYearStatus.CLOSED ? "Closed" : "Needs review");
    }
  }

  private static WebContext context(String ignored, PlanningYearStatus status) {
    var webApplication = JakartaServletWebApplication.buildApplication(new MockServletContext());
    Map<String, Object> variables = new HashMap<>();
    variables.put("profile", Map.of("portfolioId", 1L));
    variables.put("planningYear", Map.of("year", 2025, "status", status));
    variables.put("planningDisplayCurrency", CurrencyType.PLN);
    variables.put("selectedPlanId", null);
    variables.put("selectedScenario", SimulationScenario.BASE);
    return new WebContext(
        webApplication.buildExchange(new MockHttpServletRequest(), new MockHttpServletResponse()),
        java.util.Locale.US,
        variables);
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
    StringTemplateResolver inline = new StringTemplateResolver();
    inline.setTemplateMode(TemplateMode.HTML);
    inline.setCacheable(false);
    engine.addTemplateResolver(inline);
    return engine;
  }
}
