package com.smartbox.investory.investment.imports;

import com.smartbox.investory.investment.api.importing.ImportBroker;
import com.smartbox.investory.investment.api.importing.ImportSource;
import com.smartbox.investory.investment.api.importing.ImportStatus;
import com.smartbox.investory.investment.api.importing.InvestmentImportApi;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Owns broker detection and import orchestration behind the public API. */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvestmentImportApplicationService implements InvestmentImportApi {
  private final ImportOrchestratorService importOrchestrator;

  @Override
  public ImportResult importAuto(
      String fileName, byte[] content, ImportSource source, String sourceRef) {
    try {
      String normalized = fileName.toLowerCase(Locale.ROOT);
      BrokerType broker;
      if (normalized.endsWith(".csv")) broker = BrokerType.IBKR;
      else if (normalized.endsWith(".xlsx") || normalized.endsWith(".zip")) broker = BrokerType.XTB;
      else throw new IllegalArgumentException("Unsupported import file extension: " + fileName);
      return importForBroker(
          ImportBroker.valueOf(broker.name()), fileName, content, source, sourceRef);
    } catch (RuntimeException exception) {
      log.error("Investment import failed: file={}", fileName, exception);
      throw exception;
    }
  }

  @Override
  public ImportResult importForBroker(
      ImportBroker broker, String fileName, byte[] content, ImportSource source, String sourceRef) {
    try {
      ImportBatchResponse result =
          importOrchestrator.importFile(
              BrokerType.valueOf(broker.name()),
              content,
              fileName,
              ImportSourceType.valueOf(source.name()),
              sourceRef);
      ImportResult importResult =
          new ImportResult(
              result.batchId(),
              result.broker().name(),
              ImportStatus.valueOf(result.status().name()),
              result.rowsTotal(),
              result.rowsApplied(),
              result.rowsFailed(),
              result.message(),
              result.duplicate());
      log.info(
          "Investment import succeeded: file={} broker={} batchId={}",
          fileName,
          result.broker(),
          result.batchId());
      return importResult;
    } catch (ImportFailedException exception) {
      log.error("Investment import failed: file={} broker={}", fileName, broker, exception);
      throw new ImportFailure(exception.getMessage(), exception);
    } catch (RuntimeException exception) {
      log.error("Investment import failed: file={} broker={}", fileName, broker, exception);
      throw exception;
    }
  }
}
