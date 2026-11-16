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
  private static long received, selectionAt, openedAt, closedAt;
  private static boolean down;
  private static int selected, focus = -1, focusTicks;
  private static Object level;
  private static final Map<Long, Long> arrivals = new HashMap<>();
  private static final Map<Integer, Long> flashes = new HashMap<>();
  private static Matrix4f projection = new Matrix4f(), view = new Matrix4f();
  private static Vec3 camera = Vec3.ZERO;
  private static boolean matrices;
  private static final int CYAN = 0xff65ebe8,
      RED = 0xfff65e64,
      WHITE = 0xffc6f7f2,
      MUTED = 0xff7badae,
      NAVY = 0xdf081f2a;

  @EventBusSubscriber(
      modid = HeavenlyRestriction.ID,
      value = Dist.CLIENT,
      bus = EventBusSubscriber.Bus.MOD)
  public static final class Setup {
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
    return active() && e.getId() == focus;
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
    }
    boolean next = held();
    if (next != down) {
      down = next;
      if (down) {
        openedAt = System.nanoTime();
        closedAt = 0;
      } else {
        closedAt = System.nanoTime();
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
      if (!m.player.hasLineOfSight(t)) continue;
      var box = t.getBoundingBox().inflate(.24);
      var clip = box.clip(eye, eye.add(look.scale(range)));
      if (clip.isPresent()) {
        double d = eye.distanceToSqr(clip.get());
        if (d < nearest) {
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
    if (amount != 0) {
      selected = Math.floorMod(selected + (amount > 0 ? -1 : 1), 5);
      selectionAt = System.nanoTime();
    }
    return true;
  }

  public static void click(int button, int action) {
    if (!active() || action != 1) return;
    if (button == 0 && focus >= 0) send(1, focus, selected);
    else if (button == 1) send(2, -1, 0);
  }

  @SubscribeEvent
  public static void layers(RenderGuiLayerEvent.Pre e) {
    if (active()
        && java.util.Set.of("minecraft:crosshair", "minecraft:chat", "minecraft:overlay_message")
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
    long now = System.nanoTime();
    double transition =
        down
            ? ease((now - openedAt) / 220e6)
            : closedAt == 0 ? 0 : 1 - ease((now - closedAt) / 180e6);
    var g = e.getGuiGraphics();
    float w = m.getWindow().getGuiScaledWidth(), h = m.getWindow().getGuiScaledHeight();
    if (transition > 0) {
      var full = new ScannerCanvas(g, (float) transition);
      full.rect(0, 0, w, h, 0x26137d46);
      full.gradient(0, 0, w * .28f, h, 0x7d200e17, 0x00200e17, 0x00200e17, 0x7d200e17);
      full.gradient(w * .72f, 0, w * .28f, h, 0x00200e17, 0x7d200e17, 0x7d200e17, 0x00200e17);
      full.gradient(0, 0, w, h * .22f, 0x6e140d16, 0x6e140d16, 0x00140d16, 0x00140d16);
      full.gradient(0, h * .78f, w, h * .22f, 0x00140d16, 0x00140d16, 0x6e140d16, 0x6e140d16);
      float scale = Math.min(w / 1920, h / 1080);
      g.pose().pushPose();
      g.pose().translate((w - 1920 * scale) / 2, (h - 1080 * scale) / 2, 0);
      g.pose().scale(scale, scale, 1);
      var c = new ScannerCanvas(g, (float) transition);
      scanner(c, now, transition);
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
  }

  private static void scanner(ScannerCanvas c, long now, double open) {
    float iris = (float) (.94 + .06 * open), r = 465 * iris;
    float end = (float) (Math.PI * 2 * open);
    c.arc(960, 540, r, 0, end, 1.1f, 0x99f2686c);
    c.arc(960, 540, 57, 0, (float) (Math.PI * 2), 1.2f, RED);
    c.arc(960, 540, 45, 0, (float) (Math.PI * 2), .8f, 0x77f2686c);
    c.line(944, 540, 976, 540, 1, RED);
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 5; col++) {
        float x = 775 + 92.5f * col, y = 432 + 108 * row;
        c.stroke(
            List.of(
                new Point(x - 3, y - 3),
                new Point(x + 3, y - 3),
                new Point(x + 3, y + 3),
                new Point(x - 3, y + 3)),
            1,
            RED);
      }
    for (int k = -6; k <= 6; k++) {
      float y = 540 + k * 25;
      float x = 960 - (float) Math.sqrt(Math.max(0, r * r - (y - 540) * (y - 540)));
      c.line(x - 9, y, x + (k % 3 == 0 ? 15 : 3), y, 1, RED);
      c.line(1920 - x - 3, y, 1920 - x + 9, y, 1, RED);
    }
    double mp = state.getDouble("mp"), max = state.getDouble("maxMp");
    c.text("MAGICULES  " + number(mp) + " / " + number(max), 693, 113, 25, RED);
    int cells = 32, lit = max <= 0 ? 0 : (int) Math.ceil(mp / max * cells);
    for (int k = 0; k < cells; k++)
      c.rect(693 + k * 17.6f, 160, 10, 40, k < lit ? 0xff66ddd9 : 0x604e7e81);
    float slide = (float) ((1 - ease((now - openedAt - 35e6) / 185e6)) * -35);
    c.g.pose().pushPose();
    c.g.pose().translate(slide, 0, 0);
    c.text("AVAILABLE QUICKHACKS:", 200, 321, 30, CYAN);
    c.line(200, 355, 595, 355, 1, 0x8888eae7);
    var hacks = state.getList("hacks", 10);
    for (int k = 0; k < 5; k++) {
      float y = 378 + k * 65;
      boolean on = k == selected;
      float shift = on ? 8 : 0;
      var h = Quickhack.values()[k];
      var row = k < hacks.size() ? hacks.getCompound(k) : new CompoundTag();
      int cd = row.getInt("cooldown");
      double cost = row.contains("cost") ? row.getDouble("cost") : h.baseCost;
      int tint = cd > 0 ? 0xff78999a : CYAN;
      c.panel(230 + shift, y, 400, 57, 12, on ? 0xef0c3541 : NAVY, on ? CYAN : 0x8851a4aa);
      if (on) {
        float sheen = (float) Math.clamp((now - selectionAt) / 180e6, 0, 1);
        c.polygon(
            List.of(
                new Point(240 + shift, y + 1),
                new Point(430 + sheen * 160, y + 1),
                new Point(450 + sheen * 130, y + 56),
                new Point(240 + shift, y + 56)),
            0x2065ebe8);
        c.line(234 + shift, y + 13, 234 + shift, y + 53, 2, CYAN);
      }
      c.text(h.title, 251 + shift, y + 7, 22, tint);
      c.text(
          cd > 0
              ? String.format(Locale.ROOT, "COOLDOWN  %.1fs", (double) cd)
              : k == 2 || k == 3 ? "READY  /  CONTROL" : "READY  /  COMBAT",
          252 + shift,
          y + 35,
          13,
          on ? WHITE : MUTED);
      c.right(number(cost), 574 + shift, y + 12, 25, tint);
      c.icon(k, 580 + shift, y + 9, 37, tint);
    }
    c.text("SCROLL TO SELECT A QUICKHACK", 250, 716, 16, MUTED);
    c.g.pose().popPose();
    var target = state.getCompound("target");
    boolean has = target.contains("id") && target.getInt("id") == focus;
    c.g.pose().pushPose();
    c.g.pose().translate(-slide, 0, 0);
    c.panel(1390, 298, 202, 48, 12, 0xee0d3540, CYAN);
    c.text("DATA", 1410, 308, 27, CYAN);
    c.panel(1390, 347, 418, 572, 14, 0xe908202a, 0xaa65ebe8);
    c.text(has ? fit(target.getString("name"), 24) : "NO TARGET", 1414, 372, 31, CYAN);
    c.text(
        has ? fit(local(target.getString("race")), 30) : "AIM AT A LIVING TARGET",
        1414,
        414,
        15,
        MUTED);
    if (has) c.text(number(target.getDouble("ep")) + " EP", 1414, 442, 19, WHITE);
    c.line(1414, 474, 1782, 474, 1, 0x8865ebe8);
    String[] labels = {"HP", "SHP", "Magicules", "Aura"},
        keys = {"hp", "shp", "mp", "aura"},
        maxKeys = {"maxHp", "maxShp", "maxMp", "maxAura"};
    int[] colors = {RED, 0xffd8acff, CYAN, 0xffffd58b};
    for (int k = 0; k < 4; k++) {
      float y = 490 + k * 57;
      double value = has ? target.getDouble(keys[k]) : 0,
          cap = has ? target.getDouble(maxKeys[k]) : 0;
      c.text(labels[k], 1414, y, 21, MUTED);
      c.right(has ? number(value) + " / " + number(cap) : "—", 1782, y, 21, WHITE);
      c.rect(1414, y + 29, 368, 3, 0x6053787a);
      c.rect(
          1414, y + 29, (float) (368 * Math.clamp(value / Math.max(1, cap), 0, 1)), 3, colors[k]);
    }
    c.text("ACTIVE EFFECTS", 1414, 735, 16, MUTED);
    var effects = target.getList("effects", 8);
    for (int k = 0; k < Math.min(3, effects.size()); k++)
      c.text(fit(local(effects.getString(k)), 29), 1414, 762 + k * 26, 20, CYAN);
    if (effects.size() > 3) c.text("+" + (effects.size() - 3) + " more", 1414, 832, 14, MUTED);
    if (has && effects.isEmpty()) c.text("No status effects", 1414, 763, 19, MUTED);
    c.text("CURRENT CAST", 1414, 850, 15, MUTED);
    c.text(
        has
            ? fit(
                target.getString("cast").isEmpty()
                    ? "No active chant"
                    : local(target.getString("cast")),
                29)
            : "—",
        1414,
        874,
        20,
        CYAN);
    c.text("TENSURA // NETRUNNER", 1414, 939, 17, CYAN);
    c.g.pose().popPose();
    c.text("LEFT CLICK  /  QUEUE QUICKHACK", 1390, 963, 19, CYAN);
    c.text("RIGHT CLICK  /  CANCEL LAST UPLOAD", 1390, 994, 17, MUTED);
    c.text("RELEASE SKILL KEY  /  CLOSE SCANNER", 1390, 1024, 17, MUTED);
    String message = state.getString("message");
    if (!message.isEmpty() && now - received < 2e9) c.text(message, 760, 860, 21, CYAN);
    // A single scan line crosses the view during entry; no perpetual flashing full-screen texture.
    double sweep = Math.clamp((now - openedAt) / 550e6, 0, 1);
    if (sweep < 1)
      c.line(510, (float) (105 + sweep * 870), 1370, (float) (105 + sweep * 870), 1.3f, 0x9965ebe8);
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
      if (active()) {
        String name = fit(t.getName().getString().toUpperCase(Locale.ROOT), 28);
        c.center(name, 0, -37, 22, CYAN);
        c.line(-5, -8, 0, -2, 1, CYAN);
        c.line(0, -2, 5, -8, 1, CYAN);
      }
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
        c.panel(x0, y0, 42, 42, 5, 0xe51c1b24, first ? RED : 0x997f9c9d);
        if (first)
          c.rect(
              x0 + 1, y0 + 41 - (float) (40 * progress), 40, (float) (40 * progress), 0x807c323d);
        c.icon(hack, x0 + 6, y0 + 5, 30, first ? RED : WHITE);
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

  private static String fit(String t, int n) {
    return t.length() > n ? t.substring(0, n - 1) + "…" : t;
  }

  private NetrunnerClient() {}
}
