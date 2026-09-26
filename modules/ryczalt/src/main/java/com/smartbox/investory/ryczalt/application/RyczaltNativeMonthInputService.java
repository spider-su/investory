package com.smartbox.investory.ryczalt.application;

import com.smartbox.investory.ryczalt.persistence.RyczaltNativeMonthInputEntity;
import com.smartbox.investory.ryczalt.persistence.RyczaltNativeMonthInputJpaRepository;
import com.smartbox.investory.ryczalt.persistence.RyczaltPeriodLifecycleService;
import java.time.YearMonth;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RyczaltNativeMonthInputService {
  private final RyczaltNativeMonthInputJpaRepository inputs;
  private final RyczaltPeriodLifecycleService lifecycle;

  public RyczaltNativeMonthInputService(
      RyczaltNativeMonthInputJpaRepository inputs, RyczaltPeriodLifecycleService lifecycle) {
    this.inputs = inputs;
    this.lifecycle = lifecycle;
  }

  @Transactional
  public void save(long profileId, YearMonth month, Command command) {
    Objects.requireNonNull(month, "month");
    Objects.requireNonNull(command, "command");
    RyczaltNativeMonthInputEntity input =
        inputs
            .findByProfileIdAndYearAndMonth(profileId, month.getYear(), month.getMonthValue())
            .orElse(null);
    if (input == null) {
      inputs.save(new RyczaltNativeMonthInputEntity(profileId, month, command));
      return;
    }
    var changes = input.changesComparedTo(command);
    input.update(command);
    inputs.save(input);
    for (var change : changes) {
      lifecycle.invalidate(profileId, month, change, "native-month-input");
    }
  }

  public record Command(
      boolean jdgActive,
      boolean qualifyingUop,
      String zusRegime,
      boolean voluntarySickness,
      java.math.BigDecimal ytdRyczaltRevenue,
      java.math.BigDecimal fullJdgSocial,
      java.math.BigDecimal socialContributionDeduction,
      java.math.BigDecimal healthContributionOverride,
      java.math.BigDecimal healthContributionPaidOverride,
      java.math.BigDecimal deductionsAlreadyConsumed,
      java.math.BigDecimal salesCorrections,
      java.math.BigDecimal explicitVatAdjustments) {
    public Command(
        boolean jdgActive,
        boolean qualifyingUop,
        String zusRegime,
        boolean voluntarySickness,
        java.math.BigDecimal ytdRyczaltRevenue,
        java.math.BigDecimal fullJdgSocial,
        java.math.BigDecimal socialContributionDeduction,
        java.math.BigDecimal healthContributionOverride,
        java.math.BigDecimal deductionsAlreadyConsumed,
        java.math.BigDecimal salesCorrections,
        java.math.BigDecimal explicitVatAdjustments) {
      this(
          jdgActive,
          qualifyingUop,
          zusRegime,
          voluntarySickness,
          ytdRyczaltRevenue,
          fullJdgSocial,
          socialContributionDeduction,
          healthContributionOverride,
          null,
          deductionsAlreadyConsumed,
          salesCorrections,
          explicitVatAdjustments);
    }

    public Command {
      ytdRyczaltRevenue = nonNegative(ytdRyczaltRevenue, "ytdRyczaltRevenue");
      deductionsAlreadyConsumed =
          nonNegative(deductionsAlreadyConsumed, "deductionsAlreadyConsumed");
      Objects.requireNonNull(salesCorrections, "salesCorrections");
      Objects.requireNonNull(explicitVatAdjustments, "explicitVatAdjustments");
      if (fullJdgSocial != null && fullJdgSocial.signum() < 0)
        throw new IllegalArgumentException("fullJdgSocial must not be negative");
      if (socialContributionDeduction != null && socialContributionDeduction.signum() < 0)
        throw new IllegalArgumentException("socialContributionDeduction must not be negative");
      if (healthContributionOverride != null && healthContributionOverride.signum() < 0)
        throw new IllegalArgumentException("healthContributionOverride must not be negative");
      if (healthContributionPaidOverride != null && healthContributionPaidOverride.signum() < 0)
        throw new IllegalArgumentException("healthContributionPaidOverride must not be negative");
    }

    private static java.math.BigDecimal nonNegative(java.math.BigDecimal value, String name) {
      Objects.requireNonNull(value, name);
      if (value.signum() < 0) throw new IllegalArgumentException(name + " must not be negative");
      return value;
    }
  }
}
