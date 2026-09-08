package com.smartbox.investory.longterm;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshotReader;
import com.smartbox.investory.longterm.api.LongTermAssetProfileReader;
import com.smartbox.investory.longterm.api.LongTermAssetsApi;
import com.smartbox.investory.longterm.api.model.CashFlowType;
import com.smartbox.investory.longterm.api.model.Frequency;
import com.smartbox.investory.longterm.api.model.RealEstateCommand;
import com.smartbox.investory.longterm.api.model.RentalContractCommand;
import com.smartbox.investory.longterm.api.model.RentalContractStatusModel;
import com.smartbox.investory.longterm.api.model.RentalTermCommand;
import com.smartbox.investory.longterm.api.model.UpdateRentalContractCommand;
import com.smartbox.investory.shared.assets.AssetEconomicCategory;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Proves the Long-Term command, JPA, PostgreSQL, economics, and read-model lifecycle. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "investory.time.fixed-instant=2026-09-07T12:00:00Z")
@Transactional
class LongTermAssetLifecyclePostgresIT extends FastDatabaseTest {
  private static final Long PORTFOLIO_ID = 7_000_001L;

  @Autowired private LongTermAssetsApi assets;
  @Autowired private LongTermAssetProfileReader profiles;
  @Autowired private LongTermAssetAnnualSnapshotReader annualSnapshots;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entityManager;

