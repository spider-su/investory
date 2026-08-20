package com.smartbox.investory.retirement.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.smartbox.investory.retirement.infrastructure.simulation.SimulationPlan;
import com.smartbox.investory.retirement.planning.*;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.profile.InvestmentProfileFacade;
import com.smartbox.investory.retirement.simulation.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

@ExtendWith(MockitoExtension.class)
class RetirementSimulationControllerTest {
  @Mock InvestmentProfileFacade profiles;
  @Mock RetirementSimulationService simulations;
  @Mock SimulationPlanService plans;
  @Mock SustainableSpendingAnalysisService sustainableSpending;
  @Mock SimulationSensitivityAnalysisService sensitivity;
  @Mock RetirementAgeAnalysisService retirementAgeAnalysis;
  @Mock PlanningTimelineFacade planningTimeline;
  @Mock PlanningCurrencyPresentationService planningPresentation;
  @Mock ForwardSimulationInputService forwardInputs;
  MockMvc mockMvc;
  RetirementSimulationController controller;

  @BeforeEach
  void setUp() {
    InternalResourceViewResolver resolver = new InternalResourceViewResolver();
    resolver.setPrefix("/WEB-INF/views/");
    resolver.setSuffix(".jsp");
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
    lenient().when(planningPresentation.displaySummaries(any(), any())).thenReturn(Map.of());
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
    mockMvc = MockMvcBuilders.standaloneSetup(controller).setViewResolvers(resolver).build();
  }

