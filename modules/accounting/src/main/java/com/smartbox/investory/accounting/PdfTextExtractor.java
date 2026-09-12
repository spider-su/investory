package com.smartbox.investory.accounting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

@Component
class PdfTextExtractor {
  private final DocumentScannerProperties properties;

  PdfTextExtractor(DocumentScannerProperties properties) {
    this.properties = properties;
  }

  DocumentText extract(byte[] bytes) {
    if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("PDF file is empty");
    if (bytes.length > properties.getMaxBytes()) {
      throw new IllegalArgumentException("PDF file is too large; maximum size is " + properties.getMaxBytes() + " bytes");
    }
    try (PDDocument document = Loader.loadPDF(bytes)) {
      if (document.isEncrypted()) throw new IllegalArgumentException("Encrypted/password-protected PDFs are not supported");
      if (document.getNumberOfPages() > properties.getMaxPages()) {
        throw new IllegalArgumentException("PDF has too many pages; maximum is " + properties.getMaxPages());
      }
      LayoutStripper stripper = new LayoutStripper();
      stripper.setSortByPosition(true);
      stripper.getText(document);
      return new DocumentText(stripper.pages);
    } catch (IOException exception) {
      throw new IllegalArgumentException("PDF text extraction failed", exception);
    }
  }

  private static final class LayoutStripper extends PDFTextStripper {
    private final List<DocumentText.Page> pages = new ArrayList<>();
    private final List<List<TextPosition>> currentLines = new ArrayList<>();

    private LayoutStripper() throws IOException {}

    @Override
    protected void writeString(String text, List<TextPosition> positions) throws IOException {
      if (!positions.isEmpty()) currentLines.add(new ArrayList<>(positions));
    }

    @Override
    protected void endPage(PDPage page) throws IOException {
      List<DocumentText.Line> lines = currentLines.stream()
          .map(items -> items.stream().sorted(Comparator.comparing(TextPosition::getX)).toList())
          .map(items -> new DocumentText.Line(
              items.stream().map(TextPosition::getUnicode).reduce("", String::concat).replace('\u00a0', ' ').trim(),
              items.get(0).getYDirAdj(),
              items.stream().map(p -> new DocumentText.Token(p.getUnicode().replace('\u00a0', ' '), getCurrentPageNo(), p.getXDirAdj(), p.getYDirAdj(), p.getWidthDirAdj())).toList()))
          .filter(line -> !line.text().isBlank())
          .toList();
      pages.add(new DocumentText.Page(getCurrentPageNo(), lines));
      currentLines.clear();
      super.endPage(page);
    }
  }
}
