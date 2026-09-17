package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MockZusDraSubmissionClientTest {
  @Test
  void storesAndReadsAnUnsignedFixtureSubmission(@TempDir Path root) {
    var client = new MockZusDraSubmissionClient(root);
    var written = client.submit(declaration());

    assertThat(written.status()).isEqualTo(ZusDraSubmission.Status.MOCK_STORED);
    assertThat(written.payloadFile()).isRegularFile();
    assertThat(Files.exists(root.resolve(written.id()).resolve("submission.properties"))).isTrue();

    var read = client.read(written.id()).orElseThrow();
    assertThat(read.payload()).isEqualTo(written.payload());
    assertThat(read.payloadHash()).isEqualTo(AccountingFilingFingerprint.sha256(read.payload()));
    assertThat(client.read("missing")).isEmpty();
    assertThat(client.read("../missing")).isEmpty();
  }

  private ZusDraDeclaration declaration() {
    return new ZusDraDeclaration(
        LocalDate.of(2025, 10, 1),
        "01",
        "6",
        new ZusDraDeclaration.Payer(
            "8133703437",
            "389371191",
            "85091019311",
            "1",
            null,
            "ALEX KOTIK",
            "KOTIK",
            "ALEX",
            LocalDate.of(1985, 9, 10)),
        1,
        new BigDecimal("1.67"),
        List.of(),
        List.of(),
        List.of("0.00", "1384.97", "0.00", "0.00", "1384.97", "0.00", "1384.97"),
        List.of(),
        List.of(
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "true",
            "",
            "true",
            "1558122.92",
            "15388.52",
            "1384.97",
            "",
            "",
            ""),
        new ZusDraDeclaration.IncomeDeclaration(
            "05 10 00", null, null, null, new BigDecimal("15388.52"), null),
        List.of(),
        LocalDate.of(2025, 11, 8));
  }
}
