package com.jeladastudios.lostsignal.tower;

import com.jeladastudios.lostsignal.Config;
import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.director.Director;
import com.jeladastudios.lostsignal.finder.FireFinder;
import com.jeladastudios.lostsignal.mission.Lookouts;
import com.jeladastudios.lostsignal.mission.Missions;
import com.jeladastudios.lostsignal.radio.Broadcast;
import com.jeladastudios.lostsignal.rescue.Lines;
import com.jeladastudios.lostsignal.signal.ModParticles;
import com.jeladastudios.lostsignal.signal.Signals;
import com.jeladastudios.lostsignal.signal.Sightline;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Someone lost out on the mountain, and the lookouts round guiding them in. Called on a handheld, a keeper puts a flare
 * up from the lookout to be walked towards, lights the lamp by night, and asks for a flare back: one seen from the
 * lookout is taken a bearing on, and the way in told over the radio. A flare, or a lamp swung at night, seen from a
 * lookout with no call is answered too: on the radio, to a handheld in touch, else with a flare sent up from the lookout.
 * From a lookout no one keeps, someone answers as well.
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class Guide {

    private Guide() {}

    /** A handheld's five watts, as a share of a station's: what a lookout's set makes of a call from one. */
    static final float HANDHELD = 0.15f;
    /** How long a keeper watches for the flare they asked for, in ticks: five minutes. */
    static final int WATCHES = 20 * 60 * 5;
    /** How long a lamp lit for someone out there is kept burning, in ticks: ten minutes. */
    static final int LAMP_BURNS = 20 * 60 * 10;
    /** The least time between one lookout's answering flares, in ticks. */
    static final int ANSWERS = 20 * 90;
    /** How far off a flare or a lamp is seen from a lookout, in blocks: as far as one is drawn. */
    static final double SEES = 512;
    /** The chance a keeper not watching for it sees a flare by day; by night one is seen by anyone awake. */
    static final float SEEN_BY_DAY = 0.5f;
    /** How far a player goes before the lookouts round them are worked out again, in blocks. */
    static final int MOVED = 256;
    /** How many lookouts may answer one flare with one of their own. */
    static final int FLARES_BACK = 2;

    /** The lookouts round a player out on the mountain, worked out where they were. */
    private record Round(BlockPos at, List<BlockPos> feet) {}

    private static final Map<UUID, Round> ROUND = new HashMap<>();

    /** The keeper watching for a player's flare: their lookout, by its place, and until when. */
    private record Watch(long tower, long until) {}

    private static final Map<UUID, Watch> WATCHING = new HashMap<>();

    /** A lamp lit for someone out there, by the lookout's place: where it hangs, and until when. */
    private record Lit(Vec3 at, long until) {}

    private static final Map<Long, Lit> LIT = new HashMap<>();

    /** When each lookout last sent up a flare for someone. */
    private static final Map<Long, Long> ANSWERED = new HashMap<>();

    /**
     * The lookouts round a player out on the mountain, nearest first, handed to what wants them: at once where they
     * are known for about here, else once worked out off the server's thread.
     */
    public static void round(ServerPlayer p, java.util.function.Consumer<List<Tower>> then) {
        ServerLevel level = p.serverLevel();
        BlockPos at = p.blockPosition();
        Round r = ROUND.get(p.getUUID());
        if (r != null && r.at.distSqr(at) < (double) MOVED * MOVED) {
            then.accept(towers(level, r.feet, p));
            return;
        }
        Lookouts.offThread(level, () -> Lookouts.aroundOffThread(level, at, 1), feet -> {
            List<BlockPos> found = feet == null ? List.of() : feet;
            ROUND.put(p.getUUID(), new Round(at, found));
            if (!p.hasDisconnected()) then.accept(towers(level, found, p));
        });
    }

    /** The lookouts round a player out on the mountain, as last worked out for about where they are; none if not yet. */
    public static List<Tower> known(ServerPlayer p) {
        Round r = ROUND.get(p.getUUID());
        if (r == null || r.at.distSqr(p.blockPosition()) >= (double) MOVED * MOVED) return List.of();
        return towers(p.serverLevel(), r.feet, p);
    }

    static List<Tower> towers(ServerLevel level, List<BlockPos> feet, ServerPlayer p) {
        List<Tower> list = new ArrayList<>();
        for (BlockPos foot : feet) list.add(Towers.at(level, foot));
        list.sort(java.util.Comparator.comparingDouble(t -> Math.hypot(t.x - p.getX(), t.z - p.getZ())));
        return list;
    }

    /** Whether a lookout and a player's handheld are in touch: each heard by the other, five watts the weaker way. */
    static boolean inTouch(ServerLevel level, ServerPlayer p, Tower t) {
        var heard = Broadcast.heardBy(level, t.cab(), 1f, p);
        return heard != null && heard.heard() && Broadcast.station(level, p.getEyePosition(), HANDHELD, t.cab()).heard();
    }

    /** A lookout called from a handheld out on the mountain: one of those round, as the panel listed them. */
    public static void call(ServerPlayer p, long key) {
        ServerLevel level = p.serverLevel();
        Tower found = null;
        for (Tower n : known(p)) if (n.key() == key) found = n;
        if (found == null) return;
        Tower t = found;
        p.displayClientMessage(Component.translatable("call.fts_lostsignal.calling", t.number), true);
        if (!inTouch(level, p, t)) {
            Speech.after(level, 80, () -> p.displayClientMessage(Component.translatable("call.fts_lostsignal.no_answer", t.number), true));
            return;
        }
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("GUIDE call to {} ({}), {} blocks", t.number, t.state,
                (int) Math.hypot(t.x - p.getX(), t.z - p.getZ()));
        switch (t.state) {
            case KEPT -> lost(level, p, t);
            case STRANGER -> his(level, p, t);
            default -> Speech.after(level, 80, () -> p.displayClientMessage(Component.translatable("call.fts_lostsignal.no_answer", t.number), true));
        }
    }

    /**
     * A keeper answering someone lost: a flare up from the lookout to walk towards, the lamp lit by night, and a flare
     * asked for back, to take a bearing on.
     */
    static void lost(ServerLevel level, ServerPlayer p, Tower t) {
        Component who = Calls.lookout(t, t.keeper);
        Vec3 from = t.cab();
        boolean night = Director.dark(level);
        String hello = level.random.nextBoolean() ? "K_ANSWER_01" : "K_ANSWER_02";
        int at = 40;
        Speech.after(level, at, () -> Lines.sayAs(level, from, who, hello, 1f, t.pitch, Calls.REACH));
        at += Speech.length(hello);
        Speech.after(level, at, () -> Lines.sayAs(level, from, who, "K_LOST_FLARE", 1f, t.pitch, Calls.REACH));
        at += Speech.length("K_LOST_FLARE") + 20;
        // Out on the walkway and up it goes, as they said.
        Speech.after(level, at, () -> {
            send(level, t);
            if (Director.dark(level)) light(level, t);
        });
        // Once it is up and seen.
        at += 80;
        if (night) {
            Speech.after(level, at, () -> Lines.sayAs(level, from, who, "K_LOST_LAMP", 1f, t.pitch, Calls.REACH));
            at += Speech.length("K_LOST_LAMP");
        }
        String ask = night ? "K_LOST_ASK_NIGHT" : "K_LOST_ASK";
        UUID id = p.getUUID();
        Speech.after(level, at, () -> {
            Lines.sayAs(level, from, who, ask, 1f, t.pitch, Calls.REACH);
            WATCHING.put(id, new Watch(t.key(), level.getGameTime() + WATCHES));
        });
    }

    /** From a lookout no one keeps: someone there has seen them, and is coming down for them. */
    static void his(ServerLevel level, ServerPlayer p, Tower t) {
        Speech.after(level, 70 + level.random.nextInt(60), () -> Lines.sayAs(level, t.cab(), Calls.lookout(t, null), "S_LOST", 1f, 1f, Calls.REACH));
        if (!Config.on(Config.STRANGER)) return;
        UUID id = p.getUUID();
        Speech.after(level, 20 * (60 + level.random.nextInt(60)), () -> {
            ServerPlayer now = level.getServer().getPlayerList().getPlayer(id);
            // Gone in, or gone, meanwhile: not out where he said he would find them.
            if (now == null || now.serverLevel() != level || now.isSpectator() || Missions.towerAt(level, now) != null) return;
            if (!Director.dark(level)) Director.comeUp(level, now);
            else if (Director.put(level, now) == null) Director.put(level, now, true);
        });
    }

    /**
     * A flare fired or a lamp swung by a player away from a lookout, looked for from the lookouts round once it is up
     * where it is seen (a flare at the top of its climb), some ticks from now.
     */
    public static void fired(ServerPlayer p, String kind, Vec3 at, int after) {
        ServerLevel level = p.serverLevel();
        if (level.dimension() != Level.OVERWORLD || Missions.towerAt(level, p) != null) return;
        round(p, towers -> Speech.after(level, after, () -> seen(level, p, kind, at, towers)));
    }

    /**
     * Who sees it: the keeper watching for it, and by night anyone at a window with a clear line to it. The keeper who
     * asked for it, or else the nearest who sees it, tells the way in on the radio, where they are in touch; the
     * lookouts that see it send a flare up back, a few of them, so it is seen where they are. Not seen by the keeper who
     * asked for it: they say so.
     */
    static void seen(ServerLevel level, ServerPlayer p, String kind, Vec3 at, List<Tower> towers) {
        boolean night = Director.dark(level);
        // A lamp is not seen by day from a lookout miles off.
        if (kind.equals("lamp") && !night) return;
        Watch watch = WATCHING.get(p.getUUID());
        if (watch != null && watch.until < level.getGameTime()) {
            WATCHING.remove(p.getUUID());
            watch = null;
        }
        List<Tower> saw = new ArrayList<>();
        for (Tower t : towers) {
            if (t.state != Tower.State.KEPT && t.state != Tower.State.STRANGER) continue;
            // His lookout only answers in the dark.
            if (t.state == Tower.State.STRANGER && !night) continue;
            Vec3 cab = t.cab();
            if (cab.distanceTo(at) > SEES) continue;
            boolean asked = watch != null && watch.tower == t.key();
            if (!asked && !night && level.random.nextFloat() >= SEEN_BY_DAY) continue;
            // From the windows: the line to it clear from a little way out of the cab.
            if (!Sightline.clear(level, cab.add(at.subtract(cab).normalize().scale(6)), at)) continue;
            saw.add(t);
        }
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) {
            LostSignal.LOGGER.info("GUIDE {} by {}: {} round, seen from {}", kind, p.getName().getString(), towers.size(),
                    saw.stream().map(t -> t.number + " " + t.state).toList());
        }
        Tower teller = null;
        for (Tower t : saw) {
            if (t.state != Tower.State.KEPT || !inTouch(level, p, t)) continue;
            if (watch != null && t.key() == watch.tower) {
                teller = t;
                break;
            }
            if (teller == null) teller = t;
        }
        if (teller != null) tell(level, p, teller);
        else if (watch != null) {
            Tower w = Towers.known(level, watch.tower);
            if (w != null && w.state == Tower.State.KEPT && inTouch(level, p, w)) {
                Speech.after(level, 60, () -> Lines.sayAs(level, w.cab(), Calls.lookout(w, w.keeper), "K_LOST_NOT_SEEN", 1f, w.pitch, Calls.REACH));
            }
        }
        int back = 0;
        for (Tower t : saw) {
            if (back >= FLARES_BACK) break;
            if (answer(level, t)) back++;
        }
    }

    /** The way in, told by a keeper who has the player on the fire finder: which way to walk, and about how far. */
    static void tell(ServerLevel level, ServerPlayer p, Tower t) {
        WATCHING.remove(p.getUUID());
        int dx = t.x - p.getBlockX(), dz = t.z - p.getBlockZ();
        int bearing = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz))), 360);
        // By eye off the finder: to the nearest fifty.
        int far = Math.max(50, (int) Math.round(Math.hypot(dx, dz) / 50.0) * 50);
        Component note = Component.translatable("guide.fts_lostsignal.way", t.number, far,
                Component.translatable("radio.fts_lostsignal.point." + FireFinder.point(Math.floorMod(bearing + 180, 360))), bearing,
                Component.translatable("radio.fts_lostsignal.point." + FireFinder.point(bearing))).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        UUID id = p.getUUID();
        Speech.after(level, 40, () -> {
            Lines.sayAs(level, t.cab(), Calls.lookout(t, t.keeper), "K_LOST_SEEN", 1f, t.pitch, Calls.REACH);
            ServerPlayer now = level.getServer().getPlayerList().getPlayer(id);
            if (now != null) now.sendSystemMessage(note);
        });
        if (Director.dark(level)) light(level, t);
    }

    /** A flare sent up from a lookout for someone out there, if it has not sent one lately: there it is, walk to it. */
    static boolean answer(ServerLevel level, Tower t) {
        Long last = ANSWERED.get(t.key());
        if (last != null && level.getGameTime() - last < ANSWERS) return false;
        ANSWERED.put(t.key(), level.getGameTime());
        Speech.after(level, 200 + level.random.nextInt(100), () -> {
            Signals.flare(level, t.cab().add(0, 3, 0));
            if (t.state == Tower.State.KEPT && Director.dark(level)) light(level, t);
        });
        return true;
    }

    /** A flare up from a lookout's walkway now. */
    static void send(ServerLevel level, Tower t) {
        ANSWERED.put(t.key(), level.getGameTime());
        Signals.flare(level, t.cab().add(0, 3, 0));
    }

    /**
     * A lookout's lamp lit for someone out there, for a while: its light sent to whoever is far off, as a lamp the
     * keepers light is, whether or not its ground is loaded. Hung on a corner of the walkway.
     */
    static void light(ServerLevel level, Tower t) {
        LIT.put(t.key(), new Lit(new Vec3(t.x + 5.5, t.y + 22.5, t.z + 5.5), level.getGameTime() + LAMP_BURNS));
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        long now = level.getGameTime();
        if (now % 4 != 0 || LIT.isEmpty()) return;
        // Put out when the time is up, and at first light.
        if (!Director.dark(level)) {
            LIT.clear();
            return;
        }
        LIT.values().removeIf(l -> l.until < now);
        for (Lit l : LIT.values()) {
            for (ServerPlayer p : level.players()) {
                double d = p.position().distanceTo(l.at);
                if (d < 24 || d > SEES) continue;
                level.sendParticles(p, ModParticles.LAMP.get(), true, l.at.x, l.at.y, l.at.z, 1, 0, 0, 0, 0);
            }
        }
    }

    /** A server stopped: nothing of its kept for the next world. */
    @SubscribeEvent
    public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        ROUND.clear();
        WATCHING.clear();
        LIT.clear();
        ANSWERED.clear();
    }
}
