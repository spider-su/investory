package com.smartbox.investory.ryczalt.web;

import com.smartbox.investory.ryczalt.persistence.CalculationStatus;

public record CalculationResponse(String type, CalculationStatus status, String amount) {}
