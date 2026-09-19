package com.smartbox.investory.ryczalt.persistence;

import com.smartbox.investory.ryczalt.domain.Invoice;
import com.smartbox.investory.ryczalt.domain.Obligation;
import com.smartbox.investory.ryczalt.domain.Transaction;
import java.util.Currency;

final class RyczaltDomainMapper {
  private RyczaltDomainMapper() {}

  static Invoice invoice(RyczaltInvoiceEntity entity) {
    return new Invoice(
        entity.getReference(),
        entity.getIssueDate(),
        entity.getAccountingDate(),
        entity.getNetAmount(),
        entity.getVatAmount(),
        entity.getGrossAmount(),
        Currency.getInstance(entity.getCurrency().name()),
        entity.getBookedNetPln(),
        entity.getRyczaltRate(),
        entity.getDeductibleVat());
  }

  static Transaction transaction(RyczaltTransactionEntity entity) {
    return new Transaction(
        entity.getReference() == null ? "" : entity.getReference(),
        entity.getBookingDate(),
        entity.getAmount(),
        Currency.getInstance(entity.getCurrency().name()),
        entity.getCounterparty(),
        entity.getDescription());
  }

  static Obligation obligation(RyczaltObligationEntity entity) {
    return new Obligation(
        entity.getType(),
        entity.getAmount(),
        Currency.getInstance(entity.getCurrency().name()),
        entity.getDueDate(),
        entity.getStatus());
  }
}
