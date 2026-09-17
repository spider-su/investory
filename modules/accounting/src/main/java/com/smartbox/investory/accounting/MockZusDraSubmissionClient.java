package com.smartbox.investory.accounting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/**
 * Local stand-in for the future signed ZUS/EWD client.
 *
 * <p>It validates and renders the declaration, then stores the unsigned KEDU and metadata under a
 * caller-owned directory. It never contacts ZUS and its status is deliberately {@code MOCK_STORED},
 * not submitted or accepted.
 */
public final class MockZusDraSubmissionClient implements ZusDraSubmissionClient {
  private static final String ID_PATTERN = "[A-Za-z0-9-]+";

  private final Path root;
  private final ZusDraKeduRenderer renderer;
  private final ZusDraKeduXmlValidator xmlValidator;

  public MockZusDraSubmissionClient(Path root) {
    this(root, new ZusDraKeduRenderer(), new ZusDraKeduXmlValidator());
  }

  MockZusDraSubmissionClient(
      Path root, ZusDraKeduRenderer renderer, ZusDraKeduXmlValidator xmlValidator) {
    this.root = root;
    this.renderer = renderer;
    this.xmlValidator = xmlValidator;
  }

  @Override
  public ZusDraSubmission submit(ZusDraDeclaration declaration) {
    byte[] payload = renderer.render(declaration);
    xmlValidator.validate(payload);
    String id = UUID.randomUUID().toString();
    Instant createdAt = Instant.now();
    Path directory = root.resolve(id);
    Path payloadFile = directory.resolve("submission.kedu.xml");
    try {
      Files.createDirectories(directory);
      Path temporaryPayload = directory.resolve("submission.kedu.xml.tmp");
      Files.write(temporaryPayload, payload);
      Files.move(temporaryPayload, payloadFile, StandardCopyOption.ATOMIC_MOVE);
      Properties metadata = new Properties();
      metadata.setProperty("id", id);
      metadata.setProperty("period", declaration.period().toString());
      metadata.setProperty("createdAt", createdAt.toString());
      metadata.setProperty("payloadHash", AccountingFilingFingerprint.sha256(payload));
      metadata.setProperty("status", ZusDraSubmission.Status.MOCK_STORED.name());
      try (var output = Files.newOutputStream(directory.resolve("submission.properties"))) {
        metadata.store(output, "Investory mock ZUS DRA submission; not submitted to ZUS");
      }
      return new ZusDraSubmission(
          id,
          declaration.period(),
          payload,
          metadata.getProperty("payloadHash"),
          createdAt,
          payloadFile,
          ZusDraSubmission.Status.MOCK_STORED);
    } catch (IOException exception) {
      throw new IllegalStateException("ZUS_DRA_MOCK_SUBMISSION_WRITE_FAILED", exception);
    }
  }

  @Override
  public Optional<ZusDraSubmission> read(String submissionId) {
    if (submissionId == null || !submissionId.matches(ID_PATTERN)) return Optional.empty();
    Path directory = root.resolve(submissionId);
    Path payloadFile = directory.resolve("submission.kedu.xml");
    Path metadataFile = directory.resolve("submission.properties");
    if (!Files.isRegularFile(payloadFile) || !Files.isRegularFile(metadataFile))
      return Optional.empty();
    try (var input = Files.newInputStream(metadataFile)) {
      Properties metadata = new Properties();
      metadata.load(input);
      byte[] payload = Files.readAllBytes(payloadFile);
      return Optional.of(
          new ZusDraSubmission(
              metadata.getProperty("id"),
              java.time.LocalDate.parse(metadata.getProperty("period")),
              payload,
              metadata.getProperty("payloadHash"),
              Instant.parse(metadata.getProperty("createdAt")),
              payloadFile,
              ZusDraSubmission.Status.valueOf(metadata.getProperty("status"))));
    } catch (IOException | RuntimeException exception) {
      throw new IllegalStateException("ZUS_DRA_MOCK_SUBMISSION_READ_FAILED", exception);
    }
  }
}
