package com.smartbox.investory.accounting;

import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
class PdfTextExtractor {
  String extract(byte[] bytes) {
    try (PDDocument document = Loader.loadPDF(bytes)) {
      return new PDFTextStripper().getText(document).replace('\u00a0', ' ').trim();
    } catch (IOException exception) {
      throw new IllegalArgumentException("PDF text extraction failed", exception);
    }
  }
}
