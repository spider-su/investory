package com.smartbox.investory.retirement.api.model;

import java.util.List;

/** Application-layer close readiness for a historical planning draft. */
public record PlanningYearCloseStatus(boolean canClose, List<String> missingMetrics) {}
