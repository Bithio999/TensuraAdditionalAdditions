package dev.heavenlyrestriction;

import dev.heavenlyrestriction.client.netrunner.NetrunnerClient;
import dev.heavenlyrestriction.netrunner.*;
import io.github.manasmods.manascore.skill.api.*;
import io.github.manasmods.manascore.storage.api.StorageHolder;
import io.github.manasmods.tensura.client.TensuraKeybinds;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.util.EnergyHelper;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid = HeavenlyRestriction.ID, value = Dist.CLIENT)
public final class NetrunnerReview {
  static final Path OUT = Path.of("/workspace/validation-netrunner/capture");
  static int age, ticks;
  static volatile boolean ready;
  static volatile String error;
  static volatile LivingEntity target;
  static boolean finished;
  static int entry;
  static final int[] ENTRY_TIMES = {40, 160, 300, 480, 640, 820};

  @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
  public static void entryFrame(RenderGuiEvent.Post e) {
    if (ticks < 100 || ticks > 125 || entry >= ENTRY_TIMES.length || !NetrunnerClient.active())
      return;
    try {
      var field = NetrunnerClient.class.getDeclaredField("MOTION");
      field.setAccessible(true);
      var controller = (dev.heavenlyrestriction.client.netrunner.ScannerMotion) field.get(null);
      var lastFrame = controller.getClass().getDeclaredField("lastFrame");
      lastFrame.setAccessible(true);
      var motion = controller.sample(lastFrame.getLong(controller));
      if (motion.age() < ENTRY_TIMES[entry]) return;
      if (entry < 3 && (motion.hacks() != 0 || motion.data() != 0))
        throw new IllegalStateException(
            "Panels appeared during lens/link acquisition at " + motion.age());
      Files.writeString(
          OUT.resolve("entry-timing.txt"),
          ENTRY_TIMES[entry]
              + ": "
              + motion.age()
              + "ms lens="
              + motion.lens()
              + " hacks="
              + motion.hacks()
              + " data="
              + motion.data()
              + "\n",
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
      entry++;
    } catch (Throwable ex) {
      error = ex.toString();
      ex.printStackTrace();
    }
  }

  static void server(java.util.function.Consumer<ServerPlayer> action) {
    var s = Minecraft.getInstance().getSingleplayerServer();
    s.execute(
        () -> {
          try {
            action.accept(s.getPlayerList().getPlayers().getFirst());
          } catch (Throwable e) {
            error = e.toString();
            e.printStackTrace();
          }
        });
  }

  static void shot(String name) throws Exception {
    Files.createDirectories(OUT);
    try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
      image.writeToFile(OUT.resolve(name + ".png"));
    }
  }

