package com.smartbox.investory.longterm.api;

import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import java.time.LocalDate;
import java.util.List;

/** Long-Term facts needed to build retirement planning inputs. */
public interface LongTermAssetProjectionReader {
  List<LongTermAssetProjectionModel> projectionInputs(Long portfolioId, LocalDate date);
}
