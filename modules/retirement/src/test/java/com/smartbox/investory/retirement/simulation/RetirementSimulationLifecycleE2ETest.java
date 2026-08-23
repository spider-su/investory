package com.smartbox.investory.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.smartbox.investory.longterm.api.model.InterestTreatmentModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel;
import com.smartbox.investory.retirement.infrastructure.simulation.SimulationPlanEntity;
import com.smartbox.investory.retirement.infrastructure.simulation.SimulationPlanEventRepository;
import com.smartbox.investory.retirement.infrastructure.simulation.SimulationPlanRepository;
import com.smartbox.investory.retirement.infrastructure.simulation.SimulationPlanRevisionEntity;
import com.smartbox.investory.retirement.infrastructure.simulation.SimulationPlanRevisionEventRepository;
import com.smartbox.investory.retirement.infrastructure.simulation.SimulationPlanRevisionRepository;
import com.smartbox.investory.retirement.profile.EconomicBucket;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.profile.Liquidity;
import com.smartbox.investory.retirement.profile.ProjectedLongTermAsset;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * High-level deterministic lifecycle acceptance contract for Retirement Simulation.
 *
 * <p>The repository has no browser-driver dependency. This test therefore uses the existing service
 * and Thymeleaf contract layers instead of introducing a second E2E framework. It keeps the browser
 * lifecycle stages explicit and compares every scenario to the canonical simulator.
 */
class RetirementSimulationLifecycleE2ETest {

  @Test
  void goldenPlanLifecyclePersistsRecalculatesAndKeepsScenariosIsolated() throws Exception {
    InvestmentProfile profile = goldenProfile();
    SimulationAssumptions original = goldenAssumptions(profile);
    InMemoryPlanStore store = new InMemoryPlanStore();
    SimulationPlanService plans = store.service();

    // Create through the same plan-service boundary used by the UI POST handler.
    SimulationPlanEntity created = plans.create(1L, "Golden lifecycle", original);
    assertThat(plans.assumptions(1L, created.getId())).isEqualTo(original);
    assertThat(store.persistedRevision().getAnnualLivingExpenses())
        .isEqualByComparingTo(original.annualLivingExpenses());

    // Edit Plan round-trip: change a safe value, persist it, then restore the golden value.
    SimulationAssumptions edited = original.withRecurringSpending(new BigDecimal("250000"));
    plans.update(1L, created.getId(), "Golden lifecycle", edited);
    assertThat(plans.assumptions(1L, created.getId()).annualLivingExpenses())
        .isEqualByComparingTo(edited.annualLivingExpenses());
    plans.update(1L, created.getId(), "Golden lifecycle", original);
    assertThat(plans.assumptions(1L, created.getId())).isEqualTo(original);

    RetirementSimulationService simulator = new RetirementSimulationService();
    Map<SimulationScenario, SimulationResult> scenarios =
        simulator.compareScenarios(profile, plans.assumptions(1L, created.getId()));

    // Base, Conservative, and Optimistic all use the authoritative simulator result.
    assertThat(scenarios)
        .containsKeys(
            SimulationScenario.BASE,
            SimulationScenario.CONSERVATIVE,
            SimulationScenario.OPTIMISTIC);
    for (SimulationScenario scenario : SimulationScenario.values()) {
      SimulationResult direct = simulator.simulate(profile, original, scenario);
      assertThat(scenarios.get(scenario).years()).isEqualTo(direct.years());
      assertLifecycleStates(scenarios.get(scenario));
    }
    assertThat(scenarios.get(SimulationScenario.CONSERVATIVE).years())
        .isNotEqualTo(scenarios.get(SimulationScenario.OPTIMISTIC).years());

    // Scenario switching is transient. It must not mutate the persisted plan assumptions.
    assertThat(plans.assumptions(1L, created.getId())).isEqualTo(original);
    assertThat(store.persistedRevision().getAnnualLivingExpenses())
        .isEqualByComparingTo(original.annualLivingExpenses());

    // The rendered UI contract must contain the complete desktop lifecycle surfaces.
    String simulation = readTemplate("simulation.html");
    String editor = readTemplate("simulation-plan-edit.html");
    assertThat(simulation)
        .contains(
            "Scenario",
            "Yearly projection",
            "Cash flow / funding",
            "Financial outlook",
            "Plan status",
            "Projected");
    assertThat(editor)
        .contains(
            "1. Timeline",
            "2. Spending",
            "3. Income",
            "4. Events",
            "5. Reserve &amp; funding",
            "Total annual spending",
            "Save");
    assertThat(editor).doesNotContain("new Date()", "T(java.math.BigDecimal)");
  }

