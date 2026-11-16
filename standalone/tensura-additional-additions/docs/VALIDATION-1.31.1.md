# Additions 1.31.1 — Scanner fidelity

This client-focused patch compares the native Netrunner HUD against the approved preferred preview and the user-supplied Cyberpunk scanner video. It preserves the five quickhacks and their gameplay rules.

## Visual comparison

| Area | Native implementation |
| --- | --- |
| Resource display | 29 clipped cells, dark inset notches, highlights, glow, reserved/empty states and micro lettering |
| Quickhacks | Concave stepped chip cards; left-offset selection; diagonal sheen; framed icons, status/type badges and MP indicators |
| Typography | Preview Rajdhani Medium/Semibold, 48 px source atlases, prefiltered 24/12/6 px variants, fractional advances/kerning and linear filtering; native Unicode fallback |
| Red detailing | Soft line/letter halos, warm edge vignette, fine scan lines, target grid/chevrons, tracking and corner microtext |
| Data/controls | Matching tab/body corner cuts, thin joined borders with a one-framebuffer-pixel minimum, compact target stats and horizontal mouse/key prompts |
| Target uploads | Actual world-space head tracking and glowing tiles, retained after release |

The reference video stages lens and target acquisition before exposing the menus. The native timeline now reveals lens (0 ms), link chrome (90 ms), resource cascade (230 ms), staggered chip rows (420 ms) and data (500 ms). Queue input becomes available at 660 ms. Closing fades the current partial state over 190 ms; a short tap never reveals the full menu. Glyph rendering explicitly rejects near-zero alpha, avoiding Minecraft’s low-alpha-to-opaque text behavior. Typography textures prewarm during resource reload, and actual draw materials prime offscreen before use. Slow frames advance acquisition by at most 100 ms, so a rendering stall cannot jump straight to the menu. Release fades the last rendered state.

Native status plaques and hotbar yield while the scanner is held, preventing overlap with edge lettering. They return on release. Other menus suppress the scanner. Dynamic target names and labels truncate to available width, including Unicode fallback.

## Verification

- **135 JVM tests pass** (no failures, errors or skips), including closed chip/pip paths, staged acquisition, short taps, closing mid-animation and rendering stalls.
- The real client review passes on **Tensura 2.0.1.3 / NeoForge 21.1.209** and **Tensura 2.0.1.4 / NeoForge 21.1.248**. It exercises actual skill-key and mouse callbacks, two queued uploads, target movement, post-release uploads and damage, plus GUI scales 1/2/3 and 4:3/standard/ultrawide layouts.
- Six render-stage samples confirm lens acquisition before panels, followed by resource and panel assembly. The native recording supplies the opening montage, avoiding framebuffer readback during the animation.
- The final small-screen pass uses prefiltered type and a one-pixel minimum for outline strokes, preventing missing glyph/frame strokes at fractional scales.
- Gameplay/server code is unchanged; the prior 1.31.0 combat integration suite was not repeated for this visual patch.

Commands:

```sh
./gradlew runClient -I docs/review-netrunner/client.init.gradle
./gradlew test runClient -PneoOtherworldDirectory=/tmp/realm-api-profile -I docs/review-high-realm/api-runtime.init.gradle -I docs/review-netrunner/client.init.gradle
./gradlew clean test build --offline
python3 scripts/verify-distribution.py
```

The API profile needs its documented external runtime setup. These native profiles are not a full modpack playthrough. Existing worlds need no migration. Release packaging, wiki validation and checksum follow below. Review fixtures remain opt-in and excluded from the JAR. Reference footage and its stills are not distributed.

## Release results

Clean production build and distribution verification passed. The JAR contains source-matching typography, filtered atlases, icons and client mixins; opt-in review classes are excluded. The native 1280×720, 960×720 and 1920×720 captures were reviewed, and the updated 30 fps video decodes without errors. Wiki exports and privacy checks are recorded in the release commit.

Release SHA-256: `e5de8cb8e242f853ef9ea8697ccfc4925637c34ff10b98ac7dc006e4102b18c0`.
