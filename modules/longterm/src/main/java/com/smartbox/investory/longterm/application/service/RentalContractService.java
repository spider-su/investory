package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.model.RentalContractModel;
import com.smartbox.investory.longterm.api.model.RentalContractStatusModel;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetEntity;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetRepository;
import com.smartbox.investory.longterm.infrastructure.asset.LongTermAssetType;
import com.smartbox.investory.longterm.infrastructure.rental.CashFlowType;
import com.smartbox.investory.longterm.infrastructure.rental.Frequency;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractEntity;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractRepository;
import com.smartbox.investory.longterm.infrastructure.rental.LongTermAssetRentalContractTermEntity;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contract-first rental history. Legacy cash-flow rows remain migration compatibility only. */
@Service
@RequiredArgsConstructor
@Transactional
public class RentalContractService {
  private static final Pattern EMAIL =
      Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", Pattern.CASE_INSENSITIVE);

  private final LongTermAssetRepository assets;
  private final LongTermAssetRentalContractRepository contracts;
  private final Clock clock;

  @Transactional(readOnly = true)
  public List<LongTermAssetRentalContractEntity> list(Long portfolioId, Long assetId) {
    requireRealEstate(portfolioId, assetId);
    return contracts.findAllByAssetIdOrderByStartDateDescIdDesc(assetId);
  }

  @Transactional(readOnly = true)
  public Optional<LongTermAssetRentalContractEntity> effective(
      Long portfolioId, Long assetId, LocalDate date) {
    return list(portfolioId, assetId).stream().filter(c -> applies(c, date)).findFirst();
  }

  /** Latest known contract is the projection baseline, including a known future contract. */
  @Transactional(readOnly = true)
  public Optional<LongTermAssetRentalContractEntity> latestKnown(Long portfolioId, Long assetId) {
    return list(portfolioId, assetId).stream().findFirst();
  }

  public LongTermAssetRentalContractEntity create(
      Long portfolioId,
      Long assetId,
      LocalDate start,
      LocalDate end,
      List<RentalContractModel.Term> terms) {
    return create(portfolioId, assetId, null, null, null, start, end, null, terms, false);
  }

  public LongTermAssetRentalContractEntity create(
      Long portfolioId,
      Long assetId,
      LocalDate start,
      LocalDate end,
      Boolean taxPaidByTenant,
      List<RentalContractModel.Term> terms) {
    return create(
        portfolioId, assetId, null, null, null, start, end, taxPaidByTenant, terms, false);
  }

  public LongTermAssetRentalContractEntity create(
      Long portfolioId,
      Long assetId,
      String tenantName,
      String tenantEmail,
      String tenantPhone,
      LocalDate start,
      LocalDate end,
      Boolean taxPaidByTenant,
      List<RentalContractModel.Term> terms,
      boolean endCurrentContractBeforeStart) {
    return create(
        portfolioId,
        assetId,
        tenantName,
        tenantEmail,
        tenantPhone,
        start,
        end,
        null,
        taxPaidByTenant,
        terms,
        endCurrentContractBeforeStart);
  }

  public LongTermAssetRentalContractEntity create(
      Long portfolioId,
      Long assetId,
      String tenantName,
      String tenantEmail,
      String tenantPhone,
      LocalDate start,
      LocalDate end,
      BigDecimal monthlyTaxBase,
      Boolean taxPaidByTenant,
      List<RentalContractModel.Term> terms,
      boolean endCurrentContractBeforeStart) {
    LongTermAssetEntity asset = requireRealEstate(portfolioId, assetId);
    validatePeriod(start, end, null);
    validateMonthlyTaxBase(monthlyTaxBase);
    Tenant tenant = validateTenant(tenantName, tenantEmail, tenantPhone);
    List<LongTermAssetRentalContractTermEntity> supplied = validatedTerms(terms);
    List<LongTermAssetRentalContractEntity> all =
        contracts.findAllByAssetIdOrderByStartDateDescIdDesc(assetId);

    if (endCurrentContractBeforeStart) closePreviousExpectedPeriod(all, start);
    rejectOverlap(all, null, start, end);

    var contract = new LongTermAssetRentalContractEntity();
    contract.setAssetId(assetId);
    contract.setMonthlyTaxBase(monthlyTaxBase == null ? asset.getTaxBase() : monthlyTaxBase);
    replaceContractState(
        contract,
        tenant,
        start,
        end,
        taxPaidByTenant == null ? asset.isRentalTaxPaidByTenant() : taxPaidByTenant,
        supplied,
        null);
    return contracts.save(contract);
  }

