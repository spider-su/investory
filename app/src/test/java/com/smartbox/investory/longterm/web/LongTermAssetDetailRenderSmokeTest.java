package com.smartbox.investory.ui.longterm;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.api.model.CashFlowType;
import com.smartbox.investory.longterm.api.model.Frequency;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.longterm.api.model.RentalContractStatusModel;
import com.smartbox.investory.longterm.application.model.AnnualEconomics;
import com.smartbox.investory.longterm.application.model.LongTermAssetSummary;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetEntity;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

@DisplayName("Long Term Asset Detail Render Smoke")
class LongTermAssetDetailRenderSmokeTest {

  @DisplayName("long Term Asset Detail Template Renders Through Thymeleaf")
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

    assertThat(html).contains("Cash reserve", "Gross annual income", "Planning periods");
  }

  @DisplayName("real Estate Detail Renders Multiple Contract Accordion Through Thymeleaf")
  @Test
  void realEstateDetailRendersMultipleContractAccordionThroughThymeleaf() {
    LocalDate today = LocalDate.of(2026, 8, 24);
    var asset =
        new AssetView(
            7L,
            1L,
            "Rental home",
            LongTermAssetType.REAL_ESTATE,
            CurrencyType.PLN,
            null,
            new BigDecimal("700000"),
            new BigDecimal("800000"),
            new BigDecimal("3000"),
            true,
            null,
            false);
    var economics =
        new AnnualEconomicsView(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    var planning =
        new RealEstatePlanningView(
            new BigDecimal("3000"),
            new BigDecimal("4000"),
            new BigDecimal("4000"),
            new BigDecimal("500"),
            new BigDecimal("250"),
            new BigDecimal("3250"),
            new BigDecimal("0.04875"));
    var current =
        contract(
            22L,
            "Current tenant",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
            LocalDate.of(2026, 12, 31),
            RentalContractStatusModel.CURRENT,
            new BigDecimal("4000"));
    var ended =
        contract(
            11L,
            "Former tenant",
            LocalDate.of(2025, 1, 1),
            LocalDate.of(2025, 12, 31),
            LocalDate.of(2025, 12, 31),
            RentalContractStatusModel.ENDED,
            new BigDecimal("3500"));
    var contracts = List.of(current, ended);

    Map<String, Object> variables = new HashMap<>();
    variables.put("asset", asset);
    variables.put("portfolioId", 1L);
    variables.put(
        "summary",
        new AssetSummaryView(
            7L,
            "Rental home",
            LongTermAssetType.REAL_ESTATE,
            CurrencyType.PLN,
            asset.currentValue(),
            null,
            BigDecimal.ZERO,
            economics,
            planning,
            null,
            null));
    variables.put("contracts", contracts);
    variables.put(
        "contractForms",
        Map.of(
            current.id(), RentalContractForm.from(current),
            ended.id(), RentalContractForm.from(ended)));
    variables.put("rentalContract", new RentalContractForm());
    variables.put("today", today);
    variables.put("expectedPropertyGrowth", null);
    variables.put("valuationPeriods", List.of());

    var webApplication = JakartaServletWebApplication.buildApplication(new MockServletContext());
    var context =
        new WebContext(
            webApplication.buildExchange(
                new MockHttpServletRequest(), new MockHttpServletResponse()),
            java.util.Locale.US,
            variables);

    String html = templateEngine().process("real-estate-detail", context);

    assertThat(html).contains("Current tenant", "Former tenant", "2025-12-31");
    assertThat(html)
        .contains("name=\"terminationDate\" type=\"date\" min=\"2026-01-01\" max=\"2026-08-24\"");
    assertThat(
            org.springframework.util.StringUtils.countOccurrencesOf(
                html, "<details class=\"iv-rental-contract\""))
        .isEqualTo(2);
    assertThat(org.springframework.util.StringUtils.countOccurrencesOf(html, " open=\"open\""))
        .isEqualTo(1);
  }

  private static RentalContractView contract(
      Long id,
      String tenant,
      LocalDate start,
      LocalDate expectedEnd,
      LocalDate effectiveEnd,
      RentalContractStatusModel status,
      BigDecimal rent) {
    return new RentalContractView(
        id,
        tenant,
        tenant.toLowerCase().replace(' ', '.') + "@example.com",
        null,
        start,
        expectedEnd,
        null,
        effectiveEnd,
        status,
        null,
        new BigDecimal("2500"),
        List.of(new RentalTermView(CashFlowType.RENT, rent, Frequency.MONTHLY, false)));
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
