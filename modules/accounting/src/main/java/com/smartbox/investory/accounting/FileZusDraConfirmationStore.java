package com.smartbox.investory.accounting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Persists the raw ZUS confirmation package and SOAP response for audit/review. */
public final class FileZusDraConfirmationStore {
  private final Path root;

  public FileZusDraConfirmationStore(Path root) {
    this.root = root;
  }

  public Path save(ZusDraConfirmation confirmation) {
    Path directory = root.resolve(confirmation.submissionId());
    try {
      Files.createDirectories(directory);
      Files.write(directory.resolve("confirmation.package"), confirmation.packagePayload());
      Files.write(directory.resolve("confirmation.soap.xml"), confirmation.rawSoapResponse());
      Properties metadata = new Properties();
      metadata.setProperty("submissionId", confirmation.submissionId());
      metadata.setProperty("taskId", value(confirmation.taskId()));
      metadata.setProperty("type", value(confirmation.type()));
      metadata.setProperty("packageHash", confirmation.packageHash());
      if (confirmation.writtenAt() != null)
        metadata.setProperty("writtenAt", confirmation.writtenAt().toString());
      metadata.setProperty("fetchedAt", confirmation.fetchedAt().toString());
      try (var output = Files.newOutputStream(directory.resolve("confirmation.properties"))) {
        metadata.store(output, "ZUS EWD confirmation; raw authority response");
      }
      return directory;
    } catch (IOException exception) {
      throw new IllegalStateException("ZUS_EWD_CONFIRMATION_STORE_FAILED", exception);
    }
  }

  private static String value(String value) {
    return value == null ? "" : value;
  }
}
