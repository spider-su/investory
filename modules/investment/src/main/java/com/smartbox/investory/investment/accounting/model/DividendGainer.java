package com.smartbox.investory.investment.accounting.model;

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
