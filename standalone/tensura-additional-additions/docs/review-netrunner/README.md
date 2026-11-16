# Native Netrunner HUD review

Opt-in actual Minecraft review. `client.init.gradle` adds this source directory only for the review run. It refuses saves outside the disposable `Wayfarer Review` fixture, learns Netrunner, creates a controlled target and uses native Tensura key input plus real MouseHandler wheel/button callbacks. It verifies queueing, continuation after release and resulting damage, captures six activation stages (40–820 ms), then captures three GUI scales and standard, 4:3 and ultrawide windows.

Captures and pass/fail markers go to `/workspace/validation-netrunner/capture`; none of the review code is part of the production release. The video is a recording of the actual native HUD, not the earlier HTML concept.

The uploaded Cyberpunk reference informed entry/exit timing, cyan/red plaques, reticle, target-data placement and overhead upload icons. No reference footage or Cyberpunk assets are redistributed.

The entry handler samples the timestamp used for native GUI rendering, rather than the end of a potentially slow frame. `entry-timing.txt` records six acquisition stages. Entry images are extracted from the X11 recording; framebuffer readback and PNG compression are kept out of the opening animation.

`frame-times.csv` records tick, scanner-active flag, whole-frame interval, GUI-pass duration, batched material count and sprite/text job count. Compare runs without a recorder; exclude the screenshot ticks when measuring. Opening samples use ticks 100–118, steady scanning uses 124–143, and reopening uses 235–253. The capture machine uses software OpenGL; frame rates are not a hardware performance guarantee.
