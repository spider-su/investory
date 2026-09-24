package com.smartbox.investory.longterm.web;

import com.smartbox.investory.longterm.api.LongTermAssetsApi;
import com.smartbox.investory.longterm.api.model.*;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for factual Long-Term fixed-income asset round trips. */
@RestController
@Validated
@RequestMapping("/api/v1/portfolios/{portfolioId}/long-term-assets")
@RequiredArgsConstructor
public class LongTermAssetsRestController {
  private final LongTermAssetsApi assets;

  @GetMapping
  public LongTermOverviewView overview(
      @PathVariable @Positive Long portfolioId, @RequestParam LocalDate date) {
    return assets.overview(portfolioId, date);
  }

  @GetMapping("/archived")
  public List<AssetSummaryView> archived(
      @PathVariable @Positive Long portfolioId, @RequestParam LocalDate date) {
    return assets.archived(portfolioId, date);
  }

  @PostMapping("/bond")
  public BondView createBond(
      @PathVariable @Positive Long portfolioId, @RequestBody BondCommand command) {
    return assets.createBond(withPortfolio(command, portfolioId));
  }

  @GetMapping("/bond/{id}")
  public BondView bond(@PathVariable @Positive Long portfolioId, @PathVariable @Positive Long id) {
    return assets.bond(portfolioId, id);
  }

  @PutMapping("/bond/{id}")
  public BondView updateBond(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @RequestBody BondCommand command) {
    return assets.updateBond(
        new BondCommand(
            portfolioId,
            id,
            command.name(),
            command.currency(),
            command.value(),
            command.acquisitionDate(),
            command.interestRate(),
            command.maturityDate(),
            command.notes()));
  }

  @GetMapping("/real-estate/{id}")
  public RealEstateView realEstate(
      @PathVariable @Positive Long portfolioId, @PathVariable @Positive Long id) {
    return assets.realEstate(portfolioId, id);
  }

  @PostMapping("/real-estate")
  public RealEstateView createRealEstate(
      @PathVariable @Positive Long portfolioId, @RequestBody RealEstateCommand command) {
    return assets.createRealEstate(withPortfolio(command, portfolioId));
  }

  @PutMapping("/real-estate/{id}")
  public RealEstateView updateRealEstate(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @RequestBody RealEstateCommand command) {
    return assets.updateRealEstate(withPortfolio(command, portfolioId, id));
  }

  @GetMapping("/real-estate/{id}/summary")
  public AssetSummaryView realEstateSummary(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @RequestParam LocalDate date) {
    return assets.realEstateSummary(portfolioId, id, date);
  }

  @GetMapping("/cash-reserve/{id}")
  public CashReserveView cashReserve(
      @PathVariable @Positive Long portfolioId, @PathVariable @Positive Long id) {
    return assets.cashReserve(portfolioId, id);
  }

  @PutMapping("/cash-reserve/{id}")
  public CashReserveView updateCashReserve(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @RequestBody CashReserveCommand command) {
    return assets.updateCashReserve(
        new CashReserveCommand(
            portfolioId,
            id,
            command.name(),
            command.currency(),
            command.value(),
            command.acquisitionDate(),
            command.interestRate(),
            command.maturityDate(),
            command.notes()));
  }

  @PostMapping("/cash-reserve")
  public CashReserveView createCashReserve(
      @PathVariable @Positive Long portfolioId, @RequestBody CashReserveCommand command) {
    return assets.createCashReserve(withPortfolio(command, portfolioId));
  }

  @GetMapping("/personal-asset/{id}")
  public PersonalAssetView personalAsset(
      @PathVariable @Positive Long portfolioId, @PathVariable @Positive Long id) {
    return assets.personalAsset(portfolioId, id);
  }

  @PostMapping("/personal-asset")
  public PersonalAssetView createPersonalAsset(
      @PathVariable @Positive Long portfolioId, @RequestBody PersonalAssetCommand command) {
    return assets.createPersonalAsset(withPortfolio(command, portfolioId));
  }

  @PutMapping("/personal-asset/{id}")
  public PersonalAssetView updatePersonalAsset(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @RequestBody PersonalAssetCommand command) {
    return assets.updatePersonalAsset(withPortfolio(command, portfolioId, id));
  }

  @PostMapping("/real-estate/{id}/rental-contracts")
  public RentalContractView createRentalContract(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @RequestBody RentalContractCommand command) {
    return assets.createRentalContract(withRentalAsset(command, portfolioId, id));
  }

