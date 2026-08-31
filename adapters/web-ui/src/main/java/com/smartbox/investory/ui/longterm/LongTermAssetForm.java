package com.smartbox.investory.ui.longterm;

import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** Generic long-term asset HTML form. */
@Getter
@Setter
public final class LongTermAssetForm {
  private Long id;
  private Long portfolioId;
  private String name;
  private String notes;
  private LongTermAssetType type;
  private CurrencyType currency;
  private LocalDate acquisitionDate;
  private BigDecimal acquisitionValue;
  private BigDecimal currentValue;
  private BigDecimal taxBase;
  private boolean active;

  AssetCommand command(Long portfolioId) {
    return command(portfolioId, id, null);
  }

  AssetCommand command(Long portfolioId, Long assetId, BigDecimal tax) {
    return new AssetCommand(
        portfolioId,
        assetId,
        name,
        type,
        currency,
        acquisitionDate,
        acquisitionValue,
        currentValue,
        tax == null ? taxBase : tax,
        active,
        notes,
        false);
  }
}
