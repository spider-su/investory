# Mission: <short name>

- Status: `DRAFT` / `READY`
- Environment: `<deployed develop base URL>`
- Profile/user scope: `<operator-provided scope>`
- Browser/viewport: `<browser at 2560x1440>`
- Mission revision: `<date or identifier>`

## Question

<One observable UI question. Example: can a read-only user move from the dashboard to the profile
and long-term-assets pages without visible errors or broken desktop rendering?>

## Routes and source contracts

| Route | Why in scope | Expected source |
| --- | --- | --- |
| `<route>` | `<user-visible behavior>` | `<quality plan, UI test, or HappyInvestor fact class>` |

Use links to existing sources. Do not paste expected financial facts here.

## Allowed actions

- Navigate to the listed routes and safe linked read-only pages.
- Reload, use back/forward, change read-only filters/query parameters, and expand read-only
  sections.
- Inspect rendered text, accessible structure, geometry, page errors, console, and network events.

## Forbidden actions

- Any form submission or create/edit/delete/archive/reactivate/import/export/refresh/reconciliation
  action.
- Any direct API, console, fetch/XHR, or browser-storage mutation.
- Any persistent-data, settings, provider, scheduler, or credential change.

## Checks

- [ ] Each route loads with the expected HTTP response and visible shell/content.
- [ ] Safe navigation and reload preserve the expected route/state.
- [ ] At `2560x1440`, content renders without obvious clipping or overlap.
- [ ] No unexplained page errors, relevant console errors, or failed first-party requests.
- [ ] Useful evidence is captured for every suspicious observation.

## Stop conditions

Stop before a mutation, unexpected auth boundary, unsafe data exposure, or environment blocker. Log
the stop reason and classify the mission according to `qa/ai/instructions.md`.
