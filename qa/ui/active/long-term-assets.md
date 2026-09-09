# Long-Term Assets active scenario

## Scope

Read-only exploratory verification of active controls on the Long-Term Assets overview and its asset
detail/edit representations.

## Safe exploration

Record a collapsed overview, expand each available category, inspect every asset row, and open every
available asset GET detail/edit route. Confirm each opens the expected representation, then leave via
Cancel, Back, Close, or browser Back without Save. Exercise safe disclosures, tooltips, popups,
dialogs, menus, links, reload, back/forward, and one desktop plus one narrow/mobile viewport where
the UI provides them. Let the browser agent choose semantic or accessible paths; do not require a
fixed selector order.

## Invariants

- No Save, confirmation, archive, delete, create, import, export, refresh, or other mutation action.
- No POST/PUT/PATCH/DELETE or browser/API/storage mutation is sent or performed.
- Returning to the overview preserves the read-only baseline.
- Every discovered control class is exercised safely or marked `N/A` when absent.
- URL, visible state, navigation result, console/page errors, relevant first-party requests, and
  useful visual evidence are recorded.

## References

Use `LongTermAssetCrudUiIT`, `UiPageSmokeIT`, `HappyInvestorReadOnlyUiIT`, and
`LongTermAssetsReadOnlyStress` to understand existing behavior. Their write tests are references
only and must not be executed against the target.
