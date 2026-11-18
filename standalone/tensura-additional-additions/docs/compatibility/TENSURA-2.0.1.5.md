# Tensura 2.0.1.5 compatibility

AdditionalAdditions **1.31.4** targets **Minecraft 1.21.1 / NeoForge** and accepts Tensura **2.0.1.3, 2.0.1.4 and 2.0.1.5**. Future Tensura releases remain outside the declared range until checked.

## Updating an instance

Replace the previous AdditionalAdditions JAR with [TensuraAdditionalAdditions-1.31.4.jar](../../../../releases/TensuraAdditionalAdditions-1.31.4.jar). Keep only one copy. Use the same versions on client and server.

Tensura 2.0.1.5 uses the existing ManasCore **4.0.0.2** APIs. AdditionalAdditions continues to require Architectury **13.0.8+**, GeckoLib **4.9.2+ within major 4**, and Tensura's SmartBrainLib/TerraBlender libraries. Minecraft remains **1.21.1**, with Java **21** and NeoForge **21.1.209+**.

Existing worlds, skill instances, race progression, equipment and hidden world libraries retain their registry IDs and data formats. No save migration is introduced. The discontinued Myths distribution is not updated and must not be installed alongside AdditionalAdditions.

The exact Neo Otherworld 1.1.3a development profile retains that pack's Tensura **2.0.1.4** / NeoForge **21.1.248** dependencies. It does not silently upgrade the modpack's other mods.

## Upstream changes

The latest 1.21.1 release was published on **November 6, 2026**:

- [CurseForge release 9476869](https://www.curseforge.com/minecraft/mc-mods/tensura-reincarnated/files/9476869).
- [Modrinth release 2.0.1.5](https://modrinth.com/mod/tensura-reincarnated/version/WuoYFS45).
- [Pinned release IDs, URLs and SHA-512 checksums](tensura-supported-apis.lock.json).

The published CurseMaven and Modrinth binaries are identical. Compared with 2.0.1.4, ten native classes change; the generated Architectury bridge also changes its build-specific name. Creator's mastered-created skill timer changes, Infinite Regeneration applies the correct effect strength and source attribution, weather barriers clean up when their owner disappears, and race reset preserves schematics. Existing addon mixin targets and public native API signatures remain available.

Kael's native Infinite Regeneration effect is corrected to its intended strength on older supported APIs. This does not refill his resources or alter ordinary players/mobs, and 2.0.1.5's already-correct effect is left intact. Creator-generated skills continue to follow the installed Tensura version's native lifetime rules; Chained inheritance does not override them.

## Reproducing validation

Run from `standalone/tensura-additional-additions` with Java 21:

```sh
python3 scripts/fetch-runtime.py
./gradlew test build
./gradlew runGameTestServer -I docs/compatibility/tensura-api-matrix.init.gradle
```

The default target is Tensura 2.0.1.5. Select an older supported release explicitly:

```sh
# Tensura 2.0.1.3 / NeoForge 21.1.209
./gradlew runGameTestServer -I docs/compatibility/tensura-api-matrix.init.gradle -PtensuraFileId=8793360

# Tensura 2.0.1.4 / NeoForge 21.1.248
./gradlew runGameTestServer -I docs/compatibility/tensura-api-matrix.init.gradle -PtensuraFileId=9289888 -PneoForgeVersion=21.1.248

# Actual client input, native Tensura HUD and Netrunner scanner on 2.0.1.5
./gradlew runClient -I docs/review-netrunner/client.init.gradle
```

The server profile enables every maintained gameplay-review namespace and keeps test saves separate by Tensura file ID. `-PcompatibilityRoot=/path/to/disposable/tests` chooses the root directory. `-PcompatibilityNamespaces=netrunnerreview,laplacereview,metisreview` selects a focused regression run. The client review uses the existing disposable `Wayfarer Review` save and requires a graphical display.

Clean before packaging so optional review classes and resources are excluded:

```sh
./gradlew clean test build
python3 scripts/verify-distribution.py
```

[Release validation](../VALIDATION-1.31.4.md) · [Neo Otherworld compatibility](NEO-OTHERWORLD-1.1.3a.md)
