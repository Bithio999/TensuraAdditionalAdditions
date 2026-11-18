package dev.heavenlyrestriction.mixin;

import dev.heavenlyrestriction.highrealm.boss.KaelVeyrEntity;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.extra.InfiniteRegenerationSkill;
import io.github.manasmods.tensura.effect.template.TensuraMobEffect;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import io.github.manasmods.tensura.registry.skill.ExtraSkills;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep Kael's promised Infinite Regeneration on older native APIs; 2.0.1.5 already fixes it. */
@Mixin(value = InfiniteRegenerationSkill.class, remap = false)
public abstract class WayfarerInfiniteRegenerationMixin {
  @Inject(method = "onToggleOn", at = @At("RETURN"))
  private void additions$legacyBossStrength(
      ManasSkillInstance instance, LivingEntity owner, CallbackInfo ci) {
    if (!(owner instanceof KaelVeyrEntity) || owner.level().isClientSide()) return;
    var effect = TensuraMobEffects.getReference(TensuraMobEffects.INSTANT_REGENERATION);
    var current = owner.getEffect(effect);
    if (current == null || current.getAmplifier() >= 1) return;
    TensuraMobEffect.addEffect(
        owner,
        new MobEffectInstance(
            effect,
            current.getDuration(),
            1,
            current.isAmbient(),
            current.isVisible(),
            current.showIcon()),
        null,
        ExtraSkills.INFINITE_REGENERATION.get());
  }
}
