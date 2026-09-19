package com.smartbox.investory.ryczalt.calculation.application;

public record CalculationExecution<T>(T result, boolean reused, int revision, String fingerprint) {}
