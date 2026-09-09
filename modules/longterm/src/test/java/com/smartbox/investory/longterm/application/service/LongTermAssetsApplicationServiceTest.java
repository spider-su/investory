package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.longterm.api.model.AssetSummaryView;
import com.smartbox.investory.longterm.api.model.CashFlowType;
import com.smartbox.investory.longterm.api.model.Frequency;
import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.longterm.api.model.RentalContractCommand;
import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.longterm.api.model.RentalContractView;
import com.smartbox.investory.longterm.api.model.ResourceNotFoundException;
import com.smartbox.investory.longterm.api.model.UpdateRentalContractCommand;
import com.smartbox.investory.longterm.infrastructure.bond.BondEntity;
import com.smartbox.investory.longterm.infrastructure.bond.BondRepository;
import com.smartbox.investory.longterm.infrastructure.cash.CashReserveEntity;
import com.smartbox.investory.longterm.infrastructure.cash.CashReserveRepository;
import com.smartbox.investory.longterm.infrastructure.personal.PersonalAssetEntity;
import com.smartbox.investory.longterm.infrastructure.personal.PersonalAssetRepository;
import com.smartbox.investory.longterm.infrastructure.realestate.RealEstateEntity;
import com.smartbox.investory.longterm.infrastructure.realestate.RealEstateRepository;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractEntity;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractRepository;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractTermEntity;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.portfolio.PortfolioContext;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LongTermAssetsApplicationServiceTest {
  private static final Long PORTFOLIO_ID = 1L;
  private static final LocalDate DATE = LocalDate.of(2026, 9, 6);

  private final BondRepository bonds = mock(BondRepository.class);
  private final RealEstateRepository realEstates = mock(RealEstateRepository.class);
  private final CashReserveRepository cashReserves = mock(CashReserveRepository.class);
  private final PersonalAssetRepository personalAssets = mock(PersonalAssetRepository.class);
  private final LongTermAssetRentalContractRepository contracts =
      mock(LongTermAssetRentalContractRepository.class);
  private final BondCommandService bondCommands = mock(BondCommandService.class);
  private final RealEstateCommandService realEstateCommands = mock(RealEstateCommandService.class);
  private final CashReserveCommandService cashReserveCommands =
      mock(CashReserveCommandService.class);
  private final PersonalAssetCommandService personalAssetCommands =
      mock(PersonalAssetCommandService.class);
  private final RentalContractService rentalCommandService = mock(RentalContractService.class);
  private final PortfolioContextReader portfolios = mock(PortfolioContextReader.class);
  private final CurrencyConversion conversion = mock(CurrencyConversion.class);
  private final com.smartbox.investory.longterm.infrastructure.lifecycle
          .LongTermAssetHistoryRepository
      lifecycle =
          mock(
              com.smartbox.investory.longterm.infrastructure.lifecycle
                  .LongTermAssetHistoryRepository.class);
  private final LongTermAssetReadService reads =
      new LongTermAssetReadService(
          bonds, realEstates, cashReserves, personalAssets, contracts, conversion, portfolios);
  private final LongTermAssetHistoricalSnapshotService historical =
      new LongTermAssetHistoricalSnapshotService(
          realEstates, contracts, lifecycle, conversion, portfolios);
  private final LongTermAssetsApplicationService service =
      new LongTermAssetsApplicationService(
          bondCommands,
          realEstateCommands,
          cashReserveCommands,
          personalAssetCommands,
          rentalCommandService,
          reads,
          historical,
          mock(LongTermAssetLifecycleService.class),
          Clock.systemUTC());

  @Test
  void missingAssetDetailsAreReportedAtTheApplicationBoundary() {
    when(bondCommands.find(PORTFOLIO_ID, 1L)).thenReturn(Optional.empty());
    when(realEstateCommands.find(PORTFOLIO_ID, 2L)).thenReturn(Optional.empty());
    when(cashReserveCommands.find(PORTFOLIO_ID, 3L)).thenReturn(Optional.empty());
    when(personalAssetCommands.find(PORTFOLIO_ID, 4L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.bond(PORTFOLIO_ID, 1L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Bond not found");
    assertThatThrownBy(() -> service.realEstate(PORTFOLIO_ID, 2L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Real estate not found");
    assertThatThrownBy(() -> service.cashReserve(PORTFOLIO_ID, 3L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Cash reserve not found");
    assertThatThrownBy(() -> service.personalAsset(PORTFOLIO_ID, 4L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Personal asset not found");
  }

  @Test
  void rentalAndLifecycleCommandsRejectMissingInputAndDelegateScope() {
    assertThatThrownBy(() -> service.createRentalContract(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.updateRentalContract(null))
        .isInstanceOf(IllegalArgumentException.class);

    service.deleteRentalContract(PORTFOLIO_ID, 5L, 6L);
    service.terminateRentalContract(PORTFOLIO_ID, 5L, 6L, DATE);
    service.archive(PORTFOLIO_ID, 7L);
    service.reactivate(PORTFOLIO_ID, 7L);

    verify(rentalCommandService).delete(PORTFOLIO_ID, 5L, 6L);
  }

  @Test
  void simpleCommandsAndReadListsStayAtTheApplicationBoundary() {
    assertThat(service.createBond(null)).isNull();
    assertThat(service.updateBond(null)).isNull();
    assertThat(service.createRealEstate(null)).isNull();
    assertThat(service.updateRealEstate(null)).isNull();
    assertThat(service.createCashReserve(null)).isNull();
    assertThat(service.updateCashReserve(null)).isNull();
    assertThat(service.createPersonalAsset(null)).isNull();
    assertThat(service.updatePersonalAsset(null)).isNull();

    when(reads.archived(PORTFOLIO_ID, DATE)).thenReturn(List.of());
    when(rentalCommandService.list(PORTFOLIO_ID, 8L)).thenReturn(List.of());
    assertThat(service.archived(PORTFOLIO_ID, DATE)).isEmpty();
    assertThat(service.rentalContracts(PORTFOLIO_ID, 8L, DATE)).isEmpty();
  }

  @Test
  void rentalCommandsMapReturnedDomainContractToPublicView() {
    var model =
        new RentalContractModel(
            90L,
            DATE.minusDays(1),
            DATE.plusDays(10),
            null,
            "Tenant",
            "tenant@example.com",
            "555",
            List.of(
                new RentalContractModel.Term(
                    CashFlowType.RENT, new BigDecimal("100"), Frequency.MONTHLY, false)));
    when(rentalCommandService.create(
            any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
        .thenReturn(model);
    when(rentalCommandService.update(any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(model);
    when(rentalCommandService.end(any(), any(), any(), any())).thenReturn(model);

    RentalContractCommand command =
        new RentalContractCommand(
            PORTFOLIO_ID,
            8L,
            "Tenant",
            "tenant@example.com",
            "555",
            DATE.minusDays(1),
            DATE.plusDays(10),
            false,
            List.of());
    RentalContractView created = service.createRentalContract(command);
    assertThat(created.id()).isEqualTo(90L);
    assertThat(created.tenantName()).isEqualTo("Tenant");
    assertThat(created.terms())
        .singleElement()
        .satisfies(term -> assertThat(term.amount()).isEqualByComparingTo("100"));

    assertThat(
            service.updateRentalContract(
                new UpdateRentalContractCommand(
                    PORTFOLIO_ID,
                    8L,
                    90L,
                    "Tenant",
                    "tenant@example.com",
                    "555",
                    DATE.minusDays(1),
                    DATE.plusDays(10),
                    List.of())))
        .isNotNull();
    assertThat(service.endRentalContract(PORTFOLIO_ID, 8L, 90L, DATE)).isNotNull();
  }

  @BeforeEach
  void setUp() {
    when(portfolios.findById(PORTFOLIO_ID))
        .thenReturn(
            Optional.of(new PortfolioContext(PORTFOLIO_ID, CurrencyType.USD, CurrencyType.PLN)));
    when(bonds.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(PORTFOLIO_ID))
        .thenReturn(List.of());
    when(realEstates.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(PORTFOLIO_ID))
        .thenReturn(List.of());
    when(cashReserves.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(PORTFOLIO_ID))
        .thenReturn(List.of());
    when(personalAssets.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(PORTFOLIO_ID))
        .thenReturn(List.of());
  }

  @Test
  void personalAssetsStayInTotalAndAllocationButDoNotDiluteInvestmentYield() {
    BondEntity bond = bond(1L, new BigDecimal("1000"), new BigDecimal("0.10"));
    PersonalAssetEntity personal = personal(2L, new BigDecimal("9000"));
    when(bonds.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(PORTFOLIO_ID))
        .thenReturn(List.of(bond));
    when(personalAssets.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(PORTFOLIO_ID))
        .thenReturn(List.of(personal));

    var overview = service.overview(PORTFOLIO_ID, DATE);

    assertThat(overview.currency()).isEqualTo(CurrencyType.PLN);
    assertThat(overview.totalValue()).isEqualByComparingTo("10000");
    assertThat(overview.investmentValue()).isEqualByComparingTo("1000");
    assertThat(overview.personalAssetValue()).isEqualByComparingTo("9000");
    assertThat(overview.economics().grossYield()).isEqualByComparingTo("0.10");
    assertThat(overview.economics().netYieldAfterTax()).isEqualByComparingTo("0.081");
    assertThat(group(overview, LongTermAssetType.PERSONAL_ASSET).totalValue())
        .isEqualByComparingTo("9000");
  }

  @Test
  void interestBearingCashReserveUsesGlobalProfitTaxAndOptionalMaturity() {
    CashReserveEntity reserve = new CashReserveEntity();
    reserve.setId(4L);
    reserve.setPortfolioId(PORTFOLIO_ID);
    reserve.setName("Term cash");
    reserve.setCurrency(CurrencyType.PLN);
    reserve.setValue(new BigDecimal("50000"));
    reserve.setInterestRate(new BigDecimal("0.04"));
    reserve.setMaturityDate(DATE.plusYears(1));
    when(cashReserves.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(PORTFOLIO_ID))
        .thenReturn(List.of(reserve));

    AssetSummaryView asset =
        group(service.overview(PORTFOLIO_ID, DATE), LongTermAssetType.CASH_RESERVE)
            .assets()
            .getFirst();

    assertThat(asset.annualEconomics().grossAnnualIncome()).isEqualByComparingTo("2000");
    assertThat(asset.annualEconomics().annualTax()).isEqualByComparingTo("380");
    assertThat(asset.annualEconomics().netAnnualIncomeAfterTax()).isEqualByComparingTo("1620");
    assertThat(asset.annualEconomics().grossYield()).isEqualByComparingTo("0.04");
    assertThat(asset.annualEconomics().netYieldAfterTax()).isEqualByComparingTo("0.0324");
    assertThat(asset.maturityDate()).isEqualTo(DATE.plusYears(1));
    assertThat(service.snapshot(PORTFOLIO_ID, DATE).projectionInputs())
        .singleElement()
        .satisfies(projection -> assertThat(projection.fundingAvailable()).isFalse());
  }

  @Test
  void zeroInterestCashReserveKeepsMaturityForFundingAvailability() {
    CashReserveEntity reserve = new CashReserveEntity();
    reserve.setId(5L);
    reserve.setPortfolioId(PORTFOLIO_ID);
    reserve.setName("Locked cash");
    reserve.setCurrency(CurrencyType.PLN);
    reserve.setValue(new BigDecimal("50000"));
    reserve.setInterestRate(BigDecimal.ZERO);
    reserve.setMaturityDate(DATE.plusDays(1));
    when(cashReserves.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(PORTFOLIO_ID))
        .thenReturn(List.of(reserve));

    assertThat(service.snapshot(PORTFOLIO_ID, DATE).projectionInputs())
        .singleElement()
        .satisfies(
            projection -> {
              assertThat(projection.maturityDate()).isEqualTo(DATE.plusDays(1));
              assertThat(projection.fundingAvailable()).isFalse();
            });
  }

  @Test
  void bondAndCashProjectionPeriodsAreNotRentalIncome() {
    BondEntity bond = bond(5L, new BigDecimal("1000"), new BigDecimal("0.04"));
    CashReserveEntity cash = new CashReserveEntity();
    cash.setId(6L);
    cash.setPortfolioId(PORTFOLIO_ID);
    cash.setName("Cash");
    cash.setCurrency(CurrencyType.PLN);
    cash.setValue(new BigDecimal("500"));
    cash.setInterestRate(new BigDecimal("0.02"));
    when(bonds.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(PORTFOLIO_ID))
        .thenReturn(List.of(bond));
    when(cashReserves.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(PORTFOLIO_ID))
        .thenReturn(List.of(cash));

    assertThat(
            service.snapshot(PORTFOLIO_ID, DATE).projectionInputs().stream()
                .flatMap(input -> input.periods().stream())
                .map(LongTermAssetProjectionModel.Period::cashFlowType))
        .containsOnlyNulls();
  }

  @Test
  void maturedBondAndCashKeepValueButStopIncomeInOverviewAndProfileProjection() {
    BondEntity activeBond = bond(12L, new BigDecimal("1000"), new BigDecimal("0.10"));
    BondEntity maturedBond = bond(13L, new BigDecimal("2000"), new BigDecimal("0.10"));
    maturedBond.setMaturityDate(DATE.minusDays(1));
    CashReserveEntity activeCash = cash(14L, new BigDecimal("500"), new BigDecimal("0.20"));
    CashReserveEntity maturedCash = cash(15L, new BigDecimal("700"), new BigDecimal("0.20"));
    maturedCash.setMaturityDate(DATE);
    when(bonds.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(PORTFOLIO_ID))
        .thenReturn(List.of(activeBond, maturedBond));
    when(cashReserves.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(PORTFOLIO_ID))
        .thenReturn(List.of(activeCash, maturedCash));

    var overview = service.overview(PORTFOLIO_ID, DATE);
    var snapshot = service.snapshot(PORTFOLIO_ID, DATE);

    assertThat(overview.investmentValue()).isEqualByComparingTo("4200");
    assertThat(overview.economics().grossAnnualIncome()).isEqualByComparingTo("200");
    assertThat(overview.economics().netAnnualIncomeAfterTax()).isEqualByComparingTo("162");
    assertThat(snapshot.summary().netAnnualIncomeAfterTax()).isEqualByComparingTo("162");
    assertThat(snapshot.projectionInputs())
        .filteredOn(input -> input.maturityDate() != null && !input.periods().isEmpty())
        .allSatisfy(input -> assertThat(input.periods().getFirst().annualIncome()).isPositive());
    assertThat(snapshot.projectionInputs())
        .filteredOn(input -> input.maturityDate() != null && input.periods().isEmpty())
        .hasSize(2);
  }

  @Test
  void realEstateUsesAnnualTaxBaseAndExposesDerivedMonthlyValues() {
    RealEstateEntity realEstate = new RealEstateEntity();
    realEstate.setId(3L);
    realEstate.setPortfolioId(PORTFOLIO_ID);
    realEstate.setName("Happy Investor home");
    realEstate.setCurrency(CurrencyType.PLN);
    realEstate.setValue(new BigDecimal("10000"));
    realEstate.setTaxBase(new BigDecimal("8700"));

    LongTermAssetRentalContractEntity contract = new LongTermAssetRentalContractEntity();
    contract.setAssetId(3L);
    contract.setStartDate(DATE.minusDays(1));
    LongTermAssetRentalContractTermEntity rent = new LongTermAssetRentalContractTermEntity();
    rent.setType(CashFlowType.RENT);
    rent.setAmount(new BigDecimal("100"));
    rent.setFrequency(Frequency.MONTHLY);
    contract.setTerms(List.of(rent));
    when(realEstates.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(PORTFOLIO_ID))
        .thenReturn(List.of(realEstate));
    when(contracts.findAllWithTermsByAssetIdIn(List.of(3L))).thenReturn(List.of(contract));

    var asset =
        service.overview(PORTFOLIO_ID, DATE).groups().stream()
            .filter(group -> group.key().equals(LongTermAssetType.REAL_ESTATE.name()))
            .findFirst()
            .orElseThrow()
            .assets()
            .getFirst();

    assertThat(asset.annualEconomics().annualTax()).isEqualByComparingTo("739.5");
    assertThat(asset.annualEconomics().monthlyTaxBase()).isEqualByComparingTo("725");
    assertThat(asset.annualEconomics().monthlyTax()).isEqualByComparingTo("61.625");
    assertThat(asset.annualEconomics().monthlyNetIncomeAfterTax()).isEqualByComparingTo("38.375");
  }

  @Test
  void realEstateOverviewUsesEffectiveTerminationDateAsRentEnd() {
    RealEstateEntity realEstate = new RealEstateEntity();
    realEstate.setId(11L);
    realEstate.setPortfolioId(PORTFOLIO_ID);
    realEstate.setName("Terminated rental");
    realEstate.setCurrency(CurrencyType.PLN);
    realEstate.setValue(new BigDecimal("10000"));

    LongTermAssetRentalContractEntity contract = new LongTermAssetRentalContractEntity();
    contract.setAssetId(11L);
    contract.setStartDate(LocalDate.of(2026, 1, 1));
    contract.setEndDate(LocalDate.of(2026, 12, 31));
    contract.setTerminatedDate(LocalDate.of(2026, 6, 15));
    contract.setTerms(List.of(newTerm(CashFlowType.RENT, "100", false)));
    when(realEstates.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(PORTFOLIO_ID))
        .thenReturn(List.of(realEstate));
    when(contracts.findAllWithTermsByAssetIdIn(List.of(11L))).thenReturn(List.of(contract));

    assertThat(
            service.overview(PORTFOLIO_ID, LocalDate.of(2026, 6, 15)).groups().stream()
                .filter(group -> group.key().equals(LongTermAssetType.REAL_ESTATE.name()))
                .findFirst()
                .orElseThrow()
                .assets()
                .getFirst()
                .rentEnd())
        .isEqualTo(LocalDate.of(2026, 6, 15));
  }

  @Test
  void overlappingRentalContractsDegradeOnlyTheAffectedAsset() {
    RealEstateEntity invalid = estate(16L, "Invalid rental", "10000");
    RealEstateEntity healthy = estate(17L, "Healthy rental", "20000");
    var first = new LongTermAssetRentalContractEntity();
    first.setAssetId(16L);
    first.setStartDate(DATE.minusDays(2));
    first.setEndDate(DATE.plusDays(2));
    var second = new LongTermAssetRentalContractEntity();
    second.setAssetId(16L);
    second.setStartDate(DATE.minusDays(1));
    second.setEndDate(DATE.plusDays(3));
    when(realEstates.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(PORTFOLIO_ID))
        .thenReturn(List.of(invalid, healthy));
    when(contracts.findAllWithTermsByAssetIdIn(List.of(16L, 17L)))
        .thenReturn(List.of(first, second));

    var realEstate = group(service.overview(PORTFOLIO_ID, DATE), LongTermAssetType.REAL_ESTATE);

    assertThat(realEstate.assets()).hasSize(2);
    assertThat(realEstate.assets()).filteredOn(AssetSummaryView::integrityWarning).singleElement();
    assertThat(realEstate.assets())
        .filteredOn(asset -> !asset.integrityWarning())
        .singleElement()
        .satisfies(asset -> assertThat(asset.currentValue()).isEqualByComparingTo("100000"));
    assertThat(realEstate.assets())
        .filteredOn(AssetSummaryView::integrityWarning)
        .singleElement()
        .satisfies(asset -> assertThat(asset.annualEconomics()).isNull());
  }

  @Test
  void realEstateProjectionCarriesPersistedRentalContractsAndTerms() {
    var realEstate = new RealEstateEntity();
    realEstate.setId(12L);
    realEstate.setPortfolioId(PORTFOLIO_ID);
    realEstate.setName("Projected rental");
    realEstate.setCurrency(CurrencyType.PLN);
    realEstate.setValue(new BigDecimal("10000"));
    var contract = new LongTermAssetRentalContractEntity();
    contract.setId(120L);
    contract.setAssetId(12L);
    contract.setStartDate(LocalDate.of(2026, 1, 1));
    contract.setEndDate(LocalDate.of(2026, 12, 31));
    contract.setTerminatedDate(LocalDate.of(2026, 6, 15));
    contract.setTerms(List.of(newTerm(CashFlowType.RENT, "100", false)));
    when(realEstates.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(PORTFOLIO_ID))
        .thenReturn(List.of(realEstate));
    when(contracts.findAllWithTermsByAssetIdIn(List.of(12L))).thenReturn(List.of(contract));

    var projection =
        service.snapshot(PORTFOLIO_ID, LocalDate.of(2026, 6, 15)).projectionInputs().getFirst();

    assertThat(projection.rentalContracts()).hasSize(1);
    assertThat(projection.rentalContracts().getFirst().id()).isEqualTo(120L);
    assertThat(projection.rentalContracts().getFirst().terminatedDate())
        .isEqualTo(LocalDate.of(2026, 6, 15));
    assertThat(projection.rentalContracts().getFirst().terms().getFirst().type())
        .isEqualTo(CashFlowType.RENT);
  }

  @Test
  void rentalTaxDoesNotDependOnRentalTerms() {
    var asset =
        realEstateWithTerms(
            newTerm(CashFlowType.RENT, "100", false),
            newTerm(CashFlowType.PARKING_RENT, "20", false),
            newTerm(CashFlowType.ADMIN_FEE, "30", true),
            newTerm(CashFlowType.UTILITIES, "40", true));

    assertThat(asset.annualEconomics().grossAnnualIncome()).isEqualByComparingTo("1440");
    assertThat(asset.annualEconomics().annualTax()).isEqualByComparingTo("0");
  }

  @Test
  void rentalExpensesStillExcludeTenantPaidCosts() {
    var asset =
        realEstateWithTerms(
            newTerm(CashFlowType.RENT, "1000", false),
            newTerm(CashFlowType.OTHER_INCOME, "120", false),
            newTerm(CashFlowType.PROPERTY_TAX, "50", false),
            newTerm(CashFlowType.INSURANCE, "25", true),
            newTerm(CashFlowType.OTHER_EXPENSE, "10", false));

    assertThat(asset.annualEconomics().annualTax()).isEqualByComparingTo("0");
    assertThat(asset.annualEconomics().annualExpenses()).isEqualByComparingTo("720");
  }

  @Test
  void realEstateEconomicsUseIndependentAnnualGrossExpenseTaxAndYieldFacts() {
    RealEstateEntity realEstate = new RealEstateEntity();
    realEstate.setId(7L);
    realEstate.setPortfolioId(PORTFOLIO_ID);
    realEstate.setName("Rental property");
    realEstate.setCurrency(CurrencyType.PLN);
    realEstate.setValue(new BigDecimal("1000000"));
    realEstate.setTaxBase(new BigDecimal("1000"));

    LongTermAssetRentalContractEntity contract = new LongTermAssetRentalContractEntity();
    contract.setAssetId(7L);
    contract.setStartDate(DATE.minusDays(1));
    LongTermAssetRentalContractTermEntity annualPropertyTax =
        newTerm(CashFlowType.PROPERTY_TAX, "10000", false);
    annualPropertyTax.setFrequency(Frequency.ANNUAL);
    contract.setTerms(List.of(newTerm(CashFlowType.RENT, "5000", false), annualPropertyTax));
    when(realEstates.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(PORTFOLIO_ID))
        .thenReturn(List.of(realEstate));
    when(contracts.findAllWithTermsByAssetIdIn(List.of(7L))).thenReturn(List.of(contract));

    AssetSummaryView asset =
        group(service.overview(PORTFOLIO_ID, DATE), LongTermAssetType.REAL_ESTATE)
            .assets()
            .getFirst();

    assertThat(asset.currentValue()).isEqualByComparingTo("1000000");
    assertThat(asset.annualEconomics().grossAnnualIncome()).isEqualByComparingTo("60000");
    assertThat(asset.annualEconomics().annualExpenses()).isEqualByComparingTo("10000");
    assertThat(asset.annualEconomics().annualTax()).isEqualByComparingTo("85");
    assertThat(asset.annualEconomics().netAnnualIncomeAfterTax()).isEqualByComparingTo("49915");
    assertThat(asset.annualEconomics().grossYield()).isEqualByComparingTo("0.06");
    assertThat(asset.annualEconomics().netYieldAfterTax()).isEqualByComparingTo("0.049915");
  }

  @Test
  void longTermTotalIncludesInvestmentAndPersonalAssetsButEconomicsExcludePersonalAssets() {
    when(realEstates.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(PORTFOLIO_ID))
        .thenReturn(List.of());
    when(bonds.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(PORTFOLIO_ID))
        .thenReturn(List.of(bond(8L, new BigDecimal("200000"), new BigDecimal("0.05"))));
    CashReserveEntity reserve = new CashReserveEntity();
    reserve.setId(9L);
    reserve.setPortfolioId(PORTFOLIO_ID);
    reserve.setName("Reserve");
    reserve.setCurrency(CurrencyType.PLN);
    reserve.setValue(new BigDecimal("50000"));
    reserve.setInterestRate(new BigDecimal("0.05"));
    when(cashReserves.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(PORTFOLIO_ID))
        .thenReturn(List.of(reserve));
    when(personalAssets.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(PORTFOLIO_ID))
        .thenReturn(List.of(personal(10L, new BigDecimal("25000"))));

    var overview = service.overview(PORTFOLIO_ID, DATE);

    assertThat(overview.totalValue()).isEqualByComparingTo("275000");
    assertThat(overview.investmentValue()).isEqualByComparingTo("250000");
    assertThat(overview.personalAssetValue()).isEqualByComparingTo("25000");
    assertThat(overview.economics().grossAnnualIncome()).isEqualByComparingTo("12500");
    assertThat(overview.economics().netAnnualIncomeAfterTax()).isEqualByComparingTo("10125");
    assertThat(service.snapshot(PORTFOLIO_ID, DATE).summary().totalCurrentValue())
        .isEqualByComparingTo("275000");
    assertThat(service.snapshot(PORTFOLIO_ID, DATE).assets())
        .filteredOn(
            asset ->
                asset.category()
                    == com.smartbox.investory.shared.assets.AssetEconomicCategory.LIQUID_CASH)
        .singleElement()
        .satisfies(asset -> assertThat(asset.fundingAvailable()).isTrue());
    assertThat(service.snapshot(PORTFOLIO_ID, DATE).projectionInputs())
        .filteredOn(
            projection ->
                projection.category()
                    == com.smartbox.investory.shared.assets.AssetEconomicCategory.LIQUID_CASH)
        .singleElement()
        .satisfies(projection -> assertThat(projection.fundingAvailable()).isTrue());
  }

  @Test
  void totalPaymentMonthlyExcludesTenantPaidExpenses() {
    var asset =
        realEstateWithTerms(
            newTerm(CashFlowType.RENT, "1000", false),
            newTerm(CashFlowType.PARKING_RENT, "100", false),
            newTerm(CashFlowType.ADMIN_FEE, "50", true),
            newTerm(CashFlowType.UTILITIES, "25", true),
            newTerm(CashFlowType.OTHER_INCOME, "500", false),
            newTerm(CashFlowType.PROPERTY_TAX, "75", false));

    assertThat(asset.totalPaymentMonthly()).isEqualByComparingTo("1600");
  }

  @Test
  void historicalRentalIncomeUsesCalendarContractsAndAnnualTaxBase() {
    RealEstateEntity estate = new RealEstateEntity();
    estate.setId(20L);
    estate.setPortfolioId(PORTFOLIO_ID);
    estate.setName("Historical rental");
    estate.setCurrency(CurrencyType.USD);
    estate.setValue(new BigDecimal("100000"));
    estate.setTaxBase(new BigDecimal("365"));
    estate.setAcquisitionDate(LocalDate.of(2024, 1, 1));
    var contract = new LongTermAssetRentalContractEntity();
    contract.setId(21L);
    contract.setAssetId(20L);
    contract.setStartDate(LocalDate.of(2025, 1, 1));
    contract.setEndDate(LocalDate.of(2025, 6, 30));
    contract.setTerms(List.of(newTerm(CashFlowType.RENT, "1000", false)));
    when(realEstates.findAllByPortfolioIdOrderByName(PORTFOLIO_ID)).thenReturn(List.of(estate));
    when(contracts.findAllWithTermsByAssetIdIn(List.of(20L))).thenReturn(List.of(contract));
    when(lifecycle.completeRealEstateIds(List.of(20L))).thenReturn(java.util.Set.of(20L));
    when(conversion.convertToBaseCurrency(
            any(BigDecimal.class),
            eq(CurrencyType.PLN),
            eq(CurrencyType.USD),
            eq(LocalDate.of(2025, 12, 31))))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var historical = service.historicalAnnualSnapshot(PORTFOLIO_ID, 2025);

    assertThat(historical.rentalIncome()).isEqualByComparingTo("5984.615");
    assertThat(historical.realEstateValue()).isNull();
    assertThat(historical.bondIncome()).isNull();
  }

  @Test
  void realEstateGroupMonthlyTaxIsTheDirectSumOfPropertyRows() {
    RealEstateEntity first = estate(30L, "First", "3200");
    RealEstateEntity second = estate(31L, "Second", "3000");
    when(realEstates.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(PORTFOLIO_ID))
        .thenReturn(List.of(first, second));
    when(contracts.findAllWithTermsByAssetIdIn(List.of(30L, 31L))).thenReturn(List.of());

    var group = group(service.overview(PORTFOLIO_ID, DATE), LongTermAssetType.REAL_ESTATE);

    assertThat(group.assets())
        .satisfiesExactly(
            row -> {
              assertThat(row.annualEconomics().monthlyTaxBase())
                  .isEqualByComparingTo("266.666666666667");
              assertThat(row.annualEconomics().monthlyTax())
                  .isEqualByComparingTo("22.666666666667");
            },
            row -> {
              assertThat(row.annualEconomics().monthlyTaxBase()).isEqualByComparingTo("250");
              assertThat(row.annualEconomics().monthlyTax()).isEqualByComparingTo("21.25");
            });
    assertThat(group.annualEconomics().monthlyTaxBase()).isEqualByComparingTo("516.666666666667");
    assertThat(group.annualEconomics().monthlyTax()).isEqualByComparingTo("43.916666666667");
    assertThat(group.annualEconomics().annualTax()).isEqualByComparingTo("527");
  }

  private AssetSummaryView realEstateWithTerms(LongTermAssetRentalContractTermEntity... terms) {
    RealEstateEntity realEstate = new RealEstateEntity();
    realEstate.setId(3L);
    realEstate.setPortfolioId(PORTFOLIO_ID);
    realEstate.setName("Happy Investor home");
    realEstate.setCurrency(CurrencyType.PLN);
    realEstate.setValue(new BigDecimal("10000"));

    LongTermAssetRentalContractEntity contract = new LongTermAssetRentalContractEntity();
    contract.setAssetId(3L);
    contract.setStartDate(DATE.minusDays(1));
    contract.setTerms(List.of(terms));
    when(realEstates.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(PORTFOLIO_ID))
        .thenReturn(List.of(realEstate));
    when(contracts.findAllWithTermsByAssetIdIn(List.of(3L))).thenReturn(List.of(contract));
    return service.overview(PORTFOLIO_ID, DATE).groups().stream()
        .filter(group -> group.key().equals(LongTermAssetType.REAL_ESTATE.name()))
        .findFirst()
        .orElseThrow()
        .assets()
        .getFirst();
  }

  private static LongTermAssetRentalContractTermEntity newTerm(
      CashFlowType type, String amount, boolean paidByTenant) {
    LongTermAssetRentalContractTermEntity term = new LongTermAssetRentalContractTermEntity();
    term.setType(type);
    term.setAmount(new BigDecimal(amount));
    term.setFrequency(Frequency.MONTHLY);
    term.setPaidByTenant(paidByTenant);
    return term;
  }

  private static BondEntity bond(Long id, BigDecimal value, BigDecimal rate) {
    BondEntity bond = new BondEntity();
    bond.setId(id);
    bond.setPortfolioId(PORTFOLIO_ID);
    bond.setName("Happy Investor bond");
    bond.setCurrency(CurrencyType.PLN);
    bond.setValue(value);
    bond.setInterestRate(rate);
    bond.setMaturityDate(DATE.plusYears(1));
    return bond;
  }

  private static CashReserveEntity cash(Long id, BigDecimal value, BigDecimal rate) {
    CashReserveEntity cash = new CashReserveEntity();
    cash.setId(id);
    cash.setPortfolioId(PORTFOLIO_ID);
    cash.setName("Cash " + id);
    cash.setCurrency(CurrencyType.PLN);
    cash.setValue(value);
    cash.setInterestRate(rate);
    return cash;
  }

  private static PersonalAssetEntity personal(Long id, BigDecimal value) {
    PersonalAssetEntity personal = new PersonalAssetEntity();
    personal.setId(id);
    personal.setPortfolioId(PORTFOLIO_ID);
    personal.setName("Happy Investor home");
    personal.setCategory(PersonalAssetEntity.Category.HOME);
    personal.setCurrency(CurrencyType.PLN);
    personal.setValue(value);
    return personal;
  }

  private static RealEstateEntity estate(Long id, String name, String annualTaxBase) {
    RealEstateEntity estate = new RealEstateEntity();
    estate.setId(id);
    estate.setPortfolioId(PORTFOLIO_ID);
    estate.setName(name);
    estate.setCurrency(CurrencyType.PLN);
    estate.setValue(new BigDecimal("100000"));
    estate.setTaxBase(new BigDecimal(annualTaxBase));
    return estate;
  }

  private static com.smartbox.investory.longterm.api.model.AssetGroupView group(
      com.smartbox.investory.longterm.api.model.LongTermOverviewView overview,
      LongTermAssetType type) {
    return overview.groups().stream()
        .filter(group -> group.key().equals(type.name()))
        .findFirst()
        .orElseThrow();
  }
}
