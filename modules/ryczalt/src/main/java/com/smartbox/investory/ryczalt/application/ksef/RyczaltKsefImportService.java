package com.smartbox.investory.ryczalt.application.ksef;

import com.smartbox.investory.ryczalt.application.RyczaltCounterpartyService;
import com.smartbox.investory.ryczalt.calculation.InputChange;
import com.smartbox.investory.ryczalt.domain.ApprovalMethod;
import com.smartbox.investory.ryczalt.domain.ApprovalStatus;
import com.smartbox.investory.ryczalt.domain.CounterpartyRule;
import com.smartbox.investory.ryczalt.domain.InvoiceCandidate;
import com.smartbox.investory.ryczalt.domain.PaymentVerificationPolicy;
import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import com.smartbox.investory.ryczalt.domain.RuleMatchResult;
import com.smartbox.investory.ryczalt.integration.fx.FxRate;
import com.smartbox.investory.ryczalt.integration.fx.RyczaltFxRateService;
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
import java.math.RoundingMode;
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
  private final RyczaltFxRateService fxRates;

  public RyczaltKsefImportService(
      InvoiceSourcePort source,
      RyczaltPeriodJpaRepository periods,
      RyczaltInvoiceJpaRepository invoices,
      RyczaltSourceReferenceJpaRepository sourceReferences,
      RyczaltPeriodLifecycleService lifecycle,
      RyczaltCounterpartyService counterparties,
      RyczaltFxRateService fxRates) {
    this.source = source;
    this.periods = periods;
    this.invoices = invoices;
    this.sourceReferences = sourceReferences;
    this.lifecycle = lifecycle;
    this.counterparties = counterparties;
    this.fxRates = fxRates;
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
        FxRate fx = fxRate(record);
        invoice.update(
            record.issueDate(),
            record.accountingDate(),
            record.netAmount(),
            record.vatAmount(),
            record.grossAmount(),
            currency(record.currency()),
            bookedNetPln(record, fx),
            fx == null ? null : fx.rate(),
            fx == null ? null : fx.effectiveDate(),
            fx == null ? null : fx.provider(),
            fx == null ? null : fx.providerReference());
        invoice.setBookedVatPln(bookedVatPln(record, fx));
        invoices.save(invoice);
        updated++;
        touched.computeIfAbsent(record.direction(), ignored -> new HashSet<>()).add(current);
        touched.get(record.direction()).add(target);
        continue;
      }
      RyczaltPeriodEntity period = requireMutable(profileId, target);
      FxRate fx = fxRate(record);
      CounterpartyRule matchedRule = rule;
      ApprovalStatus status =
          matchedRule != null && matchedRule.autoApprove()
              ? ApprovalStatus.APPROVED
              : ApprovalStatus.NEEDS_REVIEW;
      BigDecimal deductibleVat = deductibleVat(record, matchedRule);
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
                  bookedNetPln(record, fx),
                  rule == null ? record.ryczaltRate() : rule.ryczaltRate(),
                  deductibleVat));
      saved.setBookedNetPln(
          bookedNetPln(record, fx),
          fx == null ? null : fx.rate(),
          fx == null ? null : fx.effectiveDate(),
          fx == null ? null : fx.provider(),
          fx == null ? null : fx.providerReference());
      saved.setBookedVatPln(bookedVatPln(record, fx));
      saved.applyDecision(
          counterparty,
          rule == null ? null : rule.classification(),
          rule == null ? null : rule.vatTreatment(),
          rule == null ? null : rule.vatDeductionRatio(),
          rule == null ? record.ryczaltRate() : rule.ryczaltRate(),
          rule == null ? PaymentVerificationPolicy.REQUIRED : rule.paymentVerificationPolicy(),
          status,
          rule == null ? ApprovalMethod.KSEF_TRUSTED : ApprovalMethod.COUNTERPARTY_RULE);
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

  private FxRate fxRate(InvoiceSourceRecord record) {
    return currency(record.currency()) == CurrencyType.PLN
        ? null
        : fxRates.rateFor(record.currency(), record.accountingDate());
  }

  private BigDecimal bookedNetPln(InvoiceSourceRecord record, FxRate fx) {
    return fx == null
        ? record.netAmount()
        : record.netAmount().multiply(fx.rate()).setScale(4, RoundingMode.HALF_UP);
  }

  private BigDecimal bookedVatPln(InvoiceSourceRecord record, FxRate fx) {
    return fx == null
        ? record.vatAmount()
        : record.vatAmount().multiply(fx.rate()).setScale(4, RoundingMode.HALF_UP);
  }

  private BigDecimal deductibleVat(InvoiceSourceRecord record, CounterpartyRule rule) {
    if (rule == null || rule.vatDeductionRatio() == null) return null;
    return record.vatAmount().multiply(rule.vatDeductionRatio()).setScale(4, RoundingMode.HALF_UP);
  }
}
