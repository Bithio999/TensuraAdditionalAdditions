# Netrunner artwork

Original scanner glyphs from the approved overlay prototype. White transparent SVGs are tinted by the native HUD. The skill portrait is an original circuit-face design. No game artwork, footage or textures from Cyberpunk 2077 are packaged in the mod.

Scanner typography: Rajdhani, copyright Indian Type Foundry, licensed under SIL OFL 1.1; the unmodified license is bundled beside the font.

`python3 art/netrunner/bake-typography.py` (Pillow) rebuilds the 48 px Medium/Semibold glyph atlases, blur layers and shared metrics from the bundled fonts. Prefiltered 24/12/6 px copies preserve thin strokes when the HUD scales down. The renderer selects by actual framebuffer size and retains the game’s Unicode fallback.
