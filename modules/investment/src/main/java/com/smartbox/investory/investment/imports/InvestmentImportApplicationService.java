package com.smartbox.investory.investment.imports;

import com.smartbox.investory.investment.api.InvestmentImportApi;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Owns broker detection and import orchestration behind the public API. */
@Service
@RequiredArgsConstructor
public class InvestmentImportApplicationService implements InvestmentImportApi {
  private final ImportOrchestratorService imports;

  @Override
  public Object importAuto(String fileName, byte[] content, String source, String sourceRef) {
    String normalized = fileName.toLowerCase(Locale.ROOT);
    BrokerType broker;
    if (normalized.endsWith(".csv")) broker = BrokerType.IBKR;
    else if (normalized.endsWith(".xlsx") || normalized.endsWith(".zip")) broker = BrokerType.XTB;
    else throw new IllegalArgumentException("Unsupported import file extension: " + fileName);
    return importForBroker(broker.name(), fileName, content, source, sourceRef);
  }

  @Override
  public Object importForBroker(
      String broker, String fileName, byte[] content, String source, String sourceRef) {
    try {
      return imports.importFile(
          BrokerType.fromValue(broker),
          content,
          fileName,
          ImportSourceType.valueOf(source),
          sourceRef);
    } catch (ImportFailedException exception) {
      throw new ImportFailure(exception.getMessage(), exception);
    }
  }
}
