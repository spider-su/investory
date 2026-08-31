package com.smartbox.investory.retirement.api;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.RetirementAnalysisResult;
import com.smartbox.investory.retirement.api.model.RetirementProjectionContext;

/** Public query boundary for interpreting a prepared projection. */
public interface RetirementAnalysisApi {
  RetirementAnalysisResult analyze(RetirementProjectionContext projection);
}
