package com.smartbox.investory.longterm.web;

import com.smartbox.investory.longterm.api.LongTermAssetReadApi;
import com.smartbox.investory.longterm.api.LongTermAssetWriteApi;
import com.smartbox.investory.longterm.api.LongTermAssetsApi;
import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.web.LongTermAssetRestContracts.AssetPatchRequest;
import com.smartbox.investory.longterm.web.LongTermAssetRestContracts.AssetRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP boundary for the Long-Term module. In-process consumers use {@link LongTermAssetsApi}. */
@RestController
@Validated
@RequestMapping("/api/v1/portfolios/{portfolioId}/long-term-assets")
public class LongTermAssetsRestController {
  private final LongTermAssetReadApi reads;
  private final LongTermAssetWriteApi writes;

  @Autowired
  public LongTermAssetsRestController(
      @Qualifier("longTermAssetsApplicationService") LongTermAssetReadApi reads,
      @Qualifier("longTermAssetsApplicationService") LongTermAssetWriteApi writes) {
    this.reads = reads;
    this.writes = writes;
  }

  LongTermAssetsRestController(LongTermAssetsApi assets) {
    this(assets, assets);
  }

  @GetMapping
  public PageSnapshot page(
      @PathVariable @Positive Long portfolioId, @RequestParam @NotNull LocalDate date) {
    return reads.page(portfolioId, date);
  }

  @GetMapping("/active")
  public List<AssetSummaryView> list(
      @PathVariable @Positive Long portfolioId, @RequestParam @NotNull LocalDate date) {
    return reads.list(portfolioId, date);
  }

  @GetMapping("/archived")
  public List<AssetSummaryView> archived(
      @PathVariable @Positive Long portfolioId, @RequestParam @NotNull LocalDate date) {
    return reads.archived(portfolioId, date);
  }

  @GetMapping("/groups")
  public List<AssetGroupView> grouped(
      @PathVariable @Positive Long portfolioId, @RequestParam @NotNull LocalDate date) {
    return reads.grouped(portfolioId, date);
  }

  @GetMapping("/aggregate")
  public AggregateView aggregate(
      @PathVariable @Positive Long portfolioId, @RequestParam @NotNull LocalDate date) {
    return reads.aggregate(portfolioId, date);
  }

  @GetMapping("/{id}")
  public AssetView asset(
      @PathVariable @Positive Long portfolioId, @PathVariable @Positive Long id) {
    return reads.asset(portfolioId, id);
  }

  @GetMapping("/{id}/rental-contracts/{contractId}")
  public LongTermAssetDetailResponse.RentalContractResponse rentalContract(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @PathVariable @Positive Long contractId) {
    return LongTermAssetDetailResponse.RentalContractResponse.from(
        reads.rentalContract(portfolioId, id, contractId));
  }

  @GetMapping("/{id}/valuations/{periodId}")
  public ValuationView valuation(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @PathVariable @Positive Long periodId) {
    return reads.valuation(portfolioId, id, periodId);
  }

  @GetMapping("/rental-tax-policies/{policyId}")
  public RentalTaxView rentalTaxPolicy(
      @PathVariable @Positive Long portfolioId, @PathVariable @Positive Long policyId) {
    return reads.rentalTaxPolicy(portfolioId, policyId);
  }

  @GetMapping("/{id}/details")
  public LongTermAssetDetailResponse details(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @RequestParam @NotNull LocalDate date) {
    return LongTermAssetDetailResponse.from(reads.details(portfolioId, id, date));
  }

  @PostMapping
  public ResponseEntity<AssetView> create(
      @PathVariable @Positive Long portfolioId, @Valid @RequestBody AssetRequest request) {
    return created(portfolioId, writes.create(request.toCommand(portfolioId, null)));
  }

  @PatchMapping("/{id}")
  public AssetView update(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @Valid @RequestBody AssetPatchRequest request) {
    return writes.patch(request.toCommand(portfolioId, id));
  }

  @PostMapping("/{id}/archive")
  public AssetView archive(
      @PathVariable @Positive Long portfolioId, @PathVariable @Positive Long id) {
    writes.archive(portfolioId, id);
    return reads.asset(portfolioId, id);
  }

  @PostMapping("/{id}/reactivate")
  public AssetView reactivate(
      @PathVariable @Positive Long portfolioId, @PathVariable @Positive Long id) {
    writes.reactivate(portfolioId, id);
    return reads.asset(portfolioId, id);
  }

  private static ResponseEntity<AssetView> created(Long portfolioId, AssetView asset) {
    return ResponseEntity.created(
            URI.create(
                "/api/v1/portfolios/%d/long-term-assets/%d".formatted(portfolioId, asset.id())))
        .body(asset);
  }
}
