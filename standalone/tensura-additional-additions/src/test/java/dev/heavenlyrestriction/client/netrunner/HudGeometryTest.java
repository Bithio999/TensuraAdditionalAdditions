package dev.heavenlyrestriction.client.netrunner;

import static dev.heavenlyrestriction.client.netrunner.HudGeometry.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

final class HudGeometryTest {
  @Test
  void panelsHaveFullAreaAndClosedCornerJoins() {
    for (float width : new float[] {202, 400, 418}) {
      var p = panel(230, 378, width, 57, 12);
      var triangles = triangulate(p);
      assertEquals(p.size() - 2, triangles.size());
      double area =
          triangles.stream()
              .mapToDouble(
                  t ->
                      Math.abs(
                              (t.b().x() - t.a().x()) * (t.c().y() - t.a().y())
                                  - (t.b().y() - t.a().y()) * (t.c().x() - t.a().x()))
                          / 2)
              .sum();
      assertEquals(width * 57 - 144, area, .01);
      for (float d : new float[] {-.6f, .6f}) {
        var ring = offset(p, d);
        assertEquals(p.size(), ring.size());
        for (var q : ring) {
          assertTrue(Float.isFinite(q.x()) && Float.isFinite(q.y()));
          assertTrue(q.x() > 228 && q.x() < 232 + width);
          assertTrue(q.y() > 376 && q.y() < 437);
        }
      }
    }
  }

  @Test
  void concaveNotchesTriangulateWithoutFillingTheCutout() {
    var p =
        List.of(
            new Point(0, 0),
            new Point(100, 0),
            new Point(100, 60),
            new Point(20, 60),
            new Point(20, 40),
            new Point(0, 40));
    assertEquals(4, triangulate(p).size());
  }

  @Test
  void animationIsBoundedMonotonicAndReachesEndpoints() {
    assertEquals(0, ease(-1));
    assertEquals(1, ease(2));
    double old = 0;
    for (int k = 0; k <= 100; k++) {
      double x = ease(k / 100d);
      assertTrue(x >= old && x <= 1);
      old = x;
    }
    assertEquals(.6, progress(10, 20, 2), .0001);
    assertEquals(1, progress(19, 20, 50));
    assertEquals(.25, progress(0, 20, 100));
  }

  @Test
  void chipStepAndNotchedPipsRemainClosedCompleteShapes() {
    var chip = chip(230, 379, 400, 57);
    assertEquals(7, chip.size());
    assertEquals(5, triangulate(chip).size());
    assertEquals(230, chip.get(0).x());
    assertEquals(236, chip.get(5).x());
    assertTrue(chip.get(5).y() > chip.get(6).y());
    var cell = pip(720, 163, 13.55f, 44);
    assertEquals(3, triangulate(cell).size());
    assertTrue(cell.get(3).x() < cell.get(2).x());
    assertTrue(cell.get(3).y() > cell.get(2).y());
    for (var point : offset(chip, -.425f)) {
      assertTrue(Float.isFinite(point.x()) && Float.isFinite(point.y()));
    }
  }
}
