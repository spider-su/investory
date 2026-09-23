package com.smartbox.investory.ryczalt.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RyczaltUploadSupportTest {
  @Test
  void normalizesUnixAndWindowsPathsAndControls() {
    assertThat(RyczaltUploadSupport.filename("../../invoice.pdf", "fallback.pdf"))
        .isEqualTo("invoice.pdf");
    assertThat(RyczaltUploadSupport.filename("C:\\fakepath\\bank.csv", "fallback.csv"))
        .isEqualTo("bank.csv");
    assertThat(RyczaltUploadSupport.filename("invoice\r\nfake.pdf", "fallback.pdf"))
        .isEqualTo("invoice__fake.pdf");
  }

  @Test
  void preservesExtensionWhenBoundingLength() {
    String name = "a".repeat(300) + ".csv";
    String normalized = RyczaltUploadSupport.filename(name, "fallback.csv");
    assertThat(normalized).hasSize(255).endsWith(".csv");
  }

  @Test
  void acceptsSupportedBankCsvVariants() {
    byte[] csv = "date,amount\n2026-01-01,-1\n".getBytes();
    assertThat(RyczaltUploadSupport.requireBank("BANK.CSV", "text/csv", csv)).isEqualTo("BANK.CSV");
    assertThat(RyczaltUploadSupport.requireBank("bank.csv", "application/octet-stream", csv))
        .isEqualTo("bank.csv");
  }

  @Test
  void rejectsInvalidBankAndInvoiceUploads() {
    assertThatThrownBy(
            () -> RyczaltUploadSupport.requireBank("bank.txt", "text/csv", new byte[] {1}))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(
            () -> RyczaltUploadSupport.requireInvoice("invoice.png", "image/png", new byte[] {1}))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(
            () ->
                RyczaltUploadSupport.requireInvoice(
                    "invoice.pdf", "application/pdf", new byte[] {1}))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void acceptsOnlyPdfInvoiceWithPdfSignature() {
    assertThat(
            RyczaltUploadSupport.requireInvoice(
                "invoice.pdf", "application/pdf", "%PDF-1.7".getBytes()))
        .isEqualTo("invoice.pdf");
  }
}
