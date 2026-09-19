package com.smartbox.investory.ryczalt.web;

import com.smartbox.investory.ryczalt.checker.CheckSeverity;

public record IssueResponse(String code, CheckSeverity severity, String message) {}
