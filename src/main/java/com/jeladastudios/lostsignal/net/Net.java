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
    private static final String VERSION = "5";

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
        CHANNEL.registerMessage(8, Meet.class, Meet::write, Meet::read, Meet::handle, toClient);
        CHANNEL.registerMessage(9, Bid.class, Bid::write, Bid::read, Bid::handle, toServer);
        CHANNEL.registerMessage(10, TermState.class, TermState::write, TermState::read, TermState::handle, toClient);
        CHANNEL.registerMessage(11, TermAct.class, TermAct::write, TermAct::read, TermAct::handle, toServer);
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

    /** A line of a list on the terminal: a name or key, and two numbers (an order's lot and price, a record's count). */
    public record Line(String key, int a, int b) {}

    /** What the terminal's screen shows, sent as it opens and after each thing sent from it. */
    public record TermState(boolean open, long pos, boolean posted, int number, int here, String name, int credit, int season, int phase, int daysLeft,
                            long day, int time, int present, int done, int missed, int applies, boolean observed, boolean observedRight,
                            int daysOnDuty, int dutiesDone, int bonus, int nextPay, java.util.List<com.jeladastudios.lostsignal.desk.Mail> inbox,
                            java.util.List<Line> stats, java.util.List<Line> catalog) {

        static void write(TermState m, FriendlyByteBuf buf) {
            buf.writeBoolean(m.open);
            buf.writeLong(m.pos);
            buf.writeBoolean(m.posted);
            buf.writeVarInt(m.number);
            buf.writeVarInt(m.here);
            buf.writeUtf(m.name, 32);
            buf.writeVarInt(m.credit);
            buf.writeVarInt(m.season);
            buf.writeVarInt(m.phase);
            buf.writeVarInt(m.daysLeft);
            buf.writeVarLong(m.day);
            buf.writeVarInt(m.time);
            buf.writeVarInt(m.present);
            buf.writeVarInt(m.done);
            buf.writeVarInt(m.missed);
            buf.writeVarInt(m.applies);
            buf.writeBoolean(m.observed);
            buf.writeBoolean(m.observedRight);
            buf.writeVarInt(m.daysOnDuty);
            buf.writeVarInt(m.dutiesDone);
            buf.writeVarInt(m.bonus);
            buf.writeVarInt(m.nextPay);
            buf.writeVarInt(m.inbox.size());
            for (var mail : m.inbox) mail.write(buf);
            lines(buf, m.stats);
            lines(buf, m.catalog);
        }

        static void lines(FriendlyByteBuf buf, java.util.List<Line> list) {
            buf.writeVarInt(list.size());
            for (Line l : list) {
                buf.writeUtf(l.key(), 128);
                buf.writeVarInt(l.a());
                buf.writeVarInt(l.b());
            }
        }

        static java.util.List<Line> lines(FriendlyByteBuf buf) {
            int n = Math.min(buf.readVarInt(), 64);
            java.util.List<Line> list = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) list.add(new Line(buf.readUtf(128), buf.readVarInt(), buf.readVarInt()));
            return list;
        }

        static TermState read(FriendlyByteBuf buf) {
            boolean open = buf.readBoolean();
            long pos = buf.readLong();
            boolean posted = buf.readBoolean();
            int number = buf.readVarInt(), here = buf.readVarInt();
            String name = buf.readUtf(32);
            int credit = buf.readVarInt(), season = buf.readVarInt(), phase = buf.readVarInt(), daysLeft = buf.readVarInt();
            long day = buf.readVarLong();
            int time = buf.readVarInt(), present = buf.readVarInt(), done = buf.readVarInt(), missed = buf.readVarInt(), applies = buf.readVarInt();
            boolean observed = buf.readBoolean(), observedRight = buf.readBoolean();
            int daysOnDuty = buf.readVarInt(), dutiesDone = buf.readVarInt(), bonus = buf.readVarInt(), nextPay = buf.readVarInt();
            int n = Math.min(buf.readVarInt(), 64);
            java.util.List<com.jeladastudios.lostsignal.desk.Mail> inbox = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) inbox.add(com.jeladastudios.lostsignal.desk.Mail.read(buf));
            return new TermState(open, pos, posted, number, here, name, credit, season, phase, daysLeft, day, time, present, done, missed, applies,
                    observed, observedRight, daysOnDuty, dutiesDone, bonus, nextPay, inbox, lines(buf), lines(buf));
        }

        static void handle(TermState m, Supplier<NetworkEvent.Context> ctx) {
            var c = ctx.get();
            c.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.jeladastudios.lostsignal.client.TerminalScreen.state(m)));
            c.setPacketHandled(true);
        }
    }

    /** Something sent from the terminal: what (see desk.Terminal), a message's id where it is about one, and its numbers. */
    public record TermAct(int action, long id, int[] values) {

        static void write(TermAct m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.action);
            buf.writeLong(m.id);
            buf.writeVarIntArray(m.values);
        }

        static TermAct read(FriendlyByteBuf buf) {
            return new TermAct(buf.readVarInt(), buf.readLong(), buf.readVarIntArray(16));
        }

        static void handle(TermAct m, Supplier<NetworkEvent.Context> ctx) {
            var c = ctx.get();
            c.enqueueWork(() -> {
                var p = c.getSender();
                if (p != null && allowed(p, "term" + m.action, 250)) com.jeladastudios.lostsignal.desk.Terminal.act(p, m.action, m.id, m.values);
            });
            c.setPacketHandled(true);
        }
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

    /** A hiker spoken to face to face: the client opens the talk with them. */
    public record Meet(int entity, String name, boolean lookoutKnown) {

        static void write(Meet m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entity);
            buf.writeUtf(m.name, 32);
            buf.writeBoolean(m.lookoutKnown);
        }

        static Meet read(FriendlyByteBuf buf) {
            return new Meet(buf.readVarInt(), buf.readUtf(32), buf.readBoolean());
        }

        static void handle(Meet m, Supplier<NetworkEvent.Context> ctx) {
            var c = ctx.get();
            c.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.jeladastudios.lostsignal.client.HikerScreen.open(m)));
            c.setPacketHandled(true);
        }
    }

    /** Something asked of a hiker face to face. */
    public record Bid(int entity, int what) {

        static void write(Bid m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entity);
            buf.writeVarInt(m.what);
        }

        static Bid read(FriendlyByteBuf buf) {
            return new Bid(buf.readVarInt(), buf.readVarInt());
        }

        static void handle(Bid m, Supplier<NetworkEvent.Context> ctx) {
            var c = ctx.get();
            c.enqueueWork(() -> {
                var p = c.getSender();
                var bids = com.jeladastudios.lostsignal.entity.Hiker.Bid.values();
                if (p == null || m.what < 0 || m.what >= bids.length || !allowed(p, "bid", 600)) return;
                if (p.level().getEntity(m.entity) instanceof com.jeladastudios.lostsignal.entity.Hiker h && h.distanceTo(p) < 8) {
                    h.bid(p, bids[m.what]);
                }
            });
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

    /**
     * A lookout round the player's, as the panel lists it: and how strongly the player's set hears it, in S-units, -1
     * where it does not at all.
     */
    public record Neighbour(long key, int number, int bearing, int distance, int signal) {}

    /** The lookouts round, and the District's own call still waiting for an answer (its line; empty where none). */
    public record Neighbours(java.util.List<Neighbour> list, String district) {

        static void write(Neighbours m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.list.size());
            for (Neighbour n : m.list) {
                buf.writeLong(n.key());
                buf.writeVarInt(n.number());
                buf.writeVarInt(n.bearing());
                buf.writeVarInt(n.distance());
                buf.writeVarInt(n.signal());
            }
            buf.writeUtf(m.district, 32);
        }

        static Neighbours read(FriendlyByteBuf buf) {
            int n = Math.min(buf.readVarInt(), 8);
            java.util.List<Neighbour> list = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) list.add(new Neighbour(buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
            return new Neighbours(list, buf.readUtf(32));
        }

        static void handle(Neighbours m, Supplier<NetworkEvent.Context> ctx) {
            var c = ctx.get();
            c.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.jeladastudios.lostsignal.client.RescueScreen.neighbours(m.list, m.district)));
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
