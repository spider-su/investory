package com.smartbox.investory.ui.longterm;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** Rental-tax policy HTML form. */
@Getter
@Setter
public final class RentalTaxForm {
  private LocalDate validFrom;
  private LocalDate validTo;
}