  private static String readTemplate(String name) throws Exception {
    Path path = Path.of("adapters/web-ui/src/main/resources/templates", name);
    if (!Files.exists(path)) {
      path = Path.of("../../adapters/web-ui/src/main/resources/templates", name);
    }
    return Files.readString(path);
  }

  private static void assertLifecycleStates(SimulationResult result) {
    assertThat(result.years()).isNotEmpty();
    assertThat(result.years().getFirst().year()).isEqualTo(2025);
    assertThat(result.years().getFirst().lifecyclePhase())
        .isEqualTo(SimulationLifecyclePhase.WORKING);
    assertThat(result.years().stream().map(SimulationYear::year)).isSorted();
    assertThat(result.years()).allMatch(year -> year.year() >= 2025 && year.year() < 3000);
    for (SimulationYear year : result.years()) {
      BigDecimal cashIncome =
          year.employmentIncome()
              .add(year.rentalIncome())
              .add(year.bondIncome())
              .add(year.pensionIncome())
              .add(year.eventIncome());
      assertThat(year.totalIncome()).isEqualByComparingTo(cashIncome);
      assertThat(year.requiredPortfolioFunding())
          .isEqualByComparingTo(
              year.totalExpenses().subtract(year.totalIncome()).max(BigDecimal.ZERO));
      assertThat(year.failed()).isEqualTo(year.unfundedAmount().signum() > 0);
    }
  }

  private static SimulationAssumptions goldenAssumptions(InvestmentProfile profile) {
    return SimulationAssumptions.defaults(profile, 40, 80, 2025)
        .withRecurringSpending(new BigDecimal("240000"))
        .withInflationRate(new BigDecimal("0.025"))
        .withSpendingGrowthSpread(new BigDecimal("-0.010"))
        .withRentalIncomeGrowthSpread(new BigDecimal("-0.015"))
        .withEquityReturnRate(new BigDecimal("0.085"))
        .withAnnualEmploymentIncome(new BigDecimal("120000"))
        .withAnnualPreRetirementContribution(new BigDecimal("24000"))
        .withAnnualPension(new BigDecimal("7000"))
        .withRetirementAge(42)
        .withPensionStartAge(67)
        .withExpenseProfile(
            new ExpenseProfile(
                List.of(
                    new ExpenseProfileStep(0, new BigDecimal("1.00")),
                    new ExpenseProfileStep(10, new BigDecimal("1.00")),
                    new ExpenseProfileStep(20, new BigDecimal("0.85")),
                    new ExpenseProfileStep(30, new BigDecimal("0.75")))));
  }

