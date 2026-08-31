package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.*;
import java.math.BigDecimal;

/** One compact, display-only cash-flow connector. */
public record CashFlowFlowView(
    String source, String target, BigDecimal amount, String type, BigDecimal sharePercent) {}
