package dev.heavenlyrestriction.client.netrunner;

import static dev.heavenlyrestriction.client.netrunner.HudGeometry.*;

import dev.heavenlyrestriction.HeavenlyRestriction;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.*;

/** Thin joined strokes use the same closed polygon on both sides of every corner. */
final class ScannerCanvas {
  final GuiGraphics g;
  final float opacity;

  ScannerCanvas(GuiGraphics g, float opacity) {
    this.g = g;
    this.opacity = opacity;
  }

  int color(int c) {
    return Math.round((c >>> 24) * opacity) << 24 | c & 0xffffff;
  }

  void quad(Point a, Point b, Point c, Point d, int color) {
    var v = g.bufferSource().getBuffer(RenderType.gui());
    var p = g.pose().last();
    int tint = color(color);
    if ((b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x()) > 0) {
      Point swap = b;
      b = d;
      d = swap;
    }
    v.addVertex(p, a.x(), a.y(), 0).setColor(tint);
    v.addVertex(p, b.x(), b.y(), 0).setColor(tint);
    v.addVertex(p, c.x(), c.y(), 0).setColor(tint);
    v.addVertex(p, d.x(), d.y(), 0).setColor(tint);
  }

  void polygon(List<Point> p, int c) {
    for (var t : triangulate(p)) quad(t.a(), t.b(), t.c(), t.c(), c);
  }

  private float strokeWidth(float requested) {
    var window = Minecraft.getInstance().getWindow();
    float physicalScale =
        Math.abs(g.pose().last().pose().m00()) * window.getWidth() / window.getGuiScaledWidth();
    return Math.max(requested, 1 / Math.max(.0001f, physicalScale));
  }

  void stroke(List<Point> p, float width, int color) {
    width = strokeWidth(width);
    var outer = offset(p, -width / 2);
    var inner = offset(p, width / 2);
    for (int k = 0; k < p.size(); k++) {
      int j = (k + 1) % p.size();
      quad(outer.get(k), outer.get(j), inner.get(j), inner.get(k), color);
    }
  }

  void panel(float x, float y, float w, float h, float cut, int fill, int border) {
    var p = HudGeometry.panel(x, y, w, h, cut);
    polygon(p, fill);
    stroke(p, 1.2f, border);
  }

  void rect(float x, float y, float w, float h, int color) {
    quad(new Point(x, y), new Point(x + w, y), new Point(x + w, y + h), new Point(x, y + h), color);
  }

  void gradient(float x, float y, float w, float h, int tl, int tr, int br, int bl) {
    var v = g.bufferSource().getBuffer(RenderType.gui());
    var p = g.pose().last();
    v.addVertex(p, x, y, 0).setColor(color(tl));
    v.addVertex(p, x, y + h, 0).setColor(color(bl));
    v.addVertex(p, x + w, y + h, 0).setColor(color(br));
    v.addVertex(p, x + w, y, 0).setColor(color(tr));
  }

  void line(float x, float y, float a, float b, float width, int color) {
    width = strokeWidth(width);
    float dx = a - x, dy = b - y, len = (float) Math.hypot(dx, dy);
    if (len == 0) return;
    float nx = -dy / len * width / 2, ny = dx / len * width / 2;
    quad(
        new Point(x + nx, y + ny),
        new Point(a + nx, b + ny),
        new Point(a - nx, b - ny),
        new Point(x - nx, y - ny),
        color);
  }

  void arc(float x, float y, float r, float from, float to, float width, int color) {
    int n = Math.max(2, (int) ((to - from) * r / 4));
    for (int k = 0; k < n; k++) {
      double a = from + (to - from) * k / n, b = from + (to - from) * (k + 1) / n;
      line(
          x + (float) Math.cos(a) * r,
          y + (float) Math.sin(a) * r,
          x + (float) Math.cos(b) * r,
          y + (float) Math.sin(b) * r,
          width,
          color);
    }
  }

  ScannerCanvas fade(double factor) {
    return new ScannerCanvas(g, opacity * (float) Math.clamp(factor, 0, 1));
  }

  void shape(List<Point> path, int fill, int border) {
    polygon(path, fill);
    stroke(path, .85f, border);
  }

  void halo(List<Point> path, int tint) {
    stroke(path, 9, (tint & 0xffffff) | 0x08000000);
    stroke(path, 5, (tint & 0xffffff) | 0x15000000);
    stroke(path, 2.5f, (tint & 0xffffff) | 0x28000000);
  }

  void glowLine(float x, float y, float a, float b, float width, int tint) {
    line(x, y, a, b, width + 7, (tint & 0xffffff) | 0x08000000);
    line(x, y, a, b, width + 3, (tint & 0xffffff) | 0x15000000);
    line(x, y, a, b, width, tint);
  }

