# Full architecture analysis

Perform a deeper design review for the supplied Investory change.

Inspect relevant current code, documentation, persistence model, tests, and integration
boundaries.

Evaluate:

- domain boundaries
- dependency direction
- ports/adapters where useful
- configuration ownership
- persistence/schema impact
- migration strategy
- scheduling/background-work impact
- secrets/credentials handling
- backwards compatibility
- test strategy
- rollout/staging
- operational complexity
- likely future extension points

Keep the solution proportional to current needs.

When proposing an abstraction, identify at least two concrete consumers or a clear
near-term reason for it. Otherwise prefer a localized implementation.

For plugin/extensibility designs distinguish:

- stable core/domain contracts
- adapter/plugin implementation
- user configuration
- credentials/secrets
- enable/disable lifecycle
- scheduler configuration
- failure isolation

Do not implement the design.

## Output

# Full Architecture

## Goal

Restate the requested capability and constraints.

## Current Architecture

Only relevant components.

## Target Design

Describe components and dependency direction.

Use a small text diagram when useful.

## Decisions

For each important decision:

- choice
- alternatives
- rationale
- tradeoff

## Data / Migration Impact

Describe schema/config migration where applicable.

## Testing

Cover:

- unit
- integration
- migration
- reconciliation/golden implications

## Rollout Plan

Break into independently shippable phases where possible.

## Effort

Estimate each phase as `S | M | L | XL`.

## Risks

List concrete risks, not generic software-development risks.

## Recommendation

Give the smallest sensible first phase.
