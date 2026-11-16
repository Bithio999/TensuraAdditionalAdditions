package dev.heavenlyrestriction.client.netrunner;

import static dev.heavenlyrestriction.client.netrunner.HudGeometry.*;

import dev.heavenlyrestriction.*;
import dev.heavenlyrestriction.netrunner.*;
import io.github.manasmods.tensura.client.TensuraKeybinds;
import io.github.manasmods.tensura.storage.TensuraStorages;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Vector4f;

@EventBusSubscriber(modid = HeavenlyRestriction.ID, value = Dist.CLIENT)
public final class NetrunnerClient {
  private static CompoundTag state = new CompoundTag();
  private static long received, selectionAt;
  private static boolean down, prepared;
  private static ScannerMotion MOTION = new ScannerMotion();
  private static int selected, focus = -1, focusTicks;
  private static Object level;
  private static final Map<Long, Long> arrivals = new HashMap<>();
  private static final Map<Integer, Long> flashes = new HashMap<>();
  private static Matrix4f projection = new Matrix4f(), view = new Matrix4f();
  private static Vec3 camera = Vec3.ZERO;
  private static boolean matrices;
  private static final int CYAN = 0xff65e5f5,
      RED = 0xffff3742,
      WHITE = 0xffc6f7f2,
      MUTED = 0xff7badae;

  @EventBusSubscriber(
      modid = HeavenlyRestriction.ID,
      value = Dist.CLIENT,
      bus = EventBusSubscriber.Bus.MOD)
  public static final class Setup {
    @SubscribeEvent
    public static void reload(RegisterClientReloadListenersEvent e) {
      e.registerReloadListener(
          (net.minecraft.server.packs.resources.ResourceManagerReloadListener)
              manager -> {
                ScannerFont.warmup();
                prepared = false;
              });
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
      e.enqueueWork(
          () -> {
            NetrunnerNetwork.receiver = NetrunnerClient::receive;
            NetrunnerNetwork.impactReceiver = i -> flashes.put(i.target(), System.nanoTime());
          });
    }
  }

  public static boolean active() {
    var m = Minecraft.getInstance();
    return down && m.screen == null && m.player != null && !m.options.hideGui;
  }

  public static boolean outlined(Entity e) {
    // The native full-screen outline pass starts once acquisition is complete. Keep the animated
    // lens/grid responsive while the link is forming; the body highlight accompanies the data.
    return active() && MOTION.sample(System.nanoTime()).data() >= .98 && e.getId() == focus;
  }

  public static void receive(CompoundTag t) {
    state = t;
    received = System.nanoTime();
    for (var x : t.getList("queue", 10)) {
      var q = (CompoundTag) x;
      arrivals.putIfAbsent(q.getLong("id"), received);
    }
    var live = new HashSet<Long>();
    for (var x : t.getList("queue", 10)) live.add(((CompoundTag) x).getLong("id"));
    arrivals.keySet().retainAll(live);
  }

  private static boolean held() {
    var m = Minecraft.getInstance();
    if (m.player == null || m.screen != null || !m.player.isAlive()) return false;
    var keys =
        List.of(
            TensuraKeybinds.ACTIVATE_SLOT_1,
            TensuraKeybinds.ACTIVATE_SLOT_2,
            TensuraKeybinds.ACTIVATE_SLOT_3);
    for (int k = 0; k < 3; k++)
      if (keys.get(k).isDown()
          && TensuraStorages.getAbilityFrom(m.player)
              .isAbilityInActivePreset(k, HeavenlyRestriction.NETRUNNER.get(), 0))
        return Netrunner.owned(m.player).isPresent();
    return false;
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post e) {
    var m = Minecraft.getInstance();
    if (level != m.level) {
      state = new CompoundTag();
      arrivals.clear();
      flashes.clear();
      level = m.level;
      down = false;
      focus = -1;
      matrices = false;
      MOTION = new ScannerMotion();
    }
    boolean next = held();
    if (next != down) {
      down = next;
      MOTION.setHeld(down, System.nanoTime());
      if (!down) {
        while (m.options.keyAttack.consumeClick()) {}
        while (m.options.keyUse.consumeClick()) {}
      }
    }
    if (!active() || m.level == null) return;
    double range = Math.clamp(state.contains("range") ? state.getDouble("range") : 32, 4, 96);
    LivingEntity hit = null;
    double nearest = Double.MAX_VALUE;
    Vec3 eye = m.player.getEyePosition(), look = m.player.getLookAngle();
    for (var t :
        m.level.getEntitiesOfClass(
            LivingEntity.class,
            m.player.getBoundingBox().inflate(range),
            t -> t != m.player && t.isAlive() && !t.isSpectator())) {
      var box = t.getBoundingBox().inflate(.24);
      var clip = box.clip(eye, eye.add(look.scale(range)));
      if (clip.isPresent()) {
        double d = eye.distanceToSqr(clip.get());
        if (d < nearest && m.player.hasLineOfSight(t)) {
          nearest = d;
          hit = t;
        }
      }
    }
    if (hit != null) {
      focus = hit.getId();
      focusTicks = 5;
    } else if (--focusTicks <= 0) focus = -1;
    if (m.player.tickCount % 4 == 0 && focus >= 0) send(0, focus, selected);
  }

