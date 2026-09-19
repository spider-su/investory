package com.smartbox.investory.ryczalt.application.ksef;

/**
 * Outcome of a native KSeF acquisition run.
 *
 * @param received source invoices discovered
 * @param imported new canonical invoices created
 * @param duplicates invoices already present (idempotent no-ops in sync mode)
 * @param updated invoices refreshed (reimport mode)
 * @param failed invoices that could not be normalized
 */
public record RyczaltKsefSyncResult(
    int received, int imported, int duplicates, int updated, int failed) {}
