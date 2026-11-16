package dev.heavenlyrestriction.netrunner;

import dev.heavenlyrestriction.HeavenlyRestriction;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class NetrunnerNetwork {
  public static Consumer<CompoundTag> receiver = t -> {};
  public static Consumer<Impact> impactReceiver = t -> {};

  public record Request(UUID session, int action, int target, int hack)
      implements CustomPacketPayload {
    public static final Type<Request> TYPE =
        new Type<>(HeavenlyRestriction.id("netrunner_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeUUID(p.session);
              b.writeByte(p.action);
              b.writeInt(p.target);
              b.writeByte(p.hack);
            },
            b ->
                new Request(b.readUUID(), b.readUnsignedByte(), b.readInt(), b.readUnsignedByte()));

    public Type<Request> type() {
      return TYPE;
    }
  }

  public record State(CompoundTag data) implements CustomPacketPayload {
    public static final Type<State> TYPE = new Type<>(HeavenlyRestriction.id("netrunner_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC =
        StreamCodec.of((b, p) -> b.writeNbt(p.data), b -> new State(b.readNbt()));

    public Type<State> type() {
      return TYPE;
    }
  }

  public record Impact(int target, int hack, boolean applied) implements CustomPacketPayload {
    public static final Type<Impact> TYPE = new Type<>(HeavenlyRestriction.id("netrunner_impact"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Impact> CODEC =
        StreamCodec.of(
            (b, p) -> {
              b.writeInt(p.target);
              b.writeByte(p.hack);
              b.writeBoolean(p.applied);
            },
            b -> new Impact(b.readInt(), b.readUnsignedByte(), b.readBoolean()));

    public Type<Impact> type() {
      return TYPE;
    }
  }

  public static void register(RegisterPayloadHandlersEvent e) {
    var r = e.registrar("1");
    r.playToServer(
        Request.TYPE,
        Request.CODEC,
        (p, c) ->
            c.enqueueWork(
                () -> {
                  if (c.player() instanceof ServerPlayer player) Netrunner.request(player, p);
                }));
    r.playToClient(
        State.TYPE,
        State.CODEC,
        (p, c) ->
            c.enqueueWork(
                () -> {
                  if (p.data != null) receiver.accept(p.data);
                }));
    r.playToClient(
        Impact.TYPE, Impact.CODEC, (p, c) -> c.enqueueWork(() -> impactReceiver.accept(p)));
  }

  public static void send(ServerPlayer player, CompoundTag state) {
    PacketDistributor.sendToPlayer(player, new State(state));
  }

  private NetrunnerNetwork() {}
}
