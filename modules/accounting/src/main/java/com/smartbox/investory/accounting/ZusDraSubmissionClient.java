package com.smartbox.investory.accounting;

import java.util.Optional;

/** Boundary for writing and reading ZUS DRA submissions. Implementations may be real or mocked. */
public interface ZusDraSubmissionClient {
  ZusDraSubmission submit(ZusDraDeclaration declaration);

  Optional<ZusDraSubmission> read(String submissionId);
}
