package com.smartbox.investory.ryczalt.persistence;

import com.smartbox.investory.ryczalt.application.RyczaltNativeMonthInputService.Command;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.YearMonth;

@Entity
@Table(name = "ryczalt_native_month_input", schema = "investory")
public class RyczaltNativeMonthInputEntity extends RyczaltEntity {
  @Column(name = "profile_id", nullable = false)
  private long profileId;

  @Column(name = "tax_year", nullable = false)
  private int year;

  @Column(name = "tax_month", nullable = false)
  private int month;

  @Column(name = "jdg_active", nullable = false)
  private boolean jdgActive;

  @Column(name = "qualifying_uop", nullable = false)
  private boolean qualifyingUop;

  @Column(name = "zus_regime", length = 32)
  private String zusRegime;

  @Column(name = "voluntary_sickness", nullable = false)
  private boolean voluntarySickness;

  @Column(name = "ytd_ryczalt_revenue", nullable = false, precision = 19, scale = 4)
  private BigDecimal ytdRyczaltRevenue;

  @Column(name = "full_jdg_social", precision = 19, scale = 4)
  private BigDecimal fullJdgSocial;

  @Column(name = "deductions_already_consumed", nullable = false, precision = 19, scale = 4)
  private BigDecimal deductionsAlreadyConsumed;

  @Column(name = "sales_corrections", nullable = false, precision = 19, scale = 4)
  private BigDecimal salesCorrections;

  @Column(name = "explicit_vat_adjustments", nullable = false, precision = 19, scale = 4)
  private BigDecimal explicitVatAdjustments;

  protected RyczaltNativeMonthInputEntity() {}

  public RyczaltNativeMonthInputEntity(long profileId, YearMonth month, Command command) {
    this.profileId = profileId;
    this.year = month.getYear();
    this.month = month.getMonthValue();
    update(command);
  }

  public void update(Command command) {
    this.jdgActive = command.jdgActive();
    this.qualifyingUop = command.qualifyingUop();
    this.zusRegime = command.zusRegime();
    this.voluntarySickness = command.voluntarySickness();
    this.ytdRyczaltRevenue = command.ytdRyczaltRevenue();
    this.fullJdgSocial = command.fullJdgSocial();
    this.deductionsAlreadyConsumed = command.deductionsAlreadyConsumed();
    this.salesCorrections = command.salesCorrections();
    this.explicitVatAdjustments = command.explicitVatAdjustments();
  }

  public ZusSettings zusSettings() {
    return new ZusSettings(
        jdgActive, qualifyingUop, zusRegime, voluntarySickness, ytdRyczaltRevenue, fullJdgSocial);
  }

  public BigDecimal deductionsAlreadyConsumed() {
    return deductionsAlreadyConsumed;
  }

  public BigDecimal salesCorrections() {
    return salesCorrections;
  }

  public BigDecimal explicitVatAdjustments() {
    return explicitVatAdjustments;
  }

  public record ZusSettings(
      boolean jdgActive,
      boolean qualifyingUop,
      String zusRegime,
      boolean voluntarySickness,
      BigDecimal ytdRyczaltRevenue,
      BigDecimal fullJdgSocial) {}
}
