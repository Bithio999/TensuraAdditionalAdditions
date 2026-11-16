package dev.heavenlyrestriction.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.heavenlyrestriction.client.netrunner.NativeHudTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Preserve the fade when native HUD code changes or resets shader tint inside its draw. */
@Mixin(value = RenderSystem.class, remap = false)
public abstract class NetrunnerHudColorMixin {
  @ModifyVariable(method = "setShaderColor", at = @At("HEAD"), argsOnly = true, ordinal = 3)
  private static float additions$hudAlpha(float alpha) {
    return NativeHudTransition.modulate(alpha);
  }
}