  static void mouse(String method, Class<?>[] types, Object... args) throws Exception {
    var m = Minecraft.getInstance().mouseHandler.getClass().getDeclaredMethod(method, types);
    m.setAccessible(true);
    m.invoke(Minecraft.getInstance().mouseHandler, args);
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post e) {
    var m = Minecraft.getInstance();
    if (finished) return;
    if (++age > 3000 || error != null) {
      try {
        Files.createDirectories(OUT);
        Files.writeString(OUT.resolve("failed.txt"), error == null ? "Timeout " + ticks : error);
      } catch (Exception ignored) {
      }
      finished = true;
      m.stop();
      return;
    }
    if (m.player == null || m.level == null || m.getSingleplayerServer() == null) return;
    if (!m.getSingleplayerServer()
        .getWorldPath(LevelResource.ROOT)
        .toString()
        .contains("Wayfarer Review")) {
      error = "Refusing non-review save";
      return;
    }
    try {
      if (!ready) {
        if (ticks++ == 0) {
          Files.createDirectories(OUT);
          Files.deleteIfExists(OUT.resolve("passed.txt"));
          Files.deleteIfExists(OUT.resolve("failed.txt"));
          Files.deleteIfExists(OUT.resolve("entry-timing.txt"));
          m.options.pauseOnLostFocus = false;
          m.gui.getChat().clearMessages(true);
          m.options.guiScale().set(2);
          m.options.hideGui = false;
          m.options.fov().set(70);
          m.resizeDisplay();
          m.setScreen(null);
          server(
              p -> {
                for (var i : List.copyOf(SkillAPI.getSkillsFrom(p).getLearnedSkills()))
                  SkillAPI.getSkillsFrom(p).forgetSkill(i);
                RestrictionData.learnReward(p, HeavenlyRestriction.NETRUNNER.get());
                var i =
                    SkillAPI.getSkillsFrom(p)
                        .getSkill(HeavenlyRestriction.NETRUNNER.get())
                        .orElseThrow();
                i.setMastery(0);
                TensuraStorages.getAbilityFrom(p)
                    .setAbilitySlot(0, HeavenlyRestriction.NETRUNNER.get(), 0);
                TensuraStorages.getAbilityFrom(p).markDirty();
                EnergyHelper.setBaseMaxEP(p, 20000);
                var energy = TensuraStorages.getExistenceFrom(p);
                energy.setMagicule(EnergyHelper.getMaxMagicule(p));
                energy.setAura(EnergyHelper.getMaxAura(p));
                energy.markDirty();
                p.setItemInHand(
                    net.minecraft.world.InteractionHand.MAIN_HAND,
                    net.minecraft.world.item.ItemStack.EMPTY);
                p.teleportTo(p.server.overworld(), 0.5, 250, 0.5, Set.of(), 0, 0);
                p.setYRot(0);
                p.setXRot(0);
                for (int x = -20; x <= 20; x++)
                  for (int z = -15; z <= 30; z++) {
                    p.serverLevel()
                        .setBlock(
                            new net.minecraft.core.BlockPos(x, 249, z),
                            net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(),
                            3);
                    for (int y = 250; y <= 275; y++)
                      p.serverLevel()
                          .setBlock(
                              new net.minecraft.core.BlockPos(x, y, z),
                              net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                              3);
                  }
                for (var world : p.server.getAllLevels())
                  for (var actor :
                      java.util.stream.StreamSupport.stream(
                              world.getAllEntities().spliterator(), false)
                          .toList()) {
                    if (actor instanceof LivingEntity
                        && actor.hasCustomName()
                        && actor.getCustomName().getString().equals("Neural Link Target"))
                      actor.discard();
                    if (actor instanceof net.minecraft.world.entity.item.ItemEntity
                        && actor.position().distanceToSqr(p.position()) < 64 * 64) actor.discard();
                  }
                var t = new net.minecraft.world.entity.monster.Husk(EntityType.HUSK, p.level());
                t.setPos(.5, 250, 8.5);
                t.setNoAi(true);
                t.setInvulnerable(false);
                t.setCustomName(net.minecraft.network.chat.Component.literal("Neural Link Target"));
                t.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);
                t.setHealth(2000);
                t.getAttribute(
                        io.github.manasmods.tensura.registry.attribute.TensuraAttributes
                            .MAX_SPIRITUAL_HEALTH)
                    .setBaseValue(5000);
                TensuraStorages.getExistenceFrom(t).setSpiritualHealth(5000);
                RestrictionData.learnReward(
                    t,
                    io.github.manasmods.tensura.registry.skill.ResistanceSkills
                        .SPIRITUAL_ATTACK_RESISTANCE
                        .get());
                var resistance =
                    SkillAPI.getSkillsFrom(t)
                        .getSkill(
                            io.github.manasmods.tensura.registry.skill.ResistanceSkills
                                .SPIRITUAL_ATTACK_RESISTANCE
                                .get())
                        .orElseThrow();
                resistance.setMastery(resistance.getMaxMastery());
                resistance.setToggled(true);
                resistance.onToggleOn(t);
                t.addEffect(
                    new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 1200));
                t.addEffect(
                    new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 1200));
                p.serverLevel().addFreshEntity(t);
                target = t;
                p.serverLevel().setDayTime(6000);
                p.serverLevel().setWeatherParameters(60000, 0, false, false);
                ((StorageHolder) p).manasCore$sync();
                ready = true;
              });
        }
        return;
      }
      ticks++;
      if (ticks == 100) {
        Files.writeString(
            OUT.resolve("scan-open-ms.txt"), Long.toString(System.currentTimeMillis()));
        m.player.setYRot(0);
        m.player.setXRot(0);
        TensuraKeybinds.ACTIVATE_SLOT_1.setDown(true);
        TensuraKeybinds.onAbilityPressed(0);
      }
      if (ticks == 120) {
        if (!NetrunnerClient.active())
          throw new IllegalStateException("Native key did not open scanner");
        shot("scanner-native");
      }
      if (ticks == 130)
        mouse(
            "onPress",
            new Class[] {long.class, int.class, int.class, int.class},
            m.getWindow().getWindow(),
            0,
            1,
            0);
      if (ticks == 131)
        mouse(
            "onPress",
            new Class[] {long.class, int.class, int.class, int.class},
            m.getWindow().getWindow(),
            0,
            0,
            0);
      if (ticks == 135)
        mouse(
            "onScroll",
            new Class[] {long.class, double.class, double.class},
            m.getWindow().getWindow(),
            0d,
            -1d);
      if (ticks == 140)
        mouse(
            "onPress",
            new Class[] {long.class, int.class, int.class, int.class},
            m.getWindow().getWindow(),
            0,
            1,
            0);
      if (ticks == 141)
        mouse(
            "onPress",
            new Class[] {long.class, int.class, int.class, int.class},
            m.getWindow().getWindow(),
            0,
            0,
            0);
      if (ticks == 145) {
        shot("scanner-queue");
        server(
            p -> {
              var q = Netrunner.state(p).getList("queue", 10);
              System.out.println(
                  "NETRUNNER REVIEW COOLDOWNS " + Netrunner.state(p).getList("hacks", 10));
              if (q.size() != 2)
                throw new IllegalStateException(
                    "Native click/scroll did not queue two hacks: " + q.size());
            });
      }
      if (ticks == 146) server(p -> target.setPos(2.3, 250, 9.3));
      if (ticks == 155) {
        TensuraKeybinds.ACTIVATE_SLOT_1.setDown(false);
        TensuraKeybinds.onAbilityReleased(0);
      }
      if (ticks == 170) shot("uploads-after-release");
      if (ticks == 235) {
        server(
            p -> {
              target.setPos(.5, 250, 8.5);
              if (target.getHealth() >= 2000)
                throw new IllegalStateException("Short Circuit did not damage live target");
            });
        TensuraKeybinds.ACTIVATE_SLOT_1.setDown(true);
        TensuraKeybinds.onAbilityPressed(0);
      }
      if (ticks == 255) {
        m.options.guiScale().set(3);
        m.resizeDisplay();
      }
      if (ticks == 265) shot("scanner-compact");
      if (ticks == 275) {
        m.options.guiScale().set(1);
        m.resizeDisplay();
      }
      if (ticks == 285) shot("scanner-scale-one");
      if (ticks == 295) org.lwjgl.glfw.GLFW.glfwSetWindowSize(m.getWindow().getWindow(), 960, 720);
      if (ticks == 310) shot("scanner-four-three");
      if (ticks == 320) org.lwjgl.glfw.GLFW.glfwSetWindowSize(m.getWindow().getWindow(), 1920, 720);
      if (ticks == 335) shot("scanner-ultrawide");
      if (ticks == 345) org.lwjgl.glfw.GLFW.glfwSetWindowSize(m.getWindow().getWindow(), 1280, 720);
      if (ticks == 355) {
        if (entry != ENTRY_TIMES.length)
          throw new IllegalStateException("Missed native acquisition frames: " + entry);
        TensuraKeybinds.ACTIVATE_SLOT_1.setDown(false);
        TensuraKeybinds.onAbilityReleased(0);
        m.options.guiScale().set(2);
        m.resizeDisplay();
        Files.writeString(
            OUT.resolve("passed.txt"),
            "Native skill key, scanner, MouseHandler wheel/click, two queued uploads, release"
                + " continuation, target damage, GUI scales 1/2/3, six staged activation"
                + " frames.\n");
        finished = true;
        m.stop();
      }
    } catch (Throwable ex) {
      error = ex.toString();
      ex.printStackTrace();
    }
  }
}
