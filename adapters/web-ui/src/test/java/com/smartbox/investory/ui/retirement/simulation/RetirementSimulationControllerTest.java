package com.smartbox.investory.ui.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.RetirementSandboxApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.ForwardSimulationContext;
import com.smartbox.investory.retirement.api.model.NormalizedPlanInput;
import com.smartbox.investory.retirement.api.model.PlanEditorPreview;
import com.smartbox.investory.retirement.planning.ForwardSimulationInputService;
import com.smartbox.investory.retirement.simulation.ForwardSimulationContextFactory;
import com.smartbox.investory.retirement.simulation.RetirementAgeAnalysisService;
import com.smartbox.investory.retirement.simulation.RetirementSimulation;
import com.smartbox.investory.retirement.simulation.SimulationSensitivityAnalysisService;
import com.smartbox.investory.retirement.simulation.SustainableSpendingAnalysisService;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.ui.common.BuildMetadata;
import com.smartbox.investory.ui.presentation.UiPresentation;
import com.smartbox.investory.ui.profile.ProfileClient;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Retirement Simulation Controller")
class RetirementSimulationControllerTest {
  @Mock ProfileClient profiles;
  @Mock RetirementSimulation simulations;
  @Mock RetirementPlanClient plans;
  @Mock RetirementSandboxPlanClient sandboxPlans;
  @Mock RetirementProjectionClient projections;
  @Mock SustainableSpendingAnalysisService sustainableSpending;
  @Mock SimulationSensitivityAnalysisService sensitivity;
  @Mock RetirementAgeAnalysisService retirementAgeAnalysis;
  @Mock RetirementTimelineClient timeline;
  @Mock RetirementPresentationClient presentation;
  @Mock RetirementPlanInputClient planInput;
  @Mock RetirementPreviewClient planEditorPreview;
  @Mock ScenarioObservationService scenarioObservations;
  @Mock RetirementSandboxApi sandbox;
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
        .when(presentation.displaySummaries(any(), any()))
        .thenReturn(Map.of(SimulationScenario.BASE, summary));
    lenient().when(presentation.displayTimelineMoney(any(), any(), any())).thenReturn(Map.of());
    controller =
        new RetirementSimulationController(
            profiles,
            plans,
            sandboxPlans,
            timeline,
            presentation,
            planInput,
            projections,
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
            planEditorPreview,
            scenarioObservations,
            new SimulationCommandService(plans),
            sandbox);
    lenient()
        .when(projections.load(anyLong(), nullable(Long.class), anyInt(), anyInt()))
        .thenAnswer(
            invocation -> {
              Long portfolioId = invocation.getArgument(0);
              Long planId = invocation.getArgument(1);
              int currentAge = invocation.getArgument(2);
              int endAge = invocation.getArgument(3);
              InvestmentProfile profile = profiles.loadProfile(portfolioId);
              var details = plans.details(portfolioId, planId);
              SimulationAssumptions assumptions = details == null ? null : details.assumptions();
              if (assumptions == null) {
                assumptions = SimulationAssumptions.defaults(profile, currentAge, endAge, 2026);
              }
              var projection = mock(RetirementProjectionContext.class);
              when(projection.profile()).thenReturn(profile);
              when(projection.assumptions()).thenReturn(assumptions);
              return projection;
            });
    lenient()
        .when(planEditorPreview.preview(any(), any(), any()))
        .thenReturn(mock(PlanEditorPreview.class));
    lenient()
        .when(projections.project(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              InvestmentProfile profile = invocation.getArgument(0);
              SimulationAssumptions assumptions = invocation.getArgument(1);
              var forward = mock(ForwardSimulationInput.class);
              var forwardContext = mock(ForwardSimulationContext.class);
              when(forward.context()).thenReturn(forwardContext);
              when(forwardContext.asOfYear()).thenReturn(2026);
              var projection = mock(RetirementProjectionContext.class);
              when(projection.projectedAssumptions()).thenReturn(assumptions);
              when(projection.projectedProfile()).thenReturn(profile);
              when(projection.forward()).thenReturn(forward);
              when(projection.summaries()).thenReturn(Map.of());
              return projection;
            });
    lenient()
        .when(planInput.normalizePlanEditorInput(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              return new NormalizedPlanInput(invocation.getArgument(1), List.of());
            });
    lenient()
        .when(timeline.loadForwardTimeline(anyLong(), any(), any(), any()))
        .thenReturn(new com.smartbox.investory.retirement.api.model.PlanningTimeline(List.of()));
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
    lenient().when(presentation.fromDisplay(any(), any(), any())).thenAnswer(i -> i.getArgument(0));
    lenient().when(presentation.toDisplay(any(), any())).thenAnswer(i -> i.getArgument(0));
    lenient()
        .when(presentation.displayProfile(any(), any()))
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
        .when(sensitivity.analyze(any(), any()))
        .thenReturn(mock(SimulationSensitivityAnalysis.class));
    lenient()
        .when(retirementAgeAnalysis.analyze(any(), any()))
        .thenReturn(mock(RetirementAgeAnalysis.class));
    lenient().when(presentation.displayCharts(any(), any())).thenAnswer(i -> i.getArgument(0));
    lenient().when(presentation.displayTimelineMoney(any(), any())).thenReturn(Map.of());
    lenient()
        .when(plans.resolvePlanId(anyLong(), nullable(Long.class)))
        .thenAnswer(invocation -> Optional.ofNullable(invocation.getArgument(1)));
    InternalResourceViewResolver resolver = new InternalResourceViewResolver();
    resolver.setPrefix("/WEB-INF/views/");
    resolver.setSuffix(".jsp");
    var validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setValidator(validator)
            .setViewResolvers(resolver)
            .build();
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
  void invalidSandboxInputRendersErrorsWithoutRunningCalculation() throws Exception {
    mockMvc
        .perform(
            get("/simulation/sandbox")
                .param("currentAge", "70")
                .param("retirementAge", "65")
                .param("annualSpending", "-1"))
        .andExpect(status().isOk())
        .andExpect(view().name("simulation-sandbox"))
        .andExpect(model().attributeHasFieldErrors("sandboxSimulationForm", "annualSpending"));

    verifyNoInteractions(sandbox);
  }

