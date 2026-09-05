package com.smartbox.investory.investment.reporting.dashboard.service;

import com.smartbox.investory.investment.api.asset.model.AssetPricePointView;
import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetEntity;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetRepository;
import com.smartbox.investory.investment.valuation.price.persistence.AssetPriceChartRepository;
import com.smartbox.investory.shared.time.ApplicationTime;
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
  private final ApplicationTime applicationTime;

  public List<AssetPricePointView> findBySymbol(
      Long portfolioId, String rawSymbol, DashboardPeriod period) {
    if (portfolioId == null || portfolioId <= 0) {
      throw new IllegalArgumentException("portfolioId must be positive");
    }
    String symbol = normalize(rawSymbol);
    AssetEntity asset =
        assetRepository
            .findBySymbol(symbol)
            .orElseThrow(() -> new AssetDetailNotFoundException(symbol));
    ZonedDateTime now = applicationTime.now(applicationTime.businessZone());
    ZonedDateTime startDate = period.startDate(now);
    LocalDate dateFrom = startDate == null ? null : startDate.toLocalDate();
    return assetPriceChartRepository
        .findBestPrices(asset.getId(), dateFrom, now.toLocalDate())
        .stream()
        .map(
            row ->
                new AssetPricePointView(
                    row.getPriceDate(),
                    row.getClosePrice(),
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
