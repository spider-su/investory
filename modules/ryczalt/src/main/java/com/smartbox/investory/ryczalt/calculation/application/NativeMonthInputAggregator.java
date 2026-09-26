package com.smartbox.investory.ryczalt.calculation.application;

import com.smartbox.investory.ryczalt.application.NeedsReviewException;
import com.smartbox.investory.ryczalt.application.RyczaltNativeMonthInputService.Command;
import com.smartbox.investory.ryczalt.calculation.vat.VatCalculationInput;
import com.smartbox.investory.ryczalt.calculation.zus.ZusCalculationInput;
import com.smartbox.investory.ryczalt.domain.ApprovalStatus;
import com.smartbox.investory.ryczalt.persistence.InvoiceDirection;
import com.smartbox.investory.ryczalt.persistence.RyczaltInvoiceEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltInvoiceJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltNativeMonthInputEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltNativeMonthInputJpaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
            .orElseGet(() -> deriveMonthInput(profileId, month));
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
            zus.fullJdgSocial(),
            null,
            zus.socialContributionDeduction(),
            zus.healthContributionOverride(),
            zus.healthContributionPaidOverride()),
        settings.deductionsAlreadyConsumed(),
        settings.salesCorrections(),
        settings.explicitVatAdjustments());
  }

  private RyczaltNativeMonthInputEntity deriveMonthInput(long profileId, YearMonth month) {
    RyczaltNativeMonthInputEntity previous =
        monthInputs
            .findLatestBefore(profileId, month.getYear(), month.getMonthValue())
            .orElseThrow(() -> needsReview(month, "no prior ZUS/input settings exist"));
    var previousZus = previous.zusSettings();
    LocalDate firstDay = month.atDay(1);
    BigDecimal ytdRevenue =
        invoices.findByProfileIdOrderByAccountingDateAscIdAsc(profileId).stream()
            .filter(invoice -> invoice.getDirection() == InvoiceDirection.INCOME)
            .filter(invoice -> invoice.getApprovalStatus() == ApprovalStatus.APPROVED)
            .filter(invoice -> invoice.getAccountingDate().getYear() == month.getYear())
            .filter(invoice -> invoice.getAccountingDate().isBefore(firstDay))
            .map(RyczaltInvoiceEntity::getBookedNetPln)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    var command =
        new Command(
            previousZus.jdgActive(),
            previousZus.qualifyingUop(),
            previousZus.zusRegime(),
            previousZus.voluntarySickness(),
            ytdRevenue,
            previousZus.fullJdgSocial(),
            previousZus.socialContributionDeduction(),
            previousZus.healthContributionOverride(),
            previousZus.healthContributionPaidOverride(),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    return monthInputs.save(new RyczaltNativeMonthInputEntity(profileId, month, command));
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
      if (invoice.getDirection() == InvoiceDirection.INCOME) {
        require(invoice.getBookedNetPln(), month, "income invoice has no booked PLN net amount");
        require(invoice.getRyczaltRate(), month, "income invoice has no ryczałt rate");
        revenueByRate.merge(invoice.getRyczaltRate(), invoice.getBookedNetPln(), BigDecimal::add);
        if (invoice.getBookedVatPln() == null && invoice.getVatAmount().signum() != 0) {
          throw needsReview(month, "income invoice has no booked PLN VAT amount");
        }
        outputVat =
            outputVat.add(
                invoice.getBookedVatPln() == null ? BigDecimal.ZERO : invoice.getBookedVatPln());
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

  private NeedsReviewException needsReview(YearMonth month, String reason) {
    return new NeedsReviewException(month, reason);
  }
}
