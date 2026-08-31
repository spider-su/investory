package com.smartbox.investory.ui.retirement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.smartbox.investory.retirement.api.InvestmentProfileFacade;
import com.smartbox.investory.retirement.planning.*;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.simulation.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.ui.common.BuildMetadata;
import com.smartbox.investory.ui.presentation.UiPresentation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@ExtendWith(MockitoExtension.class)
class RetirementSimulationControllerTest {
  @Mock InvestmentProfileFacade profiles;
  @Mock RetirementSimulation simulations;
  @Mock SimulationPlanService plans;
  @Mock SustainableSpendingAnalysisService sustainableSpending;
  @Mock SimulationSensitivityAnalysisService sensitivity;
  @Mock RetirementAgeAnalysisService retirementAgeAnalysis;
  @Mock PlanningTimelineFacade planningTimeline;
  @Mock PlanningCurrencyPresentationService planningPresentation;
  @Mock ForwardSimulationInputService forwardInputs;
  MockMvc mockMvc;
  MockMvc renderingMockMvc;
  RetirementSimulationController controller;

  @BeforeEach
  void setUp() {
    var summary = mock(SimulationDecisionSummaryMoney.class);
    lenient().when(summary.failed()).thenReturn(false);
    lenient().when(summary.minimumLiquidAssetsDisplay()).thenReturn("0");
    lenient()
        .when(planningPresentation.displaySummaries(any(), any()))
        .thenReturn(Map.of(SimulationScenario.BASE, summary));
    lenient()
        .when(planningPresentation.displayTimelineMoney(any(), any(), any()))
        .thenReturn(Map.of());
    controller =
        new RetirementSimulationController(
            profiles,
            simulations,
            plans,
            sustainableSpending,
            sensitivity,
            retirementAgeAnalysis,
            planningTimeline,
            planningPresentation,
            forwardInputs,
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    lenient()
        .when(planningTimeline.loadTimeline(anyLong(), any(), any()))
        .thenReturn(new com.smartbox.investory.retirement.planning.PlanningTimeline(List.of()));
    lenient()
        .when(planningTimeline.loadForwardTimeline(anyLong(), any(), any()))
        .thenReturn(new com.smartbox.investory.retirement.planning.PlanningTimeline(List.of()));
    lenient()
        .when(planningTimeline.loadForwardTimeline(anyLong(), any(), any(), any()))
        .thenReturn(new com.smartbox.investory.retirement.planning.PlanningTimeline(List.of()));
    lenient()
        .when(planningTimeline.loadForwardTimeline(anyLong(), any(), any(), any(), any()))
        .thenReturn(new com.smartbox.investory.retirement.planning.PlanningTimeline(List.of()));
    lenient()
        .when(forwardInputs.prepare(any(), any()))
        .thenAnswer(
            invocation -> {
              InvestmentProfile profile = invocation.getArgument(0);
              SimulationAssumptions assumptions = invocation.getArgument(1);
              var context =
                  new ForwardSimulationContextFactory(
                          Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC))
                      .create(profile, assumptions);
              return new ForwardSimulationInput(
                  context,
                  profile,
                  Optional.of(
                      assumptions.rebasedTo(
                          context.firstProjectedAge(),
                          context.firstProjectedYear(),
                          context.remainingFutureEvents())));
            });
    lenient()
        .when(planningPresentation.fromDisplay(any(), any(), any()))
        .thenAnswer(i -> i.getArgument(0));
    lenient().when(planningPresentation.toDisplay(any(), any())).thenAnswer(i -> i.getArgument(0));
    lenient()
        .when(planningPresentation.displayProfile(any(), any()))
        .thenReturn(
            new PlanningProfileMoney(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO));
    lenient()
        .when(sustainableSpending.analyze(any(), any()))
        .thenReturn(mock(SustainableSpendingAnalysis.class));
    lenient()
        .when(planningPresentation.displaySustainableSpending(any(), any()))
        .thenReturn(mock(SustainableSpendingAnalysisMoney.class));
    lenient()
        .when(sensitivity.analyze(any(), any()))
        .thenReturn(mock(SimulationSensitivityAnalysis.class));
    lenient()
        .when(planningPresentation.displaySensitivity(any(), any()))
        .thenReturn(mock(SimulationSensitivityAnalysisMoney.class));
    lenient()
        .when(retirementAgeAnalysis.analyze(any(), any()))
        .thenReturn(mock(RetirementAgeAnalysis.class));
    lenient()
        .when(planningPresentation.displayRetirementAgeAnalysis(any()))
        .thenReturn(mock(RetirementAgeAnalysisMoney.class));
    lenient()
        .when(planningPresentation.displayCharts(any(), any()))
        .thenAnswer(i -> i.getArgument(0));
    lenient().when(planningPresentation.displayTimelineMoney(any(), any())).thenReturn(Map.of());
    lenient()
        .when(plans.resolvePlanId(anyLong(), nullable(Long.class)))
        .thenAnswer(invocation -> Optional.ofNullable(invocation.getArgument(1)));
    InternalResourceViewResolver resolver = new InternalResourceViewResolver();
    resolver.setPrefix("/WEB-INF/views/");
    resolver.setSuffix(".jsp");
    mockMvc = MockMvcBuilders.standaloneSetup(controller).setViewResolvers(resolver).build();
    ThymeleafViewResolver thymeleafResolver = new ThymeleafViewResolver();
    thymeleafResolver.setTemplateEngine(templateEngine());
    thymeleafResolver.setViewNames(new String[] {"*"});
    renderingMockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new TemplateModelAdvice())
            .setViewResolvers(thymeleafResolver)
            .build();
  }

  @Test
  void simulationRouteRendersTheThymeleafPage() throws Exception {
    when(profiles.loadProfile(1L)).thenReturn(profile());
    when(simulations.compareScenarios(any(), any(), anyInt())).thenReturn(Map.of());

    renderingMockMvc
        .perform(get("/simulation").param("portfolioId", "1"))
        .andExpect(status().isOk())
        .andExpect(view().name("simulation"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Scenario")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Yearly projection")))
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("Planning bucket projection")));
  }

  @Test
  void customRequestUsesSubmittedDeltasForPageAndSimulation() throws Exception {
    InvestmentProfile p = profile();
    var saved =
        SimulationAssumptions.defaults(p, 45, 90, 2026)
            .withInflationRate(new BigDecimal("0.03"))
            .withRentalIncomeGrowthSpread(new BigDecimal("-0.027"))
            .withFixedIncomeReturnRate(new BigDecimal("0.04"))
            .withEquityReturnRate(new BigDecimal("0.075"))
            .withSpendingGrowthSpread(new BigDecimal("-0.011"));
    when(profiles.loadProfile(1L)).thenReturn(p);
    when(plans.assumptions(1L, 7L)).thenReturn(saved);
    when(simulations.compareScenarios(any(), any(), anyInt(), any())).thenReturn(Map.of());

    var result =
        mockMvc
            .perform(
                get("/simulation")
                    .param("portfolioId", "1")
                    .param("planId", "7")
                    .param("selectedScenario", "CUSTOM")
                    .param("customInflationDelta", "4")
                    .param("customRentalGrowthDelta", "2")
                    .param("customBondReturnDelta", "3")
                    .param("customEquityReturnDelta", "5")
                    .param("customSpendingGrowthDelta", "2"))
            .andExpect(status().isOk())
            .andReturn();

    var page =
        (RetirementSimulationPageView) result.getModelAndView().getModel().get("simulationPage");
    assertEquals("4", page.customScenario().inflation());
    assertEquals("2", page.customScenario().rentalGrowth());
    assertEquals("3", page.customScenario().bondReturn());
    assertEquals("5", page.customScenario().equityReturn());
    assertEquals("2", page.customScenario().spendingGrowth());
    assertEquals(new BigDecimal("0.07"), row(page, "Inflation").effectiveRate());
    assertEquals(new BigDecimal("0.023"), row(page, "Rental growth").effectiveRate());
    assertEquals(new BigDecimal("0.07"), row(page, "Bond return").effectiveRate());
    assertEquals(new BigDecimal("0.125"), row(page, "Equity return").effectiveRate());
    assertEquals(new BigDecimal("0.039"), row(page, "Spending growth").effectiveRate());

    var custom = org.mockito.ArgumentCaptor.forClass(SimulationCustomDeltas.class);
    verify(simulations).compareScenarios(any(), any(), anyInt(), custom.capture());
    assertEquals(
        new SimulationCustomDeltas(
            new BigDecimal("0.04"),
            new BigDecimal("0.02"),
            new BigDecimal("0.03"),
            new BigDecimal("0.05"),
            new BigDecimal("0.02")),
        custom.getValue());
    verify(planningTimeline)
        .loadForwardTimeline(
            eq(1L), eq(p), any(), eq(SimulationScenario.CUSTOM), eq(custom.getValue()));
  }

  private static ScenarioAssumptionView row(RetirementSimulationPageView page, String name) {
    return page.scenarioAssumptionRows().stream()
        .filter(row -> row.name().equals(name))
        .findFirst()
        .orElseThrow();
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

  @ControllerAdvice
  static class TemplateModelAdvice {
    @ModelAttribute("format")
    UiPresentation format() {
      return new UiPresentation();
    }

    @ModelAttribute("buildMetadata")
    BuildMetadata buildMetadata() {
      return BuildMetadata.development();
    }
  }

  @Test
  void simulationPageExposesFocusedRawProjectionModel() throws Exception {
    InvestmentProfile p =
        new InvestmentProfile(
            1L,
            CurrencyType.PLN,
            new BigDecimal("1000"),
            BigDecimal.ZERO,
            new BigDecimal("1000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("1000"),
            BigDecimal.ZERO,
            List.of(),
            List.of());
    when(profiles.loadProfile(1L)).thenReturn(p);
    when(simulations.compareScenarios(
            org.mockito.ArgumentMatchers.eq(p),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(Map.of());
    var result =
        mockMvc
            .perform(get("/simulation"))
            .andExpect(status().isOk())
            .andExpect(view().name("simulation"))
            .andExpect(model().attributeExists("simulationPage"))
            .andReturn();
    var page =
        (RetirementSimulationPageView) result.getModelAndView().getModel().get("simulationPage");
    assertEquals(null, page.selectedPlanId());
    assertEquals("Current assumptions", page.activePlanName());
  }

  @Test
  void simulationPreservesSavedRetirementTransitionFields() throws Exception {
    InvestmentProfile p =
        new InvestmentProfile(
            1L,
            CurrencyType.PLN,
            new BigDecimal("1000"),
            BigDecimal.ZERO,
            new BigDecimal("1000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("1000"),
            BigDecimal.ZERO,
            List.of(),
            List.of());
    var saved =
        SimulationAssumptions.defaults(p, 45, 90, 2026)
            .withRetirementAge(60)
            .withAnnualEmploymentIncome(new BigDecimal("240000"))
            .withAnnualPreRetirementContribution(new BigDecimal("50000"))
            .withProjectedIncomePolicy(
                new ProjectedIncomePolicy(
                    ProjectedIncomePolicy.IncomeMode.MANUAL,
                    new BigDecimal("120000"),
                    ProjectedIncomePolicy.IncomeMode.MANUAL,
                    new BigDecimal("24000")));
    when(profiles.loadProfile(1L)).thenReturn(p);
    when(plans.assumptions(1L, 7L)).thenReturn(saved);
    when(simulations.compareScenarios(eq(p), any(), anyInt())).thenReturn(Map.of());

    mockMvc
        .perform(get("/simulation").param("portfolioId", "1").param("planId", "7"))
        .andExpect(status().isOk())
        .andExpect(model().attributeExists("simulationPage"));

    var captured = org.mockito.ArgumentCaptor.forClass(SimulationAssumptions.class);
    verify(simulations).compareScenarios(eq(p), captured.capture(), anyInt());
    assertEquals(60, captured.getValue().retirementAge());
    assertEquals(new BigDecimal("240000"), captured.getValue().annualEmploymentIncome());
    assertEquals(new BigDecimal("50000"), captured.getValue().annualPreRetirementContribution());
    assertEquals(saved.projectedIncomePolicy(), captured.getValue().projectedIncomePolicy());
  }

  @Test
  void simulationRoundsPlanInputMoneyForDisplay() throws Exception {
    InvestmentProfile p = profile();
    var saved =
        SimulationAssumptions.defaults(p, 45, 90, 2026)
            .withRecurringSpending(new BigDecimal("180000.00071684"))
            .withAnnualPension(new BigDecimal("7000.00002787"));
    when(profiles.loadProfile(1L)).thenReturn(p);
    when(plans.assumptions(1L, 7L)).thenReturn(saved);
    when(plans.name(1L, 7L)).thenReturn("Plan");
    when(simulations.compareScenarios(eq(p), any(), anyInt())).thenReturn(Map.of());

    var result =
        mockMvc
            .perform(get("/simulation").param("portfolioId", "1").param("planId", "7"))
            .andExpect(status().isOk())
            .andReturn();
    var page =
        (RetirementSimulationPageView) result.getModelAndView().getModel().get("simulationPage");

    assertEquals(0, new BigDecimal("180000").compareTo(page.annualLivingExpenses()));
    assertEquals(0, new BigDecimal("7000").compareTo(page.annualPension()));
  }

  @Test
  void simulationWithoutPlanIdUsesTheLatestSavedPlan() throws Exception {
    InvestmentProfile profile = profile();
    SimulationAssumptions latest = SimulationAssumptions.defaults(profile, 45, 90, 2026);
    when(profiles.loadProfile(1L)).thenReturn(profile);
    when(plans.resolvePlanId(1L, null)).thenReturn(Optional.of(8L));
    when(plans.assumptions(1L, 8L)).thenReturn(latest);
    when(plans.name(1L, 8L)).thenReturn("Plan B");
    when(simulations.compareScenarios(eq(profile), any(), anyInt())).thenReturn(Map.of());

    var result = mockMvc.perform(get("/simulation").param("portfolioId", "1")).andReturn();
    var page =
        (RetirementSimulationPageView) result.getModelAndView().getModel().get("simulationPage");

    assertEquals(8L, page.selectedPlanId());
    assertEquals("Plan B", page.activePlanName());
    verify(plans).resolvePlanId(1L, null);
  }

  @Test
  void simulationKeepsAnExplicitPlanIdWhenANewerPlanExists() throws Exception {
    InvestmentProfile profile = profile();
    SimulationAssumptions explicit = SimulationAssumptions.defaults(profile, 45, 90, 2026);
    when(profiles.loadProfile(1L)).thenReturn(profile);
    when(plans.resolvePlanId(1L, 7L)).thenReturn(Optional.of(7L));
    when(plans.assumptions(1L, 7L)).thenReturn(explicit);
    when(plans.name(1L, 7L)).thenReturn("Plan A");
    when(simulations.compareScenarios(eq(profile), any(), anyInt())).thenReturn(Map.of());

    var result =
        mockMvc
            .perform(get("/simulation").param("portfolioId", "1").param("planId", "7"))
            .andReturn();
    var page =
        (RetirementSimulationPageView) result.getModelAndView().getModel().get("simulationPage");

    assertEquals(7L, page.selectedPlanId());
    assertEquals("Plan A", page.activePlanName());
    verify(plans).resolvePlanId(1L, 7L);
  }

  @Test
  void editPageUsesTheSameLatestPlanResolutionAndFallsBackToDefaults() {
    InvestmentProfile profile = profile();
    SimulationAssumptions latest = SimulationAssumptions.defaults(profile, 45, 90, 2026);
    when(profiles.loadProfile(1L)).thenReturn(profile);
    when(plans.resolvePlanId(1L, null)).thenReturn(Optional.of(8L));
    when(plans.assumptions(1L, 8L)).thenReturn(latest);
    when(plans.name(1L, 8L)).thenReturn("Plan B");
    when(plans.list(1L)).thenReturn(List.of());
    when(plans.revisionHistory(1L, 8L)).thenReturn(List.of());

    ExtendedModelMap savedModel = new ExtendedModelMap();
    assertEquals(
        "simulation-plan-edit",
        controller.editPlan(1L, null, CurrencyType.PLN, SimulationScenario.BASE, savedModel));
    assertEquals(8L, savedModel.getAttribute("selectedPlanId"));
    assertEquals("Plan B", savedModel.getAttribute("planName"));
    assertEquals(2026, savedModel.getAttribute("currentPlanningYear"));
    var annualCosts = (BigDecimal) savedModel.getAttribute("displayTotalAnnualCosts");
    var monthlyCosts = (BigDecimal) savedModel.getAttribute("displayMonthlyTotalCosts");
    assertEquals(
        0,
        annualCosts
            .divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP)
            .compareTo(monthlyCosts));

    when(plans.resolvePlanId(1L, null)).thenReturn(Optional.empty());
    ExtendedModelMap defaultModel = new ExtendedModelMap();
    controller.editPlan(1L, null, CurrencyType.PLN, SimulationScenario.BASE, defaultModel);
    assertEquals(null, defaultModel.getAttribute("selectedPlanId"));
    assertEquals("", defaultModel.getAttribute("planName"));
  }

  @Test
  void percentInputsAlwaysConvertPercentagePointsToDecimalRates() {
    String[][] cases = {
      {"0", "0.00"},
      {"0.5", "0.005"},
      {"1", "0.01"},
      {"2", "0.02"},
      {"5", "0.05"},
      {"8.5", "0.085"},
      {"19", "0.19"},
      {"100", "1.00"}
    };
    for (String[] entry : cases)
      assertEquals(
          0,
          new BigDecimal(entry[1])
              .compareTo(
                  RetirementSimulationController.percentInputToRate(
                      new BigDecimal(entry[0]), BigDecimal.ZERO)),
          entry[0]);
  }

  @Test
  void savingPlanPersistsOnePercentAsDecimalRate() {
    var captured = org.mockito.ArgumentCaptor.forClass(SimulationAssumptions.class);
    controller.savePlan(
        1L,
        null,
        "Plan",
        40,
        80,
        new BigDecimal("180000"),
        BigDecimal.ZERO,
        new BigDecimal("1"),
        new BigDecimal("2"),
        new BigDecimal("2.5"),
        SimulationFundingStrategy.RESERVE_AND_HARVEST,
        new BigDecimal("5"),
        new BigDecimal("7"),
        new BigDecimal("75"),
        true,
        new BigDecimal("1"),
        new BigDecimal("1"),
        new BigDecimal("1"),
        new BigDecimal("1"),
        new BigDecimal("1"),
        67,
        BigDecimal.ZERO,
        new BigDecimal("19"),
        CurrencyType.USD);
    verify(plans).createId(eq(1L), eq("Plan"), captured.capture());
    assertEquals(new BigDecimal("0.01"), captured.getValue().realEstateReturnRate());
    assertEquals(new BigDecimal("0.02"), captured.getValue().rentalIncomeGrowthSpread());
    assertEquals(new BigDecimal("0.025"), captured.getValue().spendingGrowthSpread());
    assertEquals(SimulationFundingStrategy.SIMPLE_WATERFALL, captured.getValue().fundingStrategy());
    assertEquals(new BigDecimal("0.07"), captured.getValue().equityHarvestMinimumReturnRate());
    assertEquals(0, new BigDecimal("0.75").compareTo(captured.getValue().equityGainHarvestRate()));
    assertEquals(new BigDecimal("0.19"), captured.getValue().capitalGainTaxRate());
  }

  @Test
  void blankPensionStartAgeUsesTheDomainNoPensionSentinel() {
    var captured = org.mockito.ArgumentCaptor.forClass(SimulationAssumptions.class);
    controller.savePlan(
        1L,
        null,
        "Plan",
        40,
        80,
        new BigDecimal("180000"),
        BigDecimal.ZERO,
        new BigDecimal("1"),
        new BigDecimal("2"),
        new BigDecimal("2.5"),
        SimulationFundingStrategy.RESERVE_AND_HARVEST,
        new BigDecimal("5"),
        new BigDecimal("7"),
        new BigDecimal("75"),
        true,
        new BigDecimal("1"),
        new BigDecimal("1"),
        new BigDecimal("1"),
        new BigDecimal("1"),
        new BigDecimal("1"),
        null,
        BigDecimal.ZERO,
        new BigDecimal("19"),
        CurrencyType.PLN);

    verify(plans).createId(eq(1L), eq("Plan"), captured.capture());
    assertEquals(Integer.MAX_VALUE, captured.getValue().pensionStartAge());
    assertEquals(BigDecimal.ZERO, captured.getValue().annualPension());
  }

  @Test
  void updatingPlanPersistsOnePercentAsDecimalRate() {
    var captured = org.mockito.ArgumentCaptor.forClass(SimulationAssumptions.class);
    controller.savePlan(
        1L,
        9L,
        "Plan",
        40,
        80,
        new BigDecimal("180000"),
        BigDecimal.ZERO,
        BigDecimal.ONE,
        new BigDecimal("2"),
        new BigDecimal("2.5"),
        SimulationFundingStrategy.RESERVE_AND_HARVEST,
        new BigDecimal("5"),
        new BigDecimal("7"),
        new BigDecimal("75"),
        true,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        67,
        BigDecimal.ZERO,
        new BigDecimal("19"),
        CurrencyType.USD);
    verify(plans).updateId(eq(1L), eq(9L), eq("Plan"), captured.capture());
    assertEquals(new BigDecimal("0.01"), captured.getValue().realEstateReturnRate());
    assertEquals(new BigDecimal("0.02"), captured.getValue().rentalIncomeGrowthSpread());
    assertEquals(new BigDecimal("0.025"), captured.getValue().spendingGrowthSpread());
    assertEquals(new BigDecimal("0.19"), captured.getValue().capitalGainTaxRate());
  }

  @Test
  void editingExistingPlanPreservesItsTemporalAnchorAcrossCalendarYears() throws Exception {
    SimulationAssumptions stored =
        SimulationAssumptions.defaults(mock(InvestmentProfile.class), 40, 80, 2025)
            .withRetirementAge(45);
    when(plans.assumptions(1L, 9L)).thenReturn(stored);
    when(plans.updateId(eq(1L), eq(9L), eq("Plan"), any())).thenReturn(9L);

    var captured = org.mockito.ArgumentCaptor.forClass(SimulationAssumptions.class);
    mockMvc
        .perform(
            post("/simulation/plans")
                .param("portfolioId", "1")
                .param("planId", "9")
                .param("name", "Plan")
                .param("endAge", "80")
                .param("retirementAge", "45")
                .param("annualEmploymentIncome", "240000")
                .param("annualPreRetirementContribution", "50000")
                .param("monthlyLivingCosts", "15000")
                .param("discretionaryExpenses", "0")
                .param("inflation", "3")
                .param("rentalIncomeGrowth", "2")
                .param("spendingGrowth", "2.5")
                .param("fundingStrategy", "RESERVE_AND_HARVEST")
                .param("safeReserveYears", "5")
                .param("equityHarvestThreshold", "7")
                .param("equityHarvestShare", "75")
                .param("allowEmergencyEquityWithdrawal", "true")
                .param("equityReturn", "8")
                .param("pensionStartAge", "67")
                .param("annualPension", "0")
                .param("planningDisplayCurrency", "PLN")
                .param("selectedScenario", "BASE"))
        .andExpect(status().is3xxRedirection());

    verify(plans).updateId(eq(1L), eq(9L), eq("Plan"), captured.capture());
    SimulationAssumptions saved = captured.getValue();
    assertEquals(2025, saved.startYear());
    assertEquals(40, saved.currentAge());
    assertEquals(45, saved.retirementAge());
    assertEquals(2030, saved.startYear() + saved.retirementAge() - saved.currentAge());
    assertEquals(new BigDecimal("240000"), saved.annualEmploymentIncome());
    assertEquals(new BigDecimal("50000"), saved.annualPreRetirementContribution());
    assertEquals(new BigDecimal("0.03"), saved.inflationRate());
    assertEquals(new BigDecimal("0.05"), saved.effectiveRentalIncomeGrowthRate());
    assertEquals(new BigDecimal("0.055"), saved.effectiveSpendingGrowthRate());
    assertEquals(new BigDecimal("180000"), saved.annualLivingExpenses());
    assertEquals(0, saved.cashReturnRate().compareTo(BigDecimal.ZERO));
    assertEquals(0, saved.fixedIncomeReturnRate().compareTo(BigDecimal.ZERO));
    assertEquals(0, saved.otherReturnRate().compareTo(BigDecimal.ZERO));
    assertEquals(0, saved.capitalGainTaxRate().compareTo(BigDecimal.ZERO));
    var forward =
        new ForwardSimulationContextFactory(
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC))
            .create(mock(InvestmentProfile.class), saved);
    assertEquals(41, forward.asOfAge());
    assertEquals(42, forward.firstProjectedAge());
    assertEquals(45, forward.originalAssumptions().retirementAge());
  }

  @Test
  void canonicalSaveAsPreservesSourceTemporalAnchor() {
    SimulationAssumptions source =
        SimulationAssumptions.defaults(mock(InvestmentProfile.class), 40, 80, 2025)
            .withRetirementAge(45);
    when(plans.assumptions(1L, 9L)).thenReturn(source);
    when(plans.createId(eq(1L), eq("Copy"), any())).thenReturn(10L);

    controller.savePlan(
        1L,
        9L,
        "Copy",
        99,
        80,
        45,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new BigDecimal("180000"),
        null,
        BigDecimal.ZERO,
        new BigDecimal("2"),
        new BigDecimal("2"),
        new BigDecimal("2.5"),
        SimulationFundingStrategy.RESERVE_AND_HARVEST,
        new BigDecimal("5"),
        new BigDecimal("7"),
        new BigDecimal("75"),
        true,
        new BigDecimal("1"),
        new BigDecimal("4"),
        new BigDecimal("8"),
        new BigDecimal("3"),
        new BigDecimal("1"),
        67,
        BigDecimal.ZERO,
        new BigDecimal("19"),
        CurrencyType.PLN,
        CurrencyType.PLN,
        true,
        SimulationScenario.BASE);

    var captured = org.mockito.ArgumentCaptor.forClass(SimulationAssumptions.class);
    verify(plans).createId(eq(1L), eq("Copy"), captured.capture());
    assertEquals(2025, captured.getValue().startYear());
    assertEquals(40, captured.getValue().currentAge());
    assertEquals(45, captured.getValue().retirementAge());
    assertEquals(2030, ForwardSimulationContextFactory.retirementYear(captured.getValue()));
  }

  @Test
  void deletingNonActivePlanReturnsToEditorWithCurrentContext() {
    String redirect =
        controller.deletePlan(9L, 1L, CurrencyType.EUR, 7L, true, SimulationScenario.OPTIMISTIC);

    verify(plans).delete(1L, 9L);
    assertEquals(
        "redirect:/simulation/plan/edit?portfolioId=1&planId=7&planningDisplayCurrency=EUR&selectedScenario=OPTIMISTIC",
        redirect);
  }

  @Test
  void deletingActivePlanReturnsToEditorWithoutDeletedPlanContext() {
    when(plans.resolvePlanId(1L, null)).thenReturn(Optional.of(6L));
    String redirect =
        controller.deletePlan(7L, 1L, CurrencyType.EUR, 7L, true, SimulationScenario.CONSERVATIVE);

    verify(plans).delete(1L, 7L);
    assertEquals(
        "redirect:/simulation/plan/edit?portfolioId=1&planId=6&planningDisplayCurrency=EUR&selectedScenario=CONSERVATIVE",
        redirect);
  }

  @Test
  void planMoneyInPlanningCurrencyIsConvertedToCanonicalUsdBeforeSaving() {
    var captured = org.mockito.ArgumentCaptor.forClass(SimulationAssumptions.class);
    when(planningPresentation.fromDisplay(
            any(BigDecimal.class), eq(CurrencyType.PLN), any(BigDecimal.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(planningPresentation.fromDisplay(
            eq(new BigDecimal("45000")), eq(CurrencyType.PLN), eq(BigDecimal.ZERO)))
        .thenReturn(new BigDecimal("11250.00000000"));
    controller.savePlan(
        1L,
        null,
        "Plan",
        40,
        80,
        new BigDecimal("45000"),
        new BigDecimal("12000"),
        new BigDecimal("2.5"),
        new BigDecimal("2"),
        new BigDecimal("2.5"),
        SimulationFundingStrategy.RESERVE_AND_HARVEST,
        new BigDecimal("5"),
        new BigDecimal("7"),
        new BigDecimal("75"),
        true,
        new BigDecimal("1"),
        new BigDecimal("1"),
        new BigDecimal("7"),
        new BigDecimal("1"),
        new BigDecimal("1"),
        67,
        BigDecimal.ZERO,
        new BigDecimal("19"),
        CurrencyType.PLN);
    verify(plans).createId(eq(1L), eq("Plan"), captured.capture());
    assertEquals(0, new BigDecimal("11250").compareTo(captured.getValue().annualLivingExpenses()));
  }

  @Test
  void unchangedDisplayedMoneyKeepsItsCanonicalValueWithoutAnotherFxConversion() throws Exception {
    InvestmentProfile p =
        new InvestmentProfile(
            1L,
            CurrencyType.USD,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            List.of(),
            List.of());
    when(profiles.loadProfile(1L)).thenReturn(p);
    when(simulations.compareScenarios(eq(p), any(), anyInt())).thenReturn(Map.of());
    var result =
        mockMvc
            .perform(
                get("/simulation")
                    .param("planningDisplayCurrency", "PLN")
                    .param("cashReturn", "1.5")
                    .param("fixedIncomeReturn", "4.5")
                    .param("equityReturn", "7.5")
                    .param("realEstateReturn", "3.5")
                    .param("otherReturn", "2.5")
                    .param("annualExpenses", "45000.00")
                    .param("annualExpensesCanonical", "11250.12345678"))
            .andExpect(status().isOk())
            .andReturn();
    var page =
        (RetirementSimulationPageView) result.getModelAndView().getModel().get("simulationPage");
    assertEquals(
        0, new BigDecimal("11250.12345678").compareTo(page.assumptions().annualLivingExpenses()));
    assertEquals(0, new BigDecimal("0.015").compareTo(page.assumptions().cashReturnRate()));
    assertEquals(0, new BigDecimal("0.045").compareTo(page.assumptions().fixedIncomeReturnRate()));
    assertEquals(0, new BigDecimal("0.075").compareTo(page.assumptions().equityReturnRate()));
    assertEquals(0, new BigDecimal("0.035").compareTo(page.assumptions().realEstateReturnRate()));
    assertEquals(0, new BigDecimal("0.025").compareTo(page.assumptions().otherReturnRate()));
    verify(planningTimeline, never()).historicalYears(anyLong());
    verify(planningTimeline, never()).ensureCurrentYear(anyLong());
    verify(planningPresentation, never())
        .fromDisplay(eq(new BigDecimal("45000.00")), eq(CurrencyType.PLN), any(BigDecimal.class));
  }

  @Test
  void explicitRolloverMutatesPlanningTimelineAndKeepsSimulationContext() {
    String redirect =
        controller.rollover(1L, CurrencyType.EUR, 7L, SimulationScenario.CONSERVATIVE);

    verify(planningTimeline).historicalYears(1L);
    verify(planningTimeline).ensureCurrentYear(1L);
    assertEquals(
        "redirect:/simulation?portfolioId=1&planId=7&planningDisplayCurrency=EUR&selectedScenario=CONSERVATIVE",
        redirect);
  }

  @Test
  void savingHistoricalManualValueConvertsThenReturnsToSameDetailPage() {
    RedirectAttributesModelMap flash = new RedirectAttributesModelMap();
    when(planningPresentation.fromDisplay(
            new BigDecimal("45000"), CurrencyType.PLN, BigDecimal.ZERO))
        .thenReturn(new BigDecimal("11250"));
    String redirect =
        controller.savePastManual(
            1L,
            2025,
            PlanningMetric.CORE_SPENDING,
            new BigDecimal("45000"),
            "Actual household spending",
            CurrencyType.PLN,
            flash);
    verify(planningTimeline)
        .saveDraftManualValue(
            1L,
            2025,
            PlanningMetric.CORE_SPENDING,
            new BigDecimal("11250"),
            "Actual household spending");
    assertEquals(
        "redirect:/simulation/timeline/2025?portfolioId=1&planningDisplayCurrency=PLN", redirect);
  }

  @Test
  void incompleteHistoricalCloseReturnsToDetailWithUsefulMessage() {
    RedirectAttributesModelMap flash = new RedirectAttributesModelMap();
    doThrow(new IllegalStateException("Cannot close 2025. Missing: CORE_SPENDING"))
        .when(planningTimeline)
        .closeHistoricalDraft(1L, 2025);
    String redirect = controller.closeHistoricalDraft(1L, 2025, CurrencyType.PLN, flash);
    assertEquals(
        "redirect:/simulation/timeline/2025?portfolioId=1&planningDisplayCurrency=PLN", redirect);
    assertEquals(
        "Cannot close 2025. Missing: CORE_SPENDING",
        flash.getFlashAttributes().get("planningError"));
  }

  @Test
  void successfulCloseAndReopenReturnToTheSameHistoricalDetailPage() {
    RedirectAttributesModelMap closeFlash = new RedirectAttributesModelMap();
    assertEquals(
        "redirect:/simulation/timeline/2025?portfolioId=1&planningDisplayCurrency=EUR",
        controller.closeHistoricalDraft(1L, 2025, CurrencyType.EUR, closeFlash));
    verify(planningTimeline).closeHistoricalDraft(1L, 2025);
    RedirectAttributesModelMap reopenFlash = new RedirectAttributesModelMap();
    assertEquals(
        "redirect:/simulation/timeline/2025?portfolioId=1&planningDisplayCurrency=EUR",
        controller.reopenPlanningYear(1L, 2025, CurrencyType.EUR, reopenFlash));
    verify(planningTimeline).reopenHistoricalYear(1L, 2025);
  }

  @Test
  void historicalDetailExposesOnlyFacadeApprovedEditableMetrics() {
    InvestmentProfile profile =
        new InvestmentProfile(
            1L,
            CurrencyType.USD,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            List.of(),
            List.of());
    PastPlanningYear past =
        new PastPlanningYear(
            2025,
            PlanningYearStatus.DRAFT,
            null,
            Map.of(
                PlanningMetric.MARKET_ASSETS,
                    new PlanningMetricValue(
                        PlanningMetric.MARKET_ASSETS,
                        BigDecimal.TEN,
                        null,
                        PlanningValueSource.ACCOUNTING_DERIVED,
                        null),
                PlanningMetric.CORE_SPENDING,
                    new PlanningMetricValue(
                        PlanningMetric.CORE_SPENDING,
                        null,
                        null,
                        PlanningValueSource.UNAVAILABLE,
                        null)),
            Map.of());
    when(profiles.loadProfile(1L)).thenReturn(profile);
    when(planningTimeline.pastYear(1L, 2025)).thenReturn(past);
    when(planningPresentation.display(past, CurrencyType.PLN)).thenReturn(past);
    when(planningTimeline.isHistoricalMetricEditable(1L, 2025, PlanningMetric.MARKET_ASSETS))
        .thenReturn(false);
    when(planningTimeline.isHistoricalMetricEditable(1L, 2025, PlanningMetric.CORE_SPENDING))
        .thenReturn(true);
    when(planningTimeline.historicalCloseStatus(1L, 2025))
        .thenReturn(new PlanningYearCloseStatus(false, List.of("CORE_SPENDING")));
    ExtendedModelMap model = new ExtendedModelMap();
    assertEquals("planning-year", controller.planningYearDetail(1L, 2025, CurrencyType.PLN, model));
    assertTrue(
        ((java.util.Set<?>) model.getAttribute("editableMetrics"))
            .contains(PlanningMetric.CORE_SPENDING));
    assertTrue(
        !((java.util.Set<?>) model.getAttribute("editableMetrics"))
            .contains(PlanningMetric.MARKET_ASSETS));
    assertEquals(
        List.of("CORE_SPENDING"),
        ((PlanningYearCloseStatus) model.getAttribute("planningCloseStatus")).missingMetrics());
  }

  @Test
  void creatingHistoricalSnapshotPreservesDisplayCurrencyOnReturn() {
    assertEquals(
        "redirect:/simulation?portfolioId=1&planningDisplayCurrency=EUR",
        controller.createPastYear(1L, 2025, CurrencyType.EUR));
    verify(planningTimeline).createHistoricalDraft(1L, 2025);
  }

  private static InvestmentProfile profile() {
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        new BigDecimal("1000"),
        BigDecimal.ZERO,
        new BigDecimal("1000"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new BigDecimal("1000"),
        BigDecimal.ZERO,
        List.of(),
        List.of());
  }
}
