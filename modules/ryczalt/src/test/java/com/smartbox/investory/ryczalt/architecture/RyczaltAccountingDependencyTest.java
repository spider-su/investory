package com.smartbox.investory.ryczalt.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class RyczaltAccountingDependencyTest {
  @Test
  void productionSourceDoesNotDependOnLegacyAccountingApi() throws IOException {
    Path sourceRoot = Path.of(System.getProperty("user.dir"), "src/main/java");
    try (Stream<Path> files = Files.walk(sourceRoot)) {
      files
          .filter(path -> path.toString().endsWith(".java"))
          .forEach(this::assertNoLegacyReference);
    }
  }

  @Test
  void bankAndKsefAcquisitionDoNotDependOnAccounting() throws IOException {
    Path java = Path.of(System.getProperty("user.dir"), "src/main/java");
    for (String pkg :
        new String[] {
          "com/smartbox/investory/ryczalt/integration/bank",
          "com/smartbox/investory/ryczalt/integration/ksef",
          "com/smartbox/investory/ryczalt/application/bank",
          "com/smartbox/investory/ryczalt/application/ksef"
        }) {
      Path root = java.resolve(pkg);
      if (!Files.exists(root)) continue;
      try (Stream<Path> files = Files.walk(root)) {
        files
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(this::assertNoLegacyReference);
      }
    }
  }

  private void assertNoLegacyReference(Path file) {
    try {
      String source = Files.readString(file);
      assertFalse(source.contains("com.smartbox.investory.accounting"), file.toString());
      assertFalse(source.contains("AccountingUserApi"), file.toString());
      assertFalse(source.contains("LegacyAccountingUserApiAdapter"), file.toString());
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot read " + file, exception);
    }
  }
}
