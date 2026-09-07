# Retirement module --- quick KT

> **Goal:** navigate plan persistence, planning timeline, simulation, and analysis without mixing stored inputs with generated results.

## What it owns

Retirement owns mutable retirement plans, plan events and reviewed planning years,
planning timeline state, forward-input preparation, deterministic simulation,
and analysis. It consumes public Investment, Long-Term, and Profile APIs; it
does not use their infrastructure or persistence.

``` text
source APIs + saved plan + temporal context
                    |
                    v
       ForwardSimulationInputService
                    |
                    v
          RetirementProjectionService
                    |
                    v
          RetirementSimulationService
                    |
                    +--> SimulationResult / timeline
                    +--> RetirementAnalysisService
```

## Three kinds of state

- **Stored inputs:** `retirement_plans`, `retirement_plan_events`, and `retirement_planning_years`. Plans hold user intent and an opaque reviewed baseline; projections remain runtime output. There is no separate persisted SimulationPlan/revision model in the current implementation.
- **Runtime context:** selected scenario, display currency, current date/year, and projection context. These select how to evaluate a plan.
- **Generated results:** projections, `SimulationResult`, analysis, sensitivities, and sustainable-spending results. Recalculate these; do not persist them as source facts.

## Where to start

- Plan boundary: `RetirementPlanApi` and `CanonicalRetirementPlanService`.
- Projection boundary: `RetirementProjectionApi` and `RetirementProjectionService`.
- Timeline preparation: `ForwardSimulationInputService` and `CurrentYearProjectionBridge`.
- Engine: `RetirementSimulationService` and `RetirementBucketEngine`.
- Interpretation: `RetirementAnalysisService`, `SimulationSensitivityAnalysisService`, and `SustainableSpendingAnalysisService`.
- Sandbox: `RetirementSandboxSimulationService`; it must not mutate source domains or saved plans.

## Safe-change rules

- Keep the engine deterministic: pass baseline year/context; do not read system time inside core simulation.
- Scenarios affect projected assumptions, not actual facts.
- Preserve bucket continuity from one year to the next.
- Keep capitalized return separate from spendable cash income.
- Reuse the canonical engine from Analysis and UI; do not duplicate formulas.
- For plan persistence changes, update the Flyway migration and fast snapshot together.

Canonical detail: `docs/domain/retirement-simulation.md`, `docs/domain/retirement-analysis.md`, and `docs/domain/planning-timeline.md`.
