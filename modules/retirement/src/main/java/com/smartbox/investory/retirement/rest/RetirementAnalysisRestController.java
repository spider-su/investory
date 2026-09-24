package com.smartbox.investory.retirement.rest;

import com.smartbox.investory.retirement.api.RetirementAnalysisApi;
import com.smartbox.investory.retirement.api.RetirementPresentationApi;
import com.smartbox.investory.retirement.api.RetirementProjectionApi;
import com.smartbox.investory.retirement.api.contract.RetirementAnalysisContracts;
import com.smartbox.investory.retirement.api.contract.RetirementAnalysisContracts.AnalysisParameters;
import com.smartbox.investory.retirement.api.contract.RetirementAnalysisContracts.AnalysisResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST and in-process Java facade for retirement projection analysis. */
@RestController
@Validated
@RequestMapping("/api/v1/portfolios/{portfolioId}/retirement/analysis")
public class RetirementAnalysisRestController {
  private final RetirementAnalysisApi analyses;
  private final RetirementProjectionApi projections;
  private final RetirementPresentationApi presentation;

  @Autowired
  public RetirementAnalysisRestController(
      @Qualifier("retirementAnalysisService") RetirementAnalysisApi analyses,
      @Qualifier("retirementProjectionService") RetirementProjectionApi projections,
      @Qualifier("retirementPlanningApplicationService") RetirementPresentationApi presentation) {
    this.analyses = analyses;
    this.projections = projections;
    this.presentation = presentation;
  }

  public RetirementAnalysisRestController(
      RetirementAnalysisApi analyses, RetirementProjectionApi projections) {
    this.analyses = analyses;
    this.projections = projections;
    this.presentation = null;
  }

  @PostMapping
  public AnalysisResponse analyze(
      @PathVariable @NotNull Long portfolioId, @Valid @RequestBody AnalysisParameters request) {
    var projection =
        projections.load(
            portfolioId, request.planId(), request.defaultCurrentAge(), request.defaultEndAge());
    var result = analyses.analyze(projection);
    return presentation == null
        ? AnalysisResponse.from(result)
        : toResponse(result, projection, request.displayCurrency());
  }

  private AnalysisResponse toResponse(
      com.smartbox.investory.retirement.api.model.RetirementAnalysisResult result,
      com.smartbox.investory.retirement.api.model.RetirementProjection projection,
      com.smartbox.investory.shared.currency.CurrencyType displayCurrency) {
    return new AnalysisResponse(
        result.state(),
        result.available(),
        RetirementAnalysisContracts.AnalysisValue.from(result.sustainableSpending()),
        RetirementAnalysisContracts.AnalysisValue.from(result.retirementAge()),
        RetirementAnalysisContracts.AnalysisValue.from(result.sensitivity()),
        result.charts(),
        displayCurrency,
        presentation.displaySummaries(projection.summaries(), displayCurrency),
        result.available()
            ? presentation.displayPlanRisks(
                result.sensitivity().value().orElseThrow(), displayCurrency)
            : null,
        result.available()
            ? presentation.displayPlanningFlexibility(
                result.sustainableSpending().value().orElseThrow(),
                result.retirementAge().value().orElseThrow(),
                displayCurrency)
            : null,
        presentation.displayCharts(result.charts(), displayCurrency));
  }
}