  public LongTermAssetRentalContractEntity update(
      Long portfolioId,
      Long assetId,
      Long contractId,
      String tenantName,
      String tenantEmail,
      String tenantPhone,
      LocalDate start,
      LocalDate end,
      Boolean taxPaidByTenant,
      List<RentalContractModel.Term> terms) {
    return update(
        portfolioId,
        assetId,
        contractId,
        tenantName,
        tenantEmail,
        tenantPhone,
        start,
        end,
        null,
        taxPaidByTenant,
        false,
        terms);
  }

  public LongTermAssetRentalContractEntity update(
      Long portfolioId,
      Long assetId,
      Long contractId,
      String tenantName,
      String tenantEmail,
      String tenantPhone,
      LocalDate start,
      LocalDate end,
      BigDecimal monthlyTaxBase,
      Boolean taxPaidByTenant,
      boolean usePropertyTaxPayerDefault,
      List<RentalContractModel.Term> terms) {
    var contract = ownedContract(portfolioId, assetId, contractId);
    validatePeriod(start, end, contract.getTerminatedDate());
    validateMonthlyTaxBase(monthlyTaxBase);
    Tenant tenant = validateTenant(tenantName, tenantEmail, tenantPhone);
    List<LongTermAssetRentalContractTermEntity> supplied = validatedTerms(terms);
    if (monthlyTaxBase != null) contract.setMonthlyTaxBase(monthlyTaxBase);
    else if (contract.getMonthlyTaxBase() == null)
      contract.setMonthlyTaxBase(owned(portfolioId, assetId).getTaxBase());
    rejectOverlap(
        contracts.findAllByAssetIdOrderByStartDateDescIdDesc(assetId),
        contractId,
        start,
        effectiveEnd(end, contract.getTerminatedDate()));
    replaceContractState(
        contract,
        tenant,
        start,
        end,
        usePropertyTaxPayerDefault
            ? owned(portfolioId, assetId).isRentalTaxPaidByTenant()
            : taxPaidByTenant == null
                ? Optional.ofNullable(contract.getRentalTaxPaidByTenant())
                    .orElse(owned(portfolioId, assetId).isRentalTaxPaidByTenant())
                : taxPaidByTenant,
        supplied,
        contract.getTerminatedDate());
    return contracts.save(contract);
  }

  public void delete(Long portfolioId, Long assetId, Long contractId) {
    contracts.delete(ownedContract(portfolioId, assetId, contractId));
  }

  public LongTermAssetRentalContractEntity end(
      Long portfolioId, Long assetId, Long contractId, LocalDate end) {
    var contract = ownedContract(portfolioId, assetId, contractId);
    validatePeriod(contract.getStartDate(), end, contract.getTerminatedDate());
    rejectOverlap(
        contracts.findAllByAssetIdOrderByStartDateDescIdDesc(assetId),
        contractId,
        contract.getStartDate(),
        effectiveEnd(end, contract.getTerminatedDate()));
    contract.setEndDate(end);
    return contracts.save(contract);
  }

