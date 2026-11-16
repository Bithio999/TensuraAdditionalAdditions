# Additions 1.31.2 — Faster quickhacking

Netrunner cooldowns now start at 2 / 3 / 4 / 4 / 15 seconds for Short Circuit, Synapse Burnout, Reboot Optics, Cripple Movement and Suicide. They still begin when queued and count down while the scanner is closed. Costs, base upload times and damage are unchanged.

Mastered uploads take half their base duration: 1 / 1.25 / 0.75 / 1 / 2.5 seconds respectively. The skill tooltip, curated F8 entry and wiki match the new values. Only Additions’ F8 entry is updated; discontinued Myths data is untouched.

Existing saved cooldowns finish normally; newly queued hacks use the reduced values. No world migration is required.

Verification results and release checksum follow below.

## Scanner rendering

Batch icon, glow and lettering draws by shared material after plaque geometry, preserving saved poses and Unicode fallback. Remove per-icon flushes and GL blend-state changes. Skip invisible geometry during acquisition and reduce full-circle tessellation to cached 128/64-edge unit rings; the largest ring deviates by less than 0.15 virtual pixels. Keep glow, chip details and thin joined borders. Prepare the vanilla outline post-process before acquisition, use a smooth-start expanding iris, enable the body-outline pass after acquisition, and ray-test target boxes before performing line-of-sight checks in crowded scenes.

Both timing runs used the same 1280×720 native fixture, Java Flight Recorder and software OpenGL, without a video recorder. These are single-run review measurements, not a promise of FPS on another PC:

| Metric | Previous HUD | Updated HUD |
| --- | --- | --- |
| Average GUI time during steady scanning | 4.51 ms | 2.61 ms |
| Worst frame during first opening | 222.06 ms | 67.62 ms |
| Mean frame time during reopening | 44.21 ms | 33.77 ms |

The typical full scanner groups roughly 77–80 icon/text jobs into 12 shared materials. Native captures confirm chip cards, fonts, glow, head-tracked uploads and compact/4:3/ultrawide layouts remain intact.

## Verification

- 14 focused native Netrunner tests pass on Tensura 2.0.1.3 / NeoForge 21.1.209 and on Tensura 2.0.1.4 / NeoForge 21.1.248. Coverage includes persisted private cooldowns, the shortened two-second timer while closed, queue payment/continuation, all five mastered upload durations, targeting, controls, defenses and cancellation. The newer API run additionally checks the actual mastered Short Circuit queue contains 20 ticks.
- The native HUD review exercises mouse/key input, two moving-target uploads, release continuation, target damage and three GUI scales / standard, 4:3 and ultrawide views.
- Review fixtures do not ship. The timer test posts entity-tick events for an unspawned fake player so other arenas retain normal despawn conditions; its paid-upload target is persistent and invulnerable because the completion/mastery check must not depend on unrelated native evasion/damage behavior.

Final clean-build totals, packaging and checksum follow below.

## Release results

Clean production build: **135 JVM tests passed**, with no failures, errors or skips. Distribution verification confirms the new batch renderer, source-matching guide/resources and exclusion of opt-in review fixtures. The final native input/queue/layout review passes, and the replacement 30 fps native HUD video decodes without errors. The cloud recording uses software OpenGL; capture overhead and world rendering still affect its frame pacing.

Release SHA-256: `c1b40319fbec11213b64cf9c585b138d281b7cc72e418503dba6ca0d3889313a`.
