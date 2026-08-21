package com.smartbox.investory.longterm.api;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileAssetModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileSummaryModel;

import java.time.LocalDate;
import java.util.List;

/** Public Long-Term read boundary for retirement profile composition and simulation inputs. */
public interface LongTermAssetProfileReader {
  LongTermAssetProfileSummaryModel aggregate(Long portfolioId, LocalDate date);

  List<LongTermAssetProfileAssetModel> list(Long portfolioId, LocalDate date);

  List<LongTermAssetProjectionModel> projectionInputs(Long portfolioId, LocalDate date);
}
