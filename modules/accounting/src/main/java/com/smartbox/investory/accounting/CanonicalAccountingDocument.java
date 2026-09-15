package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Reviewed accounting fact. Staging evidence is intentionally not represented here because it may
 * be incomplete or ambiguous.
 */
public record CanonicalAccountingDocument(
    Direction direction,
    Kind kind,
    AccountingTaxPeriod taxPeriod,
    AccountingDocumentReference reference,
    String currency,
    BigDecimal netAmount,
    BigDecimal vatAmount,
    BigDecimal grossAmount,
    List<VatBucket> vatBuckets) {
  private static final Set<VatTreatment> SALE_TREATMENTS =
      Set.of(
          VatTreatment.DOMESTIC_VAT,
          VatTreatment.EU_B2B_REVERSE_CHARGE,
          VatTreatment.NON_EU_B2B_OUTSIDE_POLAND,
          VatTreatment.VAT_EXEMPT);
  private static final Set<VatTreatment> PURCHASE_TREATMENTS =
      Set.of(
          VatTreatment.DOMESTIC_PURCHASE,
          VatTreatment.IMPORT_OF_SERVICES_EU,
          VatTreatment.IMPORT_OF_SERVICES_NON_EU);

  public CanonicalAccountingDocument {
    if (direction == null || kind == null)
      throw new IllegalArgumentException("Document type is required");
    if (currency == null || !currency.matches("[A-Za-z]{3}")) {
      throw new IllegalArgumentException("Document currency must be an ISO three-letter code");
    }
    currency = currency.toUpperCase(java.util.Locale.ROOT);
    if (netAmount == null || vatAmount == null || grossAmount == null) {
      throw new IllegalArgumentException("Document amounts are required");
    }
    if (netAmount.add(vatAmount).compareTo(grossAmount) != 0) {
      throw new IllegalArgumentException("Document net plus VAT must equal gross");
    }
    vatBuckets = List.copyOf(vatBuckets);
    if (vatBuckets.isEmpty())
      throw new IllegalArgumentException("Document needs at least one VAT bucket");
    if (vatBuckets.stream()
                .map(VatBucket::netAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(netAmount)
            != 0
        || vatBuckets.stream()
                .map(VatBucket::vatAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(vatAmount)
            != 0) {
      throw new IllegalArgumentException("VAT bucket totals must equal document totals");
    }
    Set<VatTreatment> allowed = direction == Direction.SALE ? SALE_TREATMENTS : PURCHASE_TREATMENTS;
    if (vatBuckets.stream().anyMatch(bucket -> !allowed.contains(bucket.treatment()))) {
      throw new IllegalArgumentException("VAT treatment is not legal for document direction");
    }
    long distinctBuckets = vatBuckets.stream().map(VatBucket::identity).distinct().count();
    if (distinctBuckets != vatBuckets.size()) {
      throw new IllegalArgumentException("Document cannot contain duplicate VAT buckets");
    }
  }

  public enum Direction {
    SALE,
    PURCHASE
  }

  public enum Kind {
    INVOICE,
    CREDIT_NOTE
  }

  public record VatBucket(
      VatTreatment treatment,
      BigDecimal vatRate,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal deductibleVat) {
    public VatBucket {
      if (treatment == null || netAmount == null || vatAmount == null || deductibleVat == null) {
        throw new IllegalArgumentException("VAT bucket is incomplete");
      }
      boolean domestic =
          treatment == VatTreatment.DOMESTIC_VAT || treatment == VatTreatment.DOMESTIC_PURCHASE;
      if (domestic) AccountingVatRate.of(vatRate);
      if (!domestic && vatRate != null) {
        throw new IllegalArgumentException("Only domestic VAT treatment may have a VAT rate");
      }
      if (deductibleVat.signum() < 0
          || deductibleVat.compareTo(vatAmount.max(BigDecimal.ZERO)) > 0) {
        throw new IllegalArgumentException("Deductible VAT must be between zero and VAT amount");
      }
    }

    private String identity() {
      return treatment + "|" + vatRate;
    }
  }
}
