package com.jeladastudios.lostsignal.radio;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.mission.Missions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the players say on the rescue frequency, kept -- the way a man with a tape recorder by a radio keeps it. A
 * listener at each lookout a player keeps hears their transmissions, and the last few of each player's are held, a few
 * seconds each, to be played back to them some night from somewhere they are not: their own voice, a little slow,
 * with the hiss of the tape under it.
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class Tape {

    private Tape() {}

    /** How many things said are kept for each player, and how long each may be, in frames (20 ms). */
    static final int KEPT = 6, LONGEST = 50 * 10, SHORTEST = 30;

    private static final Map<BlockPos, Ear> EARS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<short[]>> SAYING = new ConcurrentHashMap<>();
    private static final Map<UUID, Deque<List<short[]>>> SAID = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (level.getGameTime() % 100 != 31) return;
        for (ServerPlayer p : level.players()) {
            BlockPos tower = Missions.towerAt(level, p);
            if (tower == null || EARS.containsKey(tower)) continue;
            Vec3 cab = Vec3.atCenterOf(tower.above(22));
            try {
                EARS.put(tower, new Ear(level, cab, Broadcast.RESCUE, h -> heard(level, h)));
            } catch (RuntimeException | LinkageError e) {
                LostSignal.LOGGER.warn("No listener at the lookout: {}", e.toString());
            }
        }
    }

    /** A frame heard: a player's, added to what they are saying; the end of it, kept. */
    static void heard(ServerLevel level, Ear.Heard h) {
        UUID who = h.speaker();
        if (who == null || level.getServer().getPlayerList().getPlayer(who) == null) return;
        if (h.end()) {
            List<short[]> said = SAYING.remove(who);
            if (said != null && said.size() >= SHORTEST) {
                Deque<List<short[]>> kept = SAID.computeIfAbsent(who, k -> new ArrayDeque<>());
                synchronized (kept) {
                    kept.addLast(said);
                    while (kept.size() > KEPT) kept.removeFirst();
                }
                if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("TAPE kept {} frames of {}", said.size(), who);
            }
            return;
        }
        if (h.pcm() == null) return;
        List<short[]> saying = SAYING.computeIfAbsent(who, k -> new ArrayList<>());
        if (saying.size() < LONGEST) saying.add(h.pcm());
    }

    /** One of the things a player said, kept, played as off a tape; or null where nothing of theirs is kept. */
    public static List<short[]> of(UUID player, Random random) {
        Deque<List<short[]>> kept = SAID.get(player);
        if (kept == null || kept.isEmpty()) return null;
        List<List<short[]>> all;
        synchronized (kept) {
            all = new ArrayList<>(kept);
        }
        return played(all.get(random.nextInt(all.size())), random.nextLong());
    }

    /**
     * Frames as a tape plays them back: a touch slow and wavering, duller, the hiss under them -- and a gap of hiss
     * before and after, as a recorder is started and stopped by hand.
     */
    public static List<short[]> played(List<short[]> frames, long seed) {
        Random r = new Random(seed);
        int lead = 15 + r.nextInt(15);
        short[] in = new short[frames.size() * Clips.FRAME];
        for (int i = 0; i < frames.size(); i++) System.arraycopy(frames.get(i), 0, in, i * Clips.FRAME, Clips.FRAME);
        // Slow by a few hundredths, wavering about that slowly.
        double speed = 0.95, at = 0, phase = r.nextDouble() * Math.PI * 2;
        List<Short> out = new ArrayList<>();
        for (int i = 0; i < lead * Clips.FRAME; i++) out.add((short) 0);
        double low = 0;
        while (at < in.length - 1) {
            int a = (int) at;
            double f = at - a, s = in[a] + (in[a + 1] - in[a]) * f;
            // Duller: the top of it gone.
            low += (s - low) * 0.35;
            out.add((short) Math.max(-32768, Math.min(32767, low * 0.85)));
            at += speed + 0.006 * Math.sin(phase + out.size() * 2 * Math.PI * 0.6 / 48000.0);
        }
        for (int i = 0; i < lead * Clips.FRAME; i++) out.add((short) 0);
        List<short[]> result = new ArrayList<>();
        for (int i = 0; i + Clips.FRAME <= out.size(); i += Clips.FRAME) {
            short[] frame = new short[Clips.FRAME];
            for (int j = 0; j < Clips.FRAME; j++) {
                // The tape's hiss, always, even in the gaps: the carrier stays up.
                frame[j] = (short) Math.max(-32768, Math.min(32767, out.get(i + j) + r.nextGaussian() * 180));
            }
            result.add(frame);
        }
        return result;
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        for (Ear ear : EARS.values()) ear.close();
        EARS.clear();
        SAYING.clear();
    }
}
