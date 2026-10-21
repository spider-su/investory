# Financial Landscape responsive background verification

2026-10-21. Local source changes previewed through Playwright request overrides against the existing server-rendered page. CSS, both landscape scripts, and new PNGs came from the checkout; the template change adds only `fl-landscape-page` to the full page body. Remaining resources and embedded data came from the server. No deployment or database mutation performed.

## PASS

- Desktop viewports: 1920x1080, 2560x1260, 3440x1260, 1200x813, 1024x768.
- Desktop scene starts at (0,0), matches viewport width/height, has zero corner radius, and causes no page overflow.
- Normal image (1672x941) selected below scene aspect ratio 2.05; wide image (1916x821) selected above it. Both decode successfully and use centered `cover`, preserving proportions.
- Visible hourglass rim follows each image's hill pin through cover scaling/cropping. Measured anchor error below 0.01px across desktop sizes. Normal and ultrawide screenshots visually inspected.
- All spending categories remain within horizontal bounds at all checked sizes.
- Smaller-screen layout checked at 768x1024, 390x844, 320x720: retains existing stacked layout with vertical scrolling; no horizontal overflow.
- Spending disclosure opens on hover.
- Fullscreen enters/exits; scene fills viewport.
- Compact link opens `?view=compact`; compact body does not receive full-page styling. Full view link returns successfully.
- Final browser run: no console/page errors. Initial preview resource-routing errors were resolved before final checks.
- JavaScript syntax: `node --check` for both changed scripts.
- `git diff --check`.
- `./mvnw -pl adapters/web-ui -am spotless:check`.

## Limits

- Browser preview verifies the local assets; the running server has not been updated.
- No backend formulas, controller, migrations, or Java tests changed. Full Maven tests and CI not run.
- Reduced-motion and physical-monitor verification were not separately exercised.
- Existing unrelated edits in `docs/architecture/reporting-pipeline.md` preserved.

## Positioning cleanup follow-up

- Consolidated the obsolete photo integration/anchor override chain into one desktop block. Existing compact and mobile rules retained.
- One scheduled layout update maps artwork to scene pixels, sets guides/glass, and publishes geometry for particles. Vapor SVG now uses the actual scene dimensions as its viewBox.
- Particle animation consumes cached geometry; instrumented idle animation over twelve frames performed zero scene element bounding-box reads.
- Normal/ultrawide, short desktop, and mobile transitions checked at 3440x1260, 1920x1080, 1200x650, 1024x768, 980x768, 390x844, 2200x1000, and 2000x1000. No horizontal overflow or browser errors; desktop fills viewport. Glass foot error below 0.01px; vapor rim endpoint error below 0.001px.
- Fullscreen enters/exits; spending disclosure opens. JavaScript syntax, diff whitespace, and Maven Spotless checks pass.
- Screenshots: `output/financial-landscape-preview/normal.png` and `output/financial-landscape-preview/wide.png`. Local browser preview only; not deployed.

## Annotated visual refinements

- Removed ruler markup and the reserve draw/save flow from the left hill to the glass. Asset-to-cloud and glass-to-spending paths retained.
- Asset labels centered above their photo-space hill/summit pins, with a 14px gap and horizontal viewport clamping.
- Both incoming cloud paths terminate at the same center point on the top rim (SVG y=14, the outer rim edge). Measured endpoint error below 0.001px at 1920x1080, 3440x1260, 1200x650, and 1024x768.
- Source vapor is 4.5px with an 18s dash animation; incoming vapor is 6px with a 16s animation. Spending lines and particles enlarged; travel rate reduced from 0.003 per frame to 0.000072 per millisecond (about 2.5 times slower at 60Hz, consistent across refresh rates).
- Full-view hourglass uses muted bronze rims, translucent sand gradients with restrained grain, and a soft shadow instead of saturated glow. Internal sand stream slowed to 3.5s.
- Desktop, mobile, fullscreen checks passed with no browser errors or overflow. Reduced-motion reload produces no particle canvas and no vapor/internal-stream animation. Syntax, diff, and Maven Spotless checks passed.
- Refined screenshots: `output/financial-landscape-preview/normal-refined.png` and `output/financial-landscape-preview/wide-refined.png`. Local preview only; no deployment.

## Legacy cleanup

