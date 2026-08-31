package com.smartbox.investory.longterm.api.model;

import java.math.BigDecimal;
import java.util.List;

/** Public Long-Term API model. */
public record DetailView(
    AssetView asset,
    AssetSummaryView summary,
    BondDetailsView bondDetails,
    DepositDetailsView depositDetails,
    List<ValuationView> valuationPeriods,
    BigDecimal expectedPropertyGrowth,
    List<RentalContractView> contracts) {}
