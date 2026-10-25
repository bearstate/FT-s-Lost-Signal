package com.jeladastudios.lostsignal.tower;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.rescue.Lines;
import com.jeladastudios.lostsignal.signal.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The crew sent to a fire called in: on the radio as they set out; by night their headlamps seen from the lookout, a
 * file of lights coming up the valley, then spread round the fire's edge; down in the trees they cannot raise the
 * District, so they ask the lookout to pass it on (a relay, answered from the panel); and on the radio again once it
 * is out.
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class Crew {

    private Crew() {}

    /** How far off they set out from, in blocks; how long they take to get there, in ticks. */
    static final double SETS_OUT = 220;
    static final int WALK = 20 * 90;
    /** How long the District waits on a relay before it tries the crew itself, in ticks. */
    static final int WAITS = 20 * 60 * 4;
    /** How far the crew's handhelds carry, in blocks; their lamps, as far as a light is drawn. */
    static final double REACH = 3000, SEEN = 512;
    /** How far over the fire's ground they call from, in blocks: the nearest rise. */
    static final double CALLS_FROM = 12;

    private static final class Out {
        final ServerLevel level;
        final Vec3 fire, start;
        final long setOff, arrive;
        final UUID caller;
        final int number;
        boolean asked, relayed, checked;

        Out(ServerLevel level, Vec3 fire, Vec3 start, long setOff, UUID caller, int number) {
            this.level = level;
            this.fire = fire;
            this.start = start;
            this.setOff = setOff;
            this.arrive = setOff + (com.jeladastudios.lostsignal.dev.TestCommands.ON ? WALK / 3 : WALK);
            this.caller = caller;
            this.number = number;
        }
    }

    private static final List<Out> OUT = new CopyOnWriteArrayList<>();

    /**
     * A crew sent to a fire called in by a player from their lookout: setting out from down the valley off to the side
     * of the fire, on the radio after the District has spoken; out for as long as the fire takes.
     */
    public static void send(ServerLevel level, ServerPlayer caller, Vec3 fire, Vec3 lookout, int after, int outIn) {
        // From down the valley: of a ring of places round the fire, the lowest over the sea, the way a road would come.
        Vec3 from = null;
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            Vec3 c = ground(level, fire.add(Math.cos(a) * SETS_OUT, 0, Math.sin(a) * SETS_OUT));
            if (c.y <= level.getSeaLevel() + 1) continue;
            if (from == null || c.y < from.y) from = c;
        }
        if (from == null) from = ground(level, fire.add(fire.subtract(lookout).multiply(1, 0, 1).normalize().scale(SETS_OUT)));
        Out o = new Out(level, fire, from, level.getGameTime() + after, caller.getUUID(), 4);
        OUT.add(o);
        Component who = Component.translatable("radio.fts_lostsignal.speaker.crew", o.number);
        Speech.after(level, after, () -> Lines.sayAs(level, o.start, who, "C_ROLLING", 0.4f, 0.95f, REACH));
        Speech.after(level, outIn, () -> {
            OUT.remove(o);
            Lines.sayAs(level, fire.add(0, CALLS_FROM, 0), who, "C_OUT", 1f, 0.95f, REACH);
        });
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("CREW {} sets out from {} to {}", o.number,
                net.minecraft.core.BlockPos.containing(o.start).toShortString(), net.minecraft.core.BlockPos.containing(fire).toShortString());
    }

    static Vec3 ground(ServerLevel level, Vec3 at) {
        Integer y = Fires.ground(level, (int) Math.floor(at.x), (int) Math.floor(at.z));
        return new Vec3(at.x, y == null ? level.getSeaLevel() : y, at.z);
    }

    /** The relay a player is asked to pass on, if one is waiting on them: the crew's, by its line. */
    public static String relayFor(ServerPlayer p) {
        for (Out o : OUT) if (o.asked && !o.relayed && o.caller.equals(p.getUUID())) return "C_RELAY";
        return null;
    }

    /**
     * The relay passed on from the panel: said to the District in the keeper's words, the District's thanks, and a
     * little to their credit. False where none was waiting on them.
     */
    public static boolean relay(ServerLevel level, ServerPlayer p, Tower home) {
        Out waiting = null;
        for (Out o : OUT) if (o.asked && !o.relayed && o.caller.equals(p.getUUID())) waiting = o;
        if (waiting == null) return false;
        waiting.relayed = true;
        String number = home == null ? "?" : String.valueOf(home.number);
        Component who = home == null ? p.getName() : Calls.lookout(home, p.getName().getString());
        Component line = Component.literal("[" + com.jeladastudios.lostsignal.radio.Broadcast.RESCUE + "] ").withStyle(ChatFormatting.DARK_GRAY)
                .append(who.copy().withStyle(ChatFormatting.GRAY))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable("district.fts_lostsignal.said.C_RELAY", number, waiting.number).withStyle(ChatFormatting.WHITE));
        p.sendSystemMessage(line);
        Vec3 from = p.position();
        Speech.after(level, 60, () -> Lines.say(level, from, Lines.Speaker.DISTRICT, "D_RELAY_COPY", 1f));
        com.jeladastudios.lostsignal.desk.Desk.earn(p, "desk.fts_lostsignal.bonus.relay", com.jeladastudios.lostsignal.desk.Desk.RELAY_PAY);
        com.jeladastudios.lostsignal.desk.Desk.count(p, "relays");
        return true;
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        long now = level.getGameTime();
        boolean dark = com.jeladastudios.lostsignal.director.Director.dark(level);
        for (Out o : OUT) {
            if (o.level != level || now < o.setOff) continue;
            // There: down in the trees, the District out of reach; the lookout who called it in is asked to pass it on.
            if (now >= o.arrive && !o.asked) {
                o.asked = true;
                if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("CREW {} on scene, asking for a relay", o.number);
                ServerPlayer caller = level.getServer().getPlayerList().getPlayer(o.caller);
                Component who = Component.translatable("radio.fts_lostsignal.speaker.crew", o.number);
                // Called from up on the nearest rise, the handheld held high: the lookout that saw the smoke hears them.
                // One that cannot (the set dead, the keeper gone from it) is not asked, and they get through some other way.
                Vec3 call = o.fire.add(0, CALLS_FROM, 0);
                var heard = caller == null ? null : com.jeladastudios.lostsignal.radio.Broadcast.heardBy(level, call, 1f, caller);
                if (heard == null || !heard.heard()) {
                    o.relayed = true;
                    continue;
                }
                Lines.sayAs(level, call, who, "C_RELAY", 1f, 0.95f, REACH);
                caller.displayClientMessage(Component.translatable("crew.fts_lostsignal.relay"), true);
            }
            if (o.asked && !o.relayed && !o.checked && now >= o.arrive + WAITS) {
                o.checked = true;
                Lines.say(level, o.fire, Lines.Speaker.DISTRICT, "D_CREW_CHECK", 1f);
            }
            if (dark && now % 4 == 0) lamps(level, o, now);
        }
    }

    /** Their headlamps: a file of four coming up the valley, then spread round the fire's edge, working it. */
    static void lamps(ServerLevel level, Out o, long now) {
        for (int i = 0; i < 4; i++) {
            Vec3 at;
            if (now < o.arrive) {
                double t = (now - o.setOff) / (double) WALK - i * 0.012;
                if (t < 0) continue;
                Vec3 flat = o.start.add(o.fire.subtract(o.start).scale(Math.min(1, t)));
                at = ground(level, flat).add(0, 1.8 + Math.sin((now + i * 7) * 0.4) * 0.08, 0);
            } else {
                double a = i * Math.PI / 2 + (now - o.arrive) * 0.004 * (i % 2 == 0 ? 1 : -1);
                at = ground(level, o.fire.add(Math.cos(a) * 9, 0, Math.sin(a) * 9)).add(0, 1.8, 0);
            }
            for (ServerPlayer p : level.players()) {
                double d = p.position().distanceTo(at);
                if (d < 24 || d > SEEN) continue;
                level.sendParticles(p, ModParticles.LAMP.get(), true, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            }
        }
    }

    @SubscribeEvent
    public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        OUT.clear();
    }
}
