package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.longterm.infrastructure.asset.*;
import com.smartbox.investory.longterm.infrastructure.rental.*;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contract-first rental history. Legacy cash-flow rows remain readable for migration compatibility.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RentalContractService {
  private final LongTermAssetRepository assets;
  private final LongTermAssetRentalContractRepository contracts;

  @Transactional(readOnly = true)
  public List<LongTermAssetRentalContractEntity> list(Long portfolioId, Long assetId) {
    var asset = owned(portfolioId, assetId);
    if (asset.getType() != LongTermAssetType.REAL_ESTATE)
      throw new IllegalArgumentException("Rental contracts apply only to real-estate assets");
    return contracts.findAllByAssetIdOrderByStartDate(assetId);
  }

  @Transactional(readOnly = true)
  public Optional<LongTermAssetRentalContractEntity> effective(
      Long portfolioId, Long assetId, LocalDate date) {
    return list(portfolioId, assetId).stream()
        .filter(c -> applies(c, date))
        .max(Comparator.comparing(LongTermAssetRentalContractEntity::getStartDate));
  }

  /** Latest known contract is the projection baseline, including a known future contract. */
  @Transactional(readOnly = true)
  public Optional<LongTermAssetRentalContractEntity> latestKnown(Long portfolioId, Long assetId) {
    return list(portfolioId, assetId).stream()
        .max(Comparator.comparing(LongTermAssetRentalContractEntity::getStartDate));
  }

  public LongTermAssetRentalContractEntity create(
      Long portfolioId,
      Long assetId,
      LocalDate start,
      LocalDate end,
      List<RentalContractModel.Term> terms) {
    return create(portfolioId, assetId, start, end, null, terms);
  }

  public LongTermAssetRentalContractEntity create(
      Long portfolioId,
      Long assetId,
      LocalDate start,
      LocalDate end,
      Boolean taxPaidByTenant,
      List<RentalContractModel.Term> terms) {
    var asset = owned(portfolioId, assetId);
    if (asset.getType() != LongTermAssetType.REAL_ESTATE)
      throw new IllegalArgumentException("Rental contracts apply only to real-estate assets");
    if (start == null || (end != null && end.isBefore(start)))
      throw new IllegalArgumentException("Invalid contract period");
    var all = contracts.findAllByAssetIdOrderByStartDate(assetId);
    var supplied = validatedTerms(terms);
    var suppliedTypes = EnumSet.noneOf(CashFlowType.class);
    supplied.forEach(term -> suppliedTypes.add(term.getType()));
    var previous =
        all.stream()
            .filter(old -> old.getStartDate().isBefore(start))
            .max(Comparator.comparing(LongTermAssetRentalContractEntity::getStartDate))
            .orElse(null);
    for (var old : all) {
      if (!overlaps(old.getStartDate(), effectiveEnd(old), start, end)) continue;
      if (old.getStartDate().isBefore(start)
          && (effectiveEnd(old) == null || !effectiveEnd(old).isBefore(start))) {
        old.setTerminatedDate(start.minusDays(1));
        contracts.save(old);
      } else throw new IllegalArgumentException("Overlapping rental contract");
    }
    var contract = new LongTermAssetRentalContractEntity();
    contract.setAssetId(assetId);
    contract.setStartDate(start);
    contract.setEndDate(end);
    contract.setRentalTaxPaidByTenant(taxPaidByTenant);
    supplied.forEach(term -> addTerm(contract, term));
    if (previous != null)
      previous.getTerms().stream()
          .filter(term -> !suppliedTypes.contains(term.getType()))
          .forEach(term -> addTerm(contract, term));
    return contracts.save(contract);
  }

  public LongTermAssetRentalContractEntity end(
      Long portfolioId, Long assetId, Long contractId, LocalDate end) {
    var contract = ownedContract(portfolioId, assetId, contractId);
    if (end == null || end.isBefore(contract.getStartDate()))
      throw new IllegalArgumentException("Invalid contract end date");
    if (contract.getTerminatedDate() != null && end.isAfter(contract.getTerminatedDate()))
      throw new IllegalArgumentException("Cannot extend a terminated contract");
    contracts.findAllByAssetIdOrderByStartDate(assetId).stream()
        .filter(other -> !Objects.equals(other.getId(), contractId))
        .filter(
            other ->
                overlaps(other.getStartDate(), effectiveEnd(other), contract.getStartDate(), end))
        .findAny()
        .ifPresent(
            other -> {
              throw new IllegalArgumentException("Overlapping rental contract");
            });
    contract.setEndDate(end);
    return contracts.save(contract);
  }

  public void terminate(Long portfolioId, Long assetId, Long contractId, LocalDate date) {
    var contract = ownedContract(portfolioId, assetId, contractId);
    if (date == null || date.isBefore(contract.getStartDate()))
      throw new IllegalArgumentException("Invalid termination date");
    if (contract.getEndDate() != null && date.isAfter(contract.getEndDate()))
      throw new IllegalArgumentException("Termination cannot follow contract end date");
    contracts.findAllByAssetIdOrderByStartDate(assetId).stream()
        .filter(other -> !Objects.equals(other.getId(), contractId))
        .filter(
            other ->
                overlaps(other.getStartDate(), effectiveEnd(other), contract.getStartDate(), date))
        .findAny()
        .ifPresent(
            other -> {
              throw new IllegalArgumentException("Overlapping rental contract");
            });
    contract.setTerminatedDate(date);
    contracts.save(contract);
  }

  private static List<LongTermAssetRentalContractTermEntity> validatedTerms(
      List<RentalContractModel.Term> inputs) {
    List<LongTermAssetRentalContractTermEntity> result = new ArrayList<>();
    EnumSet<CashFlowType> types = EnumSet.noneOf(CashFlowType.class);
    for (var input : inputs == null ? List.<RentalContractModel.Term>of() : inputs) {
      if (input == null
          || input.type() == null
          || input.frequency() == null
          || input.amount() == null
          || input.amount().signum() < 0)
        throw new IllegalArgumentException("Invalid rental contract term");
      CashFlowType type = CashFlowType.valueOf(input.type().name());
      if (!types.add(type)) throw new IllegalArgumentException("Duplicate rental contract term");
      var term = new LongTermAssetRentalContractTermEntity();
      term.setType(type);
      term.setAmount(input.amount());
      term.setFrequency(Frequency.valueOf(input.frequency().name()));
      term.setPaidByTenant(input.paidByTenant());
      result.add(term);
    }
    return result;
  }

  private static void addTerm(
      LongTermAssetRentalContractEntity contract, LongTermAssetRentalContractTermEntity source) {
    var term = new LongTermAssetRentalContractTermEntity();
    term.setContract(contract);
    term.setType(source.getType());
    term.setAmount(source.getAmount());
    term.setFrequency(source.getFrequency());
    term.setPaidByTenant(source.isPaidByTenant());
    contract.getTerms().add(term);
  }

  public static boolean applies(LongTermAssetRentalContractEntity c, LocalDate date) {
    LocalDate end = effectiveEnd(c);
    return date != null && !date.isBefore(c.getStartDate()) && (end == null || !date.isAfter(end));
  }

  private static LocalDate effectiveEnd(LongTermAssetRentalContractEntity c) {
    if (c.getEndDate() == null) return c.getTerminatedDate();
    if (c.getTerminatedDate() == null) return c.getEndDate();
    return c.getEndDate().isBefore(c.getTerminatedDate()) ? c.getEndDate() : c.getTerminatedDate();
  }

  private LongTermAssetEntity owned(Long portfolioId, Long assetId) {
    return assets
        .findByIdAndPortfolioId(assetId, portfolioId)
        .orElseThrow(() -> new NoSuchElementException("Long-term asset not found"));
  }

  private LongTermAssetRentalContractEntity ownedContract(Long portfolioId, Long assetId, Long id) {
    owned(portfolioId, assetId);
    return contracts
        .findById(id)
        .filter(c -> Objects.equals(c.getAssetId(), assetId))
        .orElseThrow(() -> new NoSuchElementException("Rental contract not found"));
  }

  private static boolean overlaps(LocalDate a, LocalDate b, LocalDate c, LocalDate d) {
    return !a.isAfter(d == null ? LocalDate.MAX : d) && !c.isAfter(b == null ? LocalDate.MAX : b);
  }
}
