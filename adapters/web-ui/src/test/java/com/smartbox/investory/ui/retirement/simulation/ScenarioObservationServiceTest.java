package com.smartbox.investory.ui.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.retirement.api.RetirementScenarioObservationApi;
import com.smartbox.investory.retirement.api.model.*;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Scenario Observation Service")
class ScenarioObservationServiceTest {
  private final RetirementScenarioObservationApi observations =
      mock(RetirementScenarioObservationApi.class);
  private final ScenarioObservationService service = new ScenarioObservationService(observations);

  @DisplayName("equity Return Uses Requested Portfolio And Keeps Missing Distinct From Zero")
  @Test
  void equityReturnUsesRequestedPortfolioAndKeepsMissingDistinctFromZero() {
    when(observations.load(7L, null))
        .thenReturn(
            Map.of(
                "Equity return",
                new com.smartbox.investory.retirement.api.model.ScenarioObservation(
                    null,
                    null,
                    null,
                    com.smartbox.investory.retirement.api.model.ScenarioObservationAvailability
                        .UNAVAILABLE)));

    ScenarioObservation missing = service.load(7L, null).get("Equity return");

    assertNull(missing.value());
    assertEquals(ScenarioAssumptionView.Availability.UNAVAILABLE, missing.availability());
    when(observations.load(7L, null))
        .thenReturn(
            Map.of(
                "Equity return",
                new com.smartbox.investory.retirement.api.model.ScenarioObservation(
                    BigDecimal.ZERO,
                    "Observed annualized",
                    "trailing 12 months",
                    com.smartbox.investory.retirement.api.model.ScenarioObservationAvailability
                        .AVAILABLE)));
    ScenarioObservation zero = service.load(7L, null).get("Equity return");
    assertEquals(0, BigDecimal.ZERO.compareTo(zero.value()));
    assertEquals(ScenarioAssumptionView.Availability.AVAILABLE, zero.availability());
  }

  @DisplayName("rental Growth Compares Annual Net Income Run Rates Without Partial Year Proration")
  @Test
  void rentalGrowthComparesAnnualNetIncomeRunRatesWithoutPartialYearProration() {
    when(observations.load(7L, null))
        .thenReturn(
            Map.of(
                "Rental growth",
                new com.smartbox.investory.retirement.api.model.ScenarioObservation(
                    new BigDecimal("0.283"),
                    "Annual net rent run rate",
                    "as of 2026-08-26 vs prior year end",
                    com.smartbox.investory.retirement.api.model.ScenarioObservationAvailability
                        .AVAILABLE)));
    ScenarioObservation rental = service.load(7L, null).get("Rental growth");

    assertEquals(0, new BigDecimal("0.283").compareTo(rental.value()));
    assertEquals("Annual net rent run rate", rental.label());
    assertEquals("as of 2026-08-26 vs prior year end", rental.period());
  }

  @DisplayName("bond Yield Is Not Misrepresented As Realized Bond Return")
  @Test
  void bondYieldIsNotMisrepresentedAsRealizedBondReturn() {
    when(observations.load(7L, null))
        .thenReturn(
            Map.of(
                "Bond return",
                new com.smartbox.investory.retirement.api.model.ScenarioObservation(
                    null,
                    null,
                    null,
                    com.smartbox.investory.retirement.api.model.ScenarioObservationAvailability
                        .UNAVAILABLE)));
    ScenarioObservation bond = service.load(7L, null).get("Bond return");

    assertNull(bond.value());
    assertEquals(ScenarioAssumptionView.Availability.UNAVAILABLE, bond.availability());
  }
}
