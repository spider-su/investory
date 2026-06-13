package com.example.demo.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "exchangeRateClient", url = "https://api.exchangerate.host")
public interface ExchangeRateClient {

    @GetMapping("/live")
    ExchangeRateResponse getLatestRates(@RequestParam("source") String source,
                                        @RequestParam("currencies") String currencies,
                                        @RequestParam("access_key") String apiKey);

    class ExchangeRateResponse {
        private Map<String, Double> quotes;

        public Map<String, Double> getQuotes() {
            return quotes;
        }

        public void setQuotes(Map<String, Double> quotes) {
            this.quotes = quotes;
        }
    }
}
