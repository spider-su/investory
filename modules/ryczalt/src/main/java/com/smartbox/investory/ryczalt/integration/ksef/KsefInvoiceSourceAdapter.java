package com.smartbox.investory.ryczalt.integration.ksef;

import com.smartbox.investory.integrations.ksef.Fa3Invoice;
import com.smartbox.investory.integrations.ksef.Fa3InvoiceParser;
import com.smartbox.investory.integrations.ksef.KsefInvoiceService;
import com.smartbox.investory.integrations.ksef.KsefSubject;
import com.smartbox.investory.ryczalt.persistence.InvoiceDirection;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Adapts the shared KSeF transport to the native Ryczalt invoice source port: discovers document
 * numbers, downloads FA(3) XML, parses it, and maps it to provider-neutral records. No accounting
 * dependency and no accounting classification guessing.
 */
@Component
public class KsefInvoiceSourceAdapter implements InvoiceSourcePort {
  private final KsefInvoiceService invoices;
  private final Fa3InvoiceParser parser;

  public KsefInvoiceSourceAdapter(KsefInvoiceService invoices, Fa3InvoiceParser parser) {
    this.invoices = invoices;
    this.parser = parser;
  }

  @Override
  public List<InvoiceSourceRecord> fetch(KsefSyncCommand command) {
    List<InvoiceSourceRecord> records = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (KsefSyncMode mode : command.modes()) {
      KsefSubject subject = mode == KsefSyncMode.SALES ? KsefSubject.SELLER : KsefSubject.BUYER;
      InvoiceDirection direction =
          mode == KsefSyncMode.SALES ? InvoiceDirection.INCOME : InvoiceDirection.COST;
      for (String ksefNumber : invoices.listInvoiceNumbers(command.month(), subject)) {
        if (!seen.add(ksefNumber)) continue;
        Fa3Invoice invoice =
            parser.parse(invoices.downloadInvoice(ksefNumber).getBytes(StandardCharsets.UTF_8));
        records.add(toRecord(ksefNumber, direction, invoice));
      }
    }
    return records;
  }

  private InvoiceSourceRecord toRecord(
      String ksefNumber, InvoiceDirection direction, Fa3Invoice invoice) {
    LocalDate issueDate = invoice.issueDate() != null ? invoice.issueDate() : invoice.saleDate();
    LocalDate accountingDate = invoice.saleDate() != null ? invoice.saleDate() : issueDate;
    if (issueDate == null || accountingDate == null) {
      throw new IllegalStateException("KSeF invoice " + ksefNumber + " has no usable date");
    }
    boolean sale = direction == InvoiceDirection.INCOME;
    String counterpartyName = sale ? invoice.buyerName() : invoice.sellerName();
    String counterpartyTaxId = sale ? invoice.buyerNip() : invoice.sellerNip();
    return new InvoiceSourceRecord(
        ksefNumber,
        direction,
        invoice.reference(),
        issueDate,
        accountingDate,
        invoice.netAmount(),
        invoice.vatAmount(),
        invoice.grossAmount(),
        invoice.currency() == null ? "PLN" : invoice.currency(),
        counterpartyName,
        counterpartyTaxId,
        // KSeF does not carry a ryczalt rate or a deductible-VAT decision; leave them unresolved.
        null,
        null);
  }
}
