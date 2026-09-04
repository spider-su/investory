package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.longterm.api.model.CashFlowType;
import com.smartbox.investory.longterm.api.model.Frequency;
import com.smartbox.investory.longterm.api.model.InterestTreatment;
import com.smartbox.investory.longterm.api.model.LongTermAssetProfileSnapshotModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetProjectionModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetType;
import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.longterm.application.model.LongTermAssetProjectionInput;
import com.smartbox.investory.longterm.application.model.LongTermAssetSummary;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetEntity;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetRepository;
import com.smartbox.investory.longterm.infrastructure.bond.LongTermAssetBondDetailsEntity;
import com.smartbox.investory.longterm.infrastructure.bond.LongTermAssetBondRatePeriodEntity;
import com.smartbox.investory.longterm.infrastructure.bond.LongTermAssetBondRatePeriodRepository;
import com.smartbox.investory.longterm.infrastructure.lifecycle.LongTermAssetLifecyclePeriodRepository;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractEntity;
import com.smartbox.investory.longterm.infrastructure.valuation.LongTermAssetValuationPeriodEntity;
import com.smartbox.investory.longterm.infrastructure.valuation.LongTermAssetValuationPeriodRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorLongTermFacts;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorTestData;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
@Import(LongTermAssetLifecyclePersistenceIT.MutableClockConfig.class)
@DisplayName("Long-Term Asset Lifecycle Persistence")
class LongTermAssetLifecyclePersistenceIT extends FastDatabaseTest {
  private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
  private static final LocalDate ACQUIRED = LocalDate.of(2026, 1, 1);

  @Autowired LongTermAssetCommandService commands;
  @Autowired LongTermAssetQueryService queries;
  @Autowired LongTermAssetProjectionQueryService projections;
  @Autowired LongTermAssetReadService reads;
  @Autowired LongTermAssetRepository assets;
  @Autowired LongTermAssetLifecyclePeriodRepository lifecyclePeriods;
  @Autowired RentalContractService rentalContracts;
  @Autowired LongTermAssetValuationPeriodRepository valuationPeriods;
  @Autowired LongTermAssetBondRatePeriodRepository bondRates;
  @Autowired MutableClock clock;

  @BeforeEach
  void resetClock() {
    clock.setDate(LocalDate.of(2026, 7, 1));
  }

  @Test
  @DisplayName("create archive historical current reactivate and query the lifecycle gap")
  void followsCompleteLifecycleThroughRepositoriesAndServices() {
    Long portfolio = createPortfolio();
    LongTermAssetEntity asset =
        commands.save(asset(portfolio, LongTermAssetType.OTHER, "Lifecycle"));
    entityFlushAndClear();

    assertThat(summaryIds(queries.list(portfolio, LocalDate.of(2026, 6, 14))))
        .containsExactly(asset.getId());

    commands.archive(portfolio, asset.getId());
    entityFlushAndClear();
    assertThat(summaryIds(queries.list(portfolio, LocalDate.of(2026, 6, 14))))
        .containsExactly(asset.getId());
    assertThat(queries.list(portfolio, LocalDate.of(2026, 7, 1))).isEmpty();

    clock.setDate(LocalDate.of(2026, 8, 1));
    commands.reactivate(portfolio, asset.getId());
    entityFlushAndClear();
    assertThat(queries.list(portfolio, LocalDate.of(2026, 7, 15))).isEmpty();
    assertThat(summaryIds(queries.list(portfolio, LocalDate.of(2026, 8, 1))))
        .containsExactly(asset.getId());
    assertThat(lifecyclePeriods.findAllByAssetIdOrderByActiveFrom(asset.getId())).hasSize(2);
  }

  @Test
  @DisplayName("excludes acquisition after the requested date")
  void excludesAssetBeforeAcquisitionAndIncludesItAfterward() {
    Long portfolio = createPortfolio();
    LongTermAssetEntity asset = asset(portfolio, LongTermAssetType.OTHER, "Future acquisition");
    asset.setAcquisitionDate(LocalDate.of(2026, 9, 1));
    asset = commands.save(asset);
    entityFlushAndClear();

    assertThat(queries.list(portfolio, LocalDate.of(2026, 8, 31))).isEmpty();
    assertThat(summaryIds(queries.list(portfolio, LocalDate.of(2026, 9, 1))))
        .containsExactly(asset.getId());
  }

