package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshot;
import com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshotReader;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/** Reads historical long-term-asset facts from the canonical Long-term Assets calculation. */
@Service
public class HistoricalLongTermAssetYearSource {
  private final LongTermAssetAnnualSnapshotReader longTermAssets;

  public HistoricalLongTermAssetYearSource(LongTermAssetAnnualSnapshotReader longTermAssets) {
    this.longTermAssets = longTermAssets;
  }

  public HistoricalLongTermAssetYear read(Long portfolioId, int year) {
    LongTermAssetAnnualSnapshot snapshot =
        longTermAssets.historicalAnnualSnapshot(portfolioId, year);
    return new HistoricalLongTermAssetYear(
        snapshot.rentalIncomeAvailable(),
        snapshot.rentalIncome(),
        snapshot.realEstateValueAvailable(),
        snapshot.realEstateValue(),
        snapshot.bondValueAvailable(),
        snapshot.bondValue(),
        snapshot.bondIncomeAvailable(),
        snapshot.bondIncome(),
        snapshot.cashReserveValueAvailable(),
        snapshot.cashReserveValue());
  }

  public record HistoricalLongTermAssetYear(
      boolean rentalIncomeAvailable,
      BigDecimal rentalIncome,
      boolean realEstateValueAvailable,
      BigDecimal realEstateValue,
      boolean bondValueAvailable,
      BigDecimal bondValue,
      boolean bondIncomeAvailable,
      BigDecimal bondIncome,
      boolean cashReserveValueAvailable,
      BigDecimal cashReserveValue) {
    /** Compatibility constructor for callers that only supplied rental facts. */
    public HistoricalLongTermAssetYear(boolean available, BigDecimal rentalIncome) {
      this(available, rentalIncome, false, null, false, null, false, null, false, null);
    }

    static HistoricalLongTermAssetYear unavailable() {
      return new HistoricalLongTermAssetYear(false, null);
    }
  }
}
