package com.smartbox.investory.ryczalt.application.ksef;

import com.smartbox.investory.ryczalt.application.RyczaltCounterpartyService;
import com.smartbox.investory.ryczalt.application.RyczaltInvoicePlnNormalizer;
import com.smartbox.investory.ryczalt.calculation.InputChange;
import com.smartbox.investory.ryczalt.domain.ApprovalMethod;
import com.smartbox.investory.ryczalt.domain.ApprovalStatus;
import com.smartbox.investory.ryczalt.domain.CounterpartyRule;
import com.smartbox.investory.ryczalt.domain.InvoiceCandidate;
import com.smartbox.investory.ryczalt.domain.PaymentVerificationPolicy;
import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import com.smartbox.investory.ryczalt.domain.RuleMatchResult;
import com.smartbox.investory.ryczalt.integration.ksef.InvoiceSourcePort;
import com.smartbox.investory.ryczalt.integration.ksef.InvoiceSourcePort.KsefSyncCommand;
import com.smartbox.investory.ryczalt.integration.ksef.InvoiceSourceRecord;
import com.smartbox.investory.ryczalt.integration.ksef.KsefSyncMode;
import com.smartbox.investory.ryczalt.persistence.FrozenPeriodMutationException;
import com.smartbox.investory.ryczalt.persistence.InvoiceDirection;
import com.smartbox.investory.ryczalt.persistence.RyczaltInvoiceEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltInvoiceJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodLifecycleService;
import com.smartbox.investory.ryczalt.persistence.RyczaltSourceReferenceEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltSourceReferenceJpaRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Native KSeF acquisition. Normalizes provider-neutral invoice facts into canonical Ryczalt
 * invoices, records KSeF provenance, and invalidates the calculations that actually depend on
 * invoices. It performs no tax math and never writes legacy accounting tables. Unresolved
 * accounting classifications are left {@code null} for completeness checks to surface.
 */
@Service
public class RyczaltKsefImportService implements RyczaltKsefApi {
  private static final String ENTITY_TYPE = "INVOICE";
  private static final String SOURCE = "KSEF";
  private static final String ACTOR = "KSEF_SYNC";

  private final InvoiceSourcePort source;
  private final RyczaltPeriodJpaRepository periods;
  private final RyczaltInvoiceJpaRepository invoices;
  private final RyczaltSourceReferenceJpaRepository sourceReferences;
  private final RyczaltPeriodLifecycleService lifecycle;
  private final RyczaltCounterpartyService counterparties;
  private final RyczaltInvoicePlnNormalizer plnNormalizer;

  public RyczaltKsefImportService(
      InvoiceSourcePort source,
      RyczaltPeriodJpaRepository periods,
      RyczaltInvoiceJpaRepository invoices,
      RyczaltSourceReferenceJpaRepository sourceReferences,
      RyczaltPeriodLifecycleService lifecycle,
      RyczaltCounterpartyService counterparties,
      RyczaltInvoicePlnNormalizer plnNormalizer) {
    this.source = source;
    this.periods = periods;
    this.invoices = invoices;
    this.sourceReferences = sourceReferences;
    this.lifecycle = lifecycle;
    this.counterparties = counterparties;
    this.plnNormalizer = plnNormalizer;
  }

  @Override
  @Transactional
  public RyczaltKsefSyncResult sync(long profileId, YearMonth month, Set<KsefSyncMode> modes) {
    return acquire(profileId, month, modes, false);
  }

  @Override
  @Transactional
  public RyczaltKsefSyncResult reimport(long profileId, YearMonth month) {
    return acquire(profileId, month, Set.of(KsefSyncMode.SALES, KsefSyncMode.PURCHASES), true);
  }

