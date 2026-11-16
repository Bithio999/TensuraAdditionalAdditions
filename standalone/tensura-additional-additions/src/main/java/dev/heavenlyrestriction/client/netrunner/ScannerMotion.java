package dev.heavenlyrestriction.client.netrunner;

/** Reference ordering: lens/link first, resource cascade next, then assembled hack/data plaques. */
public final class ScannerMotion {
  private boolean held, rendered;
  private long opened, closed, lastFrame;
  private double releasedAge;

  public void setHeld(boolean value, long now) {
    if (value == held) return;
    if (value) {
      rendered = false;
      opened = now;
      lastFrame = now;
      closed = 0;
    } else {
      releasedAge = Math.max(0, ((rendered ? lastFrame : now) - opened) / 1e6);
      closed = now;
    }
    held = value;
  }

  /**
   * Keep acquisition readable if a first-use shader compilation or a slow frame stalls rendering.
   */
  public void renderFrame(long now) {
    if (held) {
      opened += Math.max(0, now - lastFrame - 100_000_000L);
      rendered = true;
    }
    lastFrame = now;
  }

  public Frame sample(long now) {
    double age = held ? (now - opened) / 1e6 : releasedAge;
    double exit = held ? 1 : closed == 0 ? 0 : 1 - HudGeometry.ease((now - closed) / 190e6);
    return new Frame(Math.max(0, age), exit, held);
  }

  public record Frame(double age, double exit, boolean held) {
    public double layer(double start, double duration) {
      return HudGeometry.ease((age - start) / duration) * exit;
    }

    public double lens() {
      return layer(0, 260);
    }

    public double chrome() {
      return layer(90, 210);
    }

    public double resource() {
      return layer(230, 210);
    }

    public double hacks() {
      return layer(420, 240);
    }

    public double row(int row) {
      return layer(420 + row * 24, 180);
    }

    public double data() {
      return layer(500, 230);
    }

    private double nativeLayer(double start, double duration) {
      double t = Math.clamp((age - start) / duration, 0, 1);
      return t * t * (3 - 2 * t) * exit;
    }

    public double nativeStatus() {
      return 1 - nativeLayer(0, 200);
    }

    public double nativeSkills() {
      return 1 - nativeLayer(150, 200);
    }

    public double nativeHotbar() {
      return 1 - nativeLayer(170, 190);
    }

    public double nativeCrosshair() {
      return 1 - nativeLayer(0, 100);
    }

    public boolean interactive() {
      return held && age >= 660;
    }
  }
}
