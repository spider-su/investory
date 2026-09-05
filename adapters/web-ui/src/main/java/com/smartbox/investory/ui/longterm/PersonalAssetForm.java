package com.smartbox.investory.ui.longterm;

import com.smartbox.investory.longterm.api.model.PersonalAssetCategory;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PersonalAssetForm {
  private Long id;
  private String name;
  private PersonalAssetCategory category;
  private CurrencyType currency;
  private BigDecimal value;
  private LocalDate acquisitionDate;
  private String notes;
}
