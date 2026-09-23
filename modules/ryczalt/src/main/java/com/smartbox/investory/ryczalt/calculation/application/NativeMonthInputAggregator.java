package com.smartbox.investory.ryczalt.calculation.application;

import com.smartbox.investory.ryczalt.calculation.vat.VatCalculationInput;
import com.smartbox.investory.ryczalt.calculation.zus.ZusCalculationInput;
import com.smartbox.investory.ryczalt.domain.ApprovalStatus;
import com.smartbox.investory.ryczalt.persistence.InvoiceDirection;
import com.smartbox.investory.ryczalt.persistence.RyczaltInvoiceEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltInvoiceJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltNativeMonthInputEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltNativeMonthInputJpaRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Builds calculation facts from approved native invoices. Bank rows are not tax inputs. */
@Service
public class NativeMonthInputAggregator {
  private final RyczaltInvoiceJpaRepository invoices;
  private final RyczaltNativeMonthInputJpaRepository monthInputs;

  public NativeMonthInputAggregator(
      RyczaltInvoiceJpaRepository invoices, RyczaltNativeMonthInputJpaRepository monthInputs) {
    this.invoices = invoices;
    this.monthInputs = monthInputs;
  }

  public NativeMonthCalculationInput aggregate(long profileId, long periodId, YearMonth month) {
    RyczaltNativeMonthInputEntity settings =
        monthInputs
            .findByProfileIdAndYearAndMonth(profileId, month.getYear(), month.getMonthValue())
            .orElseThrow(() -> needsReview(month, "native month ZUS/input settings are missing"));
    RyczaltNativeMonthInputEntity.ZusSettings zus = settings.zusSettings();
    return aggregate(
        profileId,
        periodId,
        month,
        new ZusCalculationInput(
            zus.jdgActive(),
            zus.qualifyingUop(),
            zus.zusRegime(),
            zus.voluntarySickness(),
            zus.ytdRyczaltRevenue(),
            zus.fullJdgSocial()),
        settings.deductionsAlreadyConsumed(),
        settings.salesCorrections(),
        settings.explicitVatAdjustments());
  }

  public NativeMonthCalculationInput aggregate(
      long profileId,
      long periodId,
      YearMonth month,
      ZusCalculationInput zus,
      BigDecimal deductionsAlreadyConsumed,
      BigDecimal salesCorrections,
      BigDecimal explicitVatAdjustments) {
    List<RyczaltInvoiceEntity> rows =
        invoices.findByProfileIdAndPeriodIdOrderByAccountingDateAscIdAsc(profileId, periodId);
    List<RyczaltInvoiceEntity> unresolved =
        rows.stream().filter(row -> row.getApprovalStatus() != ApprovalStatus.APPROVED).toList();
    if (!unresolved.isEmpty()) {
      throw needsReview(month, "unapproved invoice facts remain");
    }

    Map<BigDecimal, BigDecimal> revenueByRate = new LinkedHashMap<>();
    BigDecimal outputVat = BigDecimal.ZERO;
    BigDecimal deductibleVat = BigDecimal.ZERO;
    for (RyczaltInvoiceEntity invoice : rows) {
      if (invoice.getCurrency() != CurrencyType.PLN) {
        throw needsReview(month, "foreign-currency invoice needs PLN-normalized tax facts");
      }
      if (invoice.getDirection() == InvoiceDirection.INCOME) {
        require(invoice.getBookedNetPln(), month, "income invoice has no booked PLN net amount");
        require(invoice.getRyczaltRate(), month, "income invoice has no ryczałt rate");
        revenueByRate.merge(invoice.getRyczaltRate(), invoice.getBookedNetPln(), BigDecimal::add);
        outputVat = outputVat.add(invoice.getVatAmount());
      } else {
        require(invoice.getDeductibleVat(), month, "cost invoice has unresolved deductible VAT");
        deductibleVat = deductibleVat.add(invoice.getDeductibleVat());
      }
    }
    return new NativeMonthCalculationInput(
        revenueByRate,
        new VatCalculationInput(outputVat, salesCorrections, deductibleVat, explicitVatAdjustments),
        zus,
        deductionsAlreadyConsumed);
  }

  private void require(BigDecimal value, YearMonth month, String message) {
    if (value == null) throw needsReview(month, message);
  }

  private IllegalStateException needsReview(YearMonth month, String reason) {
    return new IllegalStateException("NEEDS_REVIEW for " + month + ": " + reason);
  }
}
