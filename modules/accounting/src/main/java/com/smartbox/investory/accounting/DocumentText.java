package com.smartbox.investory.accounting;

import java.util.List;

/** Small, deterministic PDF text model. Coordinates use PDFBox's page coordinate system. */
record DocumentText(List<Page> pages) {
  String plainText() {
    return pages.stream()
        .flatMap(page -> page.lines().stream())
        .map(Line::text)
        .filter(text -> !text.isBlank())
        .reduce((left, right) -> left + "\n" + right)
        .orElse("");
  }

  record Page(int number, List<Line> lines) {}

  record Line(String text, float y, List<Token> tokens) {}

  record Token(String text, int page, float x, float y, float width) {}
}
