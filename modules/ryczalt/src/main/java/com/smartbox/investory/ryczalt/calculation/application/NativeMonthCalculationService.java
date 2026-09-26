package com.smartbox.investory.ryczalt.calculation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbox.investory.ryczalt.application.NeedsReviewException;
import com.smartbox.investory.ryczalt.calculation.InputFingerprint;
import com.smartbox.investory.ryczalt.calculation.ryczalt.RyczaltCalculationInput;
import com.smartbox.investory.ryczalt.calculation.ryczalt.RyczaltCalculationResult;
import com.smartbox.investory.ryczalt.calculation.ryczalt.RyczaltCalculator;
import com.smartbox.investory.ryczalt.calculation.vat.VatCalculationResult;
import com.smartbox.investory.ryczalt.calculation.vat.VatCalculator;
import com.smartbox.investory.ryczalt.calculation.zus.ZusCalculationResult;
import com.smartbox.investory.ryczalt.calculation.zus.ZusCalculator;
import com.smartbox.investory.ryczalt.domain.ObligationStatus;
import com.smartbox.investory.ryczalt.domain.ObligationType;
import com.smartbox.investory.ryczalt.domain.PeriodStatus;
import com.smartbox.investory.ryczalt.persistence.CalculationType;
import com.smartbox.investory.ryczalt.persistence.RyczaltCalculationEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltCalculationJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltCalculationPersistenceAdapter;
import com.smartbox.investory.ryczalt.persistence.RyczaltObligationEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltObligationJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodJpaRepository;
import com.smartbox.investory.ryczalt.settlement.SettlementService;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Runs the complete native RYCZALT, VAT, ZUS and obligation cycle for one month. */
@Service
public class NativeMonthCalculationService {
  private static final String CALCULATOR_VERSION = "native-month-1";

  private final RyczaltPeriodJpaRepository periods;
  private final NativeMonthInputAggregator inputAggregator;
  private final RyczaltCalculationPersistenceAdapter calculations;
  private final RyczaltObligationJpaRepository obligations;
  private final SettlementService settlement;
  private final RyczaltCalculationJpaRepository calculationRows;
  private final Clock clock;
  private final ObjectMapper json = new ObjectMapper();

  @Autowired
  public NativeMonthCalculationService(
      RyczaltPeriodJpaRepository periods,
      NativeMonthInputAggregator inputAggregator,
      RyczaltCalculationPersistenceAdapter calculations,
      RyczaltObligationJpaRepository obligations,
      SettlementService settlement,
      RyczaltCalculationJpaRepository calculationRows,
      Clock clock) {
    this.periods = periods;
    this.inputAggregator = inputAggregator;
    this.calculations = calculations;
    this.obligations = obligations;
    this.settlement = settlement;
    this.calculationRows = calculationRows;
    this.clock = clock;
  }

  @Transactional
  public NativeMonthCalculationResult calculateFromPersistedInvoices(
      long profileId, YearMonth month) {
    RyczaltPeriodEntity period = period(profileId, month);
    NativeMonthCalculationInput input = inputAggregator.aggregate(profileId, period.id(), month);
    return calculate(profileId, month, input);
  }

  @Transactional
  public NativeMonthCalculationResult calculate(
      long profileId, YearMonth month, NativeMonthCalculationInput input) {
    RyczaltPeriodEntity period = period(profileId, month);
    if (period.getStatus().isFrozen()) {
      throw new IllegalStateException("Frozen period cannot be recalculated: " + month);
    }

    String ryczaltRules = ruleVersion("RYCZALT", month);
    String vatRules = ruleVersion("VAT", month);
    String zusRules = ruleVersion("ZUS", month);
    ZusCalculationResult zusResult = new ZusCalculator(zusRules).calculate(input.zus());
    RyczaltCalculationResult ryczaltResult =
        new RyczaltCalculator(ryczaltRules)
            .calculate(
                new RyczaltCalculationInput(
                    input.revenueByRate(),
                    zusResult.deductibleSocial(),
                    zusResult.healthPaidForDeduction(),
                    input.deductionsAlreadyConsumed()));
    NativeMonthCalculationInput calculationInput = withVatCarryForward(profileId, month, input);
    VatCalculationResult vatResult = new VatCalculator(vatRules).calculate(calculationInput.vat());

    RyczaltCalculationEntity ryczaltCalculation =
        save(
            period,
            profileId,
            CalculationType.RYCZALT,
            json(ryczaltResult),
            InputFingerprint.ryczalt(
                new RyczaltCalculationInput(
                    calculationInput.revenueByRate(),
                    zusResult.deductibleSocial(),
                    zusResult.healthPaidForDeduction(),
                    calculationInput.deductionsAlreadyConsumed()),
                ryczaltRules),
            ryczaltRules);
    RyczaltCalculationEntity vatCalculation =
        save(
            period,
            profileId,
            CalculationType.VAT,
            json(vatResult),
            fingerprint(calculationInput.vat(), vatRules),
            vatRules);
    RyczaltCalculationEntity zusCalculation =
        save(
            period,
            profileId,
            CalculationType.ZUS,
            json(zusResult),
            fingerprint(calculationInput.zus(), zusRules),
            zusRules);

    upsertObligation(
        profileId,
        period,
        ObligationType.RYCZALT,
        ryczaltResult.calculatedTax(),
        ryczaltCalculation);
    upsertObligation(
        profileId, period, ObligationType.VAT, vatResult.calculatedVat(), vatCalculation);
    upsertObligation(profileId, period, ObligationType.ZUS, zusResult.total(), zusCalculation);
    period.markCalculated(Instant.now(clock));
    periods.save(period);
    settlement.settlePeriod(profileId, month);
    return new NativeMonthCalculationResult(
        month,
        ryczaltResult.calculatedTax(),
        vatResult.calculatedVat(),
        zusResult.total(),
        ryczaltResult,
        vatResult,
        zusResult);
  }

