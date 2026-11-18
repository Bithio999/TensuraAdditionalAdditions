# Additions 1.31.4 — Tensura compatibility update

Targets Minecraft **1.21.1 / NeoForge** with Tensura **2.0.1.3–2.0.1.5** and ManasCore **4.0.0.2**. The default development dependency is now Tensura **2.0.1.5**; the exact Neo Otherworld profile retains **2.0.1.4**. Required library ranges, registry IDs, network identifiers and saved-data formats remain unchanged.

## Changes and upstream audit

The official 2.0.1.5 release and its SHA-512 were checked against both Modrinth and CurseMaven. The binaries match. Compared with 2.0.1.4, ten native classes change, plus the build-specific Architectury bridge. Existing addon API references and mixin targets remain available. [Release sources, pinned hashes and reproduction commands](compatibility/TENSURA-2.0.1.5.md).

Kael retains the intended Infinite Regeneration strength on older supported APIs. The correction runs only for Kael when the native skill has just applied the weaker effect. It preserves duration and display flags, uses native skill-effect attribution, and does not refill HP/SHP or resource pools. Ordinary entities and the already-correct 2.0.1.5 effect are unchanged. Creator-derived skills retain their installed native version's lifetime behavior.

## Test coverage

The compatibility profile enables all **489** maintained native gameplay tests: Chained/Unbound and race progression, aura/magicules and regeneration, Battlewills, learning/reincarnation/Creator, weapons and engravings, the High Realm and Kael, Fusion, Loan Shark, Prospector, Exponentiation, world travel, the map, mentorship, Ultimate synthesis, Metis, Laplace and Netrunner.

Controlled test targets are now persistent. The mentor network test removes its registered simulated player in a `finally` block. This prevents later targets in distant arenas from naturally despawning. The standalone catalogue check now expects the current **21** skills rather than an outdated count of 18. These changes fix test isolation; they do not make ordinary gameplay mobs persistent.

The native client review exercises actual Tensura key/mouse input, scanner entry/exit, queued uploads after release, target damage, GUI scales 1/2/3, standard/4:3/ultrawide windows, and left/right native HUD handoff. It checks shader alpha and scissor state after rendering. The review uses an integrated server; the gameplay matrix uses dedicated GameTest servers.

Existing standalone worlds need no migration. Discontinued Myths source and gameplay assets remain unchanged. Clean builds exclude optional review classes and resources, and the distribution audit checks dependency metadata, mixins, assets and package boundaries.

## Results

| Tensura | NeoForge | JVM tests | Dedicated-server gameplay tests |
| --- | --- | ---: | ---: |
| 2.0.1.3 | 21.1.209 | 136 passed | 489 passed |
| 2.0.1.4 | 21.1.248 | 136 passed | 489 passed |
| 2.0.1.5 | 21.1.209 | 136 passed | 489 passed |

All three rows use the final compatibility correction. There are no failing required gameplay tests. The matrix runs with Tensura and its required libraries, rather than the full third-party Neo Otherworld modpack; its previously supported exact-pack dependency profile remains unchanged.

The completed API matrix, client result and release checksum are recorded in [the machine-readable report](compatibility/tensura-1.31.4-results.json).

The final native client review on **2.0.1.5 / NeoForge 21.1.209** passes. A clean production build, **136 JVM tests**, the 6,209-block archive context audit, and independent distribution verification also pass. Optional review fixtures and bundled dependencies are excluded.

Release SHA-256: `7dbd41a4d4dc518274554ce6ea2a6705aeeedb939fac89c83a213ccd1acdb723`.
