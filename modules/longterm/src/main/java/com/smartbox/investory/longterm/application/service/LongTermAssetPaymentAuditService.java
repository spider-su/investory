package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.LongTermAssetPaymentAuditReader;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetEntity;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetRepository;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractEntity;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LongTermAssetPaymentAuditService implements LongTermAssetPaymentAuditReader {
  private final LongTermAssetRepository assets;
  private final LongTermAssetRentalContractRepository contracts;

  public LongTermAssetPaymentAuditService(
      LongTermAssetRepository assets, LongTermAssetRentalContractRepository contracts) {
    this.assets = assets;
    this.contracts = contracts;
  }

  @Override
  public List<PaymentAuditRow> paymentAudit(Long portfolioId, LocalDate date) {
    List<LongTermAssetEntity> portfolioAssets = assets.findAllByPortfolioIdOrderByName(portfolioId);
    if (portfolioAssets.isEmpty()) return List.of();
    var names =
        portfolioAssets.stream()
            .filter(LongTermAssetEntity::isActive)
            .collect(
                java.util.stream.Collectors.toMap(
                    LongTermAssetEntity::getId, LongTermAssetEntity::getName));
    var currencies =
        portfolioAssets.stream()
            .filter(LongTermAssetEntity::isActive)
            .collect(
                java.util.stream.Collectors.toMap(
                    LongTermAssetEntity::getId, LongTermAssetEntity::getCurrency));
    var rows =
        contracts.findAllWithTermsByAssetIdIn(names.keySet()).stream()
            .filter(contract -> RentalContractService.applies(contract, date))
            .map(contract -> row(contract, names, currencies))
            .filter(java.util.Objects::nonNull)
            .toList();
    return rows;
  }

  private PaymentAuditRow row(
      LongTermAssetRentalContractEntity contract,
      java.util.Map<Long, String> names,
      java.util.Map<Long, com.smartbox.investory.shared.currency.CurrencyType> currencies) {
    BigDecimal total =
        contract.getTerms().stream()
            .filter(term -> term.isPaidByTenant())
            .map(
                term ->
                    term.getFrequency()
                            == com.smartbox.investory.longterm.api.model.Frequency.ANNUAL
                        ? term.getAmount().divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP)
                        : term.getAmount())
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
