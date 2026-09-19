package com.smartbox.investory.ryczalt.checker;

import java.util.List;

public record CheckResult(boolean complete, List<CheckIssue> issues) {
  public CheckResult {
    issues = List.copyOf(issues);
  }
}
