package dev.heavenlyrestriction.mixin.client;

import dev.heavenlyrestriction.client.netrunner.NetrunnerClient;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class NetrunnerOutlineMixin {
  @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
  private void additions$color(CallbackInfoReturnable<Integer> ci) {
    if (NetrunnerClient.outlined((Entity) (Object) this)) ci.setReturnValue(0xf65e64);
  }
}
