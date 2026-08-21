package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.application.model.LongTermAssetBootstrapDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Integer.MAX_VALUE)
@RequiredArgsConstructor
public class LongTermAssetBootstrapCommand implements ApplicationRunner {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final LongTermAssetBootstrapService service;
  private final ConfigurableApplicationContext context;

  @Override
  public void run(ApplicationArguments args) throws Exception {
    if (!args.containsOption("bootstrap-long-term-assets")) return;
    var file = args.getOptionValues("bootstrap-long-term-assets");
    if (file == null || file.size() != 1 || file.getFirst().isBlank())
      throw new IllegalArgumentException("Use --bootstrap-long-term-assets=<JSON file>");
    var document =
        objectMapper.readValue(
            Files.readString(Path.of(file.getFirst())), LongTermAssetBootstrapDocument.class);
    boolean dryRun = args.containsOption("dry-run");
    var result = service.importDocument(document, dryRun);
    System.out.printf(
        "Long-term asset bootstrap %s: %d assets to create, %d to update, %d tax policies to create, %d to update%n",
        dryRun ? "validated" : "completed",
        result.assetsToCreate(),
        result.assetsToUpdate(),
        result.taxPoliciesToCreate(),
        result.taxPoliciesToUpdate());
    System.out.printf(
        "Real-estate totals: value=%s, gross=%s, expenses=%s, tax=%s, net=%s%n",
        result.propertyValue().toPlainString(),
        result.grossAnnualIncome().toPlainString(),
        result.operatingExpenses().toPlainString(),
        result.rentalTax().toPlainString(),
        result.netAnnualIncome().toPlainString());
    SpringApplication.exit(context, () -> 0);
  }
}
