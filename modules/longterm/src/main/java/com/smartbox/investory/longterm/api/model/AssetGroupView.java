package com.smartbox.investory.longterm.api.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;

/** Public Long-Term API model. */
public record AssetGroupView(
    String key,
    String title,
    CurrencyType currency,
    List<AssetSummaryView> assets,
    BigDecimal totalValue,
    AnnualEconomicsView annualEconomics,
    RealEstateGroupPlanningView realEstatePlanning) {}