- Removed unused selectors for the old consumption dashboard, sand trails, expense streams, connectors, asset rulers, footer/reserve cards, obsolete compact panels, and old spending meters. Removed the unreferenced placeholder compact stylesheet.
- Pruned overridden declarations while retaining cascade order for the existing mobile fallback. CSS reduced from 51,692 to 33,915 bytes. Preserved variable-dependent shorthand declarations, focus outlines, reduced-motion rules, and the viewport-height fallback.
- Removed unused reserve reads, obsolete compact selector fallback, unused photo/guide-height variables, and cloud-opacity calculations overridden by fixed opacity.
- Extracted full-scene layout/fullscreen code into `financial-landscape-scene.js`; Full loads it before particles. Compact continues loading only shared value/spending logic.
- Particle scheduler cancels its frame on mobile/hidden layouts and restarts on desktop/visibility changes. Instrumented twelve frames: desktop 12 draws, mobile 0, desktop after return 12.
- Compared computed styles for landscape elements and their before/after pseudo-elements across eight viewport sizes in Full and Compact (16 states). No differences in compared properties. Animated transforms, cloud/canvas elements, and the Compact dune's animated opacity excluded from deterministic comparison. Ultrawide screenshot visually inspected after cleanup.
- Fullscreen, spending disclosure, Compact navigation, Compact cash-share/reserve state, reduced-motion reload, script syntax, diff whitespace, and Maven Spotless checks passed. Compact does not request the scene script. No browser errors observed in the interaction run.
- Removed byte-identical PNG drafts under `output/imagegen` after SHA-256 comparison; canonical runtime PNGs, generation prompts, and review screenshots retained. Existing Git staging was not modified.
- No backend or domain changes made by this cleanup. No deployment, CI, or full backend test run.

## Floating hourglass and readability pass

- Glass foot now sits above the photo ridge by a clamped 18–36px gap (2.5% of scene height); expense label remains anchored below the ridge. Checked gaps at desktop sizes: 18–31.5px. Incoming flows and their continuous haze paths still meet the moved top-center rim (endpoint error below 0.001px).
- Increased desktop asset, income, expense, and spending typography with bounded viewport sizing and stronger text shadows. Header inset grows from 24px to 48px. Mobile/Compact typography retained.
- Source/incoming strokes use shorter flat-ended dashes, reduced width/opacity, and subtle continuous haze behind incoming streams. Slow animation durations retained. Outgoing flows have a subdued dark under-stroke for contrast over bright terrain.
- Hourglass sand/rims use warmer amber gradients, lighter grain, a restrained glass highlight, and no full-view cash-share color strip. Compact cash-share strip remains active.
- Narrower individual deposits (22% inset on each side) replace the near-continuous bottom strip. Checked visible gaps remain positive at all desktop sizes.
- Normal, ultrawide, short desktop, 1024px, 980px, and mobile checks passed without overflow. Fullscreen, disclosure, Compact, reduced-motion reload, syntax, diff, and Maven Spotless checks passed.
- An extra scene script in the preview override initially produced a fullscreen user-gesture warning; corrected the override to load it once. Final fullscreen check passed without console warnings/errors. This was a preview issue, not an additional source change.
- Previews: `output/financial-landscape-preview/normal-floating.png` and `output/financial-landscape-preview/wide-floating.png`. No deployment performed by this task.

## Matching upstream/downstream flows

- Both directions now share the Canvas path/particle renderer: continuous accent stroke, subdued dark under-stroke, soft glow, traveling particles, and the same elapsed-time animation rate. Warm/cool upstream colors retained; relative upstream weights reuse the existing cash-share presentation value.
- Scene layout publishes the four cubic upstream curves alongside spending destinations. Canvas builds/caches Path2D curves on layout updates. Original SVG is hidden only when Canvas rendering is active; static continuous SVG remains visible for reduced motion or unavailable Canvas. Obsolete SVG dash animations removed.
- Desktop checks at 1920x1080, 3440x1260, 1200x650 and return from mobile: four upstream curves present, both feed endpoints exactly match the measured rim center, eight flows draw through the shared renderer. Instrumented six frames produced 96 strokes (eight paths, two strokes each frame), with zero element geometry reads.
- Mobile 390x844: zero particle draws; desktop resumes after return. No horizontal overflow, browser warnings/errors, or fullscreen failures. Reduced-motion reload: no Canvas and visible undashed, non-animated SVG fallback.
- JavaScript syntax, diff whitespace, and Maven Spotless checks passed. Preview: `output/financial-landscape-preview/wide-matched-flows.png`. No deployment performed.

