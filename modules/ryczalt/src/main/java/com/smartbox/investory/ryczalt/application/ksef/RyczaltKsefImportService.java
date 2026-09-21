package com.smartbox.investory.ryczalt.application.ksef;

import com.smartbox.investory.ryczalt.calculation.InputChange;
import com.smartbox.investory.ryczalt.domain.PeriodStatus;
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

  public RyczaltKsefImportService(
      InvoiceSourcePort source,
      RyczaltPeriodJpaRepository periods,
      RyczaltInvoiceJpaRepository invoices,
      RyczaltSourceReferenceJpaRepository sourceReferences,
      RyczaltPeriodLifecycleService lifecycle) {
    this.source = source;
    this.periods = periods;
    this.invoices = invoices;
    this.sourceReferences = sourceReferences;
    this.lifecycle = lifecycle;
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
      YearMonth target = YearMonth.from(record.accountingDate());
      var existing =
          sourceReferences.findByProfileIdAndEntityTypeAndSourceAndExternalId(
              profileId, ENTITY_TYPE, SOURCE, record.sourceExternalId());
      if (existing.isPresent()) {
        if (!reimport) {
          duplicates++;
          continue;
        }
        RyczaltInvoiceEntity invoice =
            invoices.findById(existing.get().getEntityId()).orElseThrow();
        YearMonth current =
            YearMonth.of(invoice.getPeriod().getYear(), invoice.getPeriod().getMonth());
        requireMutable(profileId, current);
        if (!current.equals(target)) {
          invoice.moveToPeriod(requireMutable(profileId, target));
        }
        invoice.update(
            record.issueDate(),
            record.accountingDate(),
            record.netAmount(),
            record.vatAmount(),
            record.grossAmount(),
            currency(record.currency()));
        invoices.save(invoice);
        updated++;
        touched.computeIfAbsent(record.direction(), ignored -> new HashSet<>()).add(current);
        touched.get(record.direction()).add(target);
        continue;
      }
      RyczaltPeriodEntity period = requireMutable(profileId, target);
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
                  null,
                  record.ryczaltRate(),
                  record.deductibleVat()));
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
    if (period.getStatus() == PeriodStatus.FROZEN) {
      throw new FrozenPeriodMutationException(profileId, month.getYear(), month.getMonthValue());
    }
    return period;
  }

  private static CurrencyType currency(String code) {
    return code == null ? CurrencyType.PLN : CurrencyType.valueOf(code);
  }
}
