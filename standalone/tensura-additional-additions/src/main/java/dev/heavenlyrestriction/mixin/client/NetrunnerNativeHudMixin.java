package dev.heavenlyrestriction.mixin.client;

import dev.heavenlyrestriction.client.netrunner.NetrunnerClient;
import io.github.manasmods.tensura.handler.client.OverlayHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Native vitals remain visible; ability plaques/appraisal yield their space to the held scanner.
 */
@Mixin(OverlayHandler.class)
public abstract class NetrunnerNativeHudMixin {
  @Inject(
      method = {"renderAbilities", "renderAnalysis"},
      at = @At("HEAD"),
      cancellable = true,
      remap = false)
  private static void additions$scanner(CallbackInfo ci) {
    if (NetrunnerClient.active()) ci.cancel();
  }
}
