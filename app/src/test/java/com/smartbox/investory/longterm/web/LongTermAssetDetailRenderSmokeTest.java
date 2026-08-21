package com.smartbox.investory.ui.longterm;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.longterm.application.model.AnnualEconomics;
import com.smartbox.investory.longterm.application.model.LongTermAssetSummary;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetEntity;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

class LongTermAssetDetailRenderSmokeTest {

  @Test
  void longTermAssetDetailTemplateRendersThroughThymeleaf() {
    LongTermAssetEntity asset = new LongTermAssetEntity();
    asset.setId(7L);
    asset.setPortfolioId(1L);
    asset.setName("Cash reserve");
    asset.setType(LongTermAssetType.CASH_RESERVE);
    asset.setCurrency(CurrencyType.PLN);
    asset.setCurrentValue(new BigDecimal("1000.00"));
    asset.setActive(true);

    AnnualEconomics economics =
        new AnnualEconomics(
            new BigDecimal("120.00"),
            new BigDecimal("20.00"),
            new BigDecimal("10.00"),
            new BigDecimal("100.00"),
            new BigDecimal("90.00"),
            new BigDecimal("0.12"),
            new BigDecimal("0.10"),
            new BigDecimal("0.09"));
    LongTermAssetSummary summary =
        new LongTermAssetSummary(
            asset.getId(),
            asset.getName(),
            asset.getType(),
            asset.getCurrency(),
            asset.getCurrentValue(),
            null,
            BigDecimal.ZERO,
            economics,
            null,
            null);

    var webApplication = JakartaServletWebApplication.buildApplication(new MockServletContext());
    Map<String, Object> variables = new HashMap<>();
    variables.put("asset", asset);
    variables.put("portfolioId", 1L);
    variables.put("summary", summary);
    variables.put("cashFlows", List.of());
    variables.put("bondDetails", null);
    variables.put("depositDetails", null);
    variables.put("valuationPeriods", List.of());
    variables.put("bondRatePeriods", List.of());
    var context =
        new WebContext(
            webApplication.buildExchange(
                new MockHttpServletRequest(), new MockHttpServletResponse()),
            java.util.Locale.US,
            variables);

    String html = templateEngine().process("long-term-asset-detail", context);

    assertThat(html).contains("Cash reserve", "Gross annual income", "Recurring cash flows");
  }

  private static SpringTemplateEngine templateEngine() {
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
