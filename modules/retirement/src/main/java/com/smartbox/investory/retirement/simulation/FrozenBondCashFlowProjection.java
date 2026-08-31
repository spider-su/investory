package com.smartbox.investory.retirement.simulation;

import org.springframework.stereotype.Service;

/**
 * Compatibility adapter for simulation services; implementation lives in the public model layer.
 */
@Service
public class FrozenBondCashFlowProjection
    extends com.smartbox.investory.retirement.api.model.FrozenBondCashFlowProjection {}