  private static InvestmentProfile goldenProfile() {
    var bond =
        new ProjectedLongTermAsset(
            10L,
            "Golden bond",
            LongTermAssetTypeModel.BOND,
            EconomicBucket.FIXED_INCOME,
            CurrencyType.PLN,
            new BigDecimal("486000"),
            Liquidity.LIQUID,
            List.of(
                new ProjectedLongTermAsset.Period(
                    LocalDate.of(2020, 1, 1),
                    null,
                    new BigDecimal("38880"),
                    BigDecimal.ZERO,
                    new BigDecimal("0.10"))),
            List.of(),
            LocalDate.of(2028, 12, 31),
            new BigDecimal("486000"),
            InterestTreatmentModel.PAY_OUT,
            new BigDecimal("0.20"),
            null,
            false);
    var rental =
        new ProjectedLongTermAsset(
            11L,
            "Golden rental",
            LongTermAssetTypeModel.REAL_ESTATE,
            EconomicBucket.REAL_ESTATE,
            CurrencyType.PLN,
            new BigDecimal("3000000"),
            Liquidity.ILLIQUID,
            List.of(
                new ProjectedLongTermAsset.Period(
                    LocalDate.of(2020, 1, 1),
                    null,
                    new BigDecimal("174803.62"),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO)),
            List.of(),
            null,
            null,
            InterestTreatmentModel.PAY_OUT,
            BigDecimal.ZERO,
            null,
            false);
    return new InvestmentProfile(
        1L,
        CurrencyType.PLN,
        new BigDecimal("550000"),
        new BigDecimal("3486000"),
        new BigDecimal("4036000"),
        BigDecimal.ZERO,
        new BigDecimal("213683.62"),
        BigDecimal.ZERO,
        new BigDecimal("100000"),
        new BigDecimal("3000000"),
        List.of(),
        List.of(rental, bond),
        new BigDecimal("174803.62"),
        new BigDecimal("38880"));
  }

  private static final class InMemoryPlanStore {
    private final SimulationPlanRepository plans = mock(SimulationPlanRepository.class);
    private final SimulationPlanEventRepository events = mock(SimulationPlanEventRepository.class);
    private final SimulationPlanRevisionRepository revisions =
        mock(SimulationPlanRevisionRepository.class);
    private final SimulationPlanRevisionEventRepository revisionEvents =
        mock(SimulationPlanRevisionEventRepository.class);
    private final List<SimulationPlanEntity> rows = new ArrayList<>();
    private final List<SimulationPlanRevisionEntity> revisionRows = new ArrayList<>();
    private SimulationPlanEntity current;

    private InMemoryPlanStore() {
      when(plans.existsByPortfolioIdAndName(anyLong(), any())).thenReturn(false);
      when(plans.save(any()))
          .thenAnswer(
              invocation -> {
                SimulationPlanEntity value = invocation.getArgument(0);
                if (value.getId() == null) value.setId(1L);
                current = value;
                if (!rows.contains(value)) rows.add(value);
                return value;
              });
      when(plans.findByIdAndPortfolioId(anyLong(), anyLong()))
          .thenAnswer(invocation -> Optional.ofNullable(current));
      when(plans.findAllByPortfolioIdOrderByName(anyLong())).thenAnswer(invocation -> rows);
      when(revisions.save(any()))
          .thenAnswer(
              invocation -> {
                SimulationPlanRevisionEntity value = invocation.getArgument(0);
                if (value.getId() == null) value.setId((long) revisionRows.size() + 1);
                revisionRows.add(value);
                return value;
              });
      when(revisions.findAllBySimulationPlanIdOrderByRevisionNumberDesc(anyLong()))
          .thenAnswer(invocation -> revisionRows.reversed());
      when(revisions.findByIdAndSimulationPlanId(anyLong(), anyLong()))
          .thenAnswer(
              invocation ->
                  revisionRows.stream()
                      .filter(row -> row.getId().equals(invocation.getArgument(0)))
                      .findFirst());
      when(revisionEvents.findAllByRevisionIdOrderByYearAscIdAsc(anyLong())).thenReturn(List.of());
      when(events.findAllBySimulationPlanIdOrderByYearAscIdAsc(anyLong())).thenReturn(List.of());
    }

    private SimulationPlanService service() {
      return new SimulationPlanService(plans, events, revisions, revisionEvents);
    }

    private SimulationPlanRevisionEntity persistedRevision() {
      return revisionRows.getLast();
    }
  }
}
