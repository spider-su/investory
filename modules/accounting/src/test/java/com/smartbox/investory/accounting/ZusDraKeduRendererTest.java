package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class ZusDraKeduRendererTest {
  @Test
  void suppliedOctober2025PdfIsKeptAsAReadableReferenceFixture() throws Exception {
    try (var pdf =
        Loader.loadPDF(
            getClass()
                .getResourceAsStream(
                    "/fixtures/zus/Deklaracja rozliczeniowa ZUS DRA 01 10-2025.pdf")
                .readAllBytes())) {
      String text = new PDFTextStripper().getText(pdf);
      assertThat(text)
          .contains(
              "DEKLARACJA ROZLICZENIOWA", "0 5 1 0 0 0", "1 5 5 8 1 2 2 , 9 2", "1 5 3 8 8 , 5 2");
    }
  }

  @Test
  void rendersTheFixtureValuesIntoKedu() {
    var declaration = fixtureDeclaration();
    byte[] payload = new ZusDraKeduRenderer().render(declaration);
    String xml = new String(payload, StandardCharsets.UTF_8);

    assertThat(xml)
        .contains("<ZUSDRA", "<p1>6</p1>", "<p1>01</p1>", "<p2>2025-10</p2>", "<p1>0510</p1>")
        .contains("<p5>15388.52</p5>", "<XI>", "<p15>1558122.92</p15>");
    new ZusDraKeduXmlValidator().validate(payload);
  }

  @Test
  void rejectsMissingIdentityAndWrongSectionShapeBeforeRendering() {
    var invalid =
        new ZusDraDeclaration(
            LocalDate.of(2025, 10, 1),
            "1",
            "6",
            new ZusDraDeclaration.Payer(null, null, null, null, null, null, null, null, null),
            1,
            new BigDecimal("1.67"),
            List.of("0.00"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            new ZusDraDeclaration.IncomeDeclaration("", null, null, null, null, null),
            List.of(),
            null);

    assertThatThrownBy(() -> new ZusDraKeduRenderer().render(invalid))
        .isInstanceOf(ZusDraValidationException.class)
        .hasMessageContaining("submissionNumber")
        .hasMessageContaining("payer NIP or PESEL")
        .hasMessageContaining("insuranceTitle");
  }

  private ZusDraDeclaration fixtureDeclaration() {
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