  @Test
  void realEstateAndRentalContractLifecycleRoundTripsThroughPostgres() {
    jdbc.update(
        "insert into portfolios(id, name, base_currency, local_currency, user_id) values (?, ?, 'USD', 'USD', 1)",
        PORTFOLIO_ID,
        "Long-Term lifecycle test");
    var estate =
        assets.createRealEstate(
            new RealEstateCommand(
                PORTFOLIO_ID,
                null,
                "Lifecycle rental",
                CurrencyType.USD,
                new BigDecimal("300000"),
                new BigDecimal("12000"),
                LocalDate.of(2025, 1, 1),
                "LT-LIFECYCLE-1",
                "PostgreSQL lifecycle test"));

    var first =
        assets.createRentalContract(
            new RentalContractCommand(
                PORTFOLIO_ID,
                estate.id(),
                "First tenant",
                "first@example.com",
                "+48 100 200 300",
                LocalDate.of(2025, 1, 1),
                null,
                false,
                terms("2000", "1200", "300")));
    var updated =
        assets.updateRentalContract(
            new UpdateRentalContractCommand(
                PORTFOLIO_ID,
                estate.id(),
                first.id(),
                "Updated tenant",
                "updated@example.com",
                "+48 300 200 100",
                LocalDate.of(2025, 1, 1),
                null,
                terms("2200", "2400", "300")));

    assertThat(updated.id()).isEqualTo(first.id());
    var current2025 = assets.realEstateSummary(PORTFOLIO_ID, estate.id(), LocalDate.of(2025, 6, 1));
    assertThat(current2025.totalPaymentMonthly()).isEqualByComparingTo("2200");
    assertThat(current2025.annualEconomics().netAnnualIncomeAfterTax())
        .isEqualByComparingTo("22980");

    var successor =
        assets.createRentalContract(
            new RentalContractCommand(
                PORTFOLIO_ID,
                estate.id(),
                "Successor tenant",
                null,
                null,
                LocalDate.of(2026, 1, 1),
                null,
                true,
                List.of(term(CashFlowType.RENT, "2500", Frequency.MONTHLY, false))));

    assertThat(assets.rentalContracts(PORTFOLIO_ID, estate.id(), LocalDate.of(2026, 6, 1)))
        .extracting(contract -> contract.id())
        .containsExactly(successor.id(), first.id());
    assertThat(
            assets.rentalContracts(PORTFOLIO_ID, estate.id(), LocalDate.of(2026, 6, 1)).stream()
                .filter(contract -> contract.id().equals(first.id()))
                .findFirst()
                .orElseThrow()
                .endDate())
        .isEqualTo(LocalDate.of(2025, 12, 31));

    var profile = profiles.snapshot(PORTFOLIO_ID, LocalDate.of(2026, 6, 1));
    assertThat(profile.assets())
        .filteredOn(asset -> asset.category() == AssetEconomicCategory.REAL_ESTATE)
        .singleElement()
        .satisfies(
            asset -> {
              assertThat(asset.currentValue()).isEqualByComparingTo("300000");
              assertThat(asset.currency()).isEqualTo(CurrencyType.USD);
            });
    assertThat(profile.projectionInputs())
        .filteredOn(input -> input.id().equals(estate.id()))
        .singleElement()
        .satisfies(input -> assertThat(input.rentalContracts()).hasSize(2));

    var historical = annualSnapshots.historicalAnnualSnapshot(PORTFOLIO_ID, 2025);
    assertThat(historical.rentalIncome()).isEqualByComparingTo("22980");
    assertThat(historical.realEstateValue()).isNull();

    assets.terminateRentalContract(
        PORTFOLIO_ID, estate.id(), successor.id(), LocalDate.of(2026, 9, 7));
    assertThat(assets.rentalContracts(PORTFOLIO_ID, estate.id(), LocalDate.of(2026, 9, 8)))
        .filteredOn(contract -> contract.id().equals(successor.id()))
        .singleElement()
        .satisfies(
            contract -> {
              assertThat(contract.status()).isEqualTo(RentalContractStatusModel.TERMINATED);
              assertThat(contract.terminatedDate()).isEqualTo(LocalDate.of(2026, 9, 7));
              assertThat(contract.effectiveEndDate()).isEqualTo(LocalDate.of(2026, 9, 7));
            });

    entityManager.flush();
    assertPersistedContractFacts(estate.id(), first.id(), successor.id());
    assertSharedAssetIdentity(estate.id());

    assets.archive(PORTFOLIO_ID, estate.id());
    entityManager.flush();
    assertThat(
            assets.overview(PORTFOLIO_ID, LocalDate.of(2026, 9, 7)).groups().stream()
                .flatMap(group -> group.assets().stream()))
        .noneMatch(asset -> asset.id().equals(estate.id()));
    assertThat(assets.archived(PORTFOLIO_ID, LocalDate.of(2026, 9, 7)))
        .extracting(asset -> asset.id())
        .contains(estate.id());
    assertThat(
            jdbc.queryForObject(
                "select archived_at from real_estate where id = ?", LocalDate.class, estate.id()))
        .isEqualTo(LocalDate.of(2026, 9, 7));

    assets.reactivate(PORTFOLIO_ID, estate.id());
    entityManager.flush();
    assertThat(
            assets.overview(PORTFOLIO_ID, LocalDate.of(2026, 9, 7)).groups().stream()
                .flatMap(group -> group.assets().stream()))
        .anyMatch(asset -> asset.id().equals(estate.id()));
    assertThat(
            jdbc.queryForObject(
                "select archived_at is null from real_estate where id = ?",
                Boolean.class,
                estate.id()))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from long_term_asset_archive_interval where asset_id = ? and archived_from = date '2026-09-07' and archived_to = date '2026-09-07'",
                Integer.class,
                estate.id()))
        .isOne();
  }

  private void assertPersistedContractFacts(Long assetId, Long firstId, Long successorId) {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from rental_contract where real_estate_id = ?",
                Integer.class,
                assetId))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select end_date from rental_contract where id = ?", LocalDate.class, firstId))
        .isEqualTo(LocalDate.of(2025, 12, 31));
    assertThat(
            jdbc.queryForObject(
                "select terminated_date from rental_contract where id = ?",
                LocalDate.class,
                successorId))
        .isEqualTo(LocalDate.of(2026, 9, 7));
    assertThat(
            jdbc.queryForObject(
                "select amount from rental_contract_term where rental_contract_id = ? and cash_flow_type = 'RENT'",
                BigDecimal.class,
                firstId))
        .isEqualByComparingTo("2200");
  }

  private void assertSharedAssetIdentity(Long assetId) {
    assertThat(
            jdbc.queryForObject(
                "select (select count(*) from real_estate where id = ?) + (select count(*) from bond where id = ?) + (select count(*) from cash_reserve where id = ?) + (select count(*) from personal_asset where id = ?)",
                Integer.class,
                assetId,
                assetId,
                assetId,
                assetId))
        .isOne();
    assertThat(
            jdbc.queryForObject(
                "select complete from long_term_asset_history where asset_id = ?",
                Boolean.class,
                assetId))
        .isTrue();
  }

  private static List<RentalTermCommand> terms(
      String rent, String insurance, String tenantUtilities) {
    return List.of(
        term(CashFlowType.RENT, rent, Frequency.MONTHLY, false),
        term(CashFlowType.INSURANCE, insurance, Frequency.ANNUAL, false),
        term(CashFlowType.UTILITIES, tenantUtilities, Frequency.MONTHLY, true));
  }

  private static RentalTermCommand term(
      CashFlowType type, String amount, Frequency frequency, boolean paidByTenant) {
    return new RentalTermCommand(type, new BigDecimal(amount), frequency, paidByTenant);
  }
}
