# Additions 1.31.0 — Netrunner

Netrunner is a Unique Skill with one **Active, Hold** ability. It uses Tensura’s normal skill slots and press/held/release packets. The scanner is a live HUD, not a screen: aiming remains available, mouse-wheel selection does not change the hotbar, and clicking does not attack or use an item.

## Gameplay

A server-owned session validates ownership, learning, active preset, target, visibility, range, PvP and skill interference. A random session token and bounded, throttled requests protect queue operations. Up to three uploads execute in order, retaining the target’s UUID and dimension; releasing the skill closes the scanner while paid uploads continue. Dead, missing, out-of-range or protected targets are rejected. Logout, death, dimension changes, losing the skill or interference cancel pending work. Payment happens once, when queued; cancelled or interrupted uploads are not refunded.

Short Circuit deals Lightning HP damage, with a bonus against a currently casting magic. Synapse Burnout deals Mind Crush SHP damage based on missing magicules and explicitly retains native Spiritual Attack Resistance/Nullification handling. Reboot Optics disrupts sight and mob targeting. Cripple Movement roots movement without reducing attack speed. Suicide uses resistible Mind Crush HP damage with a reduced fraction for players and tagged bosses; it never calls `kill` or `discard`. Attacks retain native attribution, resource-cost metadata, defenses and death/regeneration hooks.

Each hack has an independent persisted cooldown. ManasCore ticks selectable modes only, so the five private channels receive a separate once-per-second update through the native cooldown event; the scanner itself stays off cooldown. Mastery makes uploads 20% faster. Server defaults are documented in the [skill guide](../../../wiki/pages/Additions-Netrunner.md).

Netrunner joins the normal reincarnation Unique tag and the mentor’s normal selection. Its inline magicule costs are blacklisted for Fettered Human starts; the held interface is excluded from Metis auto-casting.

## HUD and reference

The user-supplied Cyberpunk 2077 MP4 guided the geometry, palette and motion. Only original scanner artwork and licensed Rajdhani typography are distributed; the reference footage and game assets are not packaged.

The native renderer uses closed polygon paths with joined outlines, consistent winding and complete corner geometry. It fits one virtual canvas inside the current viewport, including 4:3 and ultrawide windows. Texture icons explicitly flush pending panel geometry before drawing, preventing the overhead icons from being covered by their own background. The interface includes:

- A 220 ms entry iris, staggered panel slide and initial scan sweep; a 180 ms closing transition.
- Selection sheen and a 150 ms queue-entry animation.
- Smooth upload interpolation between bounded server snapshots, with completion flashes and stable per-upload IDs.
- Real world-to-screen head tracking for each queued target, including after the scanner closes.
- Native target outlines, a green scanning tint and a dark edge vignette for floating-label contrast.
- Live HP, SHP, magicules, aura, race/species, EP, status effects and current chant.

Native vitals remain visible. Overlapping ability/appraisal, chat and overlay-message layers yield while scanning; ordinary menus suppress the scanner entirely. All network client handlers are registered only on the client, and client mixins stay in the client-only section.

## Verification

- **89 native integration tests passed on Tensura 2.0.1.3 / NeoForge 21.1.209.**
- **89 passed on Tensura 2.0.1.4 / NeoForge 21.1.248.** These include ten initial Netrunner cases plus the existing 79 Laplace/Metis regressions.
- New cases cover cooldown persistence, forged requests, paid queue continuation and moving targets, atomic unaffordability, distinct control effects, spiritual nullification, mastery, forgetting, queue limits/cancellation, closed-scanner rejection, skill interference, Chained and Metis policies.
- The registry audit now evaluates **643 boards across 375 eligible skills**, with 1,180 exposed numerical fields; Netrunner’s manual scanner is a fixed action.
- A real Minecraft client review exercises native key input, actual MouseHandler wheel/click events, two queued uploads, release continuation, target movement and damage. Captures cover GUI scales 1/2/3, 960×720 (4:3), 1280×720 and 1920×720 (ultrawide).
- Pure geometry checks cover triangulation area, closed corner joins, concave notches and bounded animation/progress interpolation.

Native review code and fixtures are opt-in and excluded from the production JAR. Commands:

```sh
./gradlew runGameTestServer -I docs/review-high-realm/server.init.gradle -PnetrunnerFocus -PnetrunnerRegression -PmentorFreshReview=netrunner-baseline
./gradlew runGameTestServer -I docs/review-high-realm/server.init.gradle -I docs/review-high-realm/api-runtime.init.gradle -PneoOtherworldDirectory=/tmp/realm-api-profile -PnetrunnerFocus -PnetrunnerRegression -PmentorFreshReview=netrunner-api
./gradlew runClient -I docs/review-netrunner/client.init.gradle
./gradlew clean test build
python3 scripts/verify-distribution.py
```

The API profile needs the runtime libraries in its expected `client/mods` paths. Client review refuses any save other than the disposable `Wayfarer Review` fixture. These native profiles do not represent a full Neo Otherworld modpack playthrough. Existing worlds require no migration for the new skill. Discontinued Myths gameplay and F8 data remain untouched.

## Final review

The follow-up Netrunner suite adds explicit finisher checks for ordinary mobs, normal player damage, spawn invulnerability and bosses, plus head targeting on tall entities. Test players override NeoForge’s automation-only invulnerability/PvP policies and clear vanilla spawn protection only for the damage portion. The queued-upload case checks paid completion/mastery rather than requiring HP loss, since native evasion can legitimately resist an upload; the actual-client recording separately verifies damage.

The renderer’s final contrast pass uses four vertex gradients, avoiding strip seams. The native recording was decoded without errors and played back; it shows opening, wheel selection, two uploads, moving-target tracking, release continuation and reopening. Final production totals and checksum follow below.

## Release results

- **129 JVM tests passed** in the final clean production build, with no failures, errors or skips.
- The full 89-case regression run passed on both supported profiles; the subsequent **13-case Netrunner suite also passed on both**, adding finisher, spawn-protection and tall-target checks.
- Final native-client captures passed for controls, queueing, damage, release continuation and all documented display sizes. The silent HUD video decodes cleanly and was played back.
- Distribution validation passed: current metadata, registered mixins, source-matching assets/fonts and 26 F8 entries; opt-in review classes and fixtures are excluded.

Release SHA-256: `0d6fd2560acd0fbeeb8ce667e6745b25feb40201fbc43d41c2ac2672e33b4f3d`.

Wiki verification passed for **213 scoped articles, 148 ability links and 214 GitHub Wiki exports**. The offline ZIP includes the linked Netrunner media. A browser check verified the article, loaded images, ability search and narrow layout. The active F8 entry exactly matches its generator literal. The raw release JAR passed the privacy audit, including compiled classes; required public font copyright remains intact.
