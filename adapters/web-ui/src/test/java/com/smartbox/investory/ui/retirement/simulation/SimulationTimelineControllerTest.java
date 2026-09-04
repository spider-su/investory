package com.smartbox.investory.ui.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanningMetric;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.ui.profile.ProfileClient;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@ExtendWith(MockitoExtension.class)
class SimulationTimelineControllerTest {
  @Mock ProfileClient profiles;
  @Mock RetirementPlanClient plans;
  @Mock RetirementPlanningClient planning;
  @Mock RetirementProjectionClient projections;

  private SimulationTimelineController controller() {
    return new SimulationTimelineController(
        profiles,
        plans,
        planning,
        projections,
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void rolloverPreservesSimulationContext() {
    String redirect =
        controller().rollover(1L, CurrencyType.EUR, 7L, SimulationScenario.CONSERVATIVE);

    verify(planning).rollover(1L);
    assertEquals(
        "redirect:/portfolios/1/simulation?planId=7&planningDisplayCurrency=EUR&selectedScenario=CONSERVATIVE",
        redirect);
  }

  @Test
  void pastManualValueConvertsAndPreservesDetailContext() {
    when(planning.fromDisplay(new BigDecimal("45000"), CurrencyType.PLN, BigDecimal.ZERO))
        .thenReturn(new BigDecimal("11250"));
    RedirectAttributesModelMap flash = new RedirectAttributesModelMap();

    String redirect =
        controller()
            .savePastManual(
                1L,
                2025,
                PlanningMetric.CORE_SPENDING,
                new BigDecimal("45000"),
                "Actual household spending",
                CurrencyType.PLN,
                7L,
                SimulationScenario.CONSERVATIVE,
                flash);

    verify(planning)
        .saveDraftManualValue(
            1L,
            2025,
            PlanningMetric.CORE_SPENDING,
            new BigDecimal("11250"),
            "Actual household spending");
    assertEquals(
        "redirect:/portfolios/1/simulation/timeline/2025?planningDisplayCurrency=PLN&planId=7&selectedScenario=CONSERVATIVE",
        redirect);
  }

  @Test
  void failedPastCloseReturnsFlashError() {
    doThrow(new IllegalStateException("Missing CORE_SPENDING"))
        .when(planning)
        .closeHistoricalDraft(1L, 2025);
    RedirectAttributesModelMap flash = new RedirectAttributesModelMap();

    String redirect =
        controller()
            .closeHistoricalDraft(1L, 2025, CurrencyType.PLN, 7L, SimulationScenario.BASE, flash);

    assertEquals(
        "redirect:/portfolios/1/simulation/timeline/2025?planningDisplayCurrency=PLN&planId=7&selectedScenario=BASE",
        redirect);
    assertEquals("Missing CORE_SPENDING", flash.getFlashAttributes().get("planningError"));
  }

  @Test
  void createPastYearWithoutPlanCreatesHistoricalDraft() {
    String redirect =
        controller().createPastYear(1L, 2024, CurrencyType.PLN, null, SimulationScenario.BASE);

    verify(planning).createHistoricalDraft(1L, 2024);
    assertEquals(
        "redirect:/portfolios/1/simulation/timeline/2024?planningDisplayCurrency=PLN", redirect);
  }

  @Test
  void prefillWithoutPlanStartsAtCurrentClockYear() {
    String redirect =
        controller()
            .prefillHistoricalYears(1L, null, CurrencyType.EUR, SimulationScenario.OPTIMISTIC);

    verify(planning).prefillHistoricalYears(1L, 2026);
    assertEquals(
        "redirect:/portfolios/1/simulation?planningDisplayCurrency=EUR&selectedScenario=OPTIMISTIC",
        redirect);
  }

  @Test
  void failedReopenPreservesErrorAndYearContext() {
    doThrow(new IllegalArgumentException("Year is not closed"))
        .when(planning)
        .reopenHistoricalYear(1L, 2025);
    RedirectAttributesModelMap flash = new RedirectAttributesModelMap();

    String redirect =
        controller()
            .reopenPlanningYear(1L, 2025, CurrencyType.PLN, 7L, SimulationScenario.BASE, flash);

    assertEquals("Year is not closed", flash.getFlashAttributes().get("planningError"));
    assertEquals(
        "redirect:/portfolios/1/simulation/timeline/2025?planningDisplayCurrency=PLN&planId=7&selectedScenario=BASE",
        redirect);
  }
}
