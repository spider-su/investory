# Retirement module

`retirement` owns saved retirement plans, planning timeline state, forward-input preparation,
deterministic simulation, projections, and analysis. It consumes public Investment, Long-Term, and
Profile APIs only; it must not reach their persistence or infrastructure.

## Flow

```text
public source APIs + saved plan + temporal context
                    -> forward simulation input
                    -> projection and deterministic simulation
                    -> timeline, analysis, and sensitivities
```

Stored plan inputs, runtime scenario context, and generated simulation results are separate
concepts. Generated results are recalculated output, not source facts. The simulation engine must
remain deterministic and must not read system time directly.

HTTP adapters live under `retirement.rest`; planning, preview, analysis, and simulation services
contain the application behavior. Funding behavior is owned by `RetirementFundingPolicy`.

Detailed guidance: [`docs/kt/retirement-module-quick-kt.md`](../../docs/kt/retirement-module-quick-kt.md),
[`docs/domain/retirement-simulation.md`](../../docs/domain/retirement-simulation.md), and
[`docs/domain/planning-timeline.md`](../../docs/domain/planning-timeline.md).
