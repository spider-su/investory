package com.smartbox.investory.accounting;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Common extraction boundary. Legacy upload callers remain on the recognition facade. */
@Service
@RequiredArgsConstructor
public class AccountingDocumentExtractionService {
  private final List<AccountingDocumentExtractor> extractors;
  private final InvoiceValidator validator;

  public ExtractionResult extract(AccountingSourceDocument source) {
    return extractors.stream()
        .filter(extractor -> extractor.supports(source))
        .findFirst()
        .map(extractor -> validate(extractor.extract(source)))
        .orElse(
            new ExtractionResult(
                null, null, ExtractionOutcome.FAILED, List.of("unsupported document")));
  }

  private ExtractionResult validate(ExtractionResult result) {
    if (result.candidate() == null) return result;
    List<String> issues = validator.validate(result.candidate());
    if (!issues.isEmpty())
      return new ExtractionResult(
          result.candidate(), result.extractorType(), ExtractionOutcome.REVIEW_REQUIRED, issues);
    return result;
  }
}
