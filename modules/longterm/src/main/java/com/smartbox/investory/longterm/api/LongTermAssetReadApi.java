package com.smartbox.investory.longterm.api;

import com.smartbox.investory.longterm.api.model.*;
import java.time.LocalDate;
import java.util.List;

/** Read-only Long-Term Assets capability for consumers that do not mutate assets. */
public interface LongTermAssetReadApi {
  List<AssetSummaryView> list(Long portfolioId, LocalDate date);

  List<AssetSummaryView> archived(Long portfolioId, LocalDate date);

  PageSnapshot page(Long portfolioId, LocalDate date);

  List<AssetGroupView> grouped(Long portfolioId, LocalDate date);

  AggregateView aggregate(Long portfolioId, LocalDate date);

  AssetView asset(Long portfolioId, Long id);

  RentalContractView rentalContract(Long portfolioId, Long assetId, Long contractId);

  ValuationView valuation(Long portfolioId, Long assetId, Long periodId);

  RentalTaxView rentalTaxPolicy(Long portfolioId, Long policyId);

  DetailView details(Long portfolioId, Long id, LocalDate date);

  List<RentalTaxView> rentalTaxPolicies(Long portfolioId);
}
