package dev.heavenlyrestriction.mixin.client;

import dev.heavenlyrestriction.client.netrunner.NetrunnerClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Minecraft.class)
public abstract class NetrunnerInputMixin {
  @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
  private void additions$attack(CallbackInfoReturnable<Boolean> ci) {
    if (NetrunnerClient.active()) ci.setReturnValue(false);
  }

  @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
  private void additions$mine(boolean held, CallbackInfo ci) {
    if (NetrunnerClient.active()) ci.cancel();
  }

  @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
  private void additions$use(CallbackInfo ci) {
    if (NetrunnerClient.active()) ci.cancel();
  }

  @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
  private void additions$outline(Entity e, CallbackInfoReturnable<Boolean> ci) {
    if (NetrunnerClient.outlined(e)) ci.setReturnValue(true);
  }
}
