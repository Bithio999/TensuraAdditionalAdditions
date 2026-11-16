package dev.heavenlyrestriction.client.netrunner;

import java.util.*;

/** Resolution-independent closed paths, shared by renderer and geometry regression tests. */
public final class HudGeometry {
  public record Point(float x, float y) {
    Point add(Point b) {
      return new Point(x + b.x, y + b.y);
    }

    Point sub(Point b) {
      return new Point(x - b.x, y - b.y);
    }

    Point mul(float n) {
      return new Point(x * n, y * n);
    }
  }

  public record Triangle(Point a, Point b, Point c) {}

  public static List<Point> panel(float x, float y, float w, float h, float cut) {
    return List.of(
        new Point(x + cut, y),
        new Point(x + w - cut, y),
        new Point(x + w, y + cut),
        new Point(x + w, y + h),
        new Point(x, y + h),
        new Point(x, y + cut));
  }

  public static List<Triangle> triangulate(List<Point> path) {
    double signed = 0;
    for (int k = 0; k < path.size(); k++) {
      var a = path.get(k);
      var b = path.get((k + 1) % path.size());
      signed += a.x * b.y - b.x * a.y;
    }
    if (signed < 0) {
      path = new ArrayList<>(path);
      Collections.reverse(path);
    }
    var indices = new ArrayList<Integer>();
    for (int k = 0; k < path.size(); k++) indices.add(k);
    var out = new ArrayList<Triangle>();
    int guard = path.size() * path.size();
    while (indices.size() > 2 && guard-- > 0) {
      boolean found = false;
      for (int k = 0; k < indices.size(); k++) {
        int ia = indices.get((k + indices.size() - 1) % indices.size()),
            ib = indices.get(k),
            ic = indices.get((k + 1) % indices.size());
        Point a = path.get(ia), b = path.get(ib), c = path.get(ic);
        if (cross(b.sub(a), c.sub(b)) <= .0001) continue;
        boolean inside = false;
        for (int id : indices) {
          if (id == ia || id == ib || id == ic) continue;
          Point p = path.get(id);
          if (cross(b.sub(a), p.sub(a)) >= 0
              && cross(c.sub(b), p.sub(b)) >= 0
              && cross(a.sub(c), p.sub(c)) >= 0) {
            inside = true;
            break;
          }
        }
        if (!inside) {
          out.add(new Triangle(a, b, c));
          indices.remove(k);
          found = true;
          break;
        }
      }
      if (!found) break;
    }
    return out;
  }

  private static float cross(Point a, Point b) {
    return a.x * b.y - a.y * b.x;
  }

  public static List<Point> offset(List<Point> p, float distance) {
    var out = new ArrayList<Point>();
    for (int k = 0; k < p.size(); k++) {
      Point at = p.get(k),
          a = at.sub(p.get((k + p.size() - 1) % p.size())),
          b = p.get((k + 1) % p.size()).sub(at);
      float al = (float) Math.hypot(a.x, a.y), bl = (float) Math.hypot(b.x, b.y);
      Point n1 = new Point(-a.y / al, a.x / al),
          n2 = new Point(-b.y / bl, b.x / bl),
          m = n1.add(n2);
      float den = m.x * n2.x + m.y * n2.y;
      float scale =
          Math.abs(den) < .001
              ? 0
              : Math.clamp(distance / den, -Math.abs(distance) * 4, Math.abs(distance) * 4);
      out.add(at.add(m.mul(scale)));
    }
    return out;
  }

  public static double ease(double t) {
    t = Math.clamp(t, 0, 1);
    return 1 - Math.pow(1 - t, 3);
  }

  public static double progress(int elapsed, int total, double sinceTicks) {
    return Math.clamp((elapsed + Math.clamp(sinceTicks, 0, 5)) / Math.max(1, total), 0, 1);
  }

  private HudGeometry() {}
}
