package com.smartbox.investory.ryczalt.application;

import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import com.smartbox.investory.ryczalt.persistence.InvoiceDirection;
import com.smartbox.investory.ryczalt.persistence.RyczaltInvoiceEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltInvoiceJpaRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the simple one-to-one manual payment confirmation for cost invoices. */
@Service
public class RyczaltInvoicePaymentService {
  private final RyczaltInvoiceJpaRepository invoices;

  public RyczaltInvoicePaymentService(RyczaltInvoiceJpaRepository invoices) {
    this.invoices = invoices;
  }

  @Transactional
  public void markPaid(long profileId, long invoiceId, LocalDate paidDate, String note) {
    RyczaltInvoiceEntity invoice = invoice(profileId, invoiceId);
    requireCost(invoice);
    requireMutable(invoice);
    invoice.markManuallyPaid(paidDate, note);
    invoices.save(invoice);
  }

  @Transactional
  public void markUnpaid(long profileId, long invoiceId) {
    RyczaltInvoiceEntity invoice = invoice(profileId, invoiceId);
    requireCost(invoice);
    requireMutable(invoice);
    if (invoice.getPaymentStatus().equals("MATCHED")
        || invoice.getPaymentStatus().equals("PARTIALLY_MATCHED")) {
      throw new IllegalStateException("Bank-matched invoice cannot be made unpaid manually");
    }
    invoice.markUnpaid();
    invoices.save(invoice);
  }

  private RyczaltInvoiceEntity invoice(long profileId, long invoiceId) {
    return invoices
        .findByIdAndProfileId(invoiceId, profileId)
        .orElseThrow(() -> new IllegalArgumentException("Invoice does not exist"));
  }

  private static void requireCost(RyczaltInvoiceEntity invoice) {
    if (invoice.getDirection() != InvoiceDirection.COST) {
      throw new IllegalArgumentException("Manual payment confirmation is available for costs only");
    }
  }

  private static void requireMutable(RyczaltInvoiceEntity invoice) {
    if (invoice.getPeriod().getStatus() == PeriodStatus.FROZEN) {
      throw new IllegalStateException("Frozen period payment confirmation is immutable");
    }
  }
}