  @Test
  @DisplayName("keeps populations isolated between portfolios")
  void isolatesTwoPortfolios() {
    Long firstPortfolio = createPortfolio();
    Long secondPortfolio = createPortfolio();
    LongTermAssetEntity first =
        commands.save(asset(firstPortfolio, LongTermAssetType.CASH_RESERVE, "First"));
    LongTermAssetEntity second =
        commands.save(asset(secondPortfolio, LongTermAssetType.CASH_RESERVE, "Second"));
    entityFlushAndClear();

    assertThat(summaryIds(queries.list(firstPortfolio, ACQUIRED))).containsExactly(first.getId());
    assertThat(summaryIds(queries.list(secondPortfolio, ACQUIRED))).containsExactly(second.getId());
    assertThat(projectionIds(projections.projectionInputs(firstPortfolio, ACQUIRED)))
        .containsExactly(first.getId());
    assertThat(projectionIds(projections.projectionInputs(secondPortfolio, ACQUIRED)))
        .containsExactly(second.getId());
  }

  @Test
  @DisplayName("honors rental start end and termination boundaries")
  void honorsRentalBoundaries() {
    Long portfolio = createPortfolio();
    LongTermAssetEntity asset =
        asset(portfolio, LongTermAssetType.REAL_ESTATE, "Rental boundaries");
    asset.setTaxBase(new BigDecimal("1000"));
    asset = commands.save(asset);
    var contract =
        rentalContracts.create(
            portfolio,
            asset.getId(),
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 6, 30),
            List.of(rentTerm(3000)));
    entityFlushAndClear();

    assertThat(grossIncome(asset.getId(), LocalDate.of(2026, 3, 31))).isZero();
    assertThat(grossIncome(asset.getId(), LocalDate.of(2026, 4, 1))).isPositive();
    assertThat(grossIncome(asset.getId(), LocalDate.of(2026, 6, 30))).isPositive();
    assertThat(grossIncome(asset.getId(), LocalDate.of(2026, 7, 1))).isZero();

