package com.jeladastudios.lostsignal.rescue;

import com.jeladastudios.lostsignal.radio.Broadcast;
import com.jeladastudios.lostsignal.radio.Clips;
import net.minecraft.ChatFormatting;
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
public final class Lines {

    private Lines() {}

    /** How far off a subtitle is shown: about as far as a hiker's handheld reaches a lookout's station. */
    static final double HEARD = 1000;

    public enum Speaker { HIKER, DISTRICT }

    public static void say(ServerLevel level, Vec3 from, Speaker speaker, String line, float power) {
        String clip = take(line, level);
        if (clip != null) Broadcast.send(level, from, Broadcast.RESCUE, power, clip);
        Component text = Component.literal("[" + Broadcast.RESCUE + "] ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.translatable("radio.fts_lostsignal.speaker." + speaker.name().toLowerCase(java.util.Locale.ROOT))
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable("radio.fts_lostsignal.line." + line).withStyle(ChatFormatting.WHITE));
        for (ServerPlayer p : level.players()) {
            if (speaker == Speaker.DISTRICT || p.position().distanceTo(from) < HEARD) p.sendSystemMessage(text);
        }
    }

    /** One of a line's recorded takes, or the line itself where it was recorded once, or null where not yet. */
    static String take(String line, ServerLevel level) {
        java.util.List<String> takes = new java.util.ArrayList<>();
        for (char c = 'a'; c <= 'e'; c++) if (Clips.exists(line + "_" + c)) takes.add(line + "_" + c);
        if (!takes.isEmpty()) return takes.get(level.random.nextInt(takes.size()));
        return Clips.exists(line) ? line : null;
    }
}