  @Test
  void simulationPageExposesProfileAssumptionsAndDisplayModels() throws Exception {
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
            org.mockito.ArgumentMatchers.eq(p), org.mockito.ArgumentMatchers.any()))
        .thenReturn(Map.of());
    mockMvc
        .perform(get("/simulation"))
        .andExpect(status().isOk())
        .andExpect(view().name("simulation"))
        .andExpect(model().attribute("profile", p))
        .andExpect(model().attribute("planningDisplayCurrency", CurrencyType.PLN))
        .andExpect(model().attribute("currentYearCloseAllowed", false))
        .andExpect(
            model()
                .attributeExists("assumptions", "displayProfile", "scenarioComparison", "charts"));
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
            .withAnnualPreRetirementContribution(new BigDecimal("50000"));
    var plan =
        mock(com.smartbox.investory.retirement.infrastructure.simulation.SimulationPlan.class);
    when(plan.getName()).thenReturn("Retirement plan");
    when(profiles.loadProfile(1L)).thenReturn(p);
    when(plans.get(1L, 7L)).thenReturn(plan);
    when(plans.assumptions(plan)).thenReturn(saved);
    when(simulations.compareScenarios(eq(p), any())).thenReturn(Map.of());

    mockMvc
        .perform(get("/simulation").param("portfolioId", "1").param("planId", "7"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("assumptions", saved));

    var captured = org.mockito.ArgumentCaptor.forClass(SimulationAssumptions.class);
    verify(simulations).compareScenarios(eq(p), captured.capture());
    assertEquals(60, captured.getValue().retirementAge());
    assertEquals(new BigDecimal("240000"), captured.getValue().annualEmploymentIncome());
    assertEquals(new BigDecimal("50000"), captured.getValue().annualPreRetirementContribution());
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
    verify(plans).create(eq(1L), eq("Plan"), captured.capture());
    assertEquals(new BigDecimal("0.01"), captured.getValue().realEstateReturnRate());
    assertEquals(new BigDecimal("0.02"), captured.getValue().rentalIncomeGrowthRate());
    assertEquals(new BigDecimal("0.025"), captured.getValue().spendingGrowthRate());
    assertEquals(
        SimulationFundingStrategy.RESERVE_AND_HARVEST, captured.getValue().fundingStrategy());
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

    verify(plans).create(eq(1L), eq("Plan"), captured.capture());
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
    verify(plans).update(eq(1L), eq(9L), eq("Plan"), captured.capture());
    assertEquals(new BigDecimal("0.01"), captured.getValue().realEstateReturnRate());
    assertEquals(new BigDecimal("0.02"), captured.getValue().rentalIncomeGrowthRate());
    assertEquals(new BigDecimal("0.025"), captured.getValue().spendingGrowthRate());
    assertEquals(new BigDecimal("0.19"), captured.getValue().capitalGainTaxRate());
  }

  @Test
  void editingExistingPlanPreservesItsTemporalAnchorAcrossCalendarYears() throws Exception {
    SimulationPlan storedPlan = new SimulationPlan();
    storedPlan.setId(9L);
    SimulationAssumptions stored =
        SimulationAssumptions.defaults(mock(InvestmentProfile.class), 40, 80, 2025)
            .withRetirementAge(45);
    when(plans.get(1L, 9L)).thenReturn(storedPlan);
    when(plans.assumptions(storedPlan)).thenReturn(stored);
    when(plans.update(eq(1L), eq(9L), eq("Plan"), any())).thenReturn(storedPlan);

    var captured = org.mockito.ArgumentCaptor.forClass(SimulationAssumptions.class);
    mockMvc
        .perform(
            post("/simulation/plans")
                .param("portfolioId", "1")
                .param("planId", "9")
                .param("name", "Plan")
                .param("currentAge", "40")
                .param("endAge", "80")
                .param("retirementAge", "45")
                .param("annualEmploymentIncome", "240000")
                .param("annualPreRetirementContribution", "50000")
                .param("monthlyLivingCosts", "15000")
                .param("discretionaryExpenses", "0")
                .param("inflation", "2")
                .param("rentalIncomeGrowth", "2")
                .param("spendingGrowth", "2.5")
                .param("fundingStrategy", "RESERVE_AND_HARVEST")
                .param("safeReserveYears", "5")
                .param("equityHarvestMinimumReturn", "7")
                .param("equityGainHarvest", "75")
                .param("allowEmergencyEquityWithdrawal", "true")
                .param("cashReturn", "1")
                .param("fixedIncomeReturn", "4")
                .param("equityReturn", "8")
                .param("realEstateReturn", "3")
                .param("otherReturn", "1")
                .param("pensionStartAge", "67")
                .param("annualPension", "0")
                .param("capitalGainTaxRate", "19")
                .param("planningDisplayCurrency", "PLN")
                .param("selectedScenario", "BASE"))
        .andExpect(status().is3xxRedirection());

    verify(plans).update(eq(1L), eq(9L), eq("Plan"), captured.capture());
    SimulationAssumptions saved = captured.getValue();
    assertEquals(2025, saved.startYear());
    assertEquals(40, saved.currentAge());
    assertEquals(45, saved.retirementAge());
    assertEquals(2030, saved.startYear() + saved.retirementAge() - saved.currentAge());
    assertEquals(new BigDecimal("240000"), saved.annualEmploymentIncome());
    assertEquals(new BigDecimal("50000"), saved.annualPreRetirementContribution());
    assertEquals(new BigDecimal("180000"), saved.annualLivingExpenses());
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
    SimulationPlan sourcePlan = new SimulationPlan();
    sourcePlan.setId(9L);
    SimulationAssumptions source =
        SimulationAssumptions.defaults(mock(InvestmentProfile.class), 40, 80, 2025)
            .withRetirementAge(45);
    SimulationPlan copy = new SimulationPlan();
    copy.setId(10L);
    when(plans.get(1L, 9L)).thenReturn(sourcePlan);
    when(plans.assumptions(sourcePlan)).thenReturn(source);
    when(plans.create(eq(1L), eq("Copy"), any())).thenReturn(copy);

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
    verify(plans).create(eq(1L), eq("Copy"), captured.capture());
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
    String redirect =
        controller.deletePlan(7L, 1L, CurrencyType.EUR, 7L, true, SimulationScenario.CONSERVATIVE);

    verify(plans).delete(1L, 7L);
    assertEquals(
        "redirect:/simulation/plan/edit?portfolioId=1&planningDisplayCurrency=EUR&selectedScenario=CONSERVATIVE",
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
    verify(plans).create(eq(1L), eq("Plan"), captured.capture());
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
    var captured = org.mockito.ArgumentCaptor.forClass(SimulationAssumptions.class);
    when(profiles.loadProfile(1L)).thenReturn(p);
    when(simulations.compareScenarios(eq(p), captured.capture())).thenReturn(Map.of());
    mockMvc
        .perform(
            get("/simulation")
                .param("planningDisplayCurrency", "PLN")
                .param("annualExpenses", "45000.00")
                .param("annualExpensesCanonical", "11250.12345678"))
        .andExpect(status().isOk());
    assertEquals(
        0, new BigDecimal("11250.12345678").compareTo(captured.getValue().annualLivingExpenses()));
    verify(planningPresentation, never())
        .fromDisplay(eq(new BigDecimal("45000.00")), eq(CurrencyType.PLN), any(BigDecimal.class));
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
}
