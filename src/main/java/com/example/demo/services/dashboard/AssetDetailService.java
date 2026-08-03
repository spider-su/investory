package com.example.demo.services.dashboard;

import com.example.demo.infrastructure.repository.Asset;
import com.example.demo.infrastructure.repository.AssetRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssetDetailService {

  private final AssetRepository assetRepository;

  public AssetDetailView findBySymbol(String rawSymbol) {
    String symbol = normalize(rawSymbol);
    Asset asset =
        assetRepository
            .findBySymbol(symbol)
            .orElseThrow(() -> new AssetDetailNotFoundException(symbol));
    return toView(asset);
  }

  private AssetDetailView toView(Asset asset) {
    return new AssetDetailView(
        asset.getId(),
        asset.getSymbol(),
        asset.getName(),
        asset.getTicker(),
        asset.getYahoo(),
        asset.getAssetType(),
        asset.getCountry(),
        asset.getCurrency(),
        asset.getMarketPrice(),
        asset.getMarketPriceUsd(),
        asset.getPriceSource(),
        asset.getPriceUpdatedAt());
  }

  private String normalize(String rawSymbol) {
    if (rawSymbol == null || rawSymbol.isBlank()) {
      throw new AssetDetailNotFoundException("");
    }
    return rawSymbol.trim().toUpperCase(Locale.ROOT);
  }
}
