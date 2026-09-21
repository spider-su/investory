package com.smartbox.investory.ryczalt.persistence;

import com.smartbox.investory.ryczalt.domain.AccountingPeriod;
import com.smartbox.investory.ryczalt.domain.Bucket;
import com.smartbox.investory.ryczalt.domain.Invoice;
import com.smartbox.investory.ryczalt.domain.Obligation;
import com.smartbox.investory.ryczalt.domain.Transaction;
import com.smartbox.investory.shared.currency.CurrencyType;
import jakarta.transaction.Transactional;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Persistence boundary for canonical Ryczalt facts. Legacy tables are not read here. */
@Repository
public class RyczaltPersistenceAdapter {
  private final RyczaltPeriodJpaRepository periods;
  private final RyczaltInvoiceJpaRepository invoices;
  private final RyczaltTransactionJpaRepository transactions;
  private final RyczaltObligationJpaRepository obligations;

  public RyczaltPersistenceAdapter(
      RyczaltPeriodJpaRepository periods,
      RyczaltInvoiceJpaRepository invoices,
      RyczaltTransactionJpaRepository transactions,
      RyczaltObligationJpaRepository obligations) {
    this.periods = periods;
    this.invoices = invoices;
    this.transactions = transactions;
    this.obligations = obligations;
  }

  @Transactional
  public Optional<AccountingPeriod> load(long profileId, YearMonth month) {
    return periods
        .findByProfileIdAndYearAndMonth(profileId, month.getYear(), month.getMonthValue())
        .map(
            period -> {
              final RyczaltPeriodEntity storedPeriod = period;
              long periodId = period.id();
              List<RyczaltInvoiceEntity> storedInvoices =
                  invoices.findByProfileIdAndPeriodIdOrderByAccountingDateAscIdAsc(
                      profileId, periodId);
              List<RyczaltTransactionEntity> storedTransactions =
                  transactions.findByProfileIdAndPeriodIdOrderByBookingDateAscIdAsc(
                      profileId, periodId);
              List<RyczaltObligationEntity> storedObligations =
                  obligations.findByProfileIdAndPeriodIdOrderByTypeAsc(profileId, periodId);
              return new AccountingPeriod(
                  month,
                  storedPeriod.getStatus(),
                  Bucket.of(
                      storedInvoices.stream()
                          .filter(i -> i.getDirection() == InvoiceDirection.INCOME)
                          .map(RyczaltDomainMapper::invoice)
                          .toList()),
                  Bucket.of(
                      storedInvoices.stream()
                          .filter(i -> i.getDirection() == InvoiceDirection.COST)
                          .map(RyczaltDomainMapper::invoice)
                          .toList()),
                  Bucket.of(
                      storedTransactions.stream().map(RyczaltDomainMapper::transaction).toList()),
                  Bucket.of(
                      storedObligations.stream().map(RyczaltDomainMapper::obligation).toList()));
            });
  }

  @Transactional
  public AccountingPeriod save(long profileId, AccountingPeriod period) {
    RyczaltPeriodEntity stored =
        periods
            .findByProfileIdAndYearAndMonth(
                profileId, period.period().getYear(), period.period().getMonthValue())
            .orElseGet(
                () ->
                    new RyczaltPeriodEntity(
                        profileId,
                        period.period().getYear(),
                        period.period().getMonthValue(),
                        period.status()));
    if (stored.getStatus() == com.smartbox.investory.ryczalt.domain.PeriodStatus.FROZEN) {
      throw new FrozenPeriodMutationException(
          profileId, period.period().getYear(), period.period().getMonthValue());
    }
    stored.setStatus(period.status());
    stored = periods.save(stored);
    final RyczaltPeriodEntity persistedPeriod = stored;

    period
        .incomeInvoices()
        .items()
        .forEach(
            invoice -> saveInvoice(persistedPeriod, profileId, InvoiceDirection.INCOME, invoice));
    period
        .costInvoices()
        .items()
        .forEach(
            invoice -> saveInvoice(persistedPeriod, profileId, InvoiceDirection.COST, invoice));
    period
        .transactions()
        .items()
        .forEach(transaction -> saveTransaction(persistedPeriod, profileId, transaction));
    period
        .obligations()
        .items()
        .forEach(obligation -> saveObligation(persistedPeriod, profileId, obligation));
    return period;
  }

  private void saveInvoice(
      RyczaltPeriodEntity period, long profileId, InvoiceDirection direction, Invoice invoice) {
    invoices.save(
        new RyczaltInvoiceEntity(
            period,
            profileId,
            direction,
            invoice.reference(),
            invoice.issueDate(),
            invoice.accountingDate(),
            invoice.netAmount(),
            invoice.vatAmount(),
            invoice.grossAmount(),
            CurrencyType.valueOf(invoice.currency().getCurrencyCode()),
            invoice.bookedNetPln(),
            invoice.ryczaltRate(),
            invoice.deductibleVat()));
  }

  private void saveTransaction(
      RyczaltPeriodEntity period, long profileId, Transaction transaction) {
    transactions.save(
        new RyczaltTransactionEntity(
            period,
            profileId,
            transaction.date(),
            transaction.amount(),
            CurrencyType.valueOf(transaction.currency().getCurrencyCode()),
            transaction.reference(),
            transaction.counterparty(),
            transaction.description()));
  }

  private void saveObligation(RyczaltPeriodEntity period, long profileId, Obligation obligation) {
    obligations.save(
        new RyczaltObligationEntity(
            period,
            profileId,
            obligation.type(),
            obligation.amount(),
            CurrencyType.valueOf(obligation.currency().getCurrencyCode()),
            obligation.dueDate(),
            obligation.status(),
            null));
  }
}
