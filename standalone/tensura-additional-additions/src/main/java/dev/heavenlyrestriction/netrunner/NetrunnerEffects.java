package dev.heavenlyrestriction.netrunner;

import dev.heavenlyrestriction.HeavenlyRestriction;
import io.github.manasmods.tensura.effect.template.TensuraMobEffect;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.*;

public final class NetrunnerEffects {
  private static final DeferredRegister<MobEffect> EFFECTS =
      DeferredRegister.create(Registries.MOB_EFFECT, HeavenlyRestriction.ID);
  public static final DeferredHolder<MobEffect, MobEffect> OPTICS =
      EFFECTS.register(
          "reboot_optics",
          () ->
              new TensuraMobEffect(MobEffectCategory.HARMFUL, 0xfb5257) {}.addAttributeModifier(
                  TensuraAttributes.DARK_VISION,
                  HeavenlyRestriction.id("reboot_optics"),
                  .8,
                  AttributeModifier.Operation.ADD_VALUE));
  public static final DeferredHolder<MobEffect, MobEffect> CRIPPLE =
      EFFECTS.register(
          "cripple_movement",
          () ->
              new TensuraMobEffect(MobEffectCategory.HARMFUL, 0x50e9e6) {}.addAttributeModifier(
                      Attributes.MOVEMENT_SPEED,
                      HeavenlyRestriction.id("cripple_movement"),
                      -1,
                      AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                  .addAttributeModifier(
                      Attributes.JUMP_STRENGTH,
                      HeavenlyRestriction.id("cripple_jump"),
                      -1,
                      AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

  public static void register(net.neoforged.bus.api.IEventBus bus) {
    EFFECTS.register(bus);
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(NetrunnerEffects::tick);
  }

  public static void tick(EntityTickEvent.Post e) {
    if (e.getEntity().level().isClientSide() || !(e.getEntity() instanceof LivingEntity t)) return;
    if (t.hasEffect(OPTICS) && t instanceof Mob m) {
      m.setTarget(null);
      m.getNavigation().stop();
    }
    if (t.hasEffect(CRIPPLE)) {
      var v = t.getDeltaMovement();
      t.setDeltaMovement(0, Math.min(0, v.y), 0);
      if (t instanceof net.minecraft.server.level.ServerPlayer p && !p.isCreative()) {
        p.getAbilities().flying = false;
        p.onUpdateAbilities();
      }
    }
  }

  private NetrunnerEffects() {}
}
