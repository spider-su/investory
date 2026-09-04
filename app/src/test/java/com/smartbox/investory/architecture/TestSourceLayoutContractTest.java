package com.smartbox.investory.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Test Source Layout Contract")
class TestSourceLayoutContractTest {

  private static final Path TEST_SOURCE_ROOT = Path.of("src", "test", "java");
  private static final Pattern PACKAGE_PATTERN =
      Pattern.compile("(?m)^\\s*package\\s+([a-zA-Z0-9_.]+)\\s*;");

  @DisplayName("test Sources Keep Package Path In Sync And Use Project Namespace")
  @Test
  void testSourcesKeepPackagePathInSyncAndUseProjectNamespace() throws IOException {
    assertTrue(Files.exists(TEST_SOURCE_ROOT), "Expected app test source root to exist");

    try (Stream<Path> files = Files.walk(TEST_SOURCE_ROOT)) {
      List<Path> javaFiles = files.filter(path -> path.toString().endsWith(".java")).toList();
      assertFalse(javaFiles.isEmpty(), "Expected Java test sources");

      for (Path file : javaFiles) {
        String source = Files.readString(file);
        Matcher matcher = PACKAGE_PATTERN.matcher(source);
        assertTrue(matcher.find(), () -> "Missing package declaration in " + file);
        String packageName = matcher.group(1);
        assertFalse(
            packageName.startsWith("com.it"),
            () -> "Legacy package namespace is not allowed: " + file + " -> " + packageName);
      }

      Path legacyNamespaceFolder = TEST_SOURCE_ROOT.resolve(Path.of("com", "it"));
      boolean legacyFolderHasJavaFiles = false;
      if (Files.exists(legacyNamespaceFolder)) {
        try (Stream<Path> legacyFiles = Files.walk(legacyNamespaceFolder)) {
          legacyFolderHasJavaFiles =
              legacyFiles.anyMatch(path -> path.toString().endsWith(".java"));
        }
      }
      assertFalse(
          legacyFolderHasJavaFiles,
          () ->
              "Legacy test namespace folder still contains Java sources: " + legacyNamespaceFolder);
    }
  }
}
