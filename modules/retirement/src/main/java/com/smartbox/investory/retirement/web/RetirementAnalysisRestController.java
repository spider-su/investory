package com.smartbox.investory.retirement.web;

import com.smartbox.investory.retirement.api.RetirementAnalysisApi;
import com.smartbox.investory.retirement.api.RetirementProjectionApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.web.RetirementAnalysisContracts.AnalysisResponse;
import com.smartbox.investory.retirement.web.RetirementProjectionContracts.ProjectionParameters;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
@RequestMapping("/api/v1/retirement/portfolios/{portfolioId}/analysis")
public class RetirementAnalysisRestController {
  private final RetirementAnalysisApi analyses;
  private final RetirementProjectionApi projections;

  public RetirementAnalysisRestController(
      @Qualifier("retirementAnalysisService") RetirementAnalysisApi analyses,
      @Qualifier("retirementProjectionFacade") RetirementProjectionApi projections) {
    this.analyses = analyses;
    this.projections = projections;
  }

  @PostMapping
  public AnalysisResponse analyze(
      @PathVariable @NotNull Long portfolioId, @Valid @RequestBody ProjectionParameters request) {
    return AnalysisResponse.from(
        analyses.analyze(
            projections.load(
                portfolioId,
                request.planId(),
                request.defaultCurrentAge(),
                request.defaultEndAge())));
  }
}
