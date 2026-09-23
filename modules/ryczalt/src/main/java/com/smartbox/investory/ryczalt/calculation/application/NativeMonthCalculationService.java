package com.smartbox.investory.ryczalt.calculation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.smartbox.investory.ryczalt.persistence.RyczaltCalculationPersistenceAdapter;
import com.smartbox.investory.ryczalt.persistence.RyczaltObligationEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltObligationJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodJpaRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Runs the complete native RYCZALT, VAT, ZUS and obligation cycle for one month. */
@Service
public class NativeMonthCalculationService {
  private static final String RYCZALT_RULES = "ryczalt-2026";
  private static final String VAT_RULES = "vat-2026";
  private static final String ZUS_RULES = "zus-2026";
  private static final String CALCULATOR_VERSION = "native-month-1";

  private final RyczaltPeriodJpaRepository periods;
  private final NativeMonthInputAggregator inputAggregator;
  private final RyczaltCalculationPersistenceAdapter calculations;
  private final RyczaltObligationJpaRepository obligations;
  private final RyczaltCalculator ryczalt = new RyczaltCalculator(RYCZALT_RULES);
  private final VatCalculator vat = new VatCalculator();
  private final ZusCalculator zus = new ZusCalculator();
  private final ObjectMapper json = new ObjectMapper();

  public NativeMonthCalculationService(
      RyczaltPeriodJpaRepository periods,
      NativeMonthInputAggregator inputAggregator,
      RyczaltCalculationPersistenceAdapter calculations,
      RyczaltObligationJpaRepository obligations) {
    this.periods = periods;
    this.inputAggregator = inputAggregator;
    this.calculations = calculations;
    this.obligations = obligations;
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

    ZusCalculationResult zusResult = zus.calculate(input.zus());
    RyczaltCalculationResult ryczaltResult =
        ryczalt.calculate(
            new RyczaltCalculationInput(
                input.revenueByRate(),
                zusResult.deductibleSocial(),
                zusResult.health(),
                input.deductionsAlreadyConsumed()));
    VatCalculationResult vatResult = vat.calculate(input.vat());

    RyczaltCalculationEntity ryczaltCalculation =
        save(
            period,
            profileId,
            CalculationType.RYCZALT,
            json(ryczaltResult),
            InputFingerprint.ryczalt(
                new RyczaltCalculationInput(
                    input.revenueByRate(),
                    zusResult.deductibleSocial(),
                    zusResult.health(),
                    input.deductionsAlreadyConsumed()),
                RYCZALT_RULES),
            RYCZALT_RULES);
    RyczaltCalculationEntity vatCalculation =
        save(
            period,
            profileId,
            CalculationType.VAT,
            json(vatResult),
            fingerprint(input.vat(), VAT_RULES),
            VAT_RULES);
    RyczaltCalculationEntity zusCalculation =
        save(
            period,
            profileId,
            CalculationType.ZUS,
            json(zusResult),
            fingerprint(input.zus(), ZUS_RULES),
            ZUS_RULES);

    upsertObligation(
        profileId,
        period,
        ObligationType.RYCZALT,
        ryczaltResult.calculatedTax(),
        ryczaltCalculation);
    upsertObligation(
        profileId, period, ObligationType.VAT, vatResult.calculatedVat(), vatCalculation);
    upsertObligation(profileId, period, ObligationType.ZUS, zusResult.total(), zusCalculation);
    period.markCalculated(java.time.Instant.now());
    periods.save(period);
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
        .findByProfileIdAndYearAndMonth(profileId, month.getYear(), month.getMonthValue())
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
}
