package com.example.demo.services.dashboard;

import com.example.demo.infrastructure.repository.Asset;
import com.example.demo.infrastructure.repository.AssetPriceChartRepository;
import com.example.demo.infrastructure.repository.AssetRepository;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssetPriceChartService {

  private final AssetRepository assetRepository;
  private final AssetPriceChartRepository assetPriceChartRepository;

  public List<AssetPricePointView> findBySymbol(String rawSymbol, DashboardPeriod period) {
    String symbol = normalize(rawSymbol);
    Asset asset =
        assetRepository
            .findBySymbol(symbol)
            .orElseThrow(() -> new AssetDetailNotFoundException(symbol));
    ZonedDateTime now = ZonedDateTime.now();
    ZonedDateTime startDate = period.startDate(now);
    LocalDate dateFrom = startDate == null ? null : startDate.toLocalDate();
    return assetPriceChartRepository
        .findBestPrices(asset.getId(), dateFrom, now.toLocalDate())
        .stream()
        .map(
            row ->
                new AssetPricePointView(
                    row.getPriceDate(),
                    row.getClosePrice().doubleValue(),
                    row.getPriceCurrency(),
                    row.getSource(),
                    row.getQualityScore(),
                    row.getQualityClass(),
                    row.getPriceOrigin()))
        .toList();
  }

  private String normalize(String rawSymbol) {
    if (rawSymbol == null || rawSymbol.isBlank()) {
      throw new AssetDetailNotFoundException("");
    }
    return rawSymbol.trim().toUpperCase(Locale.ROOT);
  }
}
