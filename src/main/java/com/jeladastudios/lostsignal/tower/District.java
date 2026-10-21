package com.jeladastudios.lostsignal.tower;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.rescue.Lines;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * The District's day on the rescue frequency, heard at every lookout with a working set: the morning radio check, a
 * call for the weather some days, the end of day check, the reminder of the rules as it gets dark -- and some nights,
 * late, a check no one asked for. Called back after one of them, the District answers that call.
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class District {

    private District() {}

    /** A call at a time of day, some days. */
    record Slot(String line, long time, float chance) {}

    static final Slot[] DAY = {
            new Slot("D_MORNING_01", 1000, 1f),
            new Slot("D_WEATHER_01", 4000, 0.5f),
            new Slot("D_EVENING_01", 11500, 1f),
            new Slot("D_NIGHT_RULES", 12600, 0.35f),
            new Slot("D_CHECK_NIGHT", 18500, 0.25f),
    };

    /** What a call back answers to, in the few minutes after the District called. */
    static final Map<String, String> REPLY = Map.of("D_MORNING_01", "D_MORNING_02", "D_WEATHER_01", "D_WEATHER_02",
            "D_EVENING_01", "D_EVENING_02");
    static final long REPLY_WITHIN = 20 * 60 * 4;

    private static final Map<String, Long> SAID = new HashMap<>();
    private static String waiting;
    private static long waitingSince;

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (level.getGameTime() % 20 != 0 || level.players().isEmpty()) return;
        long day = level.getDayTime() / 24000, time = level.getDayTime() % 24000;
        for (Slot s : DAY) {
            if (time < s.time() || time > s.time() + 200 || SAID.getOrDefault(s.line(), -1L) == day) continue;
            SAID.put(s.line(), day);
            // The rules always the first night; the rest, some days.
            boolean first = s.line().equals("D_NIGHT_RULES") && day == 0;
            if (!first && level.random.nextFloat() >= s.chance()) continue;
            Vec3 from = Vec3.atCenterOf(level.getSharedSpawnPos());
            Lines.say(level, from, Lines.Speaker.DISTRICT, s.line(), 1f);
            waiting = REPLY.containsKey(s.line()) ? s.line() : null;
            waitingSince = level.getGameTime();
        }
    }

    /** The District's answer to a call back after one of its own, if one is waited on; taken, so it is said once. */
    static String reply(ServerLevel level) {
        if (waiting == null || level.getGameTime() - waitingSince > REPLY_WITHIN) return null;
        String r = REPLY.get(waiting);
        waiting = null;
        return r;
    }

    @SubscribeEvent
    public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        SAID.clear();
        waiting = null;
    }
}
