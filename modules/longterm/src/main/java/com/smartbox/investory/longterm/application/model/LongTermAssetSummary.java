package com.smartbox.investory.longterm.application.model;

import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Common asset facts plus normalized annual economics and type-specific planning facts. */
public record LongTermAssetSummary(
    Long id,
    String name,
    LongTermAssetType type,
    CurrencyType currency,
    BigDecimal currentValue,
    LocalDate maturityDate,
    BigDecimal currentAnnualRate,
    AnnualEconomics annualEconomics,
    RealEstatePlanningSummary realEstatePlanning,
    BondPlanningSummary bondPlanning,
    LocalDate rentEnd) {}
