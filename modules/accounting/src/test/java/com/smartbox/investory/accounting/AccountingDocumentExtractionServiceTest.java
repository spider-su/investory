package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.accounting.service.AccountingDocumentExtractionService;
import com.smartbox.investory.accounting.service.AccountingFactService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountingDocumentExtractionServiceTest {
  @Test
  void selectsSupportingAdapterAndReturnsAcceptedCandidate() {
    AccountingDocumentExtractor first = mock(AccountingDocumentExtractor.class);
    AccountingDocumentExtractor second = mock(AccountingDocumentExtractor.class);
    AccountingSourceDocument source =
        new AccountingSourceDocument("invoice.pdf", "application/pdf", new byte[] {1});
    AccountingDocumentCandidate candidate = candidate("SALES_INVOICE", "1234567890", "9999999999");
    when(first.supports(source)).thenReturn(false);
    when(second.supports(source)).thenReturn(true);
    when(second.extract(1L, source))
        .thenReturn(
            new ExtractionResult(
                candidate,
                ExtractorType.PDF_LAYOUT,
                ExtractionOutcome.REVIEW_REQUIRED,
                List.of("old warning")));
    AccountingFactService facts = mock(AccountingFactService.class);
    when(facts.accountingProfile(1L))
        .thenReturn(new AccountingProfile(true, "1234567890", null, null, null, null, null, null));

    ExtractionResult result =
        new AccountingDocumentExtractionService(
                List.of(first, second), new InvoiceValidator(), facts)
            .extract(1L, source);

    assertThat(result.outcome()).isEqualTo(ExtractionOutcome.ACCEPTED);
    assertThat(result.extractorType()).isEqualTo(ExtractorType.PDF_LAYOUT);
    assertThat(result.candidate().documentType()).isEqualTo("SALES_INVOICE");
    assertThat(result.warnings()).isEmpty();
  }

  @Test
  void mapsInvalidCandidateToReviewRequired() {
    AccountingDocumentExtractor extractor = mock(AccountingDocumentExtractor.class);
    AccountingSourceDocument source =
        new AccountingSourceDocument("invoice.pdf", "application/pdf", new byte[] {1});
    when(extractor.supports(source)).thenReturn(true);
    when(extractor.extract(1L, source))
        .thenReturn(
            new ExtractionResult(
                candidate("UNKNOWN", "1234567890", "9999999999"),
                ExtractorType.AI,
                ExtractionOutcome.ACCEPTED,
                List.of()));
    AccountingFactService facts = mock(AccountingFactService.class);
    when(facts.accountingProfile(1L))
        .thenReturn(new AccountingProfile(true, "1111111111", null, null, null, null, null, null));

    ExtractionResult result =
        new AccountingDocumentExtractionService(List.of(extractor), new InvoiceValidator(), facts)
            .extract(1L, source);

    assertThat(result.outcome()).isEqualTo(ExtractionOutcome.REVIEW_REQUIRED);
    assertThat(result.warnings()).contains("invoice direction requires review");
  }

  @Test
  void returnsFailedForUnsupportedSource() {
    AccountingFactService facts = mock(AccountingFactService.class);
    ExtractionResult result =
        new AccountingDocumentExtractionService(List.of(), new InvoiceValidator(), facts)
            .extract(
                1L,
                new AccountingSourceDocument(
                    "unknown.bin", "application/octet-stream", new byte[] {1}));

    assertThat(result.outcome()).isEqualTo(ExtractionOutcome.FAILED);
    assertThat(result.warnings()).containsExactly("unsupported document");
  }

  private AccountingDocumentCandidate candidate(String type, String sellerNip, String buyerNip) {
    return new AccountingDocumentCandidate(
        type,
        "FV/1",
        LocalDate.of(2026, 1, 1),
        null,
        null,
        "Seller",
        sellerNip,
        "Buyer",
        buyerNip,
        "PLN",
        new BigDecimal("100"),
        new BigDecimal("23"),
        new BigDecimal("123"),
        List.of(),
        "OTHER",
        List.of(),
        "test-v1");
  }
}