  public void terminate(Long portfolioId, Long assetId, Long contractId, LocalDate date) {
    var contract = ownedContract(portfolioId, assetId, contractId);
    if (contract.getTerminatedDate() != null)
      throw new IllegalArgumentException("Rental contract is already terminated");
    if (date != null && date.isAfter(LocalDate.now(clock)))
      throw new IllegalArgumentException("Actual termination date cannot be later than today");
    validatePeriod(contract.getStartDate(), contract.getEndDate(), date);
    rejectOverlap(
        contracts.findAllByAssetIdOrderByStartDateDescIdDesc(assetId),
        contractId,
        contract.getStartDate(),
        effectiveEnd(contract.getEndDate(), date));
    contract.setTerminatedDate(date);
    contracts.save(contract);
  }

  private void closePreviousExpectedPeriod(
      List<LongTermAssetRentalContractEntity> all, LocalDate newStart) {
    LongTermAssetRentalContractEntity previous =
        all.stream()
            .filter(old -> old.getStartDate().isBefore(newStart))
            .max(
                Comparator.comparing(LongTermAssetRentalContractEntity::getStartDate)
                    .thenComparing(LongTermAssetRentalContractEntity::getId))
            .orElse(null);
    if (previous == null
        || !overlaps(previous.getStartDate(), effectiveEnd(previous), newStart, null)) return;
    if (previous.getTerminatedDate() != null)
      throw new IllegalArgumentException("Cannot roll over a terminated rental contract");
    LocalDate expectedEnd = newStart.minusDays(1);
    if (expectedEnd.isBefore(previous.getStartDate()))
      throw new IllegalArgumentException("Invalid rollover period");
    previous.setEndDate(expectedEnd);
    contracts.save(previous);
    // PostgreSQL checks the non-overlap exclusion constraint immediately. Ensure the shortened
    // predecessor is visible before Hibernate inserts its successor.
    contracts.flush();
  }

  private static void rejectOverlap(
      List<LongTermAssetRentalContractEntity> all,
      Long excludedContractId,
      LocalDate start,
      LocalDate end) {
    all.stream()
        .filter(other -> !Objects.equals(other.getId(), excludedContractId))
        .filter(other -> overlaps(other.getStartDate(), effectiveEnd(other), start, end))
        .findAny()
        .ifPresent(
            ignored -> {
              throw new IllegalArgumentException("Overlapping rental contract");
            });
  }

  private static void replaceContractState(
      LongTermAssetRentalContractEntity contract,
      Tenant tenant,
      LocalDate start,
      LocalDate end,
      Boolean taxPaidByTenant,
      List<LongTermAssetRentalContractTermEntity> supplied,
      LocalDate terminatedDate) {
    contract.setTenantName(tenant.name());
    contract.setTenantEmail(tenant.email());
    contract.setTenantPhone(tenant.phone());
    contract.setStartDate(start);
    contract.setEndDate(end);
    contract.setTerminatedDate(terminatedDate);
    contract.setRentalTaxPaidByTenant(taxPaidByTenant);
    replaceTerms(contract, supplied);
  }

  private static void replaceTerms(
      LongTermAssetRentalContractEntity contract,
      List<LongTermAssetRentalContractTermEntity> supplied) {
    Map<CashFlowType, LongTermAssetRentalContractTermEntity> existing = new HashMap<>();
    contract.getTerms().forEach(term -> existing.put(term.getType(), term));
    EnumSet<CashFlowType> retained = EnumSet.noneOf(CashFlowType.class);
    for (var source : supplied) {
      retained.add(source.getType());
      var target = existing.get(source.getType());
      if (target == null) {
        addTerm(contract, source);
      } else {
        target.setAmount(source.getAmount());
        target.setFrequency(source.getFrequency());
        target.setPaidByTenant(source.isPaidByTenant());
      }
    }
    contract.getTerms().removeIf(term -> !retained.contains(term.getType()));
  }

