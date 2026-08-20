package com.smartbox.investory.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ThymeleafClassReferenceTest {
  private static final Pattern APPLICATION_TYPE_LITERAL =
      Pattern.compile("T\\((com\\.smartbox\\.investory(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+)\\)");

  @Test
  void applicationTypeLiteralsResolveFromEveryTemplate() throws IOException {
    Path templates = Path.of("src", "main", "resources", "templates");
    try (Stream<Path> files = Files.walk(templates)) {
      files
          .filter(path -> path.toString().endsWith(".html"))
          .forEach(this::assertApplicationTypeLiteralsResolve);
    }
  }

  private void assertApplicationTypeLiteralsResolve(Path template) {
    String source;
    try {
      source = Files.readString(template);
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot read template " + template, exception);
    }

    Matcher matcher = APPLICATION_TYPE_LITERAL.matcher(source);
    while (matcher.find()) {
      String className = matcher.group(1);
      assertThatCode(
              () ->
                  Class.forName(
                      className, false, ThymeleafClassReferenceTest.class.getClassLoader()))
          .as("template %s references %s", template, className)
          .doesNotThrowAnyException();
    }
  }
}
