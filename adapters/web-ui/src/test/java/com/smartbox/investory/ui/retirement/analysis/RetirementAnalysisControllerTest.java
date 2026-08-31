package com.smartbox.investory.ui.retirement.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.retirement.api.model.RetirementAnalysisResult;
import com.smartbox.investory.retirement.api.model.RetirementProjectionContext;
import com.smartbox.investory.retirement.api.model.SimulationChartData;
import com.smartbox.investory.retirement.api.model.SimulationCustomDeltas;
import com.smartbox.investory.retirement.api.model.SimulationDecisionSummaryMoney;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.ui.retirement.simulation.RetirementPlanClient;
import com.smartbox.investory.ui.retirement.simulation.RetirementPresentationClient;
import com.smartbox.investory.ui.retirement.simulation.RetirementProjectionClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

class RetirementAnalysisControllerTest {
  @Test
  void noForwardHorizonRendersStableUnavailablePage() {
    RetirementProjectionClient projections = mock(RetirementProjectionClient.class);
    RetirementAnalysisClient analyses = mock(RetirementAnalysisClient.class);
    RetirementPresentationClient presentation = mock(RetirementPresentationClient.class);
    RetirementPlanClient plans = mock(RetirementPlanClient.class);
    RetirementProjectionContext projection = mock(RetirementProjectionContext.class);
    when(plans.resolvePlanId(1L, null)).thenReturn(Optional.empty());
    when(projections.load(1L, null, 40, 95, SimulationCustomDeltas.zero())).thenReturn(projection);
    when(projection.summaries()).thenReturn(Map.of());
    var charts = new SimulationChartData(Map.of(), List.of(), List.of());
    when(analyses.analyze(projection))
        .thenReturn(RetirementAnalysisResult.noForwardHorizon(charts));
    SimulationDecisionSummaryMoney summary = mock(SimulationDecisionSummaryMoney.class);
    when(presentation.displaySummaries(Map.of(), CurrencyType.PLN))
        .thenReturn(Map.of(SimulationScenario.BASE, summary));
    when(presentation.displayCharts(charts, CurrencyType.PLN)).thenReturn(charts);
    var controller = new RetirementAnalysisController(projections, analyses, presentation, plans);
    var model = new ConcurrentModel();

    assertThat(
            controller.analysis(
                1L,
                null,
                CurrencyType.PLN,
                SimulationScenario.BASE,
                null,
                null,
                null,
                null,
                null,
                model))
        .isEqualTo("retirement-analysis");
    assertThat(model.getAttribute("analysisPage")).isInstanceOf(RetirementAnalysisPageView.class);
    verify(analyses).analyze(projection);
  }
}
