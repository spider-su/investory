# Financial Landscape performance cleanup

2026-10-21. Local implementation changes; no deployment performed.

## Observed live baseline

Read-only HTTP probes against `192.168.1.101:8080` measured first byte at 22.59 s for full and 13.57 s for compact. HTML bodies were only 7,046 and 3,182 bytes. These are individual samples, not a benchmark or a post-change measurement.

## Source findings and changes

- `RetirementProfileApplicationService.java`: annual cost needed saved plan assumptions but called `RetirementProjectionService.load()`, which also loads portfolio facts, bridges the current year and computes scenario projections. It now reads the same assumptions directly through `RetirementPlanApi.details()` and retains the existing cost and currency formulas.
- `InvestmentProfileController.java`: its widget independently loaded Profile and annual costs again. It now passes its already-loaded facts to `FinancialLandscapePageAssembler.assembleCompactAttributes()`.
- `FinancialLandscapePageAssembler.java` and `FinancialLandscapeController.java`: compact skips the annual-cost category query; only full needs spending destinations.
- `FinancialLandscapePageView.java`: compact serializes five required money fields. Full adds asset values and expense groups. Removed unused duplicate long-term income, surplus, net worth and reserve fields.
- `financial-landscape.html`: removed Tabler and shared theme/navigation dependencies. The navigation module statically imported code for unrelated dashboard, simulation, editor and asset pages. This standalone page uses its own styles and controls. Favicon retained.
- `financial-landscape.css` and widget stylesheet: removed rules for deleted top/bottom totals, old clock values and duplicate wrapper sizing. Retained mobile terrain and reduced-motion fallbacks.
- `financial-landscape.js`: removed compact terrain calculations and unused serialized-surplus dependency. Full still computes its terrain and flow geometry.
- Templates/CSS now load WebP copies. PNG source artwork is preserved. CSS selects the wide background before JS runs, avoiding the default normal image download on ultrawide startup.

## Image bytes

| View | Previous PNG pair | WebP pair | Reduction |
| --- | ---: | ---: | ---: |
| Full normal | 3,999,440 | 728,308 | 81.8% |
| Full wide | 4,023,876 | 727,514 | 81.9% |
| Compact | 3,825,264 | 657,676 | 82.8% |

Pairs include the scene background and hourglass. Encoding used `cwebp -q 90 -alpha_q 100 -m 6`, preserving dimensions and transparency.

## Verification

- 40 targeted Java tests passed, covering costs, forward-year behavior, Profile, Landscape assembly/controller and view models.
- Regression assertions cover reuse of loaded Profile facts, skipping unused category reads and the restricted compact payload.
- Maven Spotless checks, three JavaScript syntax checks and scoped `git diff --check` passed.
- Local browser previews used current templates, scripts, styles and WebP assets with fixture data. Normal 1920×1080, wide 3440×1260, compact 900×600 and compact mobile 390×844 rendered without horizontal overflow or page errors. Compact data/visibility checks passed after correcting the preview fixture's initial data injection.
- Full requests only Landscape CSS, three Landscape scripts, one appropriate scene background, the hourglass and favicon. Compact requests two scoped stylesheets, shared value JS, compact background and hourglass; no geometry or Canvas script.
- Reduced-motion full preview created no Canvas. The visual background/hourglass rendering and coverage bar were inspected.
- Previews: `optimized-normal.png`, `optimized-wide.png`, `optimized-compact.png`, `optimized-mobile.png` in this directory.

## Remaining validation

The live server still runs its existing build. Measure first-byte time after deployment before claiming an end-to-end speed improvement. Remaining Profile data-source latency was not profiled or changed in this cleanup.