    var ongoing =
        rentalContracts.create(
            portfolio, asset.getId(), LocalDate.of(2026, 7, 2), null, List.of(rentTerm(3500)));
    clock.setDate(LocalDate.of(2026, 7, 20));
    rentalContracts.terminate(portfolio, asset.getId(), ongoing.getId(), LocalDate.of(2026, 7, 15));
    entityFlushAndClear();
    assertThat(grossIncome(asset.getId(), LocalDate.of(2026, 7, 15))).isPositive();
    assertThat(grossIncome(asset.getId(), LocalDate.of(2026, 7, 16))).isZero();
  }

  @Test
  @DisplayName("honors valuation and bond rate period boundaries")
  void honorsValuationAndRateBoundaries() {
    Long portfolio = createPortfolio();
    LongTermAssetEntity cash =
        commands.saveCashReserve(
            portfolio,
            null,
            "Rate boundaries",
            CurrencyType.PLN,
            new BigDecimal("1000"),
            new BigDecimal("0.01"),
            null,
            ACQUIRED);
    valuationPeriods.deleteAll(valuationPeriods.findAllByAssetIdOrderByValidFrom(cash.getId()));
    entityManager().flush();
    valuationPeriods.saveAll(
        List.of(
            valuation(cash.getId(), ACQUIRED, LocalDate.of(2026, 6, 30), "0.02"),
            valuation(cash.getId(), LocalDate.of(2026, 7, 1), null, "0.04")));

    LongTermAssetEntity bond = asset(portfolio, LongTermAssetType.BOND, "Bond rate boundaries");
    bond = commands.save(bond);
    var details = new LongTermAssetBondDetailsEntity();
    details.setMaturityDate(LocalDate.of(2030, 1, 1));
    details.setInterestTreatment(InterestTreatment.PAY_OUT);
    details.setTaxRate(new BigDecimal("0.19"));
    commands.saveBondDetails(portfolio, bond.getId(), details);
    bondRates.saveAll(
        List.of(
            bondRate(bond.getId(), ACQUIRED, LocalDate.of(2026, 6, 30), "0.03"),
            bondRate(bond.getId(), LocalDate.of(2026, 7, 1), null, "0.05")));
    entityFlushAndClear();

    assertThat(queries.summary(cash, LocalDate.of(2026, 6, 30)).currentAnnualRate())
        .isEqualByComparingTo("0.02");
    assertThat(queries.summary(cash, LocalDate.of(2026, 7, 1)).currentAnnualRate())
        .isEqualByComparingTo("0.04");
    assertThat(queries.summary(bond, LocalDate.of(2026, 6, 30)).currentAnnualRate())
        .isEqualByComparingTo("0.03");
    assertThat(queries.summary(bond, LocalDate.of(2026, 7, 1)).currentAnnualRate())
        .isEqualByComparingTo("0.05");
  }

  @Test
  @DisplayName("uses one population for profile summary and projection at the same date")
  void profileAndProjectionSharePopulation() {
    Long portfolio = createPortfolio();
    LongTermAssetEntity active =
        commands.save(asset(portfolio, LongTermAssetType.CASH_RESERVE, "Profile active"));
    LongTermAssetEntity archived =
        commands.save(asset(portfolio, LongTermAssetType.CASH_RESERVE, "Profile archived"));
    commands.archive(portfolio, archived.getId());
    clock.setDate(LocalDate.of(2026, 7, 2));
    entityFlushAndClear();

    LocalDate date = LocalDate.of(2026, 7, 2);
    List<Long> summaryIds = summaryIds(queries.list(portfolio, date));
    LongTermAssetProfileSnapshotModel snapshot = reads.snapshot(portfolio, date);

    assertThat(summaryIds).containsExactly(active.getId());
    assertThat(projectionModelIds(snapshot.projectionInputs())).containsExactly(active.getId());
    assertThat(snapshot.assets())
        .extracting(asset -> asset.type())
        .containsExactly(LongTermAssetType.CASH_RESERVE);
  }

  @Test
  @DisplayName(
      "creates canonical external assets and reads their rental contracts through services")
  void canonicalHappyInvestorProfileUsesIndividualAssetsAndContracts() {
    Long portfolio = 1L;
    LongTermAssetEntity cashReserve = assets.findById(9401L).orElseThrow();
    LongTermAssetEntity apartmentA = assets.findById(9402L).orElseThrow();
    LongTermAssetEntity apartmentB = assets.findById(9403L).orElseThrow();
    LongTermAssetEntity familyCar = assets.findById(9404L).orElseThrow();
    entityFlushAndClear();

    assertThat(queries.list(portfolio, HappyInvestorTestData.REFERENCE_DATE))
        .extracting(LongTermAssetSummary::name)
        .containsExactlyInAnyOrder(
            "Cash reserve",
            HappyInvestorTestData.APARTMENT_A_NAME,
            HappyInvestorTestData.APARTMENT_B_NAME,
            HappyInvestorTestData.FAMILY_CAR_NAME,
            "Treasury 2026",
            "Reserve deposit");
    assertThat(cashReserve.getCurrentValue())
        .isEqualByComparingTo(HappyInvestorTestData.CASH_RESERVE);
    assertThat(cashReserve.getAcquisitionValue())
        .isEqualByComparingTo(HappyInvestorTestData.CASH_RESERVE);
    assertThat(apartmentA.getCurrentValue())
        .isEqualByComparingTo(HappyInvestorTestData.APARTMENT_A_VALUE);
    assertThat(apartmentB.getCurrentValue())
        .isEqualByComparingTo(HappyInvestorTestData.APARTMENT_B_VALUE);
    assertThat(familyCar.getCurrentValue())
        .isEqualByComparingTo(HappyInvestorTestData.FAMILY_CAR_VALUE);
    assertThat(
            queries
                .aggregateForLongTermAssets(portfolio, HappyInvestorTestData.REFERENCE_DATE)
                .totalCurrentValue())
        .isEqualByComparingTo(HappyInvestorLongTermFacts.LONG_TERM_TOTAL);
    assertThat(rentalContracts.list(portfolio, apartmentA.getId())).hasSize(1);
    assertThat(rentalContracts.list(portfolio, apartmentB.getId()))
        .extracting(LongTermAssetRentalContractEntity::getStartDate)
        .containsExactlyInAnyOrder(
            HappyInvestorTestData.APARTMENT_B1_START, HappyInvestorTestData.APARTMENT_B2_START);
    assertThat(
            queries
                .summary(apartmentA, HappyInvestorTestData.REFERENCE_DATE)
                .annualEconomics()
                .grossAnnualIncome())
        .isEqualByComparingTo("38400");
    var rentalRows =
        queries.list(portfolio, HappyInvestorTestData.REFERENCE_DATE).stream()
            .filter(row -> row.type() == LongTermAssetType.REAL_ESTATE)
            .toList();
    var rentalGross =
        rentalRows.stream()
            .map(row -> row.annualEconomics().grossAnnualIncome())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(rentalGross)
        .isEqualByComparingTo(HappyInvestorLongTermFacts.RENTAL_BOUNDARY_DATE_GROSS_ANNUAL);
    assertThat(
            queries
                .summary(familyCar, HappyInvestorTestData.REFERENCE_DATE)
                .annualEconomics()
                .grossAnnualIncome())
        .isEqualTo(BigDecimal.ZERO);
  }

  private BigDecimal grossIncome(Long id, LocalDate date) {
    return queries
        .summary(assets.findById(id).orElseThrow(), date)
        .annualEconomics()
        .grossAnnualIncome();
  }

  private Long createPortfolio() {
    return ((Number)
            entityManager()
                .createNativeQuery(
                    "INSERT INTO investory.portfolios (name, base_currency, owner, user_id) "
                        + "VALUES ('Lifecycle second portfolio', 'USD', 'Test', 1) RETURNING id")
                .getSingleResult())
        .longValue();
  }

  private LongTermAssetEntity asset(Long portfolioId, LongTermAssetType type, String name) {
    var asset = new LongTermAssetEntity();
    asset.setPortfolioId(portfolioId);
    asset.setName(name);
    asset.setType(type);
    asset.setCurrency(CurrencyType.PLN);
    asset.setAcquisitionDate(ACQUIRED);
    asset.setAcquisitionValue(new BigDecimal("100000"));
    asset.setCurrentValue(new BigDecimal("100000"));
    asset.setActive(true);
    return asset;
  }

  private static RentalContractModel.Term rentTerm(int amount) {
    return new RentalContractModel.Term(
        CashFlowType.RENT, BigDecimal.valueOf(amount), Frequency.MONTHLY, false);
  }

  private static LongTermAssetValuationPeriodEntity valuation(
      Long assetId, LocalDate from, LocalDate to, String rate) {
    var period = new LongTermAssetValuationPeriodEntity();
    period.setAssetId(assetId);
    period.setValidFrom(from);
    period.setValidTo(to);
    period.setExpectedAnnualGrowthRate(new BigDecimal(rate));
    return period;
  }

  private static LongTermAssetBondRatePeriodEntity bondRate(
      Long id, LocalDate from, LocalDate to, String rate) {
    var period = new LongTermAssetBondRatePeriodEntity();
    period.setAssetId(id);
    period.setValidFrom(from);
    period.setValidTo(to);
    period.setAnnualInterestRate(new BigDecimal(rate));
    return period;
  }

  private static List<Long> summaryIds(List<LongTermAssetSummary> rows) {
    return rows.stream().map(LongTermAssetSummary::id).toList();
  }

  private static List<Long> projectionIds(List<LongTermAssetProjectionInput> rows) {
    return rows.stream().map(row -> row.id()).toList();
  }

  private static List<Long> projectionModelIds(List<LongTermAssetProjectionModel> rows) {
    return rows.stream().map(row -> row.id()).toList();
  }

  private void entityFlushAndClear() {
    entityManager().flush();
    entityManager().clear();
  }

  @Autowired private jakarta.persistence.EntityManager entityManager;

  private jakarta.persistence.EntityManager entityManager() {
    return entityManager;
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class MutableClockConfig {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock(LocalDate.of(2026, 7, 1));
    }
  }

  static class MutableClock extends Clock {
    private Instant instant;

    MutableClock(LocalDate date) {
      setDate(date);
    }

    void setDate(LocalDate date) {
      instant = date.atStartOfDay(ZONE).toInstant();
    }

    @Override
    public ZoneId getZone() {
      return ZONE;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
