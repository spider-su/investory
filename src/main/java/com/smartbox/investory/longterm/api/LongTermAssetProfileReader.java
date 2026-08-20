package com.smartbox.investory.longterm.api;

import java.time.LocalDate;
import java.util.List;

/** Public Long-Term read boundary for retirement profile composition and simulation inputs. */
public interface LongTermAssetProfileReader {
  LongTermAssetProfileSummary aggregate(Long portfolioId, LocalDate date);

  List<LongTermAssetProfileAsset> list(Long portfolioId, LocalDate date);

  List<LongTermAssetProjection> projectionInputs(Long portfolioId, LocalDate date);
}
