package com.smartbox.investory.accounting.service;

import com.smartbox.investory.accounting.*;
import com.smartbox.investory.accounting.infrastructure.persistence.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Common extraction boundary. Legacy upload callers remain on the recognition facade. */
@Service
@RequiredArgsConstructor
public class AccountingDocumentExtractionService {
  private final List<AccountingDocumentExtractor> extractors;
  private final InvoiceValidator validator;
  private final AccountingFactService factService;

  public ExtractionResult extract(long profileId, AccountingSourceDocument source) {
    return extractors.stream()
        .filter(extractor -> extractor.supports(source))
        .findFirst()
        .map(extractor -> validate(profileId, extractor.extract(profileId, source)))
        .orElse(
            new ExtractionResult(
                null, null, ExtractionOutcome.FAILED, List.of("unsupported document")));
  }

  private ExtractionResult validate(long profileId, ExtractionResult result) {
    if (result.candidate() == null) return result;
    AccountingDocumentCandidate candidate =
        validator.resolveDirection(
            result.candidate(), factService.accountingProfile(profileId).nip());
    List<String> issues = validator.validate(candidate);
    if (!issues.isEmpty())
      return new ExtractionResult(
          candidate, result.extractorType(), ExtractionOutcome.REVIEW_REQUIRED, issues);
    return new ExtractionResult(
        candidate, result.extractorType(), ExtractionOutcome.ACCEPTED, List.of());
  }
}