  private RyczaltPeriodEntity period(long profileId, YearMonth month) {
    return periods
        .findLocked(profileId, month.getYear(), month.getMonthValue())
        .orElseGet(
            () ->
                periods.save(
                    new RyczaltPeriodEntity(
                        profileId, month.getYear(), month.getMonthValue(), PeriodStatus.OPEN)));
  }

  private RyczaltCalculationEntity save(
      RyczaltPeriodEntity period,
      long profileId,
      CalculationType type,
      String result,
      String fingerprint,
      String ruleVersion) {
    return calculations.saveCurrent(
        period, profileId, type, result, fingerprint, ruleVersion, CALCULATOR_VERSION);
  }

  private void upsertObligation(
      long profileId,
      RyczaltPeriodEntity period,
      ObligationType type,
      BigDecimal amount,
      RyczaltCalculationEntity calculation) {
    List<RyczaltObligationEntity> existing =
        obligations.findByProfileIdAndPeriodIdOrderByTypeAsc(profileId, period.id());
    RyczaltObligationEntity obligation =
        existing.stream().filter(row -> row.getType() == type).findFirst().orElse(null);
    if (obligation == null) {
      obligations.save(
          new RyczaltObligationEntity(
              period,
              profileId,
              type,
              amount,
              CurrencyType.PLN,
              null,
              ObligationStatus.OPEN,
              calculation.getId()));
    } else {
      obligation.refreshCalculation(amount, calculation.getId());
      obligations.save(obligation);
    }
  }

  private String json(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Could not serialize native calculation", exception);
    }
  }

  private String fingerprint(Object value, String ruleVersion) {
    return InputFingerprint.sha256(ruleVersion + "|" + json(value));
  }

  private String ruleVersion(String type, YearMonth month) {
    return type + "_" + month.getYear() + "_POC_V1";
  }

  private NativeMonthCalculationInput withVatCarryForward(
      long profileId, YearMonth month, NativeMonthCalculationInput input) {
    if (month.getMonthValue() == 1) return withVatCarry(input, BigDecimal.ZERO);
    YearMonth previousMonth = month.minusMonths(1);
    RyczaltPeriodEntity previous =
        periods
            .findByProfileIdAndYearAndMonth(
                profileId, previousMonth.getYear(), previousMonth.getMonthValue())
            .orElseThrow(
                () ->
                    new NeedsReviewException(
                        month, "Previous VAT calculation is missing for " + previousMonth));
    RyczaltCalculationEntity previousVat =
        calculationRows
            .findByProfileIdAndPeriodIdAndTypeAndCurrentTrue(
                profileId, previous.id(), CalculationType.VAT)
            .orElseThrow(
                () ->
                    new NeedsReviewException(
                        month,
                        "Previous VAT calculation is stale or missing for " + previousMonth));
    BigDecimal carry = readCarryForward(previousVat.getResultJson(), month);
    return withVatCarry(input, carry);
  }

  private NativeMonthCalculationInput withVatCarry(
      NativeMonthCalculationInput input, BigDecimal carry) {
    var vat = input.vat();
    var withCarry =
        new com.smartbox.investory.ryczalt.calculation.vat.VatCalculationInput(
            vat.outputVatBeforeCorrections(),
            vat.salesCorrections(),
            vat.deductibleInputVat(),
            vat.explicitAdjustments(),
            carry);
    return new NativeMonthCalculationInput(
        input.revenueByRate(), withCarry, input.zus(), input.deductionsAlreadyConsumed());
  }

  private BigDecimal readCarryForward(String resultJson, YearMonth month) {
    try {
      var node = json.readTree(resultJson);
      var carry = node.get("excessVatCarryForward");
      if (carry == null || !carry.isNumber()) {
        throw new NeedsReviewException(month, "Previous VAT carry-forward is missing");
      }
      return carry.decimalValue();
    } catch (Exception exception) {
      if (exception instanceof NeedsReviewException needsReview) throw needsReview;
      throw new NeedsReviewException(month, "Previous VAT calculation JSON is malformed");
    }
  }
}
