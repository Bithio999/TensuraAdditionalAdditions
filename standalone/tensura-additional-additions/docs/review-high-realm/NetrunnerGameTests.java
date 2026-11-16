package dev.heavenlyrestriction;

import static dev.heavenlyrestriction.TestFixtures.*;

import dev.heavenlyrestriction.netrunner.*;
import io.github.manasmods.manascore.skill.api.*;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.skill.ResistanceSkills;
import io.github.manasmods.tensura.storage.TensuraStorages;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("netrunnerreview")
@PrefixGameTestTemplate(false)
public class NetrunnerGameTests {
  static ManasSkillInstance scanner(ServerPlayer p) {
    var i = SplitMindGameTests.learn(p, HeavenlyRestriction.NETRUNNER.get(), false);
    TensuraStorages.getAbilityFrom(p).setAbilitySlot(0, HeavenlyRestriction.NETRUNNER.get(), 0);
    Netrunner.open(p, i, 0);
    return i;
  }

  static void request(ServerPlayer p, int action, int target, int hack) {
    var state = Netrunner.state(p);
    if (!state.hasUUID("session"))
      throw new net.minecraft.gametest.framework.GameTestAssertException(
          "Missing live scanner session");
    Netrunner.request(
        p, new NetrunnerNetwork.Request(state.getUUID("session"), action, target, hack));
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void oneHeldModePrivateCooldownsPersist(GameTestHelper h) {
    var p = player(h);
    var i = scanner(p);
    h.assertTrue(i.getModes() == 1, "Extra selectable modes leaked");
    i.setCoolDown(8, 1);
    i.setCoolDown(60, 5);
    var copy = HeavenlyRestriction.NETRUNNER.get().createDefaultInstance();
    copy.deserialize(i.toNBT());
    h.assertTrue(
        copy.getCoolDown(1) == 8 && copy.getCoolDown(5) == 60, "Cooldowns lost on serialization");
    h.assertTrue(i.getCoolDown(0) == 0, "Scanner blocked by hack cooldown");
    Netrunner.clear(p);
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void targetValidationAndForgery(GameTestHelper h) {
    var p = player(h);
    scanner(p);
    var t = target(h, p.position().add(8, 0, 0));
    afterTracking(
        h,
        List.of(t),
        () -> {
          Netrunner.open(
              p,
              SkillAPI.getSkillsFrom(p).getSkill(HeavenlyRestriction.NETRUNNER.get()).orElseThrow(),
              0);
          double mp = TensuraStorages.getExistenceFrom(p).getMagicule();
          Netrunner.request(p, new NetrunnerNetwork.Request(UUID.randomUUID(), 1, t.getId(), 0));
          h.assertTrue(Netrunner.state(p).getList("queue", 10).isEmpty(), "Forged nonce accepted");
          h.assertTrue(!Netrunner.validTarget(p, p, 32, true), "Self target");
          t.setPos(p.position().add(-8, 0, 0));
          h.assertTrue(!Netrunner.validTarget(p, t, 32, true), "Target behind accepted");
          h.assertTrue(
              TensuraStorages.getExistenceFrom(p).getMagicule() == mp, "Rejected packet charged");
          Netrunner.clear(p);
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 140)
  public static void queuePaysOnceAndCompletesAfterRelease(GameTestHelper h) {
    var p = player(h);
    var i = scanner(p);
    var t = target(h, p.position().add(8, 0, 0));
    afterTracking(
        h,
        List.of(t),
        () -> {
          Netrunner.open(
              p,
              SkillAPI.getSkillsFrom(p).getSkill(HeavenlyRestriction.NETRUNNER.get()).orElseThrow(),
              0);
          double before = TensuraStorages.getExistenceFrom(p).getMagicule();
          request(p, 1, t.getId(), 0);
          h.assertTrue(Netrunner.state(p).getList("queue", 10).size() == 1, "No queued hack");
          h.assertTrue(
              Math.abs(before - TensuraStorages.getExistenceFrom(p).getMagicule() - 200) < .01,
              "Incorrect payment");
          h.assertTrue(i.getCoolDown(1) == 8, "Cooldown not eight seconds");
          double masteryBefore = i.getMastery();
          Netrunner.close(p);
          t.setPos(p.position().add(12, 0, 2));
          h.startSequence()
              .thenWaitUntil(
                  () -> {
                    // Native evasion may resist the hit. Paid completion must still train the
                    // skill;
                    // a lost target or cancelled session must not.
                    h.assertTrue(
                        i.getMastery() > masteryBefore,
                        "Moving upload not completed: state="
                            + Netrunner.state(p)
                            + " alive="
                            + p.isAlive()
                            + " usable="
                            + Netrunner.owned(p).isPresent());
                    h.assertTrue(
                        Netrunner.state(p).getList("queue", 10).isEmpty(), "Upload still pending");
                  })
              .thenSucceed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void unaffordableHackDoesNotQueueOrCooldown(GameTestHelper h) {
    var p = player(h);
    var i = scanner(p);
    var t = target(h, p.position().add(8, 0, 0));
    afterTracking(
        h,
        List.of(t),
        () -> {
          Netrunner.open(
              p,
              SkillAPI.getSkillsFrom(p).getSkill(HeavenlyRestriction.NETRUNNER.get()).orElseThrow(),
              0);
          TensuraStorages.getExistenceFrom(p).setMagicule(0);
          request(p, 1, t.getId(), 4);
          h.assertTrue(
              Netrunner.state(p).getList("queue", 10).isEmpty(), "Unpaid execution queued");
          h.assertTrue(i.getCoolDown(5) == 0, "Unpaid cooldown");
          Netrunner.clear(p);
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void controlHacksAreDistinctAndDoNotReduceAttackSpeed(GameTestHelper h) {
    var p = player(h);
    var i = scanner(p);
    var t = target(h, p.position().add(8, 0, 0));
    afterTracking(
        h,
        List.of(t),
        () -> {
          Netrunner.open(
              p,
              SkillAPI.getSkillsFrom(p).getSkill(HeavenlyRestriction.NETRUNNER.get()).orElseThrow(),
              0);
          double speed = t.getAttributeValue(Attributes.ATTACK_SPEED);
          h.assertTrue(Netrunner.apply(p, i, t, Quickhack.REBOOT_OPTICS), "Optics not applied");
          h.assertTrue(t.hasEffect(NetrunnerEffects.OPTICS), "Missing optics");
          h.assertTrue(Netrunner.apply(p, i, t, Quickhack.CRIPPLE_MOVEMENT), "Cripple not applied");
          h.assertTrue(t.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0, "Cripple did not root");
          h.assertTrue(
              t.getAttributeValue(Attributes.ATTACK_SPEED) == speed, "Cripple blocked attacks");
          Netrunner.clear(p);
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void synapseRespectsSpiritualNullification(GameTestHelper h) {
    var p = player(h);
    var i = scanner(p);
    var t = target(h, p.position().add(8, 0, 0));
    afterTracking(
        h,
        List.of(t),
        () -> {
          Netrunner.open(
              p,
              SkillAPI.getSkillsFrom(p).getSkill(HeavenlyRestriction.NETRUNNER.get()).orElseThrow(),
              0);
          var resist =
              SplitMindGameTests.learn(
                  t, ResistanceSkills.SPIRITUAL_ATTACK_NULLIFICATION.get(), true);
          resist.setToggled(true);
          resist.onToggleOn(t);
          double before = TensuraStorages.getExistenceFrom(t).getSpiritualHealth();
          h.assertTrue(
              !Netrunner.apply(p, i, t, Quickhack.SYNAPSE_BURNOUT),
              "Bypassed spiritual nullification");
          h.assertTrue(
              before == TensuraStorages.getExistenceFrom(t).getSpiritualHealth(),
              "SHP lost through nullification");
          Netrunner.clear(p);
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void masteredUploadsFasterAndForgetClearsQueue(GameTestHelper h) {
    h.assertTrue(
        Quickhack.SUICIDE.upload(true) == 80 && Quickhack.SUICIDE.upload(false) == 100,
        "Mastery upload ratio");
    var p = player(h);
    var i = scanner(p);
    var t = target(h, p.position().add(8, 0, 0));
    afterTracking(
        h,
        List.of(t),
        () -> {
          Netrunner.open(
              p,
              SkillAPI.getSkillsFrom(p).getSkill(HeavenlyRestriction.NETRUNNER.get()).orElseThrow(),
              0);
          request(p, 1, t.getId(), 0);
          SkillAPI.getSkillsFrom(p).forgetSkill(i);
          h.assertTrue(Netrunner.state(p).isEmpty(), "Forgotten skill retains session");
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void queueBoundCancellationAndClosedScanner(GameTestHelper h) {
    var p = player(h);
    scanner(p);
    var t = target(h, p.position().add(8, 0, 0));
    afterTracking(
        h,
        List.of(t),
        () -> {
          var i =
              SkillAPI.getSkillsFrom(p).getSkill(HeavenlyRestriction.NETRUNNER.get()).orElseThrow();
          Netrunner.open(p, i, 0);
          request(p, 1, t.getId(), 0);
          h.runAfterDelay(1, () -> request(p, 1, t.getId(), 1));
          h.runAfterDelay(2, () -> request(p, 1, t.getId(), 2));
          h.runAfterDelay(
              3,
              () -> {
                double before = TensuraStorages.getExistenceFrom(p).getMagicule();
                request(p, 1, t.getId(), 3);
                h.assertTrue(
                    Netrunner.state(p).getList("queue", 10).size() == 3, "Queue exceeds three");
                h.assertTrue(
                    TensuraStorages.getExistenceFrom(p).getMagicule() == before,
                    "Full queue charged");
              });
          h.runAfterDelay(
              4,
              () -> {
                double before = TensuraStorages.getExistenceFrom(p).getMagicule();
                request(p, 2, -1, 0);
                h.assertTrue(
                    Netrunner.state(p).getList("queue", 10).size() == 2,
                    "Cancel removed wrong entry count");
                h.assertTrue(
                    TensuraStorages.getExistenceFrom(p).getMagicule() == before,
                    "Cancellation created refund");
                Netrunner.close(p);
              });
          h.runAfterDelay(
              5,
              () -> {
                request(p, 1, t.getId(), 4);
                h.assertTrue(
                    Netrunner.state(p).getList("queue", 10).size() == 2,
                    "Closed scanner accepted cast");
                Netrunner.clear(p);
                h.succeed();
              });
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void chainedAndMetisRespectManualMagiculeSkill(GameTestHelper h) {
    var p = player(h);
    var i = scanner(p);
    h.assertTrue(
        ChainedReincarnation.hasMagiculeCost(i.getSkill(), p),
        "Inline magicule cost missed by Chained");
    h.assertTrue(
        !dev.heavenlyrestriction.metis.MetisAbilities.eligible(i),
        "Held UI was assignable to Metis");
    Netrunner.clear(p);
    Netrunner.open(p, i, -1);
    h.assertTrue(Netrunner.state(p).isEmpty(), "Negative slot accepted");
    Netrunner.open(p, i, 3);
    h.assertTrue(Netrunner.state(p).isEmpty(), "Invalid slot accepted");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void sealInterruptsPendingUploads(GameTestHelper h) {
    var p = player(h);
    scanner(p);
    var t = target(h, p.position().add(8, 0, 0));
    afterTracking(
        h,
        List.of(t),
        () -> {
          Netrunner.open(
              p,
              SkillAPI.getSkillsFrom(p).getSkill(HeavenlyRestriction.NETRUNNER.get()).orElseThrow(),
              0);
          request(p, 1, t.getId(), 0);
          p.addEffect(
              new net.minecraft.world.effect.MobEffectInstance(HeavenlyArsenal.SKILL_SEAL, 40));
          h.runAfterDelay(
              3,
              () -> {
                h.assertTrue(Netrunner.state(p).isEmpty(), "Sealed caster retained upload");
                p.removeEffect(HeavenlyArsenal.SKILL_SEAL);
                h.succeed();
              });
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void suicideUsesNormalFatalDamageOnOrdinaryMobs(GameTestHelper h) {
    var p = player(h);
    var i = scanner(p);
    var t = target(h, p.position().add(8, 0, 0));
    afterTracking(
        h,
        List.of(t),
        () -> {
          h.assertTrue(
              Netrunner.apply(p, i, t, Quickhack.SUICIDE),
              "Finisher damage was rejected on ordinary target");
          h.assertTrue(!t.isAlive(), "Ordinary target survived unresisted 200% HP finisher");
          Netrunner.clear(p);
          h.succeed();
        });
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void suicideDoesNotUnconditionallyExecutePlayersOrBosses(GameTestHelper h) {
    var p = duellist(h);
    var i = scanner(p);
    var other = duellist(h);
    float before = other.getHealth();
    boolean applied = false;
    boolean oldPvp = h.getLevel().getServer().isPvpAllowed();
    h.getLevel().getServer().setPvpAllowed(true);
    try {
      h.assertTrue(
          !Netrunner.apply(p, i, other, Quickhack.SUICIDE) && other.getHealth() == before,
          "Spawn invulnerability was bypassed");
      try {
        var f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
        f.setAccessible(true);
        f.setInt(other, 0);
      } catch (ReflectiveOperationException ex) {
        throw new IllegalStateException(ex);
      }
      applied = Netrunner.apply(p, i, other, Quickhack.SUICIDE);
    } finally {
      h.getLevel().getServer().setPvpAllowed(oldPvp);
    }
    h.assertTrue(
        other.isAlive() && other.getHealth() < before,
        "Player finisher: before="
            + before
            + " after="
            + other.getHealth()
            + " alive="
            + other.isAlive()
            + " applied="
            + applied
            + " shp="
            + TensuraStorages.getExistenceFrom(other).getSpiritualHealth()
            + " magicules="
            + TensuraStorages.getExistenceFrom(other).getMagicule());
    var boss =
        new net.minecraft.world.entity.boss.wither.WitherBoss(
            net.minecraft.world.entity.EntityType.WITHER, h.getLevel());
    boss.setPos(p.position().add(8, 0, 0));
    boss.setNoAi(true);
    boss.setNoGravity(true);
    boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
    boss.setHealth(1000);
    h.getLevel().addFreshEntity(boss);
    afterTracking(
        h,
        List.of(boss),
        () -> {
          Netrunner.apply(p, i, boss, Quickhack.SUICIDE);
          h.assertTrue(boss.isAlive(), "Boss was unconditionally executed");
          boss.discard();
          Netrunner.clear(p);
          h.succeed();
        });
  }

  // NeoForge's standard FakePlayer is always invulnerable and refuses PvP. Override those
  // fixture policies for the one case that deliberately exercises normal player damage.
  private static net.minecraft.server.level.ServerPlayer duellist(GameTestHelper h) {
    var anchor = player(h);
    var p =
        new net.neoforged.neoforge.common.util.FakePlayer(
            h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "NetrunnerDuel")) {
          @Override
          public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
            return false;
          }

          @Override
          public boolean canHarmPlayer(net.minecraft.world.entity.player.Player other) {
            return true;
          }
        };
    p.setPos(anchor.position());
    p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
    io.github.manasmods.manascore.race.api.RaceAPI.getRaceFrom(p)
        .setRace(io.github.manasmods.tensura.registry.race.TensuraRaces.HUMAN.get(), false);
    io.github.manasmods.tensura.util.EnergyHelper.setBaseMaxEP(p, 20000);
    TensuraStorages.getExistenceFrom(p).setMagicule(10000);
    TensuraStorages.getExistenceFrom(p).setAura(10000);
    TensuraStorages.getExistenceFrom(p)
        .setSpiritualHealth(p.getAttributeValue(TensuraAttributes.MAX_SPIRITUAL_HEALTH));
    p.setHealth(p.getMaxHealth());
    return p;
  }

  @GameTest(template = "empty", timeoutTicks = 100)
  public static void tallTargetsCanBeLinkedAtTheirHead(GameTestHelper h) {
    var p = player(h);
    scanner(p);
    var t = target(h, p.position().add(8, 0, 0));
    t.setBoundingBox(
        new net.minecraft.world.phys.AABB(
            t.getX() - .5, t.getY(), t.getZ() - .5, t.getX() + .5, t.getY() + 8, t.getZ() + .5));
    p.setXRot(-30);
    h.assertTrue(Netrunner.validTarget(p, t, 32, true), "Tall target's head was rejected");
    p.setYRot(90);
    h.assertTrue(!Netrunner.validTarget(p, t, 32, true), "Target behind player was accepted");
    Netrunner.clear(p);
    h.succeed();
  }
}