  @GetMapping("/real-estate/{id}/rental-contracts")
  public List<RentalContractView> rentalContracts(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @RequestParam LocalDate date) {
    return assets.rentalContracts(portfolioId, id, date);
  }

  @PutMapping("/real-estate/{id}/rental-contracts/{contractId}")
  public RentalContractView updateRentalContract(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @PathVariable @Positive Long contractId,
      @RequestBody UpdateRentalContractCommand command) {
    return assets.updateRentalContract(withRentalAsset(command, portfolioId, id, contractId));
  }

  @DeleteMapping("/real-estate/{id}/rental-contracts/{contractId}")
  public void deleteRentalContract(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @PathVariable @Positive Long contractId) {
    assets.deleteRentalContract(portfolioId, id, contractId);
  }

  @PostMapping("/real-estate/{id}/rental-contracts/{contractId}/end")
  public RentalContractView endRentalContract(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @PathVariable @Positive Long contractId,
      @RequestParam LocalDate endDate) {
    return assets.endRentalContract(portfolioId, id, contractId, endDate);
  }

  @PostMapping("/real-estate/{id}/rental-contracts/{contractId}/terminate")
  public void terminateRentalContract(
      @PathVariable @Positive Long portfolioId,
      @PathVariable @Positive Long id,
      @PathVariable @Positive Long contractId,
      @RequestParam LocalDate terminationDate) {
    assets.terminateRentalContract(portfolioId, id, contractId, terminationDate);
  }

  @PostMapping("/{id}/archive")
  public void archive(@PathVariable @Positive Long portfolioId, @PathVariable @Positive Long id) {
    assets.archive(portfolioId, id);
  }

  @PostMapping("/{id}/reactivate")
  public void reactivate(
      @PathVariable @Positive Long portfolioId, @PathVariable @Positive Long id) {
    assets.reactivate(portfolioId, id);
  }

  private static BondCommand withPortfolio(BondCommand command, Long portfolioId) {
    return new BondCommand(
        portfolioId,
        command.id(),
        command.name(),
        command.currency(),
        command.value(),
        command.acquisitionDate(),
        command.interestRate(),
        command.maturityDate(),
        command.notes());
  }

  private static CashReserveCommand withPortfolio(CashReserveCommand command, Long portfolioId) {
    return new CashReserveCommand(
        portfolioId,
        command.id(),
        command.name(),
        command.currency(),
        command.value(),
        command.acquisitionDate(),
        command.interestRate(),
        command.maturityDate(),
        command.notes());
  }

  private static RealEstateCommand withPortfolio(RealEstateCommand command, Long portfolioId) {
    return withPortfolio(command, portfolioId, command.id());
  }

  private static RealEstateCommand withPortfolio(
      RealEstateCommand command, Long portfolioId, Long id) {
    return new RealEstateCommand(
        portfolioId,
        id,
        command.name(),
        command.currency(),
        command.value(),
        command.taxBase(),
        command.acquisitionDate(),
        command.landRegisterNumber(),
        command.notes());
  }

  private static PersonalAssetCommand withPortfolio(
      PersonalAssetCommand command, Long portfolioId) {
    return withPortfolio(command, portfolioId, command.id());
  }

  private static PersonalAssetCommand withPortfolio(
      PersonalAssetCommand command, Long portfolioId, Long id) {
    return new PersonalAssetCommand(
        portfolioId,
        id,
        command.name(),
        command.category(),
        command.currency(),
        command.value(),
        command.acquisitionDate(),
        command.notes());
  }

  private static RentalContractCommand withRentalAsset(
      RentalContractCommand command, Long portfolioId, Long assetId) {
    return new RentalContractCommand(
        portfolioId,
        assetId,
        command.tenantName(),
        command.tenantEmail(),
        command.tenantPhone(),
        command.startDate(),
        command.endDate(),
        command.endCurrentContractBeforeStart(),
        command.terms());
  }

  private static UpdateRentalContractCommand withRentalAsset(
      UpdateRentalContractCommand command, Long portfolioId, Long assetId, Long contractId) {
    return new UpdateRentalContractCommand(
        portfolioId,
        assetId,
        contractId,
        command.tenantName(),
        command.tenantEmail(),
        command.tenantPhone(),
        command.startDate(),
        command.endDate(),
        command.terms());
  }
}
