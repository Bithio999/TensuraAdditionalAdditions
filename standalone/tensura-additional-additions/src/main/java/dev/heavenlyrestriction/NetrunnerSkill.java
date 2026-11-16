package dev.heavenlyrestriction;

import dev.heavenlyrestriction.netrunner.Netrunner;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** One native held ability; selection and queueing live entirely in its scanner overlay. */
public final class NetrunnerSkill extends Skill {
  public NetrunnerSkill() {
    super(SkillType.UNIQUE);
  }

  @Override
  public MutableComponent getName() {
    return Component.translatable("heavenlyrestriction.skill.netrunner");
  }

  @Override
  public MutableComponent getColoredName() {
    return getName().withStyle(getType().getChatFormatting());
  }

  @Override
  public MutableComponent getSkillDescription() {
    return Component.translatable("heavenlyrestriction.skill.netrunner.description");
  }

  @Override
  public ResourceLocation getSkillIcon() {
    return HeavenlyRestriction.id("textures/skill/netrunner.png");
  }

  @Override
  public double getDefaultAcquiringMagiculeCost() {
    return 0;
  }

  @Override
  public double getMagiculeCost(LivingEntity e, ManasSkillInstance i, int mode) {
    return 0;
  }

  @Override
  public double getAuraCost(LivingEntity e, ManasSkillInstance i, int mode) {
    return 0;
  }

  @Override
  public boolean canBeSlotted(ManasSkillInstance i, LivingEntity e, int mode) {
    return mode == 0 && super.canBeSlotted(i, e, mode);
  }

  @Override
  public String getModeId(ManasSkillInstance i, int mode) {
    return "netrunner.quickhack";
  }

  @Override
  public Component getModeName(ManasSkillInstance i, int mode) {
    return Component.translatable("heavenlyrestriction.skill.mode.netrunner.quickhack");
  }

  @Override
  public void onLearnSkill(ManasSkillInstance i, LivingEntity e) {
    super.onLearnSkill(i, e);
    Netrunner.channels(i);
  }

  @Override
  public void onPressed(ManasSkillInstance i, LivingEntity e, int key, int mode) {
    if (mode == 0 && e instanceof ServerPlayer p) Netrunner.open(p, i, key);
  }

  @Override
  public boolean onHeld(ManasSkillInstance i, LivingEntity e, int held, int mode) {
    return mode == 0 && e instanceof ServerPlayer p && Netrunner.held(p, i);
  }

  @Override
  public void onRelease(ManasSkillInstance i, LivingEntity e, int held, int key, int mode) {
    if (e instanceof ServerPlayer p) Netrunner.close(p);
  }

  @Override
  public boolean shouldTriggerReleaseOnHeldInterrupt(
      ManasSkillInstance i, LivingEntity e, int held, int mode) {
    return true;
  }

  @Override
  public void onForgetSkill(ManasSkillInstance i, LivingEntity e) {
    Netrunner.clear(e);
    super.onForgetSkill(i, e);
  }
}
