package com.smartbox.investory.longterm.web;

import com.smartbox.investory.longterm.api.LongTermAssetReadApi;
import com.smartbox.investory.longterm.api.LongTermAssetWriteApi;
import com.smartbox.investory.longterm.api.LongTermAssetsApi;
import com.smartbox.investory.longterm.api.model.*;
import com.smartbox.investory.longterm.web.LongTermAssetRestContracts.BondDetailsRequest;
import com.smartbox.investory.longterm.web.LongTermAssetRestContracts.BondRequest;
import com.smartbox.investory.longterm.web.LongTermAssetRestContracts.CashReserveRequest;
import com.smartbox.investory.longterm.web.LongTermAssetRestContracts.DepositDetailsRequest;
import com.smartbox.investory.longterm.web.LongTermAssetRestContracts.DepositRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cash-reserve, bond, and deposit HTTP commands. */
@RestController
@Validated
@RequestMapping("/api/v1/portfolios/{portfolioId}/long-term-assets")
class LongTermInstrumentRestController {
  private final LongTermAssetReadApi reads;
  private final LongTermAssetWriteApi writes;

  @Autowired
  LongTermInstrumentRestController(
      @Qualifier("longTermAssetsApplicationService") LongTermAssetReadApi reads,
      @Qualifier("longTermAssetsApplicationService") LongTermAssetWriteApi writes) {
    this.reads = reads;
    this.writes = writes;
  }

  LongTermInstrumentRestController(LongTermAssetsApi assets) {
    this(assets, assets);
  }

  @PostMapping("/cash-reserves")
  public ResponseEntity<AssetView> createCashReserve(
      @PathVariable @Positive Long portfolioId, @Valid @RequestBody CashReserveRequest request) {
    return created(
        portfolioId,
        writes.saveCashReserve(request.toCommand(portfolioId, null), request.effectiveFrom()));
  }

  @PutMapping("/{id}/cash-reserve")
  public AssetView updateCashReserve(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @Valid @RequestBody CashReserveRequest request) {
    return writes.saveCashReserve(request.toCommand(portfolioId, id), request.effectiveFrom());
  }

  @PostMapping("/bonds")
  public ResponseEntity<AssetView> createBond(
      @PathVariable @Positive Long portfolioId, @Valid @RequestBody BondRequest request) {
    return created(portfolioId, writes.createBond(request.toCommand(portfolioId, null)));
  }

  @PutMapping("/{id}/bond")
  public AssetView updateBond(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @Valid @RequestBody BondRequest request) {
    writes.updateBond(request.toCommand(portfolioId, id));
    return reads.asset(portfolioId, id);
  }

  @PostMapping("/deposits")
  public ResponseEntity<AssetView> createDeposit(
      @PathVariable @Positive Long portfolioId, @Valid @RequestBody DepositRequest request) {
    return created(portfolioId, writes.createDeposit(request.toCommand(portfolioId)));
  }

  @PutMapping("/{id}/deposit")
  public AssetView updateDeposit(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @Valid @RequestBody DepositRequest request) {
    writes.update(request.toAssetCommand(portfolioId, id));
    writes.saveDepositDetails(portfolioId, id, request.toDetailsCommand());
    return reads.asset(portfolioId, id);
  }

  @PutMapping("/{id}/bond-details")
  public ResponseEntity<Void> saveBondDetails(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @Valid @RequestBody BondDetailsRequest request) {
    writes.saveBondDetails(portfolioId, id, request.toCommand());
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{id}/deposit-details")
  public ResponseEntity<Void> saveDepositDetails(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @Valid @RequestBody DepositDetailsRequest request) {
    writes.saveDepositDetails(portfolioId, id, request.toCommand());
    return ResponseEntity.noContent().build();
  }

  private static ResponseEntity<AssetView> created(Long portfolioId, AssetView asset) {
    return ResponseEntity.created(
            URI.create(
                "/api/v1/portfolios/%d/long-term-assets/%d".formatted(portfolioId, asset.id())))
        .body(asset);
  }
}
