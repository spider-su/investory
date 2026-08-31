package com.smartbox.investory.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InvestmentAssetDetailTemplateContractTest {
  private static final Path DETAIL =
      Path.of("../adapters/web-ui/src/main/resources/templates/dashboard/asset-detail.html");
  private static final Path NOT_FOUND =
      Path.of("../adapters/web-ui/src/main/resources/templates/dashboard/asset-not-found.html");

  @Test
  void assetDetailUsesUserFacingLabelsAndReportingLanguage() throws Exception {
    String html = Files.readString(DETAIL);

    assertTrue(html.contains("Back to Investment"));
    assertTrue(html.contains(">Quantity</div>"));
    assertTrue(html.contains(">Manual price</h2>"));
    assertTrue(html.contains(">Save price</button>"));
    assertTrue(html.contains("Saves today’s price and refreshes portfolio values."));
    assertTrue(html.contains(">Settlement method</th>"));
    assertTrue(html.contains(">Transaction type</th>"));
    assertTrue(html.contains(">Broker</th>"));
    assertTrue(html.contains(">Description</th>"));
    assertTrue(html.contains(">Reporting snapshot</h2>"));
    assertTrue(
        html.contains(
            "Latest reporting values. The selected period applies to price history, closed transactions, and dividends."));
    assertTrue(html.contains(">Investory symbol</th>"));
    assertTrue(html.contains(">Market-data ticker</th>"));
    assertTrue(html.contains(">Yahoo Finance symbol</th>"));
    assertTrue(html.contains(">Price quality</th>"));
    assertTrue(html.contains("Saving price…"));
    assertTrue(html.contains("Price saved"));
    assertTrue(html.contains("Couldn’t save the price."));

    assertFalse(html.contains("Total quantity"));
    assertFalse(html.contains("Manual current price"));
    assertFalse(html.contains("rebuilds portfolio projections"));
    assertFalse(html.contains("Performance overview"));
    assertFalse(html.contains("Canonical symbol"));
    assertFalse(html.contains("Provider ticker"));
    assertFalse(html.contains("Yahoo symbol"));
  }

  @Test
  void assetNotFoundPageDoesNotRenderExceptionMessage() throws Exception {
    String html = Files.readString(NOT_FOUND);

    assertTrue(html.contains("We couldn’t find this asset."));
    assertTrue(html.contains("Back to Investment"));
    assertFalse(html.contains("${message}"));
    assertFalse(html.contains("Return to dashboard"));
  }
}
