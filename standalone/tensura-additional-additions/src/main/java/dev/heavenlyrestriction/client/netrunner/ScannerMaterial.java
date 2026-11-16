package dev.heavenlyrestriction.client.netrunner;

import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;

/**
 * Filtering is part of the material, so GUI-scale changes cannot turn type into enlarged pixels.
 */
final class ScannerMaterial extends RenderStateShard {
  private static final Map<ResourceLocation, RenderType> TYPES = new HashMap<>();

  static RenderType texture(ResourceLocation path) {
    return TYPES.computeIfAbsent(
        path,
        p ->
            RenderType.create(
                "netrunner_smooth_type",
                DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS,
                8192,
                false,
                false,
                RenderType.CompositeState.builder()
                    .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                    .setTextureState(new TextureStateShard(p, true, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false)));
  }

  private ScannerMaterial() {
    super("netrunner", () -> {}, () -> {});
  }
}
