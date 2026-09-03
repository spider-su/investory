package com.smartbox.investory.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HtmlTestSupport {
  private static final Path TEMPLATE_ROOT =
      Path.of("../adapters/web-ui/src/main/resources/templates");
  private static final Pattern REPLACED_ELEMENT =
      Pattern.compile(
          "(?s)<(?<tag>[A-Za-z][A-Za-z0-9]*)\\b(?<attributes>[^>]*\\bth:replace=\"~\\{(?<reference>[^\"]+)\\}\"[^>]*)></\\k<tag>>");

  private HtmlTestSupport() {}

  static String readTemplateWithFragments(Path template) throws IOException {
    Set<Path> activeFiles = new HashSet<>();
    activeFiles.add(template.toAbsolutePath().normalize());
    return resolve(Files.readString(template), activeFiles);
  }

  private static String resolve(String html, Set<Path> activeFiles) throws IOException {
    Matcher matcher = REPLACED_ELEMENT.matcher(html);
    StringBuffer resolved = new StringBuffer();
    while (matcher.find()) {
      String reference = matcher.group("reference");
      int separator = reference.indexOf(" ::");
      if (separator < 0) {
        continue;
      }
      if (reference.startsWith("fragments/")) {
        matcher.appendReplacement(resolved, Matcher.quoteReplacement(matcher.group()));
        continue;
      }
      Path fragment =
          TEMPLATE_ROOT
              .resolve(reference.substring(0, separator) + ".html")
              .toAbsolutePath()
              .normalize();
      String replacement = "";
      if (activeFiles.add(fragment)) {
        replacement = resolve(Files.readString(fragment), activeFiles);
        activeFiles.remove(fragment);
      }
      matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(resolved);
    return resolved.toString();
  }
}
