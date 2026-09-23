package com.smartbox.investory.ryczalt.application.bank;

import com.smartbox.investory.ryczalt.calculation.InputChange;
import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import com.smartbox.investory.ryczalt.integration.bank.BankTransactionSourcePort;
import com.smartbox.investory.ryczalt.integration.bank.BankTransactionSourcePort.BankImportSource;
import com.smartbox.investory.ryczalt.integration.bank.BankTransactionSourceRecord;
import com.smartbox.investory.ryczalt.persistence.FrozenPeriodMutationException;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodLifecycleService;
import com.smartbox.investory.ryczalt.persistence.RyczaltSourceReferenceEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltSourceReferenceJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltTransactionEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltTransactionJpaRepository;
import com.smartbox.investory.ryczalt.settlement.SettlementService;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.YearMonth;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Native bank acquisition. Normalizes provider-neutral source records into canonical Ryczalt
 * transactions and records provenance. It performs no tax math and never writes legacy accounting
 * tables.
 */
@Service
public class RyczaltBankImportService implements RyczaltBankApi {
  private static final String ENTITY_TYPE = "TRANSACTION";
  private static final String ACTOR = "BANK_IMPORT";

  private final BankTransactionSourcePort source;
  private final RyczaltPeriodJpaRepository periods;
  private final RyczaltTransactionJpaRepository transactions;
  private final RyczaltSourceReferenceJpaRepository sourceReferences;
  private final RyczaltPeriodLifecycleService lifecycle;
  private final SettlementService settlement;

  public RyczaltBankImportService(
      BankTransactionSourcePort source,
      RyczaltPeriodJpaRepository periods,
      RyczaltTransactionJpaRepository transactions,
      RyczaltSourceReferenceJpaRepository sourceReferences,
      RyczaltPeriodLifecycleService lifecycle,
      SettlementService settlement) {
    this.source = source;
    this.periods = periods;
    this.transactions = transactions;
    this.sourceReferences = sourceReferences;
    this.lifecycle = lifecycle;
    this.settlement = settlement;
  }

  @Override
  @Transactional
  public RyczaltBankImportResult importBank(
      long profileId, byte[] content, String filename, String contentType) {
    var records = source.fetch(new BankImportSource(content, filename, contentType));
    int imported = 0;
    int duplicates = 0;
    Set<YearMonth> touched = new LinkedHashSet<>();
    for (BankTransactionSourceRecord record : records) {
      if (sourceReferences
          .findByProfileIdAndEntityTypeAndSourceAndExternalId(
              profileId, ENTITY_TYPE, record.source(), record.sourceExternalId())
          .isPresent()) {
        duplicates++;
        continue;
      }
      YearMonth month =
          record.relatedPeriod() != null
              ? YearMonth.from(record.relatedPeriod())
              : YearMonth.from(record.bookingDate());
      RyczaltPeriodEntity period = period(profileId, month);
      if (period.getStatus().isFrozen()) {
        throw new FrozenPeriodMutationException(profileId, month.getYear(), month.getMonthValue());
      }
      RyczaltTransactionEntity transaction =
          transactions.save(
              new RyczaltTransactionEntity(
                  period,
                  profileId,
                  record.bookingDate(),
                  record.amount(),
                  CurrencyType.valueOf(record.currency()),
                  record.reference(),
                  record.counterparty(),
                  record.counterpartyAccount(),
                  record.description()));
      sourceReferences.save(
          new RyczaltSourceReferenceEntity(
              profileId,
              ENTITY_TYPE,
              transaction.getId(),
              record.source(),
              record.sourceExternalId(),
              null));
      imported++;
      touched.add(month);
    }
    touched.forEach(
        month -> lifecycle.invalidate(profileId, month, InputChange.TRANSACTION_CHANGED, ACTOR));
    touched.forEach(month -> settlement.settlePeriod(profileId, month));
    return new RyczaltBankImportResult(records.size(), imported, duplicates);
  }

  private RyczaltPeriodEntity period(long profileId, YearMonth month) {
    return periods
        .findByProfileIdAndYearAndMonth(profileId, month.getYear(), month.getMonthValue())
        .orElseGet(
            () ->
                periods.save(
                    new RyczaltPeriodEntity(
                        profileId, month.getYear(), month.getMonthValue(), PeriodStatus.OPEN)));
  }
}
