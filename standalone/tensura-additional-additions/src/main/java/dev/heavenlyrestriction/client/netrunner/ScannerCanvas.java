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

  void stroke(List<Point> p, float width, int color) {
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

  void text(String text, float x, float y, float size, int color) {
    var mc = Minecraft.getInstance();
    g.pose().pushPose();
    g.pose().translate(x, y, 1);
    float s = size / 9;
    g.pose().scale(s, s, 1);
    g.drawString(
        mc.font,
        Component.literal(text).withStyle(Style.EMPTY.withFont(HeavenlyRestriction.id("scanner"))),
        0,
        0,
        color(color),
        false);
    g.pose().popPose();
  }

  void center(String t, float x, float y, float size, int c) {
    var mc = Minecraft.getInstance();
    var component =
        Component.literal(t).withStyle(Style.EMPTY.withFont(HeavenlyRestriction.id("scanner")));
    text(t, x - mc.font.width(component) * size / 18, y, size, c);
  }

  void right(String t, float right, float y, float size, int c) {
    var mc = Minecraft.getInstance();
    var component =
        Component.literal(t).withStyle(Style.EMPTY.withFont(HeavenlyRestriction.id("scanner")));
    text(t, right - mc.font.width(component) * size / 9, y, size, c);
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
