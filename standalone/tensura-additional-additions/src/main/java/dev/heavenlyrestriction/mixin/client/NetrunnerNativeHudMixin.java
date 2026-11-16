package dev.heavenlyrestriction.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.heavenlyrestriction.client.netrunner.NativeHudTransition;
import io.github.manasmods.tensura.client.screen.widgets.OverlayElement;
import io.github.manasmods.tensura.handler.client.OverlayHandler;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

/** Use the pane's already-applied native transform, including user position/scale/mirroring. */
@Mixin(value = OverlayHandler.class, remap = false)
public abstract class NetrunnerNativeHudMixin {
  @Shadow private static GuiGraphics graphics;

  @WrapOperation(
      method = "renderElement",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lio/github/manasmods/tensura/client/screen/widgets/OverlayElement;render()V"))
  private static void additions$transition(
      OverlayElement receiver,
      Operation<Void> original,
      OverlayElement element,
      int id,
      float scale) {
    NativeHudTransition.tensura(graphics, receiver, id, scale, () -> original.call(receiver));
  }
}
