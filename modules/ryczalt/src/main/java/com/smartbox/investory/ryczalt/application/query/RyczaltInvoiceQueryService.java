package com.smartbox.investory.ryczalt.application.query;

import com.smartbox.investory.ryczalt.domain.InvoicePaymentStatus;
import com.smartbox.investory.ryczalt.domain.PaymentVerificationPolicy;
import com.smartbox.investory.ryczalt.persistence.RyczaltInvoiceEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltInvoiceJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltSourceReferenceEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltSourceReferenceJpaRepository;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Native invoice and provenance read queries. */
@Service
public class RyczaltInvoiceQueryService {
  private final RyczaltPeriodJpaRepository periods;
  private final RyczaltInvoiceJpaRepository invoices;
  private final RyczaltSourceReferenceJpaRepository sourceReferences;

  public RyczaltInvoiceQueryService(
      RyczaltPeriodJpaRepository periods,
      RyczaltInvoiceJpaRepository invoices,
      RyczaltSourceReferenceJpaRepository sourceReferences) {
    this.periods = periods;
    this.invoices = invoices;
    this.sourceReferences = sourceReferences;
  }

  @Transactional(readOnly = true)
  public List<RyczaltInvoiceReadModel> getInvoices(long profileId, YearMonth month) {
    RyczaltPeriodEntity period = period(profileId, month);
    return invoiceModels(
        profileId,
        invoices.findByProfileIdAndPeriodIdOrderByAccountingDateAscIdAsc(profileId, period.id()));
  }

  @Transactional(readOnly = true)
  public List<RyczaltInvoiceReadModel> getInvoices(
      long profileId, YearMonth month, long counterpartyId) {
    RyczaltPeriodEntity period = period(profileId, month);
    return invoiceModels(
        profileId,
        invoices.findByProfileIdAndPeriodIdAndCounterparty_IdOrderByAccountingDateAscIdAsc(
            profileId, period.id(), counterpartyId));
  }

  @Transactional(readOnly = true)
  public List<RyczaltInvoiceReadModel> getInvoices(
      long profileId, YearMonth month, Long counterpartyId) {
    if (month != null) {
      return counterpartyId == null
          ? getInvoices(profileId, month)
          : getInvoices(profileId, month, counterpartyId.longValue());
    }
    List<RyczaltInvoiceEntity> rows =
        counterpartyId == null
            ? invoices.findByProfileIdOrderByAccountingDateAscIdAsc(profileId)
            : invoices.findByProfileIdAndCounterparty_IdOrderByAccountingDateAscIdAsc(
                profileId, counterpartyId);
    return invoiceModels(profileId, rows);
  }

  private RyczaltPeriodEntity period(long profileId, YearMonth month) {
    return periods
        .findByProfileIdAndYearAndMonth(profileId, month.getYear(), month.getMonthValue())
        .orElseThrow(() -> new RyczaltPeriodNotFoundException(profileId, month));
  }

  private List<RyczaltInvoiceReadModel> invoiceModels(
      long profileId, List<RyczaltInvoiceEntity> rows) {
    if (rows.isEmpty()) return List.of();
    Map<Long, RyczaltSourceReferenceEntity> sourceByInvoiceId = new HashMap<>();
    if (sourceReferences != null) {
      sourceReferences
          .findByProfileIdAndEntityTypeAndEntityIdIn(
              profileId, "INVOICE", rows.stream().map(RyczaltInvoiceEntity::getId).toList())
          .forEach(
              source ->
                  sourceByInvoiceId.merge(
                      source.getEntityId(),
                      source,
                      (existing, candidate) ->
                          "KSEF".equals(candidate.getSource()) ? candidate : existing));
    }
    return rows.stream().map(row -> invoice(row, sourceByInvoiceId.get(row.getId()))).toList();
  }

  private RyczaltInvoiceReadModel invoice(
      RyczaltInvoiceEntity row, RyczaltSourceReferenceEntity source) {
    return new RyczaltInvoiceReadModel(
        row.getId(),
        row.getDirection(),
        row.getReference(),
        row.getIssueDate(),
        row.getAccountingDate(),
        row.getNetAmount(),
        row.getVatAmount(),
        row.getGrossAmount(),
        row.getCurrency(),
        row.getBookedNetPln(),
        row.getRyczaltRate(),
        row.getDeductibleVat(),
        row.getClassification(),
        row.getCounterparty() == null
            ? null
            : new RyczaltInvoiceReadModel.CounterpartyView(
                row.getCounterparty().id(),
                row.getCounterparty().getLegalName(),
                row.getCounterparty().getAlias()),
        row.getApprovalStatus(),
        row.getApprovalMethod(),
        row.getPaymentVerificationPolicy(),
        row.getPaymentVerificationPolicy() == PaymentVerificationPolicy.NOT_REQUIRED
            ? InvoicePaymentStatus.NOT_REQUIRED
            : InvoicePaymentStatus.fromPersisted(row.getPaymentStatus()),
        source == null ? null : source.getSource(),
        source == null || !"KSEF".equals(source.getSource()) ? null : source.getExternalId());
  }
}
