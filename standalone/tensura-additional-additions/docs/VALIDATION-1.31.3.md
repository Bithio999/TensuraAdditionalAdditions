# Additions 1.31.3 — Native HUD handoff

The supplied reference video retracts health/status information before fading its quick-slot strip. Native Tensura status/resource panes now retract toward their configured side over 200 ms. Skill/appraisal panes begin fading at 150 ms; the hotbar follows at 170 ms. Release restores the current partial state over the existing 190 ms closing transition.

The original native panes continue rendering during the handoff. Their native transforms supply position, scale and mirroring. Directional scissoring retracts status/resource panes; independent decorations fade. Vanilla layers use their own fade/slide/shrink scope. Shader-color modulation survives native tint resets; each scope restores pose, scissor, shader color and blend state in a finally block. Other mod layers remain outside these scopes. No framebuffer copies or frozen screenshots are used.

Native HUD and scanner sample one clock before GUI rendering, preventing a slow frame from making the two interfaces disagree. Existing scanner detail, quickhack costs/cooldowns and 50% mastery upload reduction are unchanged.

Verification results and release checksum follow below.

## Verification

- **136 JVM tests pass**, with no failures, errors or skips. The new regression checks staggered native-HUD withdrawal, continuity on release and complete restoration. Existing short-tap and stalled-frame cases still pass.
- Actual-client reviews pass on Tensura 2.0.1.3 / NeoForge 21.1.209 and Tensura 2.0.1.4 / NeoForge 21.1.248. They exercise native key/mouse input, queued uploads after release, target damage, GUI scales 1/2/3, standard/4:3/ultrawide windows, and a right-anchored status-pane handoff/return.
- The review checks shader alpha is restored and scissoring is disabled after GUI rendering. Right-side captures confirm the native pane retracts toward its configured edge.
- Clean production build and distribution verification pass. New client mixins and the helper are present; review fixtures are excluded. Discontinued Myths gameplay/F8 data remains untouched.
- Updated native screenshots, opening montage and 30 fps HUD recording show the handoff and restoration. The video decodes without errors. No reference footage or captured reference stills are distributed.

Existing saves need no migration. The cloud recording uses software OpenGL and includes capture overhead.

Release SHA-256: `d77b5e81ba51cbf9bd770c34b3600d85d6e69b76b60a0f4ee5f1b62e6c24f1fa`.