  private static Tenant validateTenant(String name, String email, String phone) {
    String normalizedName = normalize(name);
    String normalizedEmail = normalize(email);
    String normalizedPhone = normalize(phone);
    if (normalizedName != null && normalizedName.length() > 200)
      throw new IllegalArgumentException("Tenant name is too long");
    if (normalizedEmail != null && normalizedEmail.length() > 320)
      throw new IllegalArgumentException("Tenant email is too long");
    if (normalizedEmail != null && !EMAIL.matcher(normalizedEmail).matches())
      throw new IllegalArgumentException("Invalid tenant email");
    if (normalizedPhone != null && normalizedPhone.length() > 50)
      throw new IllegalArgumentException("Tenant phone is too long");
    return new Tenant(normalizedName, normalizedEmail, normalizedPhone);
  }

  private static void validateMonthlyTaxBase(BigDecimal monthlyTaxBase) {
    if (monthlyTaxBase != null && monthlyTaxBase.signum() < 0)
      throw new IllegalArgumentException("Monthly tax base cannot be negative");
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }

  private static void validatePeriod(LocalDate start, LocalDate end, LocalDate termination) {
    if (start == null) throw new IllegalArgumentException("Contract start date is required");
    if (end != null && end.isBefore(start))
      throw new IllegalArgumentException("Expected end cannot precede contract start");
    if (termination != null && termination.isBefore(start))
      throw new IllegalArgumentException("Termination cannot precede contract start");
    if (termination != null && end != null && termination.isAfter(end))
      throw new IllegalArgumentException("Termination cannot follow expected end");
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
        throw new IllegalArgumentException("Invalid rental contract amount");
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

  public static boolean applies(LongTermAssetRentalContractEntity contract, LocalDate date) {
    LocalDate end = effectiveEnd(contract);
    return date != null
        && !date.isBefore(contract.getStartDate())
        && (end == null || !date.isAfter(end));
  }

  public static LocalDate effectiveEnd(LongTermAssetRentalContractEntity contract) {
    return effectiveEnd(contract.getEndDate(), contract.getTerminatedDate());
  }

  public static RentalContractStatusModel status(
      LongTermAssetRentalContractEntity contract, LocalDate date) {
    if (date.isBefore(contract.getStartDate())) return RentalContractStatusModel.UPCOMING;
    LocalDate end = effectiveEnd(contract);
    if (end == null || !date.isAfter(end)) return RentalContractStatusModel.CURRENT;
    return contract.getTerminatedDate() == null
        ? RentalContractStatusModel.ENDED
        : RentalContractStatusModel.TERMINATED;
  }

  private static LocalDate effectiveEnd(LocalDate expectedEnd, LocalDate termination) {
    if (expectedEnd == null) return termination;
    if (termination == null) return expectedEnd;
    return expectedEnd.isBefore(termination) ? expectedEnd : termination;
  }

  private LongTermAssetEntity requireRealEstate(Long portfolioId, Long assetId) {
    LongTermAssetEntity asset = owned(portfolioId, assetId);
    if (asset.getType() != LongTermAssetType.REAL_ESTATE)
      throw new IllegalArgumentException("Rental contracts apply only to real-estate assets");
    return asset;
  }

  private LongTermAssetEntity owned(Long portfolioId, Long assetId) {
    return assets
        .findByIdAndPortfolioId(assetId, portfolioId)
        .orElseThrow(() -> new NoSuchElementException("Long-term asset not found"));
  }

  private LongTermAssetRentalContractEntity ownedContract(Long portfolioId, Long assetId, Long id) {
    requireRealEstate(portfolioId, assetId);
    return contracts
        .findById(id)
        .filter(c -> Objects.equals(c.getAssetId(), assetId))
        .orElseThrow(() -> new NoSuchElementException("Rental contract not found"));
  }

  private static boolean overlaps(LocalDate a, LocalDate b, LocalDate c, LocalDate d) {
    return !a.isAfter(d == null ? LocalDate.MAX : d) && !c.isAfter(b == null ? LocalDate.MAX : b);
  }

  private record Tenant(String name, String email, String phone) {}
}
