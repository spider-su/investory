package com.smartbox.investory.longterm.web;

import com.smartbox.investory.longterm.api.LongTermAssetRateConversion;
import com.smartbox.investory.longterm.api.LongTermAssetReadApi;
import com.smartbox.investory.longterm.api.LongTermAssetWriteApi;
import com.smartbox.investory.longterm.api.LongTermAssetsApi;
import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.web.LongTermAssetRestContracts.DateRequest;
import com.smartbox.investory.longterm.web.LongTermAssetRestContracts.PropertyGrowthRequest;
import com.smartbox.investory.longterm.web.LongTermAssetRestContracts.RealEstateRequest;
import com.smartbox.investory.longterm.web.LongTermAssetRestContracts.RentalContractCreateRequest;
import com.smartbox.investory.longterm.web.LongTermAssetRestContracts.RentalContractUpdateRequest;
import com.smartbox.investory.longterm.web.LongTermAssetRestContracts.RentalTaxOwnershipRequest;
import com.smartbox.investory.longterm.web.LongTermAssetRestContracts.RentalTaxRequest;
import com.smartbox.investory.longterm.web.LongTermAssetRestContracts.TaxBaseRequest;
import com.smartbox.investory.longterm.web.LongTermAssetRestContracts.ValuationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Real-estate, rental-contract, valuation, and rental-tax HTTP commands. */
@RestController
@Validated
@RequestMapping("/api/v1/portfolios/{portfolioId}/long-term-assets")
class LongTermRealEstateRestController {
  private final LongTermAssetReadApi reads;
  private final LongTermAssetWriteApi writes;

  @Autowired
  LongTermRealEstateRestController(
      @Qualifier("longTermAssetsApplicationService") LongTermAssetReadApi reads,
      @Qualifier("longTermAssetsApplicationService") LongTermAssetWriteApi writes) {
    this.reads = reads;
    this.writes = writes;
  }

  LongTermRealEstateRestController(LongTermAssetsApi assets) {
    this(assets, assets);
  }

  private static ResponseEntity<AssetView> created(Long portfolioId, AssetView asset) {
    return ResponseEntity.created(
            URI.create(
                "/api/v1/portfolios/%d/long-term-assets/%d".formatted(portfolioId, asset.id())))
        .body(asset);
  }

  @PostMapping("/real-estate")
  public ResponseEntity<AssetView> createRealEstate(
      @PathVariable @Positive Long portfolioId, @Valid @RequestBody RealEstateRequest request) {
    return created(portfolioId, writes.saveRealEstate(portfolioId, request.toModel()));
  }

  @PutMapping("/{id}/real-estate")
  public AssetView updateRealEstate(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @Valid @RequestBody RealEstateRequest request) {
    return writes.updateRealEstate(portfolioId, id, request.toModel());
  }

  @PostMapping("/{id}/rental-contracts")
  public ResponseEntity<RentalContractView> createRentalContract(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @Valid @RequestBody RentalContractCreateRequest request) {
    RentalContractView created = writes.createRentalContract(request.toCommand(portfolioId, id));
    return ResponseEntity.created(
            URI.create(
                "/api/v1/portfolios/%d/long-term-assets/%d/rental-contracts/%d"
                    .formatted(portfolioId, id, created.id())))
        .body(created);
  }

  @PutMapping("/{id}/rental-contracts/{contractId}")
  public RentalContractView updateRentalContract(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @PathVariable @Positive Long contractId,
      @Valid @RequestBody RentalContractUpdateRequest request) {
    return writes.updateRentalContract(request.toCommand(portfolioId, id, contractId));
  }

  @DeleteMapping("/{id}/rental-contracts/{contractId}")
  public ResponseEntity<Void> deleteRentalContract(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @PathVariable @Positive Long contractId) {
    writes.deleteRentalContract(portfolioId, id, contractId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/rental-contracts/{contractId}/end")
  public RentalContractView endRentalContract(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @PathVariable @Positive Long contractId,
      @Valid @RequestBody DateRequest request) {
    return writes.endRentalContract(portfolioId, id, contractId, request.date());
  }

  @PostMapping("/{id}/rental-contracts/{contractId}/terminate")
  public ResponseEntity<Void> terminateRentalContract(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @PathVariable @Positive Long contractId,
      @Valid @RequestBody DateRequest request) {
    writes.terminateRentalContract(portfolioId, id, contractId, request.date());
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{id}/tax-base")
  public AssetView saveTaxBase(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @Valid @RequestBody TaxBaseRequest request) {
    writes.saveTaxBase(portfolioId, id, request.value());
    return reads.asset(portfolioId, id);
  }

  @PatchMapping("/{id}/rental-tax-ownership")
  public AssetView saveRentalTaxOwnership(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @Valid @RequestBody RentalTaxOwnershipRequest request) {
    writes.saveRentalTaxOwnership(portfolioId, id, request.paidByTenant());
    return reads.asset(portfolioId, id);
  }

  @PutMapping("/{id}/property-growth")
  public ResponseEntity<Void> savePropertyGrowth(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @Valid @RequestBody PropertyGrowthRequest request) {
    writes.savePropertyGrowth(
        portfolioId,
        id,
        LongTermAssetRateConversion.percentToRate(request.growthRatePercent()),
        request.from());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/valuations")
  public ResponseEntity<ValuationView> addValuation(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @Valid @RequestBody ValuationRequest request) {
    ValuationView created = writes.addValuation(portfolioId, id, request.toCommand());
    return ResponseEntity.created(
            URI.create(
                "/api/v1/portfolios/%d/long-term-assets/%d/valuations/%d"
                    .formatted(portfolioId, id, created.id())))
        .body(created);
  }

  @PutMapping("/{id}/valuations/{periodId}")
  public ResponseEntity<Void> updateValuation(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @PathVariable @Positive Long periodId,
      @Valid @RequestBody ValuationRequest request) {
    writes.updateValuation(portfolioId, id, periodId, request.toCommand());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{id}/valuations/{periodId}")
  public ResponseEntity<Void> deleteValuation(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @PathVariable @Positive Long periodId) {
    writes.deleteValuation(portfolioId, id, periodId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/rental-tax-policies")
  public ResponseEntity<RentalTaxView> saveRentalTaxPolicy(
      @PathVariable @Positive Long portfolioId, @Valid @RequestBody RentalTaxRequest request) {
    RentalTaxView created = writes.saveRentalTaxPolicy(portfolioId, request.toCommand());
    return ResponseEntity.created(
            URI.create(
                "/api/v1/portfolios/%d/long-term-assets/rental-tax-policies/%d"
                    .formatted(portfolioId, created.id())))
        .body(created);
  }

  @PutMapping("/rental-tax-policies/{policyId}")
  public ResponseEntity<Void> updateRentalTaxPolicy(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long policyId,
      @Valid @RequestBody RentalTaxRequest request) {
    writes.updateRentalTaxPolicy(portfolioId, policyId, request.toCommand());
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/rental-tax-policies/{policyId}")
  public ResponseEntity<Void> deleteRentalTaxPolicy(
      @PathVariable @Positive Long portfolioId, @PathVariable @Positive Long policyId) {
    writes.deleteRentalTaxPolicy(portfolioId, policyId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/rental-tax-policies")
  public List<RentalTaxView> rentalTaxPolicies(@PathVariable @Positive Long portfolioId) {
    return reads.rentalTaxPolicies(portfolioId);
  }
}
