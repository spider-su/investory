package com.smartbox.investory.accounting;

public record AccountingFilingIssue(
    AccountingFilingIssueCode code, String context, String message) {}
