package com.smartbox.investory.investment.api.reporting.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DividendGainer {

  private String symbol;
  private double dividends;
}
