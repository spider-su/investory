package com.smartbox.investory.longterm.application.model;

import java.math.BigDecimal;

public record LongTermAssetBootstrapResult(
    int assetsToCreate,
    int assetsToUpdate,
    int taxPoliciesToCreate,
    int taxPoliciesToUpdate,
    boolean dryRun,
    BigDecimal propertyValue,
    BigDecimal grossAnnualIncome,
    BigDecimal operatingExpenses,
    BigDecimal rentalTax,
    BigDecimal netAnnualIncome) {}