## Matching internal sand stream

- Internal stream now uses the same Canvas flow renderer as upstream/downstream paths, including under-stroke, glow, amber particles, and elapsed-time animation. Four grains suit the short stream; outer flow density is retained.
- Scene geometry anchors it at SVG neck y=177 and lower sand surface y=258. The old stream extending into the lower sand mound is shortened; SVG remains as a continuous static reduced-motion/Canvas fallback and is hidden when Canvas is active.
- Normal, ultrawide, short desktop, and mobile-return checks passed. Both internal endpoints exactly match the resized SVG coordinates; instrumented six desktop frames produce 108 strokes (nine flows, two strokes each frame). Mobile performs zero particle strokes. Reduced-motion reload: no Canvas; internal fallback visible, undashed and not animated.
- No browser errors/warnings. JavaScript syntax, diff whitespace, and Maven Spotless checks passed. Close-up preview: `output/financial-landscape-preview/hourglass-matched-stream.png`. No deployment performed.

## Smoother, quieter upper flows

- Replaced four cloud-routed segments with two single cubic arcs from terrain sources to the glass top center. Control points interpolate between endpoints with a bounded upward bend; no cloud-specific waypoints or segment joins.
- Upstream strokes, glow, under-strokes, and particles use 65% of the downstream opacity. Downstream/internal rendering retains its existing opacity. Shared 28-second cycle retained.
- Removed obsolete feed-segment SVG markup/CSS. Reduced-motion fallback uses the same two arc geometries with lower opacity.
- Normal 1920x1080, ultrawide 3440x1260, short 1200x650, mobile, and desktop-return checks passed. Both arcs are monotonic in x and terminate exactly at the rendered rim center. No horizontal overflow or browser errors. Reduced-motion SVG remains visible with no Canvas.
- Syntax, diff whitespace, and Maven Spotless checks passed. Preview: `output/financial-landscape-preview/wide-smooth-upper-flows.png`. No deployment performed.

## Reference-inspired dimensional hourglass

- Rebuilt the full-view hourglass SVG with elliptical bronze caps, shaded glass, multiple curved reflections, restrained edge glow, fine textured amber sand, an upper surface highlight, and a shaped lower sand pile. It remains stylized vector artwork rather than a photorealistic bitmap.
- Kept the same viewBox, live Canvas grains, 28-second shared cycle, and floating position. Neck/lower-pile center remain at SVG y=177/258; cap foot remains at y=346.
- Normal, ultrawide, short desktop, mobile-return, fullscreen, and reduced-motion checks passed without overflow or browser errors/warnings. Both incoming endpoints remain exact; internal endpoints match the sand neck/surface. Reduced motion retains visible static stream with no Canvas.
- Diff whitespace and Maven Spotless checks passed. Only the full-view SVG template changed in this pass; Compact artwork retained.
- Previews: `output/financial-landscape-preview/hourglass-dimensional.png` and `output/financial-landscape-preview/wide-dimensional-hourglass.png`. No deployment performed.

## Photographic reference asset

- Replaced the drawn full-view SVG hourglass with a transparent ImageGen edit based on the user's close-up. RGBA PNG is 929x1693; sampled sky/background regions outside and through the glass have zero alpha, while sand remains opaque. Asset and exact prompt recorded in `output/imagegen/financial-landscape-hourglass-prompt.md`.
- The SVG wrapper now matches native image dimensions. Recalibrated rim/waist/sand/base anchors to y=36/895/1310/1626; retained live Canvas grains, smooth incoming arcs, floating placement and shared 28-second cycle. Sand mound shape is part of the PNG.
- Height-aware width cap adjusted for the taller image. Normal, ultrawide, 1200x650, 1024x768, mobile-return and fullscreen checks passed without overflow. Both incoming endpoint errors are zero; internal endpoints match native waist/sand crest coordinates. Reduced motion retains visible static stream with no Canvas.
- No browser errors/warnings. Syntax, diff whitespace and Maven Spotless checks passed. Close-up and full ultrawide previews visually inspected.
- Previews: `output/financial-landscape-preview/hourglass-photo-cutout.png` and `output/financial-landscape-preview/wide-photo-hourglass.png`. No deployment performed.
