package com.smartbox.investory.longterm.api.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Public Long-Term API model. */
public record AssetCommand(
    Long portfolioId,
    Long id,
    String name,
    LongTermAssetType type,
    CurrencyType currency,
    LocalDate acquisitionDate,
    BigDecimal acquisitionValue,
    BigDecimal currentValue,
    BigDecimal taxBase,
    boolean active,
    String notes,
    boolean rentalTaxPaidByTenant) {}
