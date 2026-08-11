package com.example.demo.services.reconciliation;

import com.example.demo.infrastructure.repository.reconciliation.ReconciliationReportRepository;
import com.example.demo.services.reconciliation.ReconciliationReport.AccountIssue;
import com.example.demo.services.reconciliation.ReconciliationReport.AccountSummary;
import com.example.demo.services.reconciliation.ReconciliationReport.PositionIssue;
import com.example.demo.services.reconciliation.ReconciliationReport.PositionSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReconciliationReportService {

  private final ReconciliationReportRepository repository;

  @Transactional(readOnly = true)
  public ReconciliationReport load() {
    var positionSummary = repository.summarizePositionIssues();
    var accountSummary = repository.summarizeAccountIssues();
    return new ReconciliationReport(
        new PositionSummary(
            value(positionSummary.getTotalIssues()),
            value(positionSummary.getErrors()),
            value(positionSummary.getWarnings()),
            value(positionSummary.getAffectedAssets()),
            value(positionSummary.getAffectedAccounts())),
        new AccountSummary(
            value(accountSummary.getTotalIssues()),
            value(accountSummary.getFailures()),
            value(accountSummary.getWarnings()),
            value(accountSummary.getAffectedAccounts())),
        repository.findPositionIssues().stream()
            .map(ReconciliationReportService::toPositionIssue)
            .toList(),
        repository.findAccountIssues().stream()
            .map(ReconciliationReportService::toAccountIssue)
            .toList());
  }

  private static PositionIssue toPositionIssue(
      ReconciliationReportRepository.PositionIssueRow row) {
    return new PositionIssue(
        row.getAccountId(),
        row.getAccountName(),
        row.getProvider(),
        row.getAssetId(),
        row.getSymbol(),
        row.getValuationDate(),
        row.getSeverity(),
        row.getValidationCode(),
        row.getExpectedValue(),
        row.getActualValue(),
        row.getDifference(),
        row.getRelativeDifference(),
        row.getMessage());
  }

  private static AccountIssue toAccountIssue(ReconciliationReportRepository.AccountIssueRow row) {
    return new AccountIssue(
        row.getAccountId(),
        row.getAccountName(),
        row.getProvider(),
        row.getValuationDate(),
        row.getStatus(),
        row.getSeverity(),
        row.getValidationMessage(),
        row.getMarketValueDifference(),
        row.getCashDifference(),
        row.getEquityDifference(),
        row.getCostBaseDifference(),
        row.getUnrealizedDifference());
  }

  private static long value(Long value) {
    return value == null ? 0 : value;
  }
}
