package com.smartbox.investory.ryczalt.application.bank;

/**
 * Outcome of a native bank acquisition run.
 *
 * @param received source records parsed from the export
 * @param imported new canonical transactions created
 * @param duplicates source records already present (idempotent no-ops)
 */
public record RyczaltBankImportResult(int received, int imported, int duplicates) {}
