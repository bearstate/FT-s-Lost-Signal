package com.jeladastudios.lostsignal.net;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.rescue.Order;
import com.jeladastudios.lostsignal.rescue.Rescue;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** The mod's own messages between client and server: the keeper's words from the rescue panel. */
public final class Net {

    private Net() {}

    /** Raised whenever a message's shape changes, so a client of an older version is told so at the door. */
    private static final String VERSION = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(LostSignal.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        // Each only the way it is meant to go: a client cannot send what the server sends, nor the other way.
        var toServer = java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER);
        var toClient = java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
        CHANNEL.registerMessage(0, Say.class, Say::write, Say::read, Say::handle, toServer);
        CHANNEL.registerMessage(1, Lie.class, Lie::write, Lie::read, Lie::handle, toClient);
        CHANNEL.registerMessage(2, Reading.class, Reading::write, Reading::read, Reading::handle, toClient);
        CHANNEL.registerMessage(3, Talk.class, Talk::write, Talk::read, Talk::handle, toClient);
        CHANNEL.registerMessage(4, Ask.class, Ask::write, Ask::read, Ask::handle, toServer);
        CHANNEL.registerMessage(5, Panel.class, Panel::write, Panel::read, Panel::handle, toServer);
        CHANNEL.registerMessage(6, Neighbours.class, Neighbours::write, Neighbours::read, Neighbours::handle, toClient);
        CHANNEL.registerMessage(7, Call.class, Call::write, Call::read, Call::handle, toServer);
    }

    /** When each player last sent each kind of message, by the wall clock, so none can be sent faster than it should. */
    private static final java.util.Map<String, Long> LAST = new java.util.concurrent.ConcurrentHashMap<>();

    /** Whether a player may send this kind of message now: not more than once in so many milliseconds. */
    static boolean allowed(net.minecraft.server.level.ServerPlayer p, String what, long ms) {
        String key = p.getUUID() + what;
        long now = System.currentTimeMillis();
        Long last = LAST.get(key);
        if (last != null && now - last < ms) return false;
        LAST.put(key, now);
        if (LAST.size() > 4096) LAST.clear();
        return true;
    }

    /** A keeper spoken to: the client opens the talk with them. */
    public record Talk(int entity, String name, int number) {

        static void write(Talk m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entity);
            buf.writeUtf(m.name, 32);
            buf.writeVarInt(m.number);
        }

        static Talk read(FriendlyByteBuf buf) {
            return new Talk(buf.readVarInt(), buf.readUtf(32), buf.readVarInt());
        }

        static void handle(Talk m, Supplier<NetworkEvent.Context> ctx) {
            var c = ctx.get();
            c.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.jeladastudios.lostsignal.client.KeeperScreen.open(m)));
            c.setPacketHandled(true);
        }
    }

    /** Something asked of a keeper face to face. */
    public record Ask(int entity, int topic) {

        static void write(Ask m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entity);
            buf.writeVarInt(m.topic);
        }

        static Ask read(FriendlyByteBuf buf) {
            return new Ask(buf.readVarInt(), buf.readVarInt());
        }

        static void handle(Ask m, Supplier<NetworkEvent.Context> ctx) {
            var c = ctx.get();
            c.enqueueWork(() -> {
                var p = c.getSender();
                if (p == null || m.topic < 0 || m.topic >= com.jeladastudios.lostsignal.tower.Talk.Topic.values().length || !allowed(p, "ask", 400)) return;
                if (p.level().getEntity(m.entity) instanceof com.jeladastudios.lostsignal.entity.Keeper k && k.distanceTo(p) < 8) {
                    com.jeladastudios.lostsignal.tower.Talk.asked(p, k, com.jeladastudios.lostsignal.tower.Talk.Topic.values()[m.topic]);
                }
            });
            c.setPacketHandled(true);
        }
    }

    /** The rescue panel opened: the server is asked which lookouts are round, for the calls. */
    public record Panel() {

        static void write(Panel m, FriendlyByteBuf buf) {
        }

        static Panel read(FriendlyByteBuf buf) {
            return new Panel();
        }

        static void handle(Panel m, Supplier<NetworkEvent.Context> ctx) {
            var c = ctx.get();
            c.enqueueWork(() -> {
                var p = c.getSender();
                if (p != null && allowed(p, "panel", 1000)) com.jeladastudios.lostsignal.tower.Calls.panel(p);
            });
            c.setPacketHandled(true);
        }
    }

    /** A lookout round the player's, as the panel lists it. */
    public record Neighbour(long key, int number, int bearing, int distance) {}

    public record Neighbours(java.util.List<Neighbour> list) {

        static void write(Neighbours m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.list.size());
            for (Neighbour n : m.list) {
                buf.writeLong(n.key());
                buf.writeVarInt(n.number());
                buf.writeVarInt(n.bearing());
                buf.writeVarInt(n.distance());
            }
        }

        static Neighbours read(FriendlyByteBuf buf) {
            int n = Math.min(buf.readVarInt(), 8);
            java.util.List<Neighbour> list = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) list.add(new Neighbour(buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
            return new Neighbours(list);
        }

        static void handle(Neighbours m, Supplier<NetworkEvent.Context> ctx) {
            var c = ctx.get();
            c.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.jeladastudios.lostsignal.client.RescueScreen.neighbours(m.list)));
            c.setPacketHandled(true);
        }
    }

    /** A call from the panel: to the District, a fire called in, or to a lookout by its place. */
    public record Call(int kind, long key) {

        public static final int DISTRICT = 0, FIRE = 1, LOOKOUT = 2;

        static void write(Call m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.kind);
            buf.writeLong(m.key);
        }

        static Call read(FriendlyByteBuf buf) {
            return new Call(buf.readVarInt(), buf.readLong());
        }

        static void handle(Call m, Supplier<NetworkEvent.Context> ctx) {
            var c = ctx.get();
            c.enqueueWork(() -> {
                if (c.getSender() != null && allowed(c.getSender(), "call", 2000)) com.jeladastudios.lostsignal.tower.Calls.call(c.getSender(), m.kind, m.key);
            });
            c.setPacketHandled(true);
        }
    }

    /** A reading off the fire finder: what was on the line, its bearing and its distance by eye, for the panel. */
    public record Reading(String kind, int bearing, int distance) {

        static void write(Reading m, FriendlyByteBuf buf) {
            buf.writeUtf(m.kind, 16);
            buf.writeVarInt(m.bearing);
            buf.writeVarInt(m.distance);
        }

        static Reading read(FriendlyByteBuf buf) {
            return new Reading(buf.readUtf(16), buf.readVarInt(), buf.readVarInt());
        }

        static void handle(Reading m, Supplier<NetworkEvent.Context> ctx) {
            var c = ctx.get();
            c.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.jeladastudios.lostsignal.client.RescueScreen.reading(m)));
            c.setPacketHandled(true);
        }
    }

    /**
     * The player told to lie flat, or to get up: the client keeps its own player's pose, so it is told as the server is.
     */
    public record Lie(boolean down) {

        static void write(Lie m, FriendlyByteBuf buf) {
            buf.writeBoolean(m.down);
        }

        static Lie read(FriendlyByteBuf buf) {
            return new Lie(buf.readBoolean());
        }

        static void handle(Lie m, Supplier<NetworkEvent.Context> ctx) {
            var c = ctx.get();
            c.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.jeladastudios.lostsignal.client.ClientHiding.lie(m.down)));
            c.setPacketHandled(true);
        }
    }

    /** The keeper says something from the panel: an order, and its number where it takes one (a bearing). */
    public record Say(Order order, int value) {

        static void write(Say m, FriendlyByteBuf buf) {
            buf.writeEnum(m.order);
            buf.writeVarInt(m.value);
        }

        static Say read(FriendlyByteBuf buf) {
            return new Say(buf.readEnum(Order.class), buf.readVarInt());
        }

        static void handle(Say m, Supplier<NetworkEvent.Context> ctx) {
            var c = ctx.get();
            c.enqueueWork(() -> {
                if (c.getSender() != null && allowed(c.getSender(), "say", 500)) Rescue.said(c.getSender(), m.order, m.value);
            });
            c.setPacketHandled(true);
        }
    }
}
