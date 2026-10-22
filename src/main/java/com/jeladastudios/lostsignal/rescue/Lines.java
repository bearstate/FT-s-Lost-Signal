package com.jeladastudios.lostsignal.rescue;

import com.jeladastudios.lostsignal.radio.Broadcast;
import com.jeladastudios.lostsignal.radio.Clips;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Something said on the rescue frequency by someone in the story: the line's recording sent on the air from where
 * they are, if it has been recorded yet, and its words as a subtitle to the players within reach of the radio. The
 * lines are named as in the voice script (SENARYO.md): {@code H_SEE_CREEK}, {@code D_NIGHT_RULES} and so on; a line
 * recorded in several takes is {@code H_OK_01_a}, {@code _b}, ..., one picked at random.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.jeladastudios.lostsignal.LostSignal.MODID)
public final class Lines {

    private Lines() {}

    /** How far off a subtitle is shown: about as far as a hiker's handheld reaches a lookout's station. */
    static final double HEARD = 1000;
    /** How near a station a player hears it, in blocks: a room's width. */
    static final int AT = 8;

    public enum Speaker { HIKER, DISTRICT }

    public static void say(ServerLevel level, Vec3 from, Speaker speaker, String line, float power) {
        sayAs(level, from, Component.translatable("radio.fts_lostsignal.speaker." + speaker.name().toLowerCase(java.util.Locale.ROOT)),
                line, power, 1f, speaker == Speaker.DISTRICT ? Double.MAX_VALUE : HEARD);
    }

    /**
     * Something said on the rescue frequency by anyone: a keeper at another lookout, the man in a lookout no one keeps.
     * Their voice lifted or lowered by a pitch; heard within some blocks of where it is said from.
     */
    public static void sayAs(ServerLevel level, Vec3 from, Component who, String line, float power, float pitch, double reach) {
        // One thing at a time from anyone: said while they are still talking, it waits until they have finished.
        long start = turn(level, "radio:" + who.getString(), line);
        if (start > level.getGameTime()) {
            com.jeladastudios.lostsignal.tower.Speech.after(level, (int) (start - level.getGameTime()),
                    () -> air(level, from, who, line, power, pitch, reach));
        } else air(level, from, who, line, power, pitch, reach);
    }

    /** When each speaker is done talking, in game ticks: a line said waits its turn. */
    private static final java.util.Map<String, Long> BUSY = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * When a speaker may begin a line, now or once they have finished the one before; and the time taken, so the next
     * waits for this one.
     */
    public static long turn(ServerLevel level, String speaker, String line) {
        long now = level.getGameTime();
        long start = Math.max(now, BUSY.getOrDefault(speaker, 0L));
        BUSY.put(speaker, start + com.jeladastudios.lostsignal.tower.Speech.length(line));
        if (BUSY.size() > 512) BUSY.entrySet().removeIf(e -> e.getValue() < now);
        return start;
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        BUSY.clear();
    }

    private static void air(ServerLevel level, Vec3 from, Component who, String line, float power, float pitch, double reach) {
        String clip = take(line, level);
        if (clip != null) Broadcast.send(level, from, Broadcast.RESCUE, power, pitch, clip);
        Component text = Component.literal("[" + Broadcast.RESCUE + "] ").withStyle(ChatFormatting.DARK_GRAY)
                .append(who.copy().withStyle(ChatFormatting.GRAY))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable("radio.fts_lostsignal.line." + line).withStyle(ChatFormatting.WHITE));
        // Heard by whoever is at a powered station in reach: a dead set hears nothing. Its words only where the voice gets
        // through to the set (the hills between, the distance, by the radio's own reckoning), a hiss with something in it
        // where it nearly does; the District, on its repeaters, everywhere. A sleeper there is woken.
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceTo(from) > reach) continue;
            BlockPos set = Stations.poweredNear(level, p.blockPosition(), AT);
            if (set == null) continue;
            Component heard = text;
            if (reach < Double.MAX_VALUE) {
                var r = Broadcast.reception(level, from, power, set);
                if (com.jeladastudios.lostsignal.dev.TestCommands.ON) {
                    com.jeladastudios.lostsignal.LostSignal.LOGGER.info("HEARD {} from {} m away: {} dB, S{}", line,
                            (int) from.distanceTo(Vec3.atCenterOf(set)), String.format(java.util.Locale.ROOT, "%.1f", r.marginDb()), String.format(java.util.Locale.ROOT, "%.1f", r.sUnits()));
                }
                if (r.marginDb() < -NEARLY) continue;
                if (!r.heard()) heard = Component.literal("[" + Broadcast.RESCUE + "] ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.translatable("rescue.fts_lostsignal.static").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
            if (p.isSleeping()) com.jeladastudios.lostsignal.sleep.FlowingSleep.wake(level, p.position(), 1, "radio");
            p.sendSystemMessage(heard);
        }
    }

    /** How far under the noise a voice may be and still be heard to be there, in the hiss: a few dB. */
    static final double NEARLY = 6;

    /** One of a line's recorded takes, or the line itself where it was recorded once, or null where not yet. */
    public static String take(String line, ServerLevel level) {
        java.util.List<String> takes = takes(line);
        return takes.isEmpty() ? null : takes.get(level.random.nextInt(takes.size()));
    }

    /** All of a line's recorded takes: {@code _a} to {@code _e}, or the line itself recorded once; none where not yet. */
    public static java.util.List<String> takes(String line) {
        java.util.List<String> takes = new java.util.ArrayList<>();
        for (char c = 'a'; c <= 'e'; c++) if (Clips.exists(line + "_" + c)) takes.add(line + "_" + c);
        if (takes.isEmpty() && Clips.exists(line)) takes.add(line);
        return takes;
    }
}
