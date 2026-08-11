---
name: arch
description: Analyze an Investory design/change idea before implementation. Use explicitly as $arch or $arch full followed by the idea.
---

# Investory Architecture

Planning and design only.

Do not modify code, migrations, tests, or database state.

Interpret:

- `$arch <idea>` -> BASIC
- `$arch full <idea>` -> FULL

If no concrete idea/change is supplied, ask for the change to evaluate.

Inspect only the repository areas relevant to the requested design.

## BASIC

Goal: produce the smallest practical design and an implementation-effort estimate.

Report:

1. current relevant architecture
2. proposed target design
3. affected modules/files/components
4. main risks/tradeoffs
5. staged implementation plan
6. effort estimate

Prefer incremental changes over framework-building.

Reuse existing Investory patterns and abstractions where possible.

Do not introduce plugin systems, event buses, distributed caches, generic frameworks,
or additional infrastructure unless the requested capability actually requires them.

### BASIC output

# Architecture

## Current

Short description of relevant existing design.

## Proposal

Smallest coherent target design.

## Impact

| Area | Change | Risk |
|---|---|---|

## Implementation

Numbered phases.

## Effort

Use relative sizing:

- `S`: localized change
- `M`: several components/tests
- `L`: cross-cutting architecture/migrations
- `XL`: multi-phase redesign

Give a short rationale.

## Recommendation

State whether to:

- implement now
- stage first
- defer

## FULL

Read `references/full.md` and follow it.
