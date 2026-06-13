
package com.example.demo.services.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class OpenPositionsPerformanceItem {
    String symbol;

    double dayOpen = 0.0;
    double dayOpenPercents = 0.0;

    double dayClosed = 0.0;
    double dayClosedPercents = 0.0;
}
