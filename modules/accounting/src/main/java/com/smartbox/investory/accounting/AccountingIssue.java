package com.smartbox.investory.accounting;

public record AccountingIssue(
    String type, String severity, String sourceReference, String message) {}
