package com.smartbox.investory.ryczalt.domain;

/** Facts available to rule matching. Missing facts never become a fuzzy match. */
public record InvoiceCandidate(
    long counterpartyId, String sourceType, String documentType, String serviceKey) {}
