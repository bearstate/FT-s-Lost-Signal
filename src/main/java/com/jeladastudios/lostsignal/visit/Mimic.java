package com.jeladastudios.lostsignal.visit;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.mission.Missions;
import com.jeladastudios.lostsignal.radio.Broadcast;
import com.jeladastudios.lostsignal.radio.Tape;
import com.jeladastudios.lostsignal.rescue.Lines;
import com.jeladastudios.lostsignal.rescue.Stations;
import com.jeladastudios.lostsignal.tower.Speech;
import com.jeladastudios.lostsignal.tower.Tower;
import com.jeladastudios.lostsignal.tower.Towers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * What the stranger does with the radio, some nights, to a lookout whose keeper sits up by it. Either their own voice,
 * something they said on the air days ago, played back to them off his tape from somewhere out in the dark; or a call
 * from someone lost, when no one is -- him, in his own voice, playing a part. Answer that call and he answers back, and
 * comes a little nearer each time; answer it to the end and he is on the stair.
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class Mimic {

    private Mimic() {}

    /** The chance, once a night at a lookout someone sits up in, that he is on the radio. */
    static final float CHANCE = 0.25f;
    /** How far off he calls from, in blocks. */
    static final int NEAR = 160, FAR = 280;

    /** A call of his under way at a lookout: where from, how far it has gone, when he speaks next, when he gives up. */
    static final class Call {
        final BlockPos tower;
        final Vec3 from;
        final boolean echo;
        int step, calls;
        long next;

        Call(BlockPos tower, Vec3 from, boolean echo, long next) {
            this.tower = tower;
            this.from = from;
            this.echo = echo;
            this.next = next;
        }
    }

    private static final Map<BlockPos, Call> CALLS = new HashMap<>();
    private static final Map<BlockPos, Long> LOOKED = new HashMap<>();

    /** What he says to each answer, nearer each time; the last of them, he is at the door. */
    static final String[] ANSWERS = {"R_LOST", "R_TOWER", "R_COME", "R_DOOR"};

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        long now = level.getGameTime();
        if (now % 20 != 0) return;
        long time = level.getDayTime() % 24000, night = level.getDayTime() / 24000;
        // The calls under way: called again while unanswered, a few times, then given up.
        for (Call c : new java.util.ArrayList<>(CALLS.values())) {
            if (c.echo || now < c.next) continue;
            if (c.step > 0 || c.calls >= 3 || time < 13000 || time > 23000) {
                CALLS.remove(c.tower);
                continue;
            }
            c.calls++;
            c.next = now + 1800;
            Lines.say(level, c.from, Lines.Speaker.HIKER, "R_CALL", 0.15f);
        }
        if (time < 14000 || time > 22000 || !com.jeladastudios.lostsignal.Config.stranger(com.jeladastudios.lostsignal.Config.FAKE_CALLS)) return;
        for (ServerPlayer p : level.players()) {
            BlockPos tower = Missions.towerAt(level, p);
            if (tower == null || p.getY() < tower.getY() + 18 || CALLS.containsKey(tower)) continue;
            if (LOOKED.getOrDefault(tower, -1L) == night) continue;
            if (Stations.poweredNear(level, p.blockPosition(), 8) == null) continue;
            LOOKED.put(tower, night);
            float chance = CHANCE * (com.jeladastudios.lostsignal.dev.TestCommands.ON ? 3 : 1);
            if (level.random.nextFloat() >= chance) continue;
            begin(level, p, tower, com.jeladastudios.lostsignal.Config.stranger(com.jeladastudios.lostsignal.Config.ECHO) && level.random.nextBoolean());
        }
    }

    /** Him on the radio, now: their own voice played back, or a lost hiker who is not one. */
    public static boolean begin(ServerLevel level, ServerPlayer p, BlockPos tower, boolean echo) {
        Vec3 from = whence(level, tower);
        if (echo) {
            var frames = Tape.of(p.getUUID(), new java.util.Random(level.random.nextLong()));
            if (frames != null) {
                Broadcast.sendFrames(level, from, Broadcast.RESCUE, 0.15f, frames);
                for (ServerPlayer q : level.players()) {
                    if (q.distanceToSqr(Vec3.atCenterOf(tower)) < 64 * 64 && Stations.poweredNear(level, q.blockPosition(), 8) != null) {
                        q.sendSystemMessage(Component.literal("[" + Broadcast.RESCUE + "] ").withStyle(ChatFormatting.DARK_GRAY)
                                .append(Component.translatable("radio.fts_lostsignal.speaker.unknown").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                                .append(Component.translatable("radio.fts_lostsignal.line.X_YOUR_VOICE").withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC)));
                    }
                }
                CALLS.put(tower, new Call(tower, from, true, level.getGameTime() + 2400));
                Speech.after(level, 2400, () -> CALLS.remove(tower));
                if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("MIMIC echo at {}", tower.toShortString());
                return true;
            }
            // Nothing of theirs on his tape yet: he calls as someone lost instead.
        }
        if (Missions.at(level, tower) != null) return false;
        Call c = new Call(tower, from, false, level.getGameTime() + 1800);
        c.calls = 1;
        CALLS.put(tower, c);
        Lines.say(level, from, Lines.Speaker.HIKER, "R_CALL", 0.15f);
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("MIMIC call at {}", tower.toShortString());
        return true;
    }

    /** Where he calls from: the cab of a lookout he is living in, if one is near; else out in the dark. */
    static Vec3 whence(ServerLevel level, BlockPos tower) {
        Tower home = Towers.at(level, tower);
        for (Tower t : Towers.around(level, home)) if (t.state == Tower.State.STRANGER) return t.cab();
        double a = level.random.nextDouble() * Math.PI * 2, d = NEAR + level.random.nextDouble() * (FAR - NEAR);
        int x = (int) Math.floor(tower.getX() + Math.cos(a) * d), z = (int) Math.floor(tower.getZ() + Math.sin(a) * d);
        // Its height by the ground model: out there is not loaded, and is not made to be for a voice on the radio.
        Integer y = com.jeladastudios.lostsignal.tower.Fires.ground(level, x, z);
        return new Vec3(x + 0.5, (y == null ? tower.getY() : y) + 1.6, z + 0.5);
    }

    /**
     * The keeper said something on the rescue frequency with no hiker to hear it: if he is calling, he heard. Each
     * answer, his reply comes nearer; the last, and he is coming up. True where he took it.
     */
    public static boolean answered(ServerLevel level, ServerPlayer keeper) {
        BlockPos tower = Missions.towerAt(level, keeper);
        Call c = tower == null ? null : CALLS.get(tower);
        if (c == null || c.echo || c.step >= ANSWERS.length) return false;
        String line = ANSWERS[c.step++];
        c.next = level.getGameTime() + 20 * 60 * 10;
        Speech.after(level, 40 + level.random.nextInt(40), () -> Lines.say(level, c.from, Lines.Speaker.HIKER, line, 0.15f));
        if (c.step == ANSWERS.length) {
            // He was never lost. He is on his way up.
            Speech.after(level, 600, () -> Visit.begin(level, tower));
            Speech.after(level, 1600, () -> CALLS.remove(tower));
        }
        return true;
    }

    /** His call at a lookout given up: someone really is lost out there tonight, and calling. */
    public static void cancel(BlockPos tower) {
        Call c = CALLS.get(tower);
        if (c != null && !c.echo) CALLS.remove(tower);
    }

    /** Whether he is on the radio at a lookout now, and how: "echo", "call", or null. */
    public static String on(BlockPos tower) {
        Call c = tower == null ? null : CALLS.get(tower);
        return c == null ? null : c.echo ? "echo" : "call";
    }

    @SubscribeEvent
    public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        CALLS.clear();
        LOOKED.clear();
    }
}
