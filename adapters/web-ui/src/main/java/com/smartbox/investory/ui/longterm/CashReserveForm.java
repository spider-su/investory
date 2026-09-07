package com.smartbox.investory.ui.longterm;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CashReserveForm {
  private Long id;
  private String name;
  private CurrencyType currency;
  private BigDecimal value;
  private LocalDate acquisitionDate;
  private BigDecimal interestRate = BigDecimal.ZERO;
  private LocalDate maturityDate;
  private String notes;
}
