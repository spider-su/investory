package com.smartbox.investory.investment.imports.application;

import com.smartbox.investory.investment.api.importing.ImportBroker;
import com.smartbox.investory.investment.api.importing.ImportSource;
import com.smartbox.investory.investment.api.importing.ImportStatus;
import com.smartbox.investory.investment.api.importing.InvestmentImportApi;
import com.smartbox.investory.investment.imports.BrokerType;
import com.smartbox.investory.investment.imports.ImportBatchResponse;
import com.smartbox.investory.investment.imports.ImportFailedException;
import com.smartbox.investory.investment.imports.ImportOrchestratorService;
import com.smartbox.investory.investment.imports.ImportSourceType;
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
      Long portfolioId, String fileName, byte[] content, ImportSource source, String sourceRef) {
    return importAuto(portfolioId, fileName, content, source, sourceRef, false);
  }

  @Override
  public ImportResult importAuto(
      Long portfolioId,
      String fileName,
      byte[] content,
      ImportSource source,
      String sourceRef,
      boolean deferRefresh) {
    try {
      String normalized = fileName.toLowerCase(Locale.ROOT);
      BrokerType broker;
      if (normalized.endsWith(".csv")) broker = BrokerType.IBKR;
      else if (normalized.endsWith(".xlsx") || normalized.endsWith(".zip")) broker = BrokerType.XTB;
      else throw new IllegalArgumentException("Unsupported import file extension: " + fileName);
      return importForBroker(
          portfolioId,
          broker == BrokerType.IBKR ? ImportBroker.IBKR : ImportBroker.XTB,
          fileName,
          content,
          source,
          sourceRef,
          deferRefresh);
    } catch (RuntimeException exception) {
      log.error("Investment import failed: file={}", fileName, exception);
      throw exception;
    }
  }

  @Override
  public ImportResult importForBroker(
      Long portfolioId,
      ImportBroker broker,
      String fileName,
      byte[] content,
      ImportSource source,
      String sourceRef) {
    return importForBroker(portfolioId, broker, fileName, content, source, sourceRef, false);
  }

  @Override
  public ImportResult importForBroker(
      Long portfolioId,
      ImportBroker broker,
      String fileName,
      byte[] content,
      ImportSource source,
      String sourceRef,
      boolean deferRefresh) {
    try {
      ImportBatchResponse result =
          importOrchestrator.importFile(
              portfolioId,
              BrokerType.fromApi(broker),
              content,
              fileName,
              ImportSourceType.fromApi(source),
              sourceRef,
              !deferRefresh);
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
