package com.smartbox.investory.ryczalt.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "ryczalt_invoice", schema = "investory")
public class RyczaltInvoiceEntity extends RyczaltEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "period_id", nullable = false)
  private RyczaltPeriodEntity period;

  @Column(name = "profile_id", nullable = false)
  private long profileId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 8)
  private InvoiceDirection direction;

  @Column(nullable = false, length = 128)
  private String reference;

  @Column(name = "issue_date", nullable = false)
  private LocalDate issueDate;

  @Column(name = "accounting_date", nullable = false)
  private LocalDate accountingDate;

  @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal netAmount;

  @Column(name = "vat_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal vatAmount;

  @Column(name = "gross_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal grossAmount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "booked_net_pln", precision = 19, scale = 4)
  private BigDecimal bookedNetPln;

  @Column(name = "ryczalt_rate", precision = 7, scale = 4)
  private BigDecimal ryczaltRate;

  @Column(name = "deductible_vat", precision = 19, scale = 4)
  private BigDecimal deductibleVat;

  protected RyczaltInvoiceEntity() {}

  public RyczaltInvoiceEntity(
      RyczaltPeriodEntity period,
      long profileId,
      InvoiceDirection direction,
      String reference,
      LocalDate issueDate,
      LocalDate accountingDate,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal grossAmount,
      String currency,
      BigDecimal bookedNetPln,
      BigDecimal ryczaltRate,
      BigDecimal deductibleVat) {
    this.period = period;
    this.profileId = profileId;
    this.direction = direction;
    this.reference = reference;
    this.issueDate = issueDate;
    this.accountingDate = accountingDate;
    this.netAmount = netAmount;
    this.vatAmount = vatAmount;
    this.grossAmount = grossAmount;
    this.currency = currency;
    this.bookedNetPln = bookedNetPln;
    this.ryczaltRate = ryczaltRate;
    this.deductibleVat = deductibleVat;
  }

  public RyczaltPeriodEntity getPeriod() {
    return period;
  }

  public long getProfileId() {
    return profileId;
  }

  public InvoiceDirection getDirection() {
    return direction;
  }

  public String getReference() {
    return reference;
  }

  public LocalDate getIssueDate() {
    return issueDate;
  }

  public LocalDate getAccountingDate() {
    return accountingDate;
  }

  public BigDecimal getNetAmount() {
    return netAmount;
  }

  public BigDecimal getVatAmount() {
    return vatAmount;
  }

  public BigDecimal getGrossAmount() {
    return grossAmount;
  }

  public String getCurrency() {
    return currency;
  }

  public BigDecimal getBookedNetPln() {
    return bookedNetPln;
  }

  public BigDecimal getRyczaltRate() {
    return ryczaltRate;
  }

  public BigDecimal getDeductibleVat() {
    return deductibleVat;
  }
}
