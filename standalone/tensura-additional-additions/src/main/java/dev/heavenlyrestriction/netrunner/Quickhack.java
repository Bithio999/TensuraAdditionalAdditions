package dev.heavenlyrestriction.netrunner;

/** Stable wire IDs and independent cooldown channels; none are extra selectable skill modes. */
public enum Quickhack {
  SHORT_CIRCUIT("Short Circuit", 200, 40, 8),
  SYNAPSE_BURNOUT("Synapse Burnout", 320, 50, 12),
  REBOOT_OPTICS("Reboot Optics", 160, 30, 10),
  CRIPPLE_MOVEMENT("Cripple Movement", 240, 40, 12),
  SUICIDE("Suicide", 900, 100, 60);

  public final String title;
  public final double baseCost;
  public final int uploadTicks, cooldownSeconds;

  Quickhack(String title, double cost, int upload, int cooldown) {
    this.title = title;
    this.baseCost = cost;
    this.uploadTicks = upload;
    this.cooldownSeconds = cooldown;
  }

  public int channel() {
    return ordinal() + 1;
  }

  public String key() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }

  public static Quickhack byId(int id) {
    return id >= 0 && id < values().length ? values()[id] : null;
  }

  public int upload(boolean mastered) {
    return Math.max(1, Math.round(uploadTicks * (mastered ? .8f : 1)));
  }
}
