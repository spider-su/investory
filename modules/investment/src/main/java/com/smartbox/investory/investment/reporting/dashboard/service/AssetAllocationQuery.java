package com.smartbox.investory.investment.reporting.dashboard.service;

import com.smartbox.investory.investment.accounting.model.Portfolio;
import com.smartbox.investory.investment.infrastructure.persistence.AssetEntity;
import com.smartbox.investory.investment.infrastructure.persistence.AssetRepository;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioAssetAllocationEntity;
import com.smartbox.investory.investment.infrastructure.persistence.portfolio.PortfolioAssetAllocationRepository;
import com.smartbox.investory.investment.reporting.dashboard.application.AssetAllocationView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Uses the existing valued-position projection and asset metadata for dashboard allocation. */
@Service
@Transactional(readOnly = true)
public class AssetAllocationQuery {
  private final PortfolioAssetAllocationRepository allocationRepository;
  private final AssetRepository assetRepository;

  public AssetAllocationQuery(
      PortfolioAssetAllocationRepository allocationRepository, AssetRepository assetRepository) {
    this.allocationRepository = allocationRepository;
    this.assetRepository = assetRepository;
  }

  public AssetAllocationView load(Long portfolioId, Portfolio portfolio) {
    List<PortfolioAssetAllocationEntity> rows =
        allocationRepository.findAllByPortfolioId(portfolioId);
    Map<Long, AssetEntity> assets =
        assetRepository
            .findAllById(rows.stream().map(PortfolioAssetAllocationEntity::getAssetId).toList())
            .stream()
            .collect(java.util.stream.Collectors.toMap(AssetEntity::getId, asset -> asset));
    Map<String, MutableBucket> buckets = new LinkedHashMap<>();
    double invested = 0.0;
    for (PortfolioAssetAllocationEntity row : rows) {
      double value = nz(row.getTotalValueInBaseCurrency());
      if (Math.abs(value) < 0.005) continue;
      String category = PortfolioAssetCategoryMapper.category(assets.get(row.getAssetId()));
      buckets
          .computeIfAbsent(category, ignored -> new MutableBucket())
          .add(value, row.getAssetSymbol());
      invested += value;
    }
    double cash = Math.max(0.0, portfolio.getCash());
    if (cash > 0.005)
      buckets.computeIfAbsent("Cash", ignored -> new MutableBucket()).add(cash, null);
    double total = invested + cash;
    List<AssetAllocationView.Bucket> result =
        buckets.entrySet().stream()
            .map(entry -> entry.getValue().view(entry.getKey(), total))
            .sorted(Comparator.comparing(AssetAllocationView.Bucket::value).reversed())
            .toList();
    return new AssetAllocationView(total, result);
  }

  private static double nz(Double value) {
    return value == null ? 0.0 : value;
  }

  private static final class MutableBucket {
    private double value;
    private final List<String> symbols = new ArrayList<>();

    void add(double amount, String symbol) {
      value += amount;
      if (symbol != null && !symbol.isBlank() && !symbols.contains(symbol)) symbols.add(symbol);
    }

    AssetAllocationView.Bucket view(String name, double total) {
      return new AssetAllocationView.Bucket(
          name,
          value,
          total > 0.005 ? value / total * 100.0 : 0.0,
          symbols.stream().limit(5).toList());
    }
  }
}
