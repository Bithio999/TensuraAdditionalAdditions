package dev.heavenlyrestriction.client.netrunner;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Geometry, icons, glow, then lettering: shared materials are drawn once per HUD pass. */
final class ScannerBatch {
  private record Layer(int order, ResourceLocation texture) {}

  private static Map<Layer, List<Consumer<VertexConsumer>>> pending;
  private static final List<Runnable> fallback = new ArrayList<>();
  static int lastMaterials, lastJobs;

  static void begin() {
    pending = new LinkedHashMap<>();
    fallback.clear();
    lastMaterials = lastJobs = 0;
  }

  static void submit(
      GuiGraphics g, ResourceLocation texture, int layer, Consumer<VertexConsumer> job) {
    if (pending == null) {
      job.accept(g.bufferSource().getBuffer(ScannerMaterial.texture(texture)));
      return;
    }
    pending.computeIfAbsent(new Layer(layer, texture), key -> new ArrayList<>()).add(job);
    lastJobs++;
  }

  static void fallback(Runnable job) {
    if (pending == null) job.run();
    else fallback.add(job);
  }

  static void flush(GuiGraphics g) {
    if (pending == null) return;
    var frame = pending;
    pending = null;
    // Native GUI quads must be behind every icon/text layer. No per-icon flush is necessary.
    g.flush();
    frame.entrySet().stream()
        .sorted(Comparator.comparingInt(e -> e.getKey().order()))
        .forEach(
            e -> {
              var out = g.bufferSource().getBuffer(ScannerMaterial.texture(e.getKey().texture()));
              for (var job : e.getValue()) job.accept(out);
              lastMaterials++;
            });
    g.flush();
    for (var job : fallback) job.run();
    fallback.clear();
  }

  private ScannerBatch() {}
}
