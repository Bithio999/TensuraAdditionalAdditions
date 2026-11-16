package dev.heavenlyrestriction.mixin.client;

import dev.heavenlyrestriction.client.netrunner.NetrunnerClient;
import io.github.manasmods.tensura.handler.client.OverlayHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native HUD yields its space to the held scanner; all layers return immediately on release. */
@Mixin(OverlayHandler.class)
public abstract class NetrunnerNativeHudMixin {
  @Inject(
      method = {
        "renderAbilities",
        "renderAnalysis",
        "renderStatus",
        "renderStatusBars",
        "renderFoodDecoration",
        "renderArmorDecoration",
        "renderAirDecoration",
        "renderBarrierDecoration",
        "renderMountHealthDecoration",
        "renderMountSpiritualHealthDecoration"
      },
      at = @At("HEAD"),
      cancellable = true,
      remap = false)
  private static void additions$scanner(CallbackInfo ci) {
    if (NetrunnerClient.active()) ci.cancel();
  }
}
