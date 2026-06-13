package com.example.demo.services.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FundamentalIndicator {
    private String symbol;
    private Double eps;
    private Double peRatio;
    private Double dividendYield;
    private ZonedDateTime lastUpdated;
}