  void glowArc(float x, float y, float r, float from, float to, float width, int tint) {
    arc(x, y, r, from, to, width + 6, (tint & 0xffffff) | 0x08000000);
    arc(x, y, r, from, to, width + 2, (tint & 0xffffff) | 0x18000000);
    arc(x, y, r, from, to, width, tint);
  }

  void text(String text, float x, float y, float size, int tint) {
    type(text, x, y, size, tint, true, 0, false);
  }

  void glowText(String text, float x, float y, float size, int tint) {
    type(text, x, y, size, tint, true, 0, true);
  }

  void body(String text, float x, float y, float size, int tint) {
    type(text, x, y, size, tint, false, 0, false);
  }

  void tracked(String text, float x, float y, float size, int tint, float spacing) {
    type(text, x, y, size, tint, false, spacing, false);
  }

  private void type(
      String text,
      float x,
      float y,
      float size,
      int tint,
      boolean bold,
      float spacing,
      boolean glow) {
    int rgba = color(tint);
    if ((rgba >>> 24) < 4 || text.isEmpty()) return;
    if (ScannerFont.supports(text, bold)) {
      if (glow)
        ScannerFont.draw(
            g, text, x, y, size, ((rgba >>> 24) / 2) << 24 | rgba & 0xffffff, bold, spacing, true);
      ScannerFont.draw(g, text, x, y, size, rgba, bold, spacing, false);
      return;
    }
    // Preserve full Unicode names with the game's fallback font rather than substituting boxes.
    var mc = Minecraft.getInstance();
    g.pose().pushPose();
    g.pose().translate(x, y, 1);
    float scale = size / 12;
    g.pose().scale(scale, scale, 1);
    g.drawString(
        mc.font,
        Component.literal(text).withStyle(Style.EMPTY.withFont(HeavenlyRestriction.id("scanner"))),
        0,
        0,
        rgba,
        false);
    g.pose().popPose();
  }

  float width(String t, float size, boolean bold) {
    if (ScannerFont.supports(t, bold)) return ScannerFont.width(t, size, bold, 0);
    var mc = Minecraft.getInstance();
    return mc.font.width(
            Component.literal(t).withStyle(Style.EMPTY.withFont(HeavenlyRestriction.id("scanner"))))
        * size
        / 12;
  }

  String fit(String text, float maxWidth, float size, boolean bold) {
    if (width(text, size, bold) <= maxWidth) return text;
    String value = text;
    while (!value.isEmpty() && width(value + "…", size, bold) > maxWidth)
      value = value.substring(0, value.offsetByCodePoints(value.length(), -1));
    return value + "…";
  }

  void center(String t, float x, float y, float size, int tint) {
    text(t, x - width(t, size, true) / 2, y, size, tint);
  }

  void right(String t, float x, float y, float size, int tint) {
    body(t, x - width(t, size, false), y, size, tint);
  }

  void dots(float x, float y, int cols, int rows, int tint) {
    for (int j = 0; j < rows; j++)
      for (int i = 0; i < cols; i++) rect(x + i * 5, y + j * 5, 1.4f, 1.4f, tint);
  }

  void mouse(float x, float y, int button) {
    var path =
        List.of(
            new Point(x + 5, y),
            new Point(x + 15, y),
            new Point(x + 20, y + 6),
            new Point(x + 20, y + 25),
            new Point(x + 15, y + 31),
            new Point(x + 5, y + 31),
            new Point(x, y + 25),
            new Point(x, y + 6));
    polygon(path, 0x1821bcd0);
    stroke(path, 1, 0xff66def1);
    line(x + 10, y, x + 10, y + 12, 1, 0xff66def1);
    line(x, y + 10, x + 20, y + 10, 1, 0xff66def1);
    rect(x + (button == 1 ? 10 : 1), y + 1, 9, 9, 0x2865c9d4);
  }

  void icon(int id, float x, float y, float size, int color) {
    if (id < 0 || id >= 5) return;
    String name = java.util.List.of("bolt", "brain", "eye", "legs", "skull").get(id);
    g.pose().pushPose();
    g.pose().translate(x, y, 0);
    g.pose().scale(size / 48, size / 48, 1);
    int tint = color(color);
    g.flush();
    g.setColor(
        ((tint >> 16) & 255) / 255f,
        ((tint >> 8) & 255) / 255f,
        (tint & 255) / 255f,
        (tint >>> 24) / 255f);
    boolean blended = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
    com.mojang.blaze3d.systems.RenderSystem.enableBlend();
    com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
    g.blit(
        HeavenlyRestriction.id("textures/gui/netrunner/" + name + ".png"),
        0,
        0,
        48,
        48,
        0,
        0,
        128,
        128,
        128,
        128);
    g.setColor(1, 1, 1, 1);
    if (!blended) com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    g.pose().popPose();
  }
}
