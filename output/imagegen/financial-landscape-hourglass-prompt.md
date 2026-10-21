# Photographic hourglass cutout

Tool: built-in ImageGen, edit mode, transparent background.

Input: user's attached `Screenshot 2026-10-21 192353.png`, a close-up photographic hourglass reference. No unrelated scenery or UI used as instructions.

Canonical output: `adapters/web-ui/src/main/resources/static/images/financial-landscape-hourglass-v1.png` (929x1693 RGBA). The generated edit is reference-based; it is not a pixel-exact extraction.

## Prompt

Use case: background-extraction. Edit target: the user's latest attached close-up image of a luminous photorealistic golden sand hourglass. Produce ONE faithful transparent PNG cutout of THAT EXACT hourglass, preserving its photographic shape, slender glass waist, curved clear glass, rounded dark bronze elliptical top and base rims, bright sunset reflections, warm luminous golden sand in both chambers, detailed grain texture and mound shapes. Do not redesign, stylize, or make an SVG-like rendering. Remove ALL surrounding scenery: sky, clouds, mountains, lake, rocks, pedestal and ground. Critically also remove the scenery visible THROUGH the clear glass: these interior areas must be truly transparent, retaining only glass reflections and thin edge highlights, not opaque sky colors. Keep the sand and metal opaque and natural. Remove stray flow particles outside the glass. The central empty falling channel between the waist and lower sand mound should remain clean transparent glass with subtle edge reflections, WITHOUT frozen falling grains or a painted sand stream, because the application draws live animated grains there. Retain the golden glow of sand itself, restrained and clean at cutout edges. Tight centered full-object portrait composition with approximately 3 percent transparent margin on all sides; do not clip either rim. No text, no labels, no background, no fake checkerboard, no drop shadow on a background. Transparent background required.

## Integration

Image inside a native-dimension SVG wrapper, with a static stream fallback. Canvas draws live particles through the transparent waist; sand mound shapes are part of the PNG. Full view uses image-space anchors y=36 (rim), 895 (waist), 1310 (sand crest), 1626 (base). Shared 28-second animation cycle and floating position retained.
