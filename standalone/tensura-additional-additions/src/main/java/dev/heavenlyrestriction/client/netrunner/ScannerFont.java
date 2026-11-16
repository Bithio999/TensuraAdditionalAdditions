package dev.heavenlyrestriction.client.netrunner;

import com.google.gson.*;
import dev.heavenlyrestriction.HeavenlyRestriction;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * High-resolution glyphs preserve the approved preview's two Rajdhani weights and fractional
 * spacing.
 */
final class ScannerFont {
  private record Glyph(float x, float y, float w, float h, float left, float top, float advance) {}

  private record Face(Map<Integer, Glyph> glyphs, Map<String, Float> kerning) {}

  private static final Map<Boolean, Face> FACES = new HashMap<>();

  static void clear() {
    FACES.clear();
  }

  static void warmup() {
    clear();
    for (boolean bold : new boolean[] {false, true}) {
      face(bold);
      for (String suffix :
          new String[] {"", "_6", "_12", "_24", "_glow", "_glow_6", "_glow_12", "_glow_24"})
        Minecraft.getInstance()
            .getTextureManager()
            .getTexture(
                HeavenlyRestriction.id(
                    "textures/gui/netrunner/type_"
                        + (bold ? "semibold" : "medium")
                        + suffix
                        + ".png"));
    }
  }

  private static Face face(boolean bold) {
    return FACES.computeIfAbsent(
        bold,
        b -> {
          try (var reader =
              Minecraft.getInstance()
                  .getResourceManager()
                  .openAsReader(
                      HeavenlyRestriction.id(
                          "scanner/type_" + (b ? "semibold" : "medium") + ".json"))) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            Map<Integer, Glyph> glyphs = new HashMap<>();
            for (var e : root.getAsJsonObject("glyphs").entrySet()) {
              var q = e.getValue().getAsJsonObject();
              glyphs.put(
                  Integer.parseInt(e.getKey()),
                  new Glyph(
                      q.get("x").getAsFloat(),
                      q.get("y").getAsFloat(),
                      q.get("w").getAsFloat(),
                      q.get("h").getAsFloat(),
                      q.get("left").getAsFloat(),
                      q.get("top").getAsFloat(),
                      q.get("advance").getAsFloat()));
            }
            Map<String, Float> kern = new HashMap<>();
            for (var e : root.getAsJsonObject("kerning").entrySet())
              kern.put(e.getKey(), e.getValue().getAsFloat());
            return new Face(glyphs, kern);
          } catch (java.io.IOException ex) {
            throw new IllegalStateException("Cannot load Netrunner typography", ex);
          }
        });
  }

  static boolean supports(String s, boolean bold) {
    var f = face(bold);
    return s.codePoints().allMatch(f.glyphs::containsKey);
  }

  static float width(String s, float size, boolean bold, float spacing) {
    var f = face(bold);
    float width = 0;
    String previous = "";
    for (var it = s.codePoints().iterator(); it.hasNext(); ) {
      int cp = it.nextInt();
      String ch = new String(Character.toChars(cp));
      var q = f.glyphs.get(cp);
      if (q == null) return -1;
      width +=
          q.advance * size / 48 + spacing + f.kerning.getOrDefault(previous + ch, 0f) * size / 48;
      previous = ch;
    }
    return Math.max(0, width - spacing);
  }

  static void draw(
      GuiGraphics g,
      String s,
      float x,
      float y,
      float size,
      int color,
      boolean bold,
      float spacing,
      boolean glow) {
    if ((color >>> 24) < 4) return;
    var f = face(bold);
    var window = Minecraft.getInstance().getWindow();
    float physical =
        size
            * Math.abs(g.pose().last().pose().m00())
            * window.getWidth()
            / window.getGuiScaledWidth();
    String filtered = physical < 9 ? "_6" : physical < 18 ? "_12" : physical < 36 ? "_24" : "";
    String path =
        "textures/gui/netrunner/type_"
            + (bold ? "semibold" : "medium")
            + (glow ? "_glow" : "")
            + filtered
            + ".png";
    var out = g.bufferSource().getBuffer(ScannerMaterial.texture(HeavenlyRestriction.id(path)));
    var pose = g.pose().last();
    float pen = x, scale = size / 48, baseline = y + size * .78f;
    String previous = "";
    for (var it = s.codePoints().iterator(); it.hasNext(); ) {
      int cp = it.nextInt();
      String ch = new String(Character.toChars(cp));
      var q = f.glyphs.get(cp);
      if (q == null) continue;
      pen += f.kerning.getOrDefault(previous + ch, 0f) * scale;
      float l = pen + q.left * scale,
          t = baseline + q.top * scale,
          r = l + q.w * scale,
          b = t + q.h * scale,
          u = q.x / 1024,
          v = q.y / 1024,
          ue = (q.x + q.w) / 1024,
          ve = (q.y + q.h) / 1024;
      out.addVertex(pose, l, t, 0).setUv(u, v).setColor(color);
      out.addVertex(pose, l, b, 0).setUv(u, ve).setColor(color);
      out.addVertex(pose, r, b, 0).setUv(ue, ve).setColor(color);
      out.addVertex(pose, r, t, 0).setUv(ue, v).setColor(color);
      pen += q.advance * scale + spacing;
      previous = ch;
    }
  }

  private ScannerFont() {}
}
