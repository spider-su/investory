package com.example.demo.services;

import com.example.demo.data.CurrencyType;
import com.example.demo.data.repository.OpenedPosition;
import com.example.demo.data.repository.OpenedPositionRepository;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileWriter;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class YahooExportService {

    private final CurrencyRateService currencyRateService;
    private final OpenedPositionRepository openedPositionRepository;

    private static final String[] HEADER = {
            "Symbol", "Current Price", "Date", "Time", "Change", "Open", "High", "Low", "Volume",
            "Trade Date", "Purchase Price", "Quantity", "Commission", "High Limit", "Low Limit", "Comment", "Transaction Type"
    };

    @Transactional(readOnly = true)
    public void exportToYahooCsv(String filePath) throws IOException {
        List<OpenedPosition> positions = openedPositionRepository.findAll();
        Map<String, Double> openedPricesByPosition = positions.stream()
                .collect(Collectors.groupingBy(
                        OpenedPosition::getSymbol,
                        Collectors.averagingDouble(position -> currencyRateService.convertToBaseCurrency(position.getOpenPrice(), CurrencyType.USD, position.getCurrency()))
                ));
        Map<String, Double> volumeByPosition = positions.stream()
                .collect(Collectors.groupingBy(
                        OpenedPosition::getSymbol,
                        Collectors.summingDouble(OpenedPosition::getVolume)
                ));
        Map<String, OpenedPosition> samplePosBySymbol = positions.stream()
                .collect(Collectors.toMap(
                        OpenedPosition::getSymbol,
                        pos -> pos,
                        (existing, replacement) -> existing // keep first
                ));

        try (CSVWriter writer = new CSVWriter(new FileWriter(filePath))) {
            writer.writeNext(HEADER);

            // Add cash deposit rows if needed
            writer.writeNext(new String[] {"$$CASH_TX", "", "", "", "", "", "", "", "", "20250101", "", "1.0", "", "", "", "", "DEPOSIT"});
            writer.writeNext(new String[] {"$$CASH_TX", "", "", "", "", "", "", "", "", "20241231", "", "1.0", "", "", "", "", "DEPOSIT"});

            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy/MM/dd");
            DateTimeFormatter tradeDateFmt = DateTimeFormatter.ofPattern("yyyyMMdd");
            DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm z");

            for (String symbol : openedPricesByPosition.keySet()) {
                String[] row = new String[17];
                row[0] = symbol;                              // Symbol
                row[1] = toString(samplePosBySymbol.get(symbol).getMarketPrice());                  // Current Price
                row[2] = "01-01-2025";            // Date
                row[3] = "15:31 CET";            // Time
                row[4] = "";                                            // Change
                row[5] = "";                                            // Open
                row[6] = "";                                            // High
                row[7] = "";                                            // Low
                row[8] = "";// Volume
                row[9] = "20250101";       // Trade Date
                row[10] = toString(openedPricesByPosition.get(symbol));                   // Purchase Price
                row[11] = toString(volumeByPosition.get(symbol));                      // Quantity
                row[12] = toString(samplePosBySymbol.get(symbol).getCommission());                  // Commission
                row[13] = "";                                           // High Limit
                row[14] = "";                                           // Low Limit
                row[15] = "";                            // Comment
                row[16] = samplePosBySymbol.get(symbol).getType().name();                        // Transaction Type (e.g., BUY/SELL)

                writer.writeNext(row);
            }
        }
    }

    private String toString(Double val) {
        return val == null ? "" : String.format("%.2f", val);
    }
}

