package dev.heavenlyrestriction.mixin.client;

import dev.heavenlyrestriction.client.netrunner.NetrunnerClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MouseHandler.class, priority = 1200)
public abstract class NetrunnerMouseMixin {
  @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
  private void additions$scroll(long window, double x, double y, CallbackInfo ci) {
    if (NetrunnerClient.scroll(y)) ci.cancel();
  }

  @Inject(method = "onPress", at = @At("HEAD"))
  private void additions$click(
      long window, int button, int action, int modifiers, CallbackInfo ci) {
    NetrunnerClient.click(button, action);
  }
}
