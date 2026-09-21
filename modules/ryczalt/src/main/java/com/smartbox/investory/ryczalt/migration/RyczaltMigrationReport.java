package com.smartbox.investory.ryczalt.migration;

public record RyczaltMigrationReport(
    int periods,
    int incomeInvoices,
    int costInvoices,
    int transactions,
    int obligations,
    int sourceReferences) {}
