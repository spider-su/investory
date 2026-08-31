package com.smartbox.investory.retirement.api.model;

/** Structured warning returned when retirement plan input needs attention. */
public record PlanInputWarning(String field, String code, String message) {}
