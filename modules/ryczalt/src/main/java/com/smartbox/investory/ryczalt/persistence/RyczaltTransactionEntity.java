package com.smartbox.investory.ryczalt.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "ryczalt_transaction", schema = "investory")
public class RyczaltTransactionEntity extends RyczaltEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "period_id", nullable = false)
  private RyczaltPeriodEntity period;

  @Column(name = "profile_id", nullable = false)
  private long profileId;

  @Column(name = "booking_date", nullable = false)
  private LocalDate bookingDate;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(length = 256)
  private String reference;

  @Column(length = 256)
  private String counterparty;

  @Column(name = "counterparty_account", length = 64)
  private String counterpartyAccount;

  @Column(length = 1000)
  private String description;

  protected RyczaltTransactionEntity() {}

  public RyczaltTransactionEntity(
      RyczaltPeriodEntity period,
      long profileId,
      LocalDate bookingDate,
      BigDecimal amount,
      String currency,
      String reference,
      String counterparty,
      String counterpartyAccount,
      String description) {
    this.period = period;
    this.profileId = profileId;
    this.bookingDate = bookingDate;
    this.amount = amount;
    this.currency = currency;
    this.reference = reference;
    this.counterparty = counterparty;
    this.counterpartyAccount = counterpartyAccount;
    this.description = description;
  }

  public RyczaltPeriodEntity getPeriod() {
    return period;
  }

  public long getProfileId() {
    return profileId;
  }

  public LocalDate getBookingDate() {
    return bookingDate;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public String getReference() {
    return reference;
  }

  public String getCounterparty() {
    return counterparty;
  }

  public String getCounterpartyAccount() {
    return counterpartyAccount;
  }

  public String getDescription() {
    return description;
  }
}
