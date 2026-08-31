package com.smartbox.investory.longterm.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.longterm.api.model.CashFlowTypeModel;
import com.smartbox.investory.longterm.api.model.FrequencyModel;
import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetEntity;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetRepository;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractRepository;
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
  @Autowired LongTermAssetRepository assets;
  @Autowired LongTermAssetRentalContractRepository contracts;
  @Autowired EntityManager entityManager;

  @Test
  void persistsTenantUpdatesTermsInPlaceAndDeletesThroughOrphanRemoval() {
    var asset = new LongTermAssetEntity();
    asset.setPortfolioId(1L);
    asset.setName("Rental persistence test");
    asset.setType(LongTermAssetType.REAL_ESTATE);
    asset.setCurrency(CurrencyType.PLN);
    asset.setCurrentValue(new BigDecimal("800000"));
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
