package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.LongTermAssetPaymentAuditReader;
import com.smartbox.investory.longterm.infrastructure.realestate.RealEstateRepository;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractEntity;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operational payment audit backed only by explicit real-estate persistence. */
@Service
@Transactional(
    readOnly = true,
    isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class LongTermAssetPaymentAuditService implements LongTermAssetPaymentAuditReader {
  private final RealEstateRepository realEstates;
  private final LongTermAssetRentalContractRepository contracts;

  public LongTermAssetPaymentAuditService(
      RealEstateRepository realEstates, LongTermAssetRentalContractRepository contracts) {
    this.realEstates = realEstates;
    this.contracts = contracts;
  }

  @Override
  public List<PaymentAuditRow> paymentAudit(Long portfolioId, LocalDate date) {
    var assets = realEstates.findAllByPortfolioIdAndArchivedAtIsNullOrderByName(portfolioId);
    if (assets.isEmpty()) return List.of();
    var names =
        assets.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    asset -> asset.getId(), asset -> asset.getName()));
    var currencies =
        assets.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    asset -> asset.getId(), asset -> asset.getCurrency()));
    return contracts.findAllWithTermsByAssetIdIn(names.keySet()).stream()
        .filter(contract -> RentalContractService.applies(contract, date))
        .map(contract -> row(contract, names, currencies))
        .filter(java.util.Objects::nonNull)
        .toList();
  }

  private static PaymentAuditRow row(
      LongTermAssetRentalContractEntity contract,
      java.util.Map<Long, String> names,
      java.util.Map<Long, com.smartbox.investory.shared.currency.CurrencyType> currencies) {
    BigDecimal total =
        contract.getTerms().stream()
            .map(
                term ->
                    LongTermAssetEconomics.monthlyTenantPayment(
                        term.getType(),
                        term.getAmount(),
                        term.getFrequency(),
                        term.isPaidByTenant()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (total.signum() == 0) return null;
    return new PaymentAuditRow(
        names.get(contract.getAssetId()),
        contract.getTenantName() == null || contract.getTenantName().isBlank()
            ? "(unnamed tenant)"
            : contract.getTenantName(),
        total,
        currencies.get(contract.getAssetId()));
  }
}
