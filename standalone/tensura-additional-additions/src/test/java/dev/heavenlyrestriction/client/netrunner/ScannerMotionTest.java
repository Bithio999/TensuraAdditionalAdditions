package dev.heavenlyrestriction.client.netrunner;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class ScannerMotionTest {
  @Test
  void acquisitionPrecedesPanelsAndControls() {
    var a = new ScannerMotion();
    long t = 1_000_000_000;
    a.setHeld(true, t);
    var first = a.sample(t + 40_000_000);
    assertTrue(first.lens() > 0);
    assertEquals(0, first.hacks());
    assertEquals(0, first.data());
    assertFalse(first.interactive());
    var link = a.sample(t + 280_000_000);
    assertTrue(link.resource() > 0);
    assertEquals(0, link.hacks());
    assertEquals(0, link.data());
    var plaques = a.sample(t + 520_000_000);
    assertTrue(plaques.hacks() > 0);
    assertTrue(plaques.row(0) > plaques.row(4));
    assertFalse(plaques.interactive());
    assertTrue(a.sample(t + 800_000_000).interactive());
  }

  @Test
  void shortPressDoesNotFlashACompleteMenuOnRelease() {
    var a = new ScannerMotion();
    long t = 1_000_000_000;
    a.setHeld(true, t);
    a.setHeld(false, t + 140_000_000);
    for (long ms : new long[] {141, 170, 220, 340}) {
      var f = a.sample(t + ms * 1_000_000);
      assertEquals(0, f.hacks());
      assertEquals(0, f.data());
      assertFalse(f.interactive());
    }
    assertEquals(0, a.sample(t + 340_000_000).lens());
  }

  @Test
  void stalledFramesDoNotSkipStraightToTheFullMenu() {
    var a = new ScannerMotion();
    long t = 1_000_000_000;
    a.setHeld(true, t);
    a.renderFrame(t + 40_000_000);
    a.renderFrame(t + 600_000_000);
    var afterStall = a.sample(t + 600_000_000);
    assertEquals(140, afterStall.age(), .001);
    assertTrue(afterStall.lens() > 0);
    assertEquals(0, afterStall.hacks());
    assertEquals(0, afterStall.data());
    assertFalse(afterStall.interactive());
    for (int ms = 620; ms <= 1400; ms += 20) a.renderFrame(t + ms * 1_000_000);
    assertTrue(a.sample(t + 1400_000_000).interactive());
  }

  @Test
  void releaseAfterAStallClosesTheLastVisibleState() {
    var a = new ScannerMotion();
    long t = 1_000_000_000;
    a.setHeld(true, t);
    a.renderFrame(t + 100_000_000);
    a.setHeld(false, t + 800_000_000);
    var closed = a.sample(t + 800_000_000);
    assertEquals(100, closed.age());
    assertEquals(0, closed.hacks());
    assertEquals(0, closed.data());
  }

  @Test
  void closingBeginsFromCurrentLayerOpacity() {
    var a = new ScannerMotion();
    long t = 1_000_000_000;
    a.setHeld(true, t);
    double before = a.sample(t + 550_000_000).hacks();
    a.setHeld(false, t + 550_000_000);
    assertEquals(before, a.sample(t + 550_000_000).hacks(), .00001);
    assertTrue(a.sample(t + 620_000_000).hacks() < before);
    assertEquals(0, a.sample(t + 750_000_000).hacks());
    a.setHeld(true, t + 800_000_000);
    assertEquals(0, a.sample(t + 801_000_000).hacks());
  }
}
