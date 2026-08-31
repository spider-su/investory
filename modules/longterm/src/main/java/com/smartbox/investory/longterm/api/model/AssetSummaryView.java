package com.smartbox.investory.longterm.api.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Public Long-Term API model. */
public record AssetSummaryView(
    Long id,
    String name,
    LongTermAssetType type,
    CurrencyType currency,
    BigDecimal currentValue,
    LocalDate maturityDate,
    BigDecimal currentAnnualRate,
    AnnualEconomicsView annualEconomics,
    RealEstatePlanningView realEstatePlanning,
    BondPlanningView bondPlanning,
    LocalDate rentEnd) {}
