package dev.heavenlyrestriction.netrunner;

import dev.heavenlyrestriction.*;
import io.github.manasmods.manascore.skill.api.*;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.damage.*;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.skill.ResistanceSkills;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.util.EnergyHelper;
import java.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Server-authoritative held scanner. Uploads retain identity, never a stale aim position. */
public final class Netrunner {
  private static final Map<UUID, Session> SESSIONS = new HashMap<>();

  public static final class Session {
    final UUID token = UUID.randomUUID();
    final ServerPlayer owner;
    final int slot;
    final ArrayDeque<Upload> queue = new ArrayDeque<>();
    final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
    boolean scanning = true;
    long heldAt, lastRequest = -1, lastFocus = -2, sequence;
    int focus = -1;
    String message = "";

    Session(ServerPlayer p, int slot) {
      owner = p;
      this.slot = slot;
      dimension = p.level().dimension();
      heldAt = p.level().getGameTime();
    }
  }

  private static final class Upload {
    final UUID target;
    final int targetId;
    final Quickhack hack;
    final long id;
    final int total;
    final double paidMagicules;
    int elapsed;

    Upload(LivingEntity t, Quickhack h, long id, boolean master, double paid) {
      paidMagicules = paid;
      target = t.getUUID();
      targetId = t.getId();
      hack = h;
      this.id = id;
      total = h.upload(master);
    }
  }

  public static void channels(ManasSkillInstance i) {
    var cds = new ArrayList<>(i.getCooldownList());
    while (cds.size() < 6) cds.add(0);
    i.setCoolDownList(cds);
  }

  public static Optional<ManasSkillInstance> owned(LivingEntity p) {
    return SkillAPI.getSkillsFrom(p)
        .getSkill(HeavenlyRestriction.NETRUNNER.get())
        .filter(
            i ->
                i.getMastery() >= 0
                    && i.canInteractSkill(p)
                    && i.canActivateSkill(p, 0)
                    && !Nullification.sealed(p));
  }

  public static void open(ServerPlayer p, ManasSkillInstance i, int slot) {
    if (slot < 0
        || slot >= 3
        || !p.isAlive()
        || p.isSpectator()
        || !i.canInteractSkill(p)
        || !TensuraStorages.getAbilityFrom(p).isAbilityInActivePreset(slot, i.getSkill(), 0))
      return;
    channels(i);
    var old = SESSIONS.get(p.getUUID());
    if (old != null && old.dimension == p.level().dimension()) {
      old.scanning = true;
      old.heldAt = p.level().getGameTime();
      send(old);
      return;
    }
    var s = new Session(p, slot);
    SESSIONS.put(p.getUUID(), s);
    send(s);
  }

  public static boolean held(ServerPlayer p, ManasSkillInstance i) {
    var s = SESSIONS.get(p.getUUID());
    if (s == null || !s.scanning) return false;
    s.heldAt = p.level().getGameTime();
    return true;
  }

  public static void close(ServerPlayer p) {
    var s = SESSIONS.get(p.getUUID());
    if (s != null) {
      s.scanning = false;
      send(s);
    }
  }

  public static void clear(LivingEntity p) {
    var s = SESSIONS.remove(p.getUUID());
    if (s != null) {
      s.scanning = false;
      s.queue.clear();
      send(s);
    }
  }

  public static void clearAll() {
    SESSIONS.clear();
  }

  public static boolean validTarget(ServerPlayer p, LivingEntity t, double range, boolean acquire) {
    if (!RestrictionData.canHarm(p, t) || t.isSpectator() || p.distanceToSqr(t) > range * range)
      return false;
    if (!acquire) return true;
    if (!p.hasLineOfSight(t)) return false;
    Vec3 eye = p.getEyePosition();
    // Intersect the whole body, including the head of a tall mob; the small expansion
    // covers motion during one round trip without accepting arbitrary packet targets.
    var box = t.getBoundingBox().inflate(.35 + Math.min(range, p.distanceTo(t)) * .12);
    return box.clip(eye, eye.add(p.getLookAngle().scale(range))).isPresent();
  }

