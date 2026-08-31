package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.RetirementAnalysisApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.RetirementAnalysisResult;
import com.smartbox.investory.retirement.api.model.RetirementProjectionContext;
import com.smartbox.investory.ui.retirement.analysis.RetirementAnalysisClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class InProcessRetirementAnalysisClient implements RetirementAnalysisClient {
  private final RetirementAnalysisApi retirementAnalysisApi;

  public InProcessRetirementAnalysisClient(
      @Qualifier("retirementAnalysisService") RetirementAnalysisApi retirementAnalysisApi) {
    this.retirementAnalysisApi = retirementAnalysisApi;
  }

  public RetirementAnalysisResult analyze(RetirementProjectionContext projection) {
    return retirementAnalysisApi.analyze(projection);
  }
}