  @DisplayName("simulation Route Renders The Thymeleaf Page")
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

  @DisplayName("simulation Page Exposes Focused Raw Projection Model")
  @Test
  void simulationPageExposesFocusedRawProjectionModel() throws Exception {
    InvestmentProfile p =
        new InvestmentProfile(
            1L,
            CurrencyType.PLN,
            new BigDecimal("1000"),
            BigDecimal.ZERO,
            new BigDecimal("1000"),
            new BigDecimal("1000"),
            BigDecimal.ZERO,
            List.of(),
            null,
            null,
            new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
                List.of(),
                java.math.BigDecimal.ZERO,
                0,
                com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
            (new BigDecimal("1000") == null ? java.math.BigDecimal.ZERO : new BigDecimal("1000")),
            new BigDecimal("1000")
                .subtract(
                    (new BigDecimal("1000") == null
                        ? java.math.BigDecimal.ZERO
                        : new BigDecimal("1000")))
                .max(java.math.BigDecimal.ZERO),
            com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
                BigDecimal.ZERO,
                new BigDecimal("1000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1000")),
            com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
    when(profiles.loadProfile(1L)).thenReturn(p);
    when(simulations.compareScenarios(
            org.mockito.ArgumentMatchers.eq(p),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(Map.of());
    var result =
        mockMvc
            .perform(get("/simulation").param("portfolioId", "1"))
            .andExpect(status().isOk())
            .andExpect(view().name("simulation"))
            .andExpect(model().attributeExists("simulationPage"))
            .andReturn();
    var page =
        (RetirementSimulationPageView) result.getModelAndView().getModel().get("simulationPage");
    assertEquals(null, page.selectedPlanId());
    assertEquals("Current assumptions", page.activePlanName());
  }

  @DisplayName("simulation Preserves Saved Retirement Transition Fields")
  @Test
  void simulationPreservesSavedRetirementTransitionFields() throws Exception {
    InvestmentProfile p =
        new InvestmentProfile(
            1L,
            CurrencyType.PLN,
            new BigDecimal("1000"),
            BigDecimal.ZERO,
            new BigDecimal("1000"),
            new BigDecimal("1000"),
            BigDecimal.ZERO,
            List.of(),
            null,
            null,
            new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
                List.of(),
                java.math.BigDecimal.ZERO,
                0,
                com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
            (new BigDecimal("1000") == null ? java.math.BigDecimal.ZERO : new BigDecimal("1000")),
            new BigDecimal("1000")
                .subtract(
                    (new BigDecimal("1000") == null
                        ? java.math.BigDecimal.ZERO
                        : new BigDecimal("1000")))
                .max(java.math.BigDecimal.ZERO),
            com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
                BigDecimal.ZERO,
                new BigDecimal("1000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1000")),
            com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
    var saved =
        SimulationAssumptions.defaults(p, 45, 90, 2026)
            .withRetirementAge(60)
            .withAnnualEmploymentIncome(new BigDecimal("240000"))
            .withAnnualPreRetirementContribution(new BigDecimal("50000"));
    when(profiles.loadProfile(1L)).thenReturn(p);
    when(plans.details(1L, 7L)).thenReturn(planDetails(7L, "Plan", saved));
    when(simulations.compareScenarios(eq(p), any(), anyInt())).thenReturn(Map.of());

    mockMvc
        .perform(get("/simulation").param("portfolioId", "1").param("planId", "7"))
        .andExpect(status().isOk())
        .andExpect(model().attributeExists("simulationPage"));

    var captured = org.mockito.ArgumentCaptor.forClass(SimulationAssumptions.class);
    verify(projections).project(eq(p), captured.capture(), any());
    assertEquals(60, captured.getValue().retirementAge());
    assertEquals(new BigDecimal("240000"), captured.getValue().annualEmploymentIncome());
    assertEquals(new BigDecimal("50000"), captured.getValue().annualPreRetirementContribution());
  }

  @DisplayName("simulation Rounds Plan Input Money For Display")
  @Test
  void simulationRoundsPlanInputMoneyForDisplay() throws Exception {
    InvestmentProfile p = profile();
    var saved =
        SimulationAssumptions.defaults(p, 45, 90, 2026)
            .withRecurringSpending(new BigDecimal("180000.00071684"))
            .withAnnualPension(new BigDecimal("7000.00002787"));
    when(profiles.loadProfile(1L)).thenReturn(p);
    when(plans.details(1L, 7L)).thenReturn(planDetails(7L, "Plan", saved));
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

  @DisplayName("simulation Without Plan Id Uses The Latest Saved Plan")
  @Test
  void simulationWithoutPlanIdUsesTheLatestSavedPlan() throws Exception {
    InvestmentProfile profile = profile();
    SimulationAssumptions latest = SimulationAssumptions.defaults(profile, 45, 90, 2026);
    when(profiles.loadProfile(1L)).thenReturn(profile);
    when(plans.resolvePlanId(1L, null)).thenReturn(Optional.of(8L));
    when(plans.details(1L, 8L)).thenReturn(planDetails(8L, "Plan B", latest));
    when(simulations.compareScenarios(eq(profile), any(), anyInt())).thenReturn(Map.of());

    var result = mockMvc.perform(get("/simulation").param("portfolioId", "1")).andReturn();
    var page =
        (RetirementSimulationPageView) result.getModelAndView().getModel().get("simulationPage");

    assertEquals(8L, page.selectedPlanId());
    assertEquals("Plan B", page.activePlanName());
    verify(plans).resolvePlanId(1L, null);
  }

  @DisplayName("simulation Keeps An Explicit Plan Id When ANewer Plan Exists")
  @Test
  void simulationKeepsAnExplicitPlanIdWhenANewerPlanExists() throws Exception {
    InvestmentProfile profile = profile();
    SimulationAssumptions explicit = SimulationAssumptions.defaults(profile, 45, 90, 2026);
    when(profiles.loadProfile(1L)).thenReturn(profile);
    when(plans.resolvePlanId(1L, 7L)).thenReturn(Optional.of(7L));
    when(plans.details(1L, 7L)).thenReturn(planDetails(7L, "Plan A", explicit));
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

  @DisplayName("edit Page Uses The Same Latest Plan Resolution And Falls Back To Defaults")
  @Test
  void editPageUsesTheSameLatestPlanResolutionAndFallsBackToDefaults() {
    InvestmentProfile profile = profile();
    SimulationAssumptions latest = SimulationAssumptions.defaults(profile, 45, 90, 2026);
    when(profiles.loadProfile(1L)).thenReturn(profile);
    when(plans.resolvePlanId(1L, null)).thenReturn(Optional.of(8L));
    when(plans.details(1L, 8L)).thenReturn(planDetails(8L, "Plan B", latest));

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

  @DisplayName("percent Inputs Always Convert Percentage Points To Decimal Rates")
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
                  SimulationRequestMapper.percentInputToRate(
                      new BigDecimal(entry[0]), BigDecimal.ZERO)),
          entry[0]);
  }

  @DisplayName("editing Existing Plan Preserves Its Temporal Anchor Across Calendar Years")
  @Test
  void editingExistingPlanPreservesItsTemporalAnchorAcrossCalendarYears() throws Exception {
    SimulationAssumptions stored =
        SimulationAssumptions.defaults(mock(InvestmentProfile.class), 40, 80, 2025)
            .withRetirementAge(45);
    when(plans.details(1L, 9L)).thenReturn(planDetails(9L, "Plan", stored));
    when(plans.updatePlan(any(com.smartbox.investory.retirement.api.model.UpdatePlanCommand.class)))
        .thenReturn(9L);

    var captured =
        org.mockito.ArgumentCaptor.forClass(
            com.smartbox.investory.retirement.api.model.UpdatePlanCommand.class);
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

    verify(plans).updatePlan(captured.capture());
    SimulationAssumptions saved = captured.getValue().assumptions();
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
    assertEquals(0, saved.fixedIncomeReturnRate().compareTo(stored.fixedIncomeReturnRate()));
    assertEquals(0, saved.capitalGainTaxRate().compareTo(BigDecimal.ZERO));
    var forward =
        new ForwardSimulationContextFactory(
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC))
            .create(mock(InvestmentProfile.class), saved);
    assertEquals(41, forward.asOfAge());
    assertEquals(42, forward.firstProjectedAge());
    assertEquals(45, forward.originalAssumptions().retirementAge());
  }

  @DisplayName("deleting Non Active Plan Returns To Editor With Current Context")
  @Test
  void deletingNonActivePlanReturnsToEditorWithCurrentContext() {
    String redirect =
        controller.deletePlan(9L, 1L, CurrencyType.EUR, 7L, true, SimulationScenario.OPTIMISTIC);

    verify(plans).deletePlan(1L, 9L);
    assertEquals(
        "redirect:/simulation/plan/edit?portfolioId=1&planId=7&planningDisplayCurrency=EUR&selectedScenario=OPTIMISTIC",
        redirect);
  }

  @DisplayName("deleting Active Plan Returns To Editor Without Deleted Plan Context")
  @Test
  void deletingActivePlanReturnsToEditorWithoutDeletedPlanContext() {
    when(plans.resolvePlanId(1L, null)).thenReturn(Optional.of(6L));
    String redirect =
        controller.deletePlan(7L, 1L, CurrencyType.EUR, 7L, true, SimulationScenario.CONSERVATIVE);

    verify(plans).deletePlan(1L, 7L);
    assertEquals(
        "redirect:/simulation/plan/edit?portfolioId=1&planId=6&planningDisplayCurrency=EUR&selectedScenario=CONSERVATIVE",
        redirect);
  }

  @DisplayName("unchanged Displayed Money Keeps Its Canonical Value Without Another Fx Conversion")
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
            List.of(),
            null,
            null,
            new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
                List.of(),
                java.math.BigDecimal.ZERO,
                0,
                com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
            (BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO),
            BigDecimal.ZERO
                .subtract((BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO))
                .max(java.math.BigDecimal.ZERO),
            com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO),
            com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
    when(profiles.loadProfile(1L)).thenReturn(p);
    when(simulations.compareScenarios(eq(p), any(), anyInt())).thenReturn(Map.of());
    var result =
        mockMvc
            .perform(
                get("/simulation")
                    .param("portfolioId", "1")
                    .param("planningDisplayCurrency", "PLN")
                    .param("fixedIncomeReturn", "4.5")
                    .param("equityReturn", "7.5")
                    .param("annualExpenses", "45000.00")
                    .param("annualExpensesCanonical", "11250.12345678"))
            .andExpect(status().isOk())
            .andReturn();
    var page =
        (RetirementSimulationPageView) result.getModelAndView().getModel().get("simulationPage");
    assertEquals(
        0, new BigDecimal("11250.12345678").compareTo(page.assumptions().annualLivingExpenses()));
    assertEquals(0, new BigDecimal("0.045").compareTo(page.assumptions().fixedIncomeReturnRate()));
    assertEquals(0, new BigDecimal("0.075").compareTo(page.assumptions().equityReturnRate()));
    verify(timeline, never()).rollover(anyLong());
    verify(presentation, never())
        .fromDisplay(eq(new BigDecimal("45000.00")), eq(CurrencyType.PLN), any(BigDecimal.class));
  }

  private static com.smartbox.investory.retirement.api.model.PlanDetails planDetails(
      Long id, String name, SimulationAssumptions assumptions) {
    return new com.smartbox.investory.retirement.api.model.PlanDetails(
        id, name, assumptions, null, null, null);
  }

  private static InvestmentProfile profile() {
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        new BigDecimal("1000"),
        BigDecimal.ZERO,
        new BigDecimal("1000"),
        new BigDecimal("1000"),
        BigDecimal.ZERO,
        List.of(),
        null,
        null,
        new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
            List.of(),
            java.math.BigDecimal.ZERO,
            0,
            com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
        (new BigDecimal("1000") == null ? java.math.BigDecimal.ZERO : new BigDecimal("1000")),
        new BigDecimal("1000")
            .subtract(
                (new BigDecimal("1000") == null
                    ? java.math.BigDecimal.ZERO
                    : new BigDecimal("1000")))
            .max(java.math.BigDecimal.ZERO),
        com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
            BigDecimal.ZERO,
            new BigDecimal("1000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("1000")),
        com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
  }
}
