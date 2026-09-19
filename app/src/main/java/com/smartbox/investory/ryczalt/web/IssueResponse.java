package com.smartbox.investory.ryczalt.web;

import com.smartbox.investory.ryczalt.application.query.IssueKind;
import com.smartbox.investory.ryczalt.checker.CheckSeverity;

public record IssueResponse(
    String id,
    String code,
    CheckSeverity severity,
    IssueKind kind,
    String title,
    String message,
    String sourceReference) {}
