package com.smartbox.investory.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CssTestSupport {
  private static final Path CSS_ROOT = Path.of("../adapters/web-ui/src/main/resources/static/css");
  private static final Pattern IMPORT = Pattern.compile("@import url\\(\\\"([^\\\"]+)\\\"\\);");

  private CssTestSupport() {}

  static String readComposedStylesheet() throws IOException {
    return read(CSS_ROOT.resolve("components.css"));
  }

  private static String read(Path stylesheet) throws IOException {
    String css = Files.readString(stylesheet);
    StringBuilder composed = new StringBuilder(css);
    Matcher imports = IMPORT.matcher(css);
    while (imports.find()) {
      composed.append('\n').append(read(stylesheet.getParent().resolve(imports.group(1))));
    }
    return composed.toString();
  }
}
