package com.example.demo.services.imports.yahoo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class YahooFinanceService {
    public double fetchPERatioFromApi(String symbol) {
        String endpoint = "https://query1.finance.yahoo.com/v10/finance/quoteSummary/" + symbol + "?modules=defaultKeyStatistics";

        try {
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new RuntimeException("Failed to get response from Yahoo Finance API: " + responseCode);
            }

            // Read response into a string
            Scanner scanner = new Scanner(url.openStream());
            StringBuilder response = new StringBuilder();
            while (scanner.hasNext()) {
                response.append(scanner.nextLine());
            }
            scanner.close();

            // Parse JSON
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.toString());

            JsonNode peNode = root
                    .path("quoteSummary")
                    .path("result").get(0)
                    .path("defaultKeyStatistics")
                    .path("trailingPE")
                    .path("raw");

            return peNode.isMissingNode() ? -1.0 : peNode.asDouble();

        } catch (Exception e) {
            throw new RuntimeException("Error fetching P/E ratio for " + symbol, e);
        }
    }
}
