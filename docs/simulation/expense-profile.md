# Staged expense profile

Retirement Simulation keeps two separate spending effects:

- **Inflation** models nominal price growth.
- **Expense profile** models structural, lifecycle changes in real spending, such as childcare,
  education, or costs that disappear later.

The effective recurring expense for a simulation year is:

```text
inflation-adjusted base expense × expense profile factor
```

The profile is an immutable schedule relative to the simulation start year. A step applies from its
`fromYear` until the next step. Missing or empty configuration uses factor `1.00` everywhere and
preserves legacy simulations.

Example plan value:

```text
0:1.00;8:0.90;15:0.80;22:0.75
```

This means 10% lower real spending from simulation year 8, 20% lower from year 15, and 25% lower
from year 22. Factors above `1.00` are valid. Steps must have non-negative, strictly increasing
years and positive factors.

The profile is intentionally a single factor schedule. A future structured-expense module may
derive the schedule from categories such as education, housing, travel, and healthcare without
requiring changes to the simulation engine.

Simulation remains a deterministic average-case projection. A future periodic Review feature may
recommend actions from actual results, but Review strategies are not part of this profile.