  private RyczaltKsefSyncResult acquire(
      long profileId, YearMonth month, Set<KsefSyncMode> modes, boolean reimport) {
    var records = source.fetch(new KsefSyncCommand(month, modes));
    int imported = 0;
    int duplicates = 0;
    int updated = 0;
    int failed = 0;
    Map<InvoiceDirection, Set<YearMonth>> touched = new EnumMap<>(InvoiceDirection.class);
    for (InvoiceSourceRecord record : records) {
      if (record.netAmount() == null
          || record.vatAmount() == null
          || record.grossAmount() == null
          || record.accountingDate() == null) {
        failed++;
        continue;
      }
      var existing =
          sourceReferences.findByProfileIdAndEntityTypeAndSourceAndExternalId(
              profileId, ENTITY_TYPE, SOURCE, record.sourceExternalId());
      if (existing.isPresent() && !reimport) {
        duplicates++;
        continue;
      }
      YearMonth target = YearMonth.from(record.accountingDate());
      var counterparty =
          counterparties.resolveByTaxId(
              profileId,
              record.counterpartyTaxId(),
              record.counterpartyCountry(),
              record.counterpartyName());
      CounterpartyRule rule = null;
      if (counterparty != null) {
        RuleMatchResult match =
            counterparties.match(
                new InvoiceCandidate(counterparty.id(), SOURCE, null, null), profileId);
        if (match.kind() == RuleMatchResult.Kind.MATCHED) rule = match.rule();
      }
      if (existing.isPresent()) {
        RyczaltInvoiceEntity invoice =
            invoices.findById(existing.get().getEntityId()).orElseThrow();
        YearMonth current =
            YearMonth.of(invoice.getPeriod().getYear(), invoice.getPeriod().getMonth());
        requireMutable(profileId, current);
        if (!current.equals(target)) {
          invoice.moveToPeriod(requireMutable(profileId, target));
        }
        var normalized = normalize(record, invoice.getVatDeductionRatio());
        invoice.update(
            record.issueDate(),
            record.accountingDate(),
            record.netAmount(),
            record.vatAmount(),
            record.grossAmount(),
            currency(record.currency()),
            normalized.bookedNetPln(),
            normalized.fxRate(),
            normalized.fxEffectiveDate(),
            normalized.fxProvider(),
            normalized.fxProviderReference());
        invoice.setDerivedPlnValues(
            normalized.bookedNetPln(),
            normalized.bookedVatPln(),
            normalized.deductibleVatPln(),
            normalized.fxRate(),
            normalized.fxEffectiveDate(),
            normalized.fxProvider(),
            normalized.fxProviderReference());
        invoices.save(invoice);
        updated++;
        touched.computeIfAbsent(record.direction(), ignored -> new HashSet<>()).add(current);
        touched.get(record.direction()).add(target);
        continue;
      }
      RyczaltPeriodEntity period = requireMutable(profileId, target);
      CounterpartyRule matchedRule = rule;
      ApprovalStatus status =
          matchedRule != null && matchedRule.autoApprove()
              ? ApprovalStatus.APPROVED
              : ApprovalStatus.NEEDS_REVIEW;
      var normalized =
          normalize(record, matchedRule == null ? null : matchedRule.vatDeductionRatio());
      RyczaltInvoiceEntity saved =
          invoices.save(
              new RyczaltInvoiceEntity(
                  period,
                  profileId,
                  record.direction(),
                  record.reference(),
                  record.issueDate(),
                  record.accountingDate(),
                  record.netAmount(),
                  record.vatAmount(),
                  record.grossAmount(),
                  currency(record.currency()),
                  normalized.bookedNetPln(),
                  rule == null ? record.ryczaltRate() : rule.ryczaltRate(),
                  normalized.deductibleVatPln()));
      saved.setDerivedPlnValues(
          normalized.bookedNetPln(),
          normalized.bookedVatPln(),
          normalized.deductibleVatPln(),
          normalized.fxRate(),
          normalized.fxEffectiveDate(),
          normalized.fxProvider(),
          normalized.fxProviderReference());
      saved.applyDecision(
          counterparty,
          rule == null ? null : rule.classification(),
          rule == null ? null : rule.vatTreatment(),
          rule == null ? null : rule.vatDeductionRatio(),
          rule == null ? record.ryczaltRate() : rule.ryczaltRate(),
          rule == null ? PaymentVerificationPolicy.REQUIRED : rule.paymentVerificationPolicy(),
          status,
          status == ApprovalStatus.APPROVED ? ApprovalMethod.COUNTERPARTY_RULE : null);
      saved = invoices.save(saved);
      sourceReferences.save(
          new RyczaltSourceReferenceEntity(
              profileId, ENTITY_TYPE, saved.getId(), SOURCE, record.sourceExternalId(), null));
      imported++;
      touched.computeIfAbsent(record.direction(), ignored -> new HashSet<>()).add(target);
    }
    touched.forEach(
        (direction, months) ->
            months.forEach(
                m ->
                    lifecycle.invalidate(
                        profileId,
                        m,
                        direction == InvoiceDirection.INCOME
                            ? InputChange.INCOME_INVOICE_CHANGED
                            : InputChange.COST_INVOICE_CHANGED,
                        ACTOR)));
    return new RyczaltKsefSyncResult(records.size(), imported, duplicates, updated, failed);
  }

  private RyczaltPeriodEntity requireMutable(long profileId, YearMonth month) {
    RyczaltPeriodEntity period =
        periods
            .findByProfileIdAndYearAndMonth(profileId, month.getYear(), month.getMonthValue())
            .orElseGet(
                () ->
                    periods.save(
                        new RyczaltPeriodEntity(
                            profileId, month.getYear(), month.getMonthValue(), PeriodStatus.OPEN)));
    if (period.getStatus().isFrozen()) {
      throw new FrozenPeriodMutationException(profileId, month.getYear(), month.getMonthValue());
    }
    return period;
  }

  private static CurrencyType currency(String code) {
    return code == null
        ? CurrencyType.PLN
        : CurrencyType.valueOf(code.toUpperCase(java.util.Locale.ROOT));
  }

  private RyczaltInvoicePlnNormalizer.Normalized normalize(
      InvoiceSourceRecord record, BigDecimal vatDeductionRatio) {
    return plnNormalizer.normalize(
        record.currency(),
        record.accountingDate(),
        record.netAmount(),
        record.vatAmount(),
        vatDeductionRatio);
  }
}
