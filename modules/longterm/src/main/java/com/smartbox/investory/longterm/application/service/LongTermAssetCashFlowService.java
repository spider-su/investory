package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.infrastructure.asset.*;
import com.smartbox.investory.longterm.infrastructure.rental.*;
import com.smartbox.investory.longterm.infrastructure.rental.Frequency;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns effective-dated real-estate cash-flow mutations and their atomic validation. */
@Service
@RequiredArgsConstructor
@Transactional
public class LongTermAssetCashFlowService {
  private final LongTermAssetRepository assets;
  private final LongTermAssetCashFlowRepository cashFlows;

  public LongTermAssetCashFlowEntity add(
      Long portfolioId, Long assetId, LongTermAssetCashFlowEntity flow) {
    LongTermAssetEntity asset = owned(portfolioId, assetId);
    requireRealEstate(asset);
    flow.setAssetId(assetId);
    validate(flow);
    return cashFlows.save(flow);
  }

  public void saveRentalPeriod(
      Long portfolioId,
      Long assetId,
      LocalDate effectiveFrom,
      LocalDate endDate,
      List<LongTermAssetCashFlowEntity> current) {
    owned(portfolioId, assetId);
    validateRange(effectiveFrom, endDate);
    Set<Long> editedIds =
        current.stream()
            .map(LongTermAssetCashFlowEntity::getId)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    List<LongTermAssetCashFlowEntity> all = cashFlows.findAllByAssetIdOrderByValidFrom(assetId);
    for (LongTermAssetCashFlowEntity flow : current)
      LongTermAssetPeriodRules.rejectOverlap(all, flow, effectiveFrom, endDate, editedIds);
    for (LongTermAssetCashFlowEntity flow : current) {
      flow.setValidFrom(effectiveFrom);
      flow.setValidTo(endDate);
      LongTermAssetPeriodRules.ensurePaidByTenant(flow);
    }
    current.forEach(cashFlows::save);
  }

  public LongTermAssetCashFlowEntity change(
      Long portfolioId,
      Long assetId,
      Long flowId,
      BigDecimal amount,
      Frequency frequency,
      LocalDate effectiveFrom,
      LocalDate validTo) {
    owned(portfolioId, assetId);
    LongTermAssetCashFlowEntity old =
        cashFlows
            .findById(flowId)
            .filter(f -> Objects.equals(f.getAssetId(), assetId))
            .orElseThrow(() -> new NoSuchElementException("Cash flow not found"));
    validateRange(effectiveFrom, validTo);
    if (amount == null || amount.signum() < 0)
      throw new IllegalArgumentException("Amount must be non-negative");
    LongTermAssetPeriodRules.ensurePaidByTenant(old);
    if (effectiveFrom.equals(old.getValidFrom())) {
      old.setAmount(amount);
      old.setFrequency(frequency);
      old.setValidTo(validTo);
      return cashFlows.save(old);
    }
    if (effectiveFrom.isBefore(old.getValidFrom()))
      throw new IllegalArgumentException("Effective date cannot precede the current period");
    if (old.getValidTo() != null && effectiveFrom.isAfter(old.getValidTo()))
      throw new IllegalArgumentException("Effective date is outside the current period");
    old.setValidTo(effectiveFrom.minusDays(1));
    cashFlows.save(old);
    LongTermAssetCashFlowEntity replacement = new LongTermAssetCashFlowEntity();
    replacement.setAssetId(assetId);
    replacement.setType(old.getType());
    replacement.setAmount(amount);
    replacement.setFrequency(frequency);
    replacement.setValidFrom(effectiveFrom);
    replacement.setValidTo(validTo);
    replacement.setPaidByTenant(old.isPaidByTenant());
    return cashFlows.save(replacement);
  }

  public void delete(Long portfolioId, Long assetId, Long flowId) {
    owned(portfolioId, assetId);
    LongTermAssetCashFlowEntity flow =
        cashFlows
            .findById(flowId)
            .filter(f -> Objects.equals(f.getAssetId(), assetId))
            .orElseThrow(() -> new NoSuchElementException("Cash flow not found"));
    cashFlows.delete(flow);
  }

  public void setPaidByTenant(Long portfolioId, Long assetId, Long flowId, boolean value) {
    owned(portfolioId, assetId);
    LongTermAssetCashFlowEntity flow =
        cashFlows
            .findById(flowId)
            .filter(f -> Objects.equals(f.getAssetId(), assetId))
            .orElseThrow(() -> new NoSuchElementException("Cash flow not found"));
    if (!isExpense(flow.getType()))
      throw new IllegalArgumentException("Tenant ownership applies only to expenses");
    flow.setPaidByTenant(value);
    cashFlows.save(flow);
  }

  private void validate(LongTermAssetCashFlowEntity flow) {
    validateRange(flow.getValidFrom(), flow.getValidTo());
    if (flow.getAmount() == null || flow.getAmount().signum() < 0)
      throw new IllegalArgumentException("Amount must be non-negative");
    LongTermAssetPeriodRules.ensurePaidByTenant(flow);
    LongTermAssetPeriodRules.rejectOverlap(
        cashFlows.findAllByAssetIdOrderByValidFrom(flow.getAssetId()),
        flow,
        flow.getValidFrom(),
        flow.getValidTo(),
        flow.getId() == null ? Set.of() : Set.of(flow.getId()));
  }

  private LongTermAssetEntity owned(Long portfolioId, Long assetId) {
    return assets
        .findByIdAndPortfolioId(assetId, portfolioId)
        .orElseThrow(() -> new NoSuchElementException("Long-term asset not found"));
  }

  private static void requireRealEstate(LongTermAssetEntity asset) {
    if (asset.getType()
        != com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType.REAL_ESTATE)
      throw new IllegalArgumentException("Cash flows apply only to real estate");
  }

  private static boolean isExpense(
      com.smartbox.investory.longterm.infrastructure.rental.CashFlowType type) {
    return switch (type) {
      case ADMIN_FEE, UTILITIES, PROPERTY_TAX, INSURANCE, OTHER_EXPENSE -> true;
      default -> false;
    };
  }

  private static void validateRange(LocalDate from, LocalDate to) {
    if (from == null || (to != null && to.isBefore(from)))
      throw new IllegalArgumentException("Invalid period");
  }
}
