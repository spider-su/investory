package com.smartbox.investory.accounting;

import java.util.List;

public record ExtractionResult(
    AccountingDocumentCandidate candidate,
    ExtractorType extractorType,
    ExtractionOutcome outcome,
    List<String> warnings) {}
