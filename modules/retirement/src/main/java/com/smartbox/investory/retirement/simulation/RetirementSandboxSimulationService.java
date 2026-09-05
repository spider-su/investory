package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.profile.api.model.AssetHorizon;
import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.api.model.Liquidity;
import com.smartbox.investory.profile.api.model.ProfileAllocation;
import com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation;
import com.smartbox.investory.profile.api.model.ProfileAssetProjection;
import com.smartbox.investory.profile.api.model.ProfileIncomeSummary;
import com.smartbox.investory.retirement.api.RetirementSandboxInputTranslator;
import com.smartbox.investory.retirement.api.RetirementSandboxApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Translates the simple sandbox input into the canonical retirement simulation. */
@Service
public final class RetirementSandboxSimulationService implements RetirementSandboxApi {
  private final RetirementSimulationService canonicalSimulation;

  public RetirementSandboxSimulationService() {
    this(new RetirementSimulationService());
  }

  @Autowired
  public RetirementSandboxSimulationService(RetirementSimulationService canonicalSimulation) {
    this.canonicalSimulation = canonicalSimulation;
  }

  @Override
  public SimulationResult simulate(SandboxSimulationInput input) {
    SimulationAssumptions assumptions = RetirementSandboxInputTranslator.toAssumptions(input);
    SimulationResult result =
        canonicalSimulation.simulate(profile(input), assumptions, SimulationScenario.BASE);
    List<SimulationYear> retiredYears =
        result.years().stream()
            .filter(year -> year.lifecyclePhase() == SimulationLifecyclePhase.RETIRED)
            .toList();
    return new SimulationResult(
        result.scenario(),
        result.simulationFailed(),
        result.failureAge(),
        result.firstFailureShortfall(),
        result.totalUnfundedAmount(),
        retiredYears);
  }

  private static InvestmentProfile profile(SandboxSimulationInput input) {
    BigDecimal total = input.cash().add(input.bonds()).add(input.equities());
    return new InvestmentProfile(
        0L,
        CurrencyType.PLN,
        input.equities(),
        BigDecimal.ZERO,
        total,
        input.cash(),
        BigDecimal.ZERO,
        List.of(
            allocation(EconomicBucket.FIXED_INCOME, input.bonds()),
            allocation(EconomicBucket.EQUITY, input.equities())),
        input.monthlyRentalIncome().multiply(BigDecimal.valueOf(12)),
        BigDecimal.ZERO,
        ProfileAssetProjection.EMPTY,
        input.cash(),
        input.equities(),
        new ProfileIncomeSummary(null, null, null, null, null, null, null),
        ProfileAllocationReconciliation.EMPTY);
  }

  private static ProfileAllocation allocation(EconomicBucket bucket, BigDecimal value) {
    return new ProfileAllocation(
        bucket, value, BigDecimal.ZERO, Liquidity.LIQUID, AssetHorizon.SHORT_TERM);
  }
}
