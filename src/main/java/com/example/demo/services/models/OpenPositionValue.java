package com.example.demo.services.models;

import com.example.demo.infrastructure.CurrencyType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenPositionValue {

  private String symbol;
  private double volume;
  private double costBase;
  private double averageOpenPrice;
  private double value;
  private double unrealized;
  private Double profitLossPercent;
  private CurrencyType currency;
  private double sharePercent;
}
