package com.smartbox.investory.longterm.application;

import com.smartbox.investory.longterm.api.RentalContract;
import com.smartbox.investory.longterm.infrastructure.*;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contract-first rental history. Legacy cash-flow rows remain readable for migration compatibility. */
@Service
@RequiredArgsConstructor
@Transactional
public class RentalContractService {
  private final LongTermAssetRepository assets;
  private final LongTermAssetRentalContractRepository contracts;

  @Transactional(readOnly = true)
  public List<LongTermAssetRentalContract> list(Long portfolioId, Long assetId) {
    owned(portfolioId, assetId);
    return contracts.findAllByAssetIdOrderByStartDate(assetId);
  }

  @Transactional(readOnly = true)
  public Optional<LongTermAssetRentalContract> effective(Long portfolioId, Long assetId, LocalDate date) {
    return list(portfolioId, assetId).stream()
        .filter(c -> applies(c, date))
        .max(Comparator.comparing(LongTermAssetRentalContract::getStartDate));
  }

  /** Latest known contract is the projection baseline, including a known future contract. */
  @Transactional(readOnly = true)
  public Optional<LongTermAssetRentalContract> latestKnown(Long portfolioId, Long assetId) {
    return list(portfolioId, assetId).stream()
        .max(Comparator.comparing(LongTermAssetRentalContract::getStartDate));
  }

  public LongTermAssetRentalContract create(
      Long portfolioId, Long assetId, LocalDate start, LocalDate end, List<RentalContract.Term> terms) {
    owned(portfolioId, assetId);
    if (start == null || (end != null && end.isBefore(start))) throw new IllegalArgumentException("Invalid contract period");
    var all = contracts.findAllByAssetIdOrderByStartDate(assetId);
    for (var old : all) {
      if (!overlaps(old.getStartDate(), effectiveEnd(old), start, end)) continue;
      if (old.getStartDate().isBefore(start) && (effectiveEnd(old) == null || !effectiveEnd(old).isBefore(start))) {
        old.setTerminatedDate(start.minusDays(1));
        contracts.save(old);
      } else throw new IllegalArgumentException("Overlapping rental contract");
    }
    if (terms == null || terms.isEmpty()) throw new IllegalArgumentException("At least one contract term is required");
    var contract = new LongTermAssetRentalContract();
    contract.setAssetId(assetId); contract.setStartDate(start); contract.setEndDate(end);
    for (var input : terms) {
      if (input == null || input.type() == null || input.frequency() == null || input.amount() == null || input.amount().signum() < 0)
        throw new IllegalArgumentException("Invalid rental contract term");
      var term = new LongTermAssetRentalContractTerm();
      term.setContract(contract); term.setType(input.type()); term.setAmount(input.amount());
      term.setFrequency(input.frequency()); term.setPaidByTenant(input.paidByTenant());
      contract.getTerms().add(term);
    }
    return contracts.save(contract);
  }

  public LongTermAssetRentalContract end(Long portfolioId, Long assetId, Long contractId, LocalDate end) {
    var contract = ownedContract(portfolioId, assetId, contractId);
    if (end == null || end.isBefore(contract.getStartDate())) throw new IllegalArgumentException("Invalid contract end date");
    contract.setEndDate(end); return contracts.save(contract);
  }

  public void terminate(Long portfolioId, Long assetId, Long contractId, LocalDate date) {
    var contract = ownedContract(portfolioId, assetId, contractId);
    if (date == null || date.isBefore(contract.getStartDate())) throw new IllegalArgumentException("Invalid termination date");
    if (contract.getEndDate() != null && date.isAfter(contract.getEndDate())) throw new IllegalArgumentException("Termination cannot follow contract end date");
    contract.setTerminatedDate(date);
    contracts.save(contract);
  }

  public static boolean applies(LongTermAssetRentalContract c, LocalDate date) {
    LocalDate end = effectiveEnd(c);
    return date != null && !date.isBefore(c.getStartDate()) && (end == null || !date.isAfter(end));
  }

  private static LocalDate effectiveEnd(LongTermAssetRentalContract c) {
    if (c.getEndDate() == null) return c.getTerminatedDate();
    if (c.getTerminatedDate() == null) return c.getEndDate();
    return c.getEndDate().isBefore(c.getTerminatedDate()) ? c.getEndDate() : c.getTerminatedDate();
  }

  private LongTermAsset owned(Long portfolioId, Long assetId) {
    return assets.findByIdAndPortfolioId(assetId, portfolioId).orElseThrow(() -> new NoSuchElementException("Long-term asset not found"));
  }
  private LongTermAssetRentalContract ownedContract(Long portfolioId, Long assetId, Long id) {
    owned(portfolioId, assetId);
    return contracts.findById(id).filter(c -> Objects.equals(c.getAssetId(), assetId)).orElseThrow(() -> new NoSuchElementException("Rental contract not found"));
  }
  private static boolean overlaps(LocalDate a, LocalDate b, LocalDate c, LocalDate d) {
    return !a.isAfter(d == null ? LocalDate.MAX : d) && !c.isAfter(b == null ? LocalDate.MAX : b);
  }
}