  public static void request(ServerPlayer p, NetrunnerNetwork.Request r) {
    var s = SESSIONS.get(p.getUUID());
    if (s == null || !s.scanning || !s.token.equals(r.session()) || owned(p).isEmpty()) return;
    long now = p.level().getGameTime();
    if (r.action() == 0) {
      if (now - s.lastFocus < 2) return;
      s.lastFocus = now;
    }
    if (r.action() != 0) {
      if (s.lastRequest == now) return;
      s.lastRequest = now;
    }
    if (r.action() == 2) {
      if (!s.queue.isEmpty()) s.queue.removeLast();
      s.message = "Upload cancelled";
      send(s);
      return;
    }
    if (r.action() != 0 && r.action() != 1) return;
    var target = p.level().getEntity(r.target());
    if (!(target instanceof LivingEntity t)
        || !validTarget(p, t, NetrunnerConfig.RANGE.get(), true)) {
      s.focus = -1;
      return;
    }
    s.focus = t.getId();
    if (r.action() == 0) {
      send(s);
      return;
    }
    Quickhack h = Quickhack.byId(r.hack());
    if (h == null) return;
    var i = owned(p).orElseThrow();
    channels(i);
    if (s.queue.size() >= 3) {
      s.message = "Queue full";
      send(s);
      return;
    }
    if (i.onCoolDown(h.channel())) {
      s.message = "Quickhack cooling down";
      send(s);
      return;
    }
    double cost = NetrunnerConfig.COSTS[h.ordinal()].get();
    double before = TensuraStorages.getExistenceFrom(p).getMagicule();
    if (EnergyHelper.isOutOfEnergy(p, 0, cost)) {
      s.message = "Insufficient magicules";
      send(s);
      return;
    }
    i.setCoolDown(h.cooldownSeconds, h.channel());
    i.markDirty();
    s.queue.add(
        new Upload(
            t,
            h,
            ++s.sequence,
            i.isMastered(p),
            Math.max(0, before - TensuraStorages.getExistenceFrom(p).getMagicule())));
    s.message = "";
    send(s);
  }

