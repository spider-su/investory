package com.smartbox.investory.ryczalt.application.query;

import com.smartbox.investory.ryczalt.checker.CheckSeverity;

public record RyczaltIssueReadModel(String code, CheckSeverity severity, String context) {}
