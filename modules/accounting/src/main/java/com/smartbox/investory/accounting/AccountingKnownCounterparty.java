package com.smartbox.investory.accounting;

/** Canonical counterparty identity used to stabilize reviewed document imports. */
public record AccountingKnownCounterparty(
    String taxIdentifier, String country, String canonicalName) {}
