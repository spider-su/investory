# Application read-only baseline active scenario

## Scope

Safe active-control coverage across the rendered page inventory. The browser may use shared
navigation, period/currency selectors, tabs, disclosure sections, pagination, sorting, filters,
menus, tooltips, informational dialogs, Back/Forward, and Reload when those controls exist on the
page. A missing optional control is `N/A`, not a failure.

## Page-specific rules

| Page class | Safe interaction | Detail coverage |
| --- | --- | --- |
| Dashboard and asset detail | period links, shared navigation, Back/Forward, Reload | GET asset detail only |
| Long-term assets | disclosures and GET asset detail/edit pages; leave with Cancel/Back | all available seeded asset representations, using the Long-Term Assets reference scenario |
| Profile and reconciliation | source/detail links, disclosures, Back/Forward, Reload | no reconciliation refresh |
| Simulation and reviews | display-currency/scenario selectors, timeline links, disclosures, Back/Forward, Reload | plan editor is inspected only; no Save, Add, Remove, Close, Reopen, or Refresh |
| Sandbox and settings | inspect form/layout and informational controls only | no Calculate or integration action |

## Safety and evidence

Install request/page/console listeners before navigation. Stop on any unexpected POST, PUT, PATCH,
or DELETE, authentication boundary, or environment blocker. Record route, viewport, control,
expected URL/state, observed state, and sanitized evidence for suspicious or failed behavior.

This scenario does not duplicate page facts. Canonical values remain in the HappyInvestor facts and
the Long-Term Assets facts scenario.
