package com.example.demo.services.indicators;

import com.example.demo.infrastructure.repository.*;
import com.example.demo.clients.TwelveDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class TechnicalService {
    private final TwelveDataService twelveDataService;

    private final StockRepository stockRepository;
    private final TechnicalIndicatorsRepository technicalIndicatorsRepository;

    public void createTechnicalsFromStock() {
        ZonedDateTime now = ZonedDateTime.now();
        Map<String, TechnicalIndicator> technicals = stockRepository.findAll().stream()
                .collect(Collectors.toMap(Stock::getSymbol,
                        s -> TechnicalIndicator.builder().symbol(s.getSymbol()).syncDate(now).timestamp(now).build()));
        technicalIndicatorsRepository.saveAll(technicals.values());

        updateTechnicals();
    }

    public void updateTechnicals() {

    }

    public Map<String, TechnicalIndicator> getTechnicalBySymbol(String symbol) {
        return Map.of();
    }
}
