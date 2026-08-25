package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.longterm.application.model.LongTermAssetBootstrapDocument;
import com.smartbox.investory.longterm.api.model.CashFlowTypeModel;
import com.smartbox.investory.longterm.api.model.FrequencyModel;
import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetEntity;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetRepository;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.longterm.infrastructure.rental.CashFlowType;
import com.smartbox.investory.longterm.infrastructure.rental.Frequency;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractRepository;
import com.smartbox.investory.longterm.infrastructure.tax.RentalTaxPolicyEntity;
import com.smartbox.investory.longterm.infrastructure.tax.RentalTaxPolicyRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class RentalContractPersistenceIT extends FastDatabaseTest {
  @Autowired RentalContractService service;
  @Autowired LongTermAssetBootstrapService bootstrap;
  @Autowired LongTermAssetRepository assets;
  @Autowired LongTermAssetRentalContractRepository contracts;
  @Autowired RentalTaxPolicyRepository taxPolicies;
  @Autowired EntityManager entityManager;

  @Test
  void persistsTenantUpdatesTermsInPlaceAndDeletesThroughOrphanRemoval() {
    var asset = new LongTermAssetEntity();
    asset.setPortfolioId(1L);
    asset.setName("Rental persistence test");
    asset.setType(LongTermAssetType.REAL_ESTATE);
    asset.setCurrency(CurrencyType.PLN);
    asset.setCurrentValue(new BigDecimal("800000"));
    asset.setTaxBase(new BigDecimal("2800"));
    asset.setActive(true);
    asset = assets.save(asset);

    var created =
        service.create(
            1L,
            asset.getId(),
            "Tenant One",
            "tenant.one@example.com",
            "+48 111 222 333",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
            null,
            Arrays.stream(CashFlowTypeModel.values())
                .map(type -> term(type, type.ordinal() + 1, type.name().contains("FEE")))
                .toList(),
            false);
    Long contractId = created.getId();
    entityManager.flush();
    entityManager.clear();

    var stored = service.list(1L, asset.getId()).getFirst();
    assertThat(stored.getTenantName()).isEqualTo("Tenant One");
    assertThat(stored.getTenantEmail()).isEqualTo("tenant.one@example.com");
    assertThat(stored.getTenantPhone()).isEqualTo("+48 111 222 333");
    assertThat(stored.getMonthlyTaxBase()).isEqualByComparingTo("2800");
    assertThat(stored.getTerms()).hasSize(8);

    var updated =
        service.update(
            1L,
            asset.getId(),
            contractId,
            "Tenant Two",
            "tenant.two@example.com",
            "+48 444 555 666",
            LocalDate.of(2026, 2, 1),
            LocalDate.of(2027, 1, 31),
            Boolean.TRUE,
            java.util.List.of(
                term(CashFlowTypeModel.RENT, 4000, false),
                term(CashFlowTypeModel.UTILITIES, 500, true)));
    entityManager.flush();
    entityManager.clear();

    assertThat(updated.getId()).isEqualTo(contractId);
    var reloaded = contracts.findById(contractId).orElseThrow();
    assertThat(reloaded.getTenantName()).isEqualTo("Tenant Two");
    assertThat(reloaded.getTerms()).hasSize(2);
    assertThat(
            entityManager
                .createNativeQuery(
                    "SELECT count(*) FROM investory.long_term_asset_rental_contract_terms "
                        + "WHERE contract_id = :contractId",
                    Long.class)
                .setParameter("contractId", contractId)
                .getSingleResult())
        .isEqualTo(2L);

    service.delete(1L, asset.getId(), contractId);
    entityManager.flush();
    entityManager.clear();

    assertThat(contracts.findById(contractId)).isEmpty();
    assertThat(
            entityManager
                .createNativeQuery(
                    "SELECT count(*) FROM investory.long_term_asset_rental_contract_terms "
                        + "WHERE contract_id = :contractId",
                    Long.class)
                .setParameter("contractId", contractId)
                .getSingleResult())
        .isEqualTo(0L);
  }

  @Test
  void rolloverFlushesClosedPredecessorBeforeInsertingSuccessor() {
    var asset = new LongTermAssetEntity();
    asset.setPortfolioId(1L);
    asset.setName("Rental rollover persistence test");
    asset.setType(LongTermAssetType.REAL_ESTATE);
    asset.setCurrency(CurrencyType.PLN);
    asset.setCurrentValue(new BigDecimal("800000"));
    asset.setTaxBase(new BigDecimal("3000"));
    asset.setActive(true);
    asset = assets.save(asset);

    var previous =
        service.create(
            1L,
            asset.getId(),
            "Tenant One",
            null,
            null,
            LocalDate.of(2026, 1, 1),
            null,
            null,
            java.util.List.of(term(CashFlowTypeModel.RENT, 3000, false)),
            false);

    var successor =
        service.create(
            1L,
            asset.getId(),
            "Tenant Two",
            null,
            null,
            LocalDate.of(2027, 1, 1),
            null,
            null,
            java.util.List.of(term(CashFlowTypeModel.RENT, 3200, false)),
            true);
    entityManager.flush();

    assertThat(contracts.findById(previous.getId()).orElseThrow().getEndDate())
        .isEqualTo(LocalDate.of(2026, 12, 31));
    assertThat(successor.getId()).isNotNull();
  }

  @Test
  void bootstrapCanMoveBoundaryLaterWithoutTransientContractOverlap() {
    bootstrap.importDocument(
        bootstrapDocument(
            flow("3000", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)),
            flow("3500", LocalDate.of(2026, 7, 1), null)),
        false);

    bootstrap.importDocument(
        bootstrapDocument(
            flow("3000", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 7, 31)),
            flow("3500", LocalDate.of(2026, 8, 1), null)),
        false);
    entityManager.flush();
    entityManager.clear();

    var asset = assets.findByPortfolioIdAndExternalKey(1L, "boundary-test-property").orElseThrow();
    var stored = contracts.findAllByAssetIdOrderByStartDate(asset.getId());
    assertThat(stored).hasSize(2).allMatch(contract -> contract.isBootstrapManaged());
    assertThat(stored.get(0).getStartDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(stored.get(0).getEndDate()).isEqualTo(LocalDate.of(2026, 7, 31));
    assertThat(stored.get(1).getStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    assertThat(stored.get(1).getEndDate()).isNull();
  }

  @Test
  void rentalEconomicsViewUsesAnnualizedContractTaxSnapshot() {
    var asset = new LongTermAssetEntity();
    asset.setPortfolioId(1L);
    asset.setName("Rental view tax snapshot test");
    asset.setType(LongTermAssetType.REAL_ESTATE);
    asset.setCurrency(CurrencyType.PLN);
    asset.setCurrentValue(new BigDecimal("800000"));
    asset.setTaxBase(new BigDecimal("1000"));
    asset.setActive(true);
    asset = assets.save(asset);

    var contract =
        service.create(
            1L,
            asset.getId(),
            null,
            null,
            null,
            LocalDate.of(2026, 1, 1),
            null,
            false,
            java.util.List.of(term(CashFlowTypeModel.RENT, 3000, false)),
            false);
    asset.setTaxBase(new BigDecimal("9000"));
    assets.save(asset);
    entityManager.flush();
    entityManager.clear();

    var row =
        (Object[])
            entityManager
                .createNativeQuery(
                    "SELECT tax_base, rental_tax_rate, rental_tax "
                        + "FROM investory.v_long_term_asset_rental_economics "
                        + "WHERE contract_id = :contractId")
                .setParameter("contractId", contract.getId())
                .getSingleResult();
    var taxBase = (BigDecimal) row[0];
    var taxRate = (BigDecimal) row[1];
    var rentalTax = (BigDecimal) row[2];
    assertThat(taxBase).isEqualByComparingTo("1000");
    assertThat(rentalTax)
        .isEqualByComparingTo(taxBase.multiply(BigDecimal.valueOf(12)).multiply(taxRate));
  }

  @Test
  void rentalEconomicsViewUsesPolicyEffectiveForActiveContractToday() {
    var oldPolicy = new RentalTaxPolicyEntity();
    oldPolicy.setPortfolioId(1L);
    oldPolicy.setValidFrom(LocalDate.of(2020, 1, 1));
    oldPolicy.setValidTo(LocalDate.of(2020, 12, 31));
    oldPolicy.setRate(new BigDecimal("0.08"));
    taxPolicies.save(oldPolicy);
    var currentPolicy = new RentalTaxPolicyEntity();
    currentPolicy.setPortfolioId(1L);
    currentPolicy.setValidFrom(LocalDate.of(2021, 1, 1));
    currentPolicy.setRate(new BigDecimal("0.10"));
    taxPolicies.save(currentPolicy);

    var asset = new LongTermAssetEntity();
    asset.setPortfolioId(1L);
    asset.setName("Rental view effective policy test");
    asset.setType(LongTermAssetType.REAL_ESTATE);
    asset.setCurrency(CurrencyType.PLN);
    asset.setCurrentValue(new BigDecimal("800000"));
    asset.setTaxBase(new BigDecimal("1000"));
    asset.setActive(true);
    asset = assets.save(asset);
    var contract =
        service.create(
            1L,
            asset.getId(),
            null,
            null,
            null,
            LocalDate.of(2020, 1, 1),
            null,
            false,
            java.util.List.of(term(CashFlowTypeModel.RENT, 3000, false)),
            false);
    entityManager.flush();
    entityManager.clear();

    var rate =
        (BigDecimal)
            entityManager
                .createNativeQuery(
                    "SELECT rental_tax_rate "
                        + "FROM investory.v_long_term_asset_rental_economics "
                        + "WHERE contract_id = :contractId")
                .setParameter("contractId", contract.getId())
                .getSingleResult();

    assertThat(rate).isEqualByComparingTo("0.10");
  }

  private static LongTermAssetBootstrapDocument bootstrapDocument(
      LongTermAssetBootstrapDocument.CashFlow... flows) {
    var asset =
        new LongTermAssetBootstrapDocument.AssetEntity(
            "boundary-test-property",
            LongTermAssetType.REAL_ESTATE,
            "Boundary test property",
            CurrencyType.PLN,
            LocalDate.of(2020, 1, 1),
            new BigDecimal("600000"),
            new BigDecimal("800000"),
            LocalDate.of(2026, 1, 1),
            null,
            java.util.List.of(flows),
            java.util.List.of(),
            java.util.List.of(),
            null,
            null,
            new BigDecimal("1000"),
            false);
    return new LongTermAssetBootstrapDocument(1L, java.util.List.of(), java.util.List.of(asset));
  }

  private static LongTermAssetBootstrapDocument.CashFlow flow(
      String amount, LocalDate from, LocalDate to) {
    return new LongTermAssetBootstrapDocument.CashFlow(
        CashFlowType.RENT, new BigDecimal(amount), Frequency.MONTHLY, from, to);
  }

  private static RentalContractModel.Term term(
      CashFlowTypeModel type, int amount, boolean tenantPaid) {
    return new RentalContractModel.Term(
        type,
        BigDecimal.valueOf(amount),
        type == CashFlowTypeModel.PROPERTY_TAX || type == CashFlowTypeModel.INSURANCE
            ? FrequencyModel.ANNUAL
            : FrequencyModel.MONTHLY,
        tenantPaid);
  }
}
