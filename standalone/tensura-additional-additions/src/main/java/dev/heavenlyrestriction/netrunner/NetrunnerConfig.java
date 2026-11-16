package dev.heavenlyrestriction.netrunner;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class NetrunnerConfig {
  private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
  public static final ModConfigSpec.DoubleValue RANGE =
      B.comment("Acquisition range. Uploads may finish within 1.5 times this range.")
          .defineInRange("range", 32d, 4, 96);
  public static final ModConfigSpec.DoubleValue SHORT_DAMAGE =
      B.defineInRange("shortCircuitDamage", 120d, 0, 1000000);
  public static final ModConfigSpec.DoubleValue INTERRUPT_BONUS =
      B.defineInRange("shortCircuitCastingMultiplier", 1.5d, 1, 4);
  public static final ModConfigSpec.DoubleValue SYNAPSE_BASE =
      B.defineInRange("synapseBaseDamage", 70d, 0, 1000000);
  public static final ModConfigSpec.DoubleValue SYNAPSE_DEPLETION =
      B.defineInRange("synapseDepletedMagiculeBonus", 140d, 0, 1000000);
  public static final ModConfigSpec.DoubleValue SUICIDE_MULTIPLIER =
      B.comment(
              "Resistible self-inflicted mental damage on ordinary mobs, as a multiplier of maximum"
                  + " HP.")
          .defineInRange("suicideOrdinaryHealthMultiplier", 2d, 0, 10);
  public static final ModConfigSpec.DoubleValue SUICIDE_ELITE =
      B.comment(
              "Players and bosses take this fraction of maximum HP as resistible mental damage"
                  + " instead of an unconditional execution.")
          .defineInRange("suicidePlayerBossHealthFraction", .45d, 0, 1);
  public static final ModConfigSpec.IntValue OPTICS_TICKS =
      B.defineInRange("opticsDurationTicks", 100, 1, 1200);
  public static final ModConfigSpec.IntValue CRIPPLE_TICKS =
      B.defineInRange("crippleDurationTicks", 60, 1, 1200);

  @SuppressWarnings("unchecked")
  public static final ModConfigSpec.DoubleValue[] COSTS =
      java.util.Arrays.stream(Quickhack.values())
          .map(h -> B.defineInRange(h.key() + "Magicules", h.baseCost, 0, 1e9))
          .toArray(ModConfigSpec.DoubleValue[]::new);

  public static final ModConfigSpec SPEC = B.build();

  private NetrunnerConfig() {}
}