  private static void send(int action, int target, int hack) {
    if (state.hasUUID("session") && state.getBoolean("scanning"))
      PacketDistributor.sendToServer(
          new NetrunnerNetwork.Request(state.getUUID("session"), action, target, hack));
  }

  public static boolean scroll(double amount) {
    if (!active()) return false;
    if (!MOTION.sample(System.nanoTime()).interactive()) return true;
    if (amount != 0) {
      selected = Math.floorMod(selected + (amount > 0 ? -1 : 1), 5);
      selectionAt = System.nanoTime();
    }
    return true;
  }

  public static void click(int button, int action) {
    if (!active() || !MOTION.sample(System.nanoTime()).interactive() || action != 1) return;
    if (button == 0 && focus >= 0) send(1, focus, selected);
    else if (button == 1) send(2, -1, 0);
  }

  @SubscribeEvent
  public static void layers(RenderGuiLayerEvent.Pre e) {
    if (active()
        && java.util.Set.of(
                "minecraft:crosshair",
                "minecraft:chat",
                "minecraft:overlay_message",
                "minecraft:hotbar",
                "minecraft:experience_bar",
                "minecraft:experience_level")
            .contains(e.getName().toString())) e.setCanceled(true);
  }

  @SubscribeEvent
  public static void level(RenderLevelStageEvent e) {
    if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
      projection.set(e.getProjectionMatrix());
      view.set(e.getModelViewMatrix());
      camera = e.getCamera().getPosition();
      matrices = true;
    }
  }

  @SubscribeEvent
  public static void render(RenderGuiEvent.Post e) {
    var m = Minecraft.getInstance();
    if (m.player == null || m.level == null || m.options.hideGui || m.screen != null) return;
    ScannerBatch.begin();
    try {
      long now = System.nanoTime();
      var graphics = e.getGuiGraphics();
      if (!prepared) {
        // Prime actual draw paths offscreen, before a first key press. Merely loading PNGs does not
        // create their GL materials; lazy glyph/icon uploads otherwise interrupt the acquisition.
        prepared = true;
        // Prime vanilla's outline post-process on an empty world buffer before a target is linked.
        // The first shader use must not interrupt the acquisition animation.
        m.levelRenderer.requestOutlineEffect();
        graphics.pose().pushPose();
        graphics.pose().translate(-8192, -8192, 0);
        scanner(new ScannerCanvas(graphics, 1), now, new ScannerMotion.Frame(1000, 1, true));
        graphics.flush();
        graphics.pose().popPose();
      }
      MOTION.renderFrame(now);
      var motion = MOTION.sample(now);
      double transition = motion.lens();
      var g = e.getGuiGraphics();
      float w = m.getWindow().getGuiScaledWidth(), h = m.getWindow().getGuiScaledHeight();
      if (transition > 0) {
        var full = new ScannerCanvas(g, (float) transition);
        full.rect(0, 0, w, h, 0x24134732);
        full.gradient(0, 0, w * .28f, h, 0xa636100e, 0x0036100e, 0x0036100e, 0xa636100e);
        full.gradient(w * .72f, 0, w * .28f, h, 0x0036100e, 0xa636100e, 0xa636100e, 0x0036100e);
        full.gradient(0, 0, w, h * .22f, 0x93200e0c, 0x93200e0c, 0x00200e0c, 0x00200e0c);
        full.gradient(0, h * .78f, w, h * .22f, 0x00200e0c, 0x00200e0c, 0x93200e0c, 0x93200e0c);
        // Fine lens scan lines sit behind the legible plaques, not across their text.
        for (float y = 0; y < h; y += 2.5f) full.rect(0, y, w, .35f, 0x072e0808);
        float scale = Math.min(w / 1920, h / 1080);
        g.pose().pushPose();
        g.pose().translate((w - 1920 * scale) / 2, (h - 1080 * scale) / 2, 0);
        g.pose().scale(scale, scale, 1);
        var c = new ScannerCanvas(g, 1);
        scanner(c, now, motion);
        g.pose().popPose();
      }
      overhead(g, w, h, now);
      // Reboot Optics is an actual status effect for a player victim, not just an AI-only control.
      if (m.player.hasEffect(NetrunnerEffects.OPTICS)) {
        var c = new ScannerCanvas(g, 1);
        c.rect(0, 0, w, h, 0xd4071117);
        for (int k = 0; k < 14; k++) {
          float y = (float) ((k * 67 + now / 9e6) % h);
          c.rect(0, y, w, 1, 0x5556e5e0);
        }
      }

    } finally {
      ScannerBatch.flush(e.getGuiGraphics());
    }
  }

  private static void scanner(ScannerCanvas root, long now, ScannerMotion.Frame motion) {
    var lens = root.fade(motion.lens());
    var chrome = root.fade(motion.chrome());
    float r =
        (float)
            (100 + 365 * net.minecraft.util.Mth.smoothstep(Math.clamp(motion.age() / 450, 0, 1)));
    lens.glowArc(960, 540, r, 0, (float) (Math.PI * 2), .8f, 0x85a8272e);
    lens.glowArc(960, 540, 57, 0, (float) (Math.PI * 2), .85f, 0xbfff3742);
    lens.arc(960, 540, 45, 0, (float) (Math.PI * 2), .85f, 0x80ff3742);
    lens.line(944, 540, 976, 540, .85f, RED);
    lens.stroke(List.of(new Point(953, 538), new Point(967, 538), new Point(960, 550)), .85f, RED);
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 5; col++) {
        float x = 775 + 92.5f * col, y = 432 + 108 * row;
        var p =
            List.of(
                new Point(x - 7, y - 7),
                new Point(x + 7, y - 7),
                new Point(x + 7, y + 7),
                new Point(x - 7, y + 7));
        chrome.halo(p, RED);
        chrome.stroke(p, .85f, 0xcfff3742);
      }
    for (int k = 0; k < 13; k++) {
      float y = 455 + k * 18;
      chrome.glowLine(716, y - 3, 725, y, .85f, 0xcfff3742);
      chrome.line(725, y, 716, y + 3, .85f, 0xcfff3742);
      chrome.glowLine(1213, y - 3, 1204, y, .85f, 0xcfff3742);
      chrome.line(1204, y, 1213, y + 3, .85f, 0xcfff3742);
    }
    chrome.text("1x", 675, 540, 17, RED);
    var mc = Minecraft.getInstance();
    var entity = mc.level == null ? null : mc.level.getEntity(focus);
    chrome.text(
        entity == null ? "—" : String.format(Locale.ROOT, "%.1f", mc.player.distanceTo(entity)),
        1237,
        540,
        17,
        RED);
    edge(chrome, motion);
    resource(root.fade(motion.resource()), motion);
    hacks(root, motion, now);
    data(root.fade(motion.data()), motion);
    var controls = root.fade(motion.layer(550, 180));
    controls.mouse(1298, 1004, 0);
    controls.glowText("QUEUE", 1328, 1006, 21, RED);
    controls.mouse(1410, 1004, 1);
    controls.glowText("REMOVE LAST", 1440, 1006, 21, RED);
    controls.stroke(
        List.of(
            new Point(1581, 1006),
            new Point(1662, 1006),
            new Point(1662, 1033),
            new Point(1581, 1033)),
        1,
        0xff62e9f7);
    controls.body("RELEASE", 1588, 1008, 20, 0xff79e8f7);
    controls.glowText("CLOSE SCANNER", 1675, 1006, 21, RED);
    String message = state.getString("message");
    if (!message.isEmpty() && now - received < 2e9) controls.center(message, 960, 880, 20, CYAN);
  }

  private static void edge(ScannerCanvas c, ScannerMotion.Frame motion) {
    String timer =
        String.format(
            Locale.ROOT,
            "%02d:%02d",
            (int) (motion.age() / 60000),
            (int) (motion.age() / 1000) % 60);
    var micro = c.fade(.55);
    micro.dots(55, 38, 3, 2, RED);
    micro.tracked(timer, 55, 51, 10, RED, 1);
    micro.tracked("LINK", 55, 62, 10, RED, 1);
    micro.tracked(timer, 1832, 38, 10, RED, 1);
    micro.dots(1840, 52, 3, 2, RED);
    micro.dots(55, 1017, 3, 2, RED);
    micro.tracked("LINK", 55, 1031, 10, RED, 1);
    micro.dots(1840, 1017, 3, 2, RED);
    micro.tracked(timer, 1832, 1031, 10, RED, 1);
    c.g.pose().pushPose();
    c.g.pose().translate(190, 104, 0);
    c.g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(3));
    c.tracked("SCANNER", 0, 0, 13, 0xb3f3413e, .8f);
    c.tracked("NETRUNNER", 99, 6, 8, 0x80f3413e, .7f);
    c.line(0, 22, 187, 22, 1, 0x99ab272a);
    c.g.pose().popPose();
    c.glowLine(1549, 131, 1730, 121, .85f, 0x99b1272a);
    c.glowLine(190, 950, 371, 940, .85f, 0x99b1272a);
  }

  private static void resource(ScannerCanvas c, ScannerMotion.Frame motion) {
    if (c.opacity <= 0) return;
    double mp = state.getDouble("mp"), max = state.getDouble("maxMp");
    c.dots(693, 97, 3, 5, 0x99ff323e);
    c.glowText("MAGICULES: " + number(mp) + "/" + number(max), 719, 108, 23, 0xfff53d42);
    c.glowLine(693, 151, 1257, 151, 1, 0xcfe72c38);
    c.tracked("NEURAL RESERVE", 696, 146, 7, 0x99a52a30, 1);
    int full = max <= 0 ? 0 : (int) Math.floor(Math.clamp(mp / max, 0, 1) * 29);
    double committed = 0;
    var costs = state.getList("hacks", 10);
    for (var n : state.getList("queue", 10)) {
      int h = ((CompoundTag) n).getInt("hack");
      if (h >= 0 && h < costs.size()) committed += costs.getCompound(h).getDouble("cost");
    }
    int ghost = max <= 0 ? 0 : Math.min(29 - full, (int) Math.ceil(committed / max * 29));
    for (int k = 0; k < 29; k++) {
      var cell = c.fade(motion.layer(230 + k * 4, 140) / Math.max(.0001, motion.resource()));
      float x = 720 + k * 17.55f, y = 163, w = 13.55f;
      var path = HudGeometry.pip(x, y, w, 44);
      if (k < full) {
        cell.halo(path, 0xff56dceb);
        cell.polygon(path, 0xf256dceb);
        cell.rect(x + 3, y + 4, 3, 4, 0xd0043e53);
        cell.line(x + 1, y + 1, x + w - 1, y + 1, .65f, 0xb3b3f7ff);
      } else if (k < full + ghost) {
        cell.polygon(path, 0xa83c7581);
        cell.rect(x + 3, y + 4, 3, 4, 0x90406167);
      } else {
        cell.shape(path, 0xd9190a0d, 0xd9ff323e);
      }
    }
  }

  private static void hacks(ScannerCanvas root, ScannerMotion.Frame motion, long now) {
    var title = root.fade(motion.hacks());
    title.dots(181, 297, 3, 5, 0xb3cd3538);
    title.tracked("NEURAL INTERFACE v.01", 195, 293, 12, 0xb3ac3335, .7f);
    title.glowText("AVAILABLE QUICKHACKS:", 200, 315, 26, 0xff5ce5fa);
    title.glowLine(200, 361, 598, 361, 1, 0xffd62f39);
    var hacks = state.getList("hacks", 10);
    var queue = state.getList("queue", 10);
    for (int k = 0; k < 5; k++) {
      double reveal = motion.row(k);
      if (reveal <= 0) continue;
      var c = root.fade(reveal);
      float y = 379 + k * 63, x = k == selected ? 210 : 230, w = k == selected ? 410 : 400;
      // Build the plaque out of scan fragments, then reveal its contents; no complete menu on frame
      // one.
      float assembled = (float) ease((motion.age() - 420 - k * 24) / 160);
      c.g.pose().pushPose();
      c.g.pose().translate((1 - assembled) * 18, (1 - assembled) * (k % 2 == 0 ? -3 : 3), 0);
      var h = Quickhack.values()[k];
      var row = k < hacks.size() ? hacks.getCompound(k) : new CompoundTag();
      int cd = row.getInt("cooldown");
      double cost = row.contains("cost") ? row.getDouble("cost") : h.baseCost;
      boolean queued = false;
      for (var n : queue) if (((CompoundTag) n).getInt("hack") == k) queued = true;
      boolean selectedRow = k == selected,
          poor = state.contains("mp") && state.getDouble("mp") < cost;
      int tint = poor ? 0xffff565c : CYAN;
      var path = HudGeometry.chip(x, y, w, 57);
      if (selectedRow) c.halo(path, poor ? RED : CYAN);
      c.shape(
          path,
          poor ? 0xf0421117 : selectedRow ? 0xf2123c4f : 0xf50a101f,
          poor ? 0xffd7414c : selectedRow ? 0xff70f2ff : 0xdd3f8a9a);
      if (selectedRow) {
        float sweep = (float) ease((now - selectionAt) / 180e6);
        c.polygon(
            List.of(
                new Point(x + 200 + sweep * 40, y + 1),
                new Point(x + 270 + sweep * 20, y + 1),
                new Point(x + 225 + sweep * 20, y + 56),
                new Point(x + 155 + sweep * 40, y + 56)),
            0x5330c8e5);
      }
      float content = (float) Math.clamp((motion.age() - 450 - k * 24) / 130, 0, 1);
      var ink = c.fade(content);
      if (selectedRow) ink.glowText(h.title.toUpperCase(Locale.ROOT), x + 23, y + 7, 25, tint);
      else ink.text(h.title.toUpperCase(Locale.ROOT), x + 23, y + 7, 25, tint);
      String status = queued ? "QUEUED" : cd > 0 ? "WAIT " + cd + "s" : poor ? "LOW MP" : "READY";
      float badgeWidth = ink.width(status, 12, true) + 8;
      box(ink, x + 23, y + 35, badgeWidth, 16, status, 12, poor ? RED : 0xff80d8ed);
      String kind = k == 2 || k == 3 ? "CONTROL" : k == 4 ? "FINISHER" : "COMBAT";
      box(
          ink,
          x + 28 + badgeWidth,
          y + 35,
          ink.width(kind, 11, true) + 8,
          16,
          kind,
          11,
          poor ? RED : 0xfff7b05e);
      float iconX = x + w - 49;
      ink.stroke(
          List.of(
              new Point(iconX, y + 4.5f),
              new Point(iconX + 45, y + 4.5f),
              new Point(iconX + 45, y + 52.5f),
              new Point(iconX, y + 52.5f)),
          .75f,
          poor ? RED : selectedRow ? 0xff6beaff : 0xff41859c);
      ink.icon(k, iconX + 5.5f, y + 11.5f, 34, tint);
      ink.right(number(cost), iconX - 35, y + 10, 31, tint);
      ink.line(iconX - 29, y + 16, iconX - 29, y + 38, .75f, tint);
      ink.line(iconX - 9, y + 16, iconX - 9, y + 38, .75f, tint);
      ink.text("MP", iconX - 25, y + 22, 12, tint);
      c.g.pose().popPose();
    }
    var help = root.fade(motion.layer(570, 180));
    help.g.pose().pushPose();
    help.g.pose().translate(231, 712, 0);
    help.g.pose().scale(.65f, .65f, 1);
    help.mouse(0, 0, 0);
    help.g.pose().popPose();
    help.tracked("SCROLL TO SELECT A QUICKHACK", 252, 714, 13, 0xffd35a60, .6f);
  }

  private static void box(
      ScannerCanvas c, float x, float y, float w, float h, String label, float size, int color) {
    c.stroke(
        List.of(new Point(x, y), new Point(x + w, y), new Point(x + w, y + h), new Point(x, y + h)),
        .75f,
        color);
    c.text(label, x + 4, y + 2, size, color);
  }

  private static void data(ScannerCanvas c, ScannerMotion.Frame motion) {
    if (c.opacity <= 0) return;
    var target = state.getCompound("target");
    boolean has = target.contains("id") && target.getInt("id") == focus;
    float shift = (float) ((1 - ease((motion.age() - 500) / 230)) * 12);
    c.g.pose().pushPose();
    c.g.pose().translate(shift, 0, 0);
    var tab =
        List.of(
            new Point(1390, 299),
            new Point(1584, 299),
            new Point(1593, 308),
            new Point(1593, 347),
            new Point(1390, 347));
    c.polygon(tab, 0xf20b0e1e);
    for (int i = 0; i < tab.size(); i++)
      if (i != 3) {
        var a = tab.get(i);
        var b = tab.get((i + 1) % tab.size());
        c.line(a.x(), a.y(), b.x(), b.y(), .85f, 0xff61d9ed);
      }
    c.text("DATA", 1403, 308, 25, 0xff70e9fa);
    c.shape(HudGeometry.data(1390, 347, 418, 523), 0xf2090b1a, 0xff499bb2);
    c.text(
        has ? c.fit(target.getString("name").toUpperCase(Locale.ROOT), 378, 28, true) : "NO TARGET",
        1410,
        369,
        28,
        0xffc8eff5);
    var mc = Minecraft.getInstance();
    var entity = mc.level.getEntity(focus);
    String identity =
        entity instanceof net.minecraft.world.entity.player.Player
            ? "PLAYER"
            : entity != null && entity.getSoundSource() == net.minecraft.sounds.SoundSource.HOSTILE
                ? "HOSTILE"
                : "LIVING ENTITY";
    if (has) box(c, 1410, 407, c.width(identity, 12, true) + 8, 15, identity, 12, 0xffd3e7ec);
    c.body(has ? "Race / species" : "AIM AT A LIVING TARGET", 1410, 443, 17, 0xff8abfc9);
    if (has)
      c.right(c.fit(local(target.getString("race")), 230, 17, false), 1788, 443, 17, 0xffdde6e7);
    c.body("Existence Points", 1410, 470, 20, 0xff84d9e6);
    c.right(has ? number(target.getDouble("ep")) + " EP" : "—", 1788, 470, 20, 0xffd6e2e6);
    c.line(1410, 505, 1788, 505, 1, 0x88426576);
    String[] labels = {"HP", "SHP", "Magicules", "Aura"},
        keys = {"hp", "shp", "mp", "aura"},
        caps = {"maxHp", "maxShp", "maxMp", "maxAura"};
    int[] colors = {0xffff4d57, 0xff95dbe4, 0xff58d7e8, 0xfff2b06b};
    for (int k = 0; k < 4; k++) {
      float y = 521 + k * 43;
      double value = has ? target.getDouble(keys[k]) : 0, cap = has ? target.getDouble(caps[k]) : 0;
      c.body(labels[k], 1410, y, 19, 0xff67dcf3);
      c.right(has ? number(value) + " / " + number(cap) : "—", 1788, y, 19, 0xffd3e2e4);
      c.rect(1410, y + 26, 378, 2, 0x5237657b);
      c.rect(
          1410, y + 26, (float) (378 * Math.clamp(value / Math.max(1, cap), 0, 1)), 2, colors[k]);
    }
    c.tracked("ACTIVE EFFECTS", 1410, 690, 15, 0xff7edff1, .5f);
    var effects = target.getList("effects", 8);
    for (int k = 0; k < Math.min(2, effects.size()); k++)
      c.body(
          c.fit("•  " + local(effects.getString(k)), 378, 18, false),
          1410,
          720 + k * 26,
          18,
          0xffc9dce3);
    if (has && effects.isEmpty()) c.body("No status effects", 1410, 720, 18, MUTED);
    if (effects.size() > 2) c.body("+" + (effects.size() - 2) + " more", 1410, 763, 13, MUTED);
    c.line(1410, 780, 1788, 780, 1, 0x80436a79);
    c.tracked("CURRENT CAST", 1410, 795, 15, 0xff7edff1, .5f);
    String cast = target.getString("cast");
    c.body(
        has ? cast.isEmpty() ? "No active chant" : c.fit(local(cast), 378, 19, false) : "—",
        1410,
        823,
        19,
        cast.isEmpty() ? MUTED : 0xfff7b46d);
    c.rect(1410, 848, 378, 3, 0xff324a5c);
    if (has && !cast.isEmpty()) {
      float phase = (float) ((System.nanoTime() / 1e9) % 1);
      c.rect(1410 + phase * 260, 848, 118, 3, 0xffe59663);
    }
    c.tracked("TENSURA // NETRUNNER", 1412, 880, 13, 0xff3c7d93, 2);
    c.g.pose().popPose();
  }

  private static void overhead(net.minecraft.client.gui.GuiGraphics g, float w, float h, long now) {
    var m = Minecraft.getInstance();
    if (!matrices || m.level == null) return;
    Map<Integer, List<CompoundTag>> groups = new LinkedHashMap<>();
    for (var n : state.getList("queue", 10)) {
      var t = (CompoundTag) n;
      groups.computeIfAbsent(t.getInt("target"), id -> new ArrayList<>()).add(t);
    }
    if (active() && focus >= 0) groups.computeIfAbsent(focus, id -> new ArrayList<>());
    for (int id : flashes.keySet()) groups.computeIfAbsent(id, k -> new ArrayList<>());
    for (var group : groups.entrySet()) {
      var t = m.level.getEntity(group.getKey());
      if (t == null) continue;
      Vec3 at =
          t.getPosition(m.getTimer().getGameTimeDeltaPartialTick(false))
              .add(0, t.getBbHeight() + .5, 0)
              .subtract(camera);
      var clip =
          new Vector4f((float) at.x, (float) at.y, (float) at.z, 1).mul(view).mul(projection);
      if (clip.w <= 0) continue;
      clip.div(clip.w);
      if (Math.abs(clip.x) > 1.1 || Math.abs(clip.y) > 1.1) continue;
      float scale = Math.min(w / 1920, h / 1080),
          x = (clip.x + 1) * w / 2,
          y = (1 - clip.y) * h / 2;
      g.pose().pushPose();
      g.pose().translate(x, y, 0);
      g.pose().scale(scale, scale, 1);
      var c = new ScannerCanvas(g, 1);
      if (active() && MOTION.sample(now).chrome() > 0) {
        c = c.fade(MOTION.sample(now).chrome());
        String name = c.fit(t.getName().getString().toUpperCase(Locale.ROOT), 440, 25, true);
        c.glowText(name, -c.width(name, 25, true) / 2, -37, 25, 0xff71e9fa);
        c.line(-5, -8, 0, -2, 1, CYAN);
        c.line(0, -2, 5, -8, 1, CYAN);
      }
      c = new ScannerCanvas(g, 1);
      var list = group.getValue();
      for (int k = 0; k < list.size(); k++) {
        var q = list.get(k);
        float x0 = -list.size() * 25 + 4 + k * 50;
        double entry = ease((now - arrivals.getOrDefault(q.getLong("id"), now)) / 150e6);
        float y0 = (float) (-98 - (1 - entry) * 12);
        int hack = q.getInt("hack");
        double progress =
            HudGeometry.progress(q.getInt("elapsed"), q.getInt("total"), (now - received) / 50e6);
        boolean first = q.getLong("id") == state.getList("queue", 10).getCompound(0).getLong("id");
        var tile = HudGeometry.data(x0, y0, 42, 42);
        c.halo(tile, RED);
        c.shape(tile, 0xde3d131a, 0xfffa3b46);
        if (first)
          c.rect(
              x0 + 1, y0 + 41 - (float) (40 * progress), 40, (float) (40 * progress), 0x807c323d);
        c.icon(hack, x0 + 6, y0 + 5, 30, RED);
        if (first) c.text((int) (progress * 100) + "%", x0 - 1, y0 - 20, 16, RED);
      }
      Long flash = flashes.get(t.getId());
      if (flash != null) {
        float fade = (float) (1 - (now - flash) / 400e6);
        if (fade > 0) {
          var f = new ScannerCanvas(g, fade);
          f.arc(0, -72, 24 + (1 - fade) * 30, 0, (float) (Math.PI * 2), 1.2f, CYAN);
        }
      }
      g.pose().popPose();
    }
    flashes.entrySet().removeIf(e -> now - e.getValue() > 500e6);
  }

  private static String number(double n) {
    if (n >= 1e12) return String.format(Locale.ROOT, "%.1fT", n / 1e12);
    if (n >= 1e9) return String.format(Locale.ROOT, "%.1fB", n / 1e9);
    if (n >= 1e6) return String.format(Locale.ROOT, "%.1fM", n / 1e6);
    if (n >= 1e4) return String.format(Locale.ROOT, "%.1fk", n / 1e3);
    return String.format(Locale.ROOT, "%,.0f", n);
  }

  private static String local(String text) {
    return net.minecraft.network.chat.Component.translatable(text).getString();
  }

  private NetrunnerClient() {}
}
