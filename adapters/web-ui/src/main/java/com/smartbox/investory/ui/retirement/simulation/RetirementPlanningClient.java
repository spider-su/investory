package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.RetirementPlanInputApi;
import com.smartbox.investory.retirement.api.RetirementPresentationApi;
import com.smartbox.investory.retirement.api.RetirementTimelineApi;
import com.smartbox.investory.retirement.api.model.*;

/** Compatibility aggregate for timeline-controller callers not yet migrated to focused seams. */
public interface RetirementPlanningClient
    extends RetirementPlanInputApi, RetirementTimelineApi, RetirementPresentationApi {}