  public static void tick(ServerTickEvent.Post event) {
    for (var s : new ArrayList<>(SESSIONS.values())) {
      var p = s.owner;
      var own = owned(p);
      if (p.hasDisconnected()
          || !p.isAlive()
          || p.isSpectator()
          || own.isEmpty()
          || s.dimension != p.level().dimension()
          || !TensuraStorages.getAbilityFrom(p)
              .isAbilityInActivePreset(s.slot, HeavenlyRestriction.NETRUNNER.get(), 0)) {
        clear(p);
        continue;
      }
      long now = p.level().getGameTime();
      if (s.scanning && now - s.heldAt > 5) {
        s.scanning = false;
        send(s);
      }
      if (!s.queue.isEmpty()) {
        var u = s.queue.peek();
        var entity = p.serverLevel().getEntity(u.target);
        if (!(entity instanceof LivingEntity t)
            || !validTarget(p, t, NetrunnerConfig.RANGE.get() * 1.5, false)) {
          s.queue.remove();
          s.message = "Connection lost";
          send(s);
        } else if (++u.elapsed >= u.total) {
          boolean applied = apply(p, own.orElseThrow(), t, u.hack, u.paidMagicules);
          s.queue.remove();
          own.orElseThrow().addMasteryPoint(p);
          net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
              p, new NetrunnerNetwork.Impact(t.getId(), u.hack.ordinal(), applied));
          s.message = applied ? "Upload complete" : "Target resisted";
          send(s);
        }
      }
      if (now % 4 == 0) send(s);
      if (!s.scanning && s.queue.isEmpty()) SESSIONS.remove(p.getUUID());
    }
  }

  public static boolean apply(ServerPlayer p, ManasSkillInstance i, LivingEntity t, Quickhack h) {
    return apply(p, i, t, h, NetrunnerConfig.COSTS[h.ordinal()].get());
  }

  private static boolean apply(
      ServerPlayer p, ManasSkillInstance i, LivingEntity t, Quickhack h, double paid) {
    var skill = HeavenlyRestriction.NETRUNNER.get();
    boolean result;
    switch (h) {
      case SHORT_CIRCUIT -> {
        var source =
            ((TensuraDamageSource) skill.createSource(i, p, TensuraDamageTypes.LIGHTNING, 0))
                .tensura$setMagiculeCost(paid);
        independent(source);
        boolean casting =
            SkillAPI.getSkillsFrom(t).getHeldSkills().stream()
                .anyMatch(
                    held ->
                        held.getSkill() instanceof io.github.manasmods.tensura.ability.magic.Magic);
        result =
            t.hurt(
                source,
                (float)
                    (NetrunnerConfig.SHORT_DAMAGE.get()
                        * (casting ? NetrunnerConfig.INTERRUPT_BONUS.get() : 1)));
      }
      case SYNAPSE_BURNOUT -> {
        if (SkillUtils.isSkillToggled(t, ResistanceSkills.SPIRITUAL_ATTACK_NULLIFICATION.get()))
          return false;
        double empty =
            1
                - Math.clamp(
                    TensuraStorages.getExistenceFrom(t).getMagicule()
                        / Math.max(1, EnergyHelper.getMaxMagicule(t)),
                    0,
                    1);
        float damage =
            (float)
                (NetrunnerConfig.SYNAPSE_BASE.get()
                    + NetrunnerConfig.SYNAPSE_DEPLETION.get() * empty);
        if (SkillUtils.isSkillToggled(t, ResistanceSkills.SPIRITUAL_ATTACK_RESISTANCE.get()))
          damage *= .5f;
        var source =
            ((TensuraDamageSource) skill.createSource(i, p, TensuraDamageTypes.MIND_CRUSH, 0))
                .tensura$setMagiculeCost(paid);
        independent(source);
        result = TensuraDamageHelper.directSpiritualHurt(t, p, source, damage, 0);
      }
      case REBOOT_OPTICS -> {
        skill.addEffect(t, i, 0, p, NetrunnerEffects.OPTICS, NetrunnerConfig.OPTICS_TICKS.get(), 0);
        result = t.hasEffect(NetrunnerEffects.OPTICS);
      }
      case CRIPPLE_MOVEMENT -> {
        skill.addEffect(
            t, i, 0, p, NetrunnerEffects.CRIPPLE, NetrunnerConfig.CRIPPLE_TICKS.get(), 0);
        result = t.hasEffect(NetrunnerEffects.CRIPPLE);
      }
      case SUICIDE -> {
        var source =
            ((TensuraDamageSource) skill.createSource(i, p, TensuraDamageTypes.MIND_CRUSH, 0))
                .tensura$setMagiculeCost(paid);
        independent(source);
        double factor =
            t instanceof Player
                    || t.getType()
                        .is(
                            net.minecraft.tags.TagKey.create(
                                net.minecraft.core.registries.Registries.ENTITY_TYPE,
                                net.minecraft.resources.ResourceLocation.parse("c:bosses")))
                    || t instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
                    || t instanceof net.minecraft.world.entity.boss.wither.WitherBoss
                ? NetrunnerConfig.SUICIDE_ELITE.get()
                : NetrunnerConfig.SUICIDE_MULTIPLIER.get();
        // Native hurt pipeline: resistances, barriers and fatal-hit regeneration still get their
        // say.
        result = t.hurt(source, (float) (t.getMaxHealth() * factor));
        t.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
      }
      default -> throw new IllegalStateException();
    }
    if (result) {
      var particles =
          h == Quickhack.SHORT_CIRCUIT
              ? ParticleTypes.ELECTRIC_SPARK
              : h == Quickhack.CRIPPLE_MOVEMENT
                  ? ParticleTypes.END_ROD
                  : ParticleTypes.ENCHANTED_HIT;
      p.serverLevel()
          .sendParticles(
              particles,
              t.getX(),
              t.getY() + t.getBbHeight() * .65,
              t.getZ(),
              24,
              t.getBbWidth() * .6,
              t.getBbHeight() * .35,
              t.getBbWidth() * .6,
              .04);
      p.serverLevel()
          .playSound(
              null,
              t.blockPosition(),
              h == Quickhack.SHORT_CIRCUIT
                  ? SoundEvents.TRIDENT_THUNDER.value()
                  : SoundEvents.BEACON_DEACTIVATE,
              SoundSource.PLAYERS,
              .6f,
              1.6f);
    }
    return result;
  }

  private static void independent(net.minecraft.world.damagesource.DamageSource s) {
    if (s instanceof IndependentHitSource hit) hit.restriction$independentHit();
  }

  private static void send(Session s) {
    NetrunnerNetwork.send(s.owner, snapshot(s));
  }

  public static CompoundTag state(ServerPlayer p) {
    var s = SESSIONS.get(p.getUUID());
    return s == null ? new CompoundTag() : snapshot(s);
  }

  private static CompoundTag snapshot(Session s) {
    var p = s.owner;
    var tag = new CompoundTag();
    tag.putUUID("session", s.token);
    tag.putBoolean("scanning", s.scanning);
    tag.putInt("slot", s.slot);
    tag.putString("message", s.message);
    tag.putDouble("mp", TensuraStorages.getExistenceFrom(p).getMagicule());
    tag.putDouble("maxMp", EnergyHelper.getMaxMagicule(p));
    tag.putDouble("range", NetrunnerConfig.RANGE.get());
    var own = owned(p);
    var rows = new ListTag();
    for (var h : Quickhack.values()) {
      var row = new CompoundTag();
      row.putDouble("cost", NetrunnerConfig.COSTS[h.ordinal()].get());
      row.putInt("cooldown", own.map(i -> i.getCoolDown(h.channel())).orElse(0));
      rows.add(row);
    }
    tag.put("hacks", rows);
    var queue = new ListTag();
    for (var u : s.queue) {
      var q = new CompoundTag();
      q.putLong("id", u.id);
      q.putInt("target", u.targetId);
      q.putInt("hack", u.hack.ordinal());
      q.putInt("elapsed", u.elapsed);
      q.putInt("total", u.total);
      queue.add(q);
    }
    tag.put("queue", queue);
    if (p.level().getEntity(s.focus) instanceof LivingEntity t
        && validTarget(p, t, NetrunnerConfig.RANGE.get() * 1.5, false)) {
      var data = new CompoundTag();
      var energy = TensuraStorages.getExistenceFrom(t);
      data.putInt("id", t.getId());
      data.putString(
          "name",
          t.getName().getString().substring(0, Math.min(48, t.getName().getString().length())));
      data.putFloat("hp", t.getHealth());
      data.putFloat("maxHp", t.getMaxHealth());
      data.putDouble("shp", energy.getSpiritualHealth());
      data.putDouble("maxShp", t.getAttributeValue(TensuraAttributes.MAX_SPIRITUAL_HEALTH));
      data.putDouble("mp", energy.getMagicule());
      data.putDouble("maxMp", EnergyHelper.getMaxMagicule(t));
      data.putDouble("aura", energy.getAura());
      data.putDouble("maxAura", EnergyHelper.getMaxAura(t));
      data.putDouble("ep", EnergyHelper.getMaxEP(t));
      data.putString(
          "race",
          io.github.manasmods.manascore.race.api.RaceAPI.getRaceFrom(t)
              .getRace()
              .map(r -> r.getRace().getName().getString())
              .orElse(t.getType().getDescription().getString()));
      for (var held : SkillAPI.getSkillsFrom(t).getHeldSkills())
        if (held.getSkill() instanceof io.github.manasmods.tensura.ability.magic.Magic magic) {
          data.putString("cast", magic.getName().getString());
          break;
        }
      var effects = new ListTag();
      t.getActiveEffects().stream()
          .limit(5)
          .forEach(
              e ->
                  effects.add(
                      StringTag.valueOf(e.getEffect().value().getDisplayName().getString())));
      data.put("effects", effects);
      tag.put("target", data);
    }
    return tag;
  }

  // Native cooldown timers visit selectable modes only; these five private channels need the same
  // once-per-second update event, including while the scanner is closed or a different preset is
  // active.
  public static void cooldowns(net.neoforged.neoforge.event.tick.EntityTickEvent.Post e) {
    if (!(e.getEntity() instanceof ServerPlayer p) || p.server.getTickCount() % 20 != 0) return;
    SkillAPI.getSkillsFrom(p)
        .getSkill(HeavenlyRestriction.NETRUNNER.get())
        .ifPresent(
            i -> {
              channels(i);
              for (var h : Quickhack.values()) {
                int old = i.getCoolDown(h.channel());
                if (old <= 0) continue;
                var next =
                    io.github.manasmods.manascore.network.api.util.Changeable.of(
                        Math.max(0, old - 1));
                if (!SkillEvents.SKILL_UPDATE_COOLDOWN
                    .invoker()
                    .cooldown(i, p, h.channel(), old, next)
                    .isFalse()) i.setCoolDown(next.get(), h.channel());
              }
            });
  }

  private Netrunner() {}
}
