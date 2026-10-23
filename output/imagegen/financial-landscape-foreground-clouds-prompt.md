# Foreground mist texture

Generated with built-in ImageGen. Reference: `adapters/web-ui/src/main/resources/static/images/financial-landscape-normal-v2.png`.

Runtime asset: `adapters/web-ui/src/main/resources/static/images/financial-landscape-foreground-clouds-v1.webp`. Source PNG remains under the generated-images directory. Encoded as WebP at 1280 px wide using `cwebp -resize 1280 0 -q 82 -alpha_q 85 -m 6`, preserving transparency. CSS feathers the texture with a soft mask and 4 px blur, then composites two layers at 16% and 10% opacity. One drift leg takes 220 / 310 seconds; reduced-motion mode disables movement. No additional JavaScript or data requests.

Prompt:

Use case: stylized-concept. Asset type: transparent foreground mist sprite for the existing alpine landscape website. Input image is a reference for painterly rendering and sunset lighting ONLY. Create one wide horizontal airy bank of delicate alpine valley mist, roughly 3:1 landscape composition. Wispy uneven clouds curl softly in long thin streaks, cool slate-blue translucent shadows and faint warm peach-gold rim light from the left, matching the soft low clouds nestled in the reference mountains. Loose natural vapor texture with lots of transparent holes, dissolving edges, subtle internal variation. Keep the central body soft, not bright white or solid, no harsh outlines or repeated round puff shapes. All four edges must fade completely into actual alpha transparency, with comfortable transparent margins; no rectangular boundary. Cloud/mist only, no scenery, sky, mountain, water, ground, sun, hourglass, UI, text or symbols. It will be composited at low opacity over the reference terrain and drift very slowly. Transparent background, not a checkerboard drawing.
