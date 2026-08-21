package com.smartbox.investory.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.smartbox.investory.investment.application.InvestmentAnnualProjectionService;
import com.smartbox.investory.longterm.application.service.LongTermAnnualProjectionService;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetirementSimulationAnnualRentalIncomeTest {
  @Test
  void supportsAnnualRentalIncomeThatDoesNotDivideEvenlyIntoMonths() {
    var service =
        new RetirementSimulationService(
            new LongTermAnnualProjectionService(), new InvestmentAnnualProjectionService());
    var profile =
        new InvestmentProfile(
            1L,
            CurrencyType.PLN,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            List.of(),
            List.of());
    var assumptions = SimulationAssumptions.defaults(profile, 65, 65, 2026);

    assertThatCode(() -> service.simulate(profile, assumptions, SimulationScenario.BASE))
        .doesNotThrowAnyException();
  }
}
