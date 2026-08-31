package com.smartbox.investory.longterm.api.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Public Long-Term API model. */
public record AssetPatchCommand(
    Long portfolioId,
    Long id,
    String name,
    LongTermAssetType type,
    CurrencyType currency,
    LocalDate acquisitionDate,
    BigDecimal acquisitionValue,
    BigDecimal currentValue,
    BigDecimal taxBase,
    Boolean active,
    String notes,
    Boolean rentalTaxPaidByTenant) {}
