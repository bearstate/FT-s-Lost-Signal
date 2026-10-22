package com.jeladastudios.lostsignal.radio;

import com.codinglitch.simpleradio.radio.CommonRadioPlugin;
import com.jeladastudios.lostsignal.rescue.Lines;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.UUID;

/**
 * Something said aloud, not over the radio: the line's recording played from the speaker where they stand, through
 * Simple Voice Chat, so it is heard as a voice nearby is (from that way, fainter further off) and its words as a
 * subtitle to the players within earshot.
 */
public final class Voice {

    private Voice() {}

    /**
     * A line said by someone, heard within some blocks of them. Their voice is lifted or lowered by a pitch (1: as
     * recorded), so the one voice that records them all is many people.
     */
    public static void say(Entity speaker, Component who, String line, double reach, float pitch) {
        if (!(speaker.level() instanceof ServerLevel level)) return;
        short[] pcm = recording(level, line, pitch);
        if (pcm != null) {
            var api = CommonRadioPlugin.serverApi;
            var channel = api.createEntityAudioChannel(UUID.randomUUID(), api.fromEntity(speaker));
            if (channel != null) {
                channel.setDistance((float) reach);
                api.createAudioPlayer(channel, api.createEncoder(), pcm).startPlaying();
            }
        }
        subtitle(level, speaker.position(), who, line, reach);
    }

    /**
     * A sound someone makes, heard from where they are and following them, with no words to show: breath as they walk
     * past. Nothing where it is not recorded.
     */
    public static void sound(Entity speaker, String line, double reach) {
        if (!(speaker.level() instanceof ServerLevel level)) return;
        short[] pcm = recording(level, line, 1f);
        if (pcm == null) return;
        var api = CommonRadioPlugin.serverApi;
        var channel = api.createEntityAudioChannel(UUID.randomUUID(), api.fromEntity(speaker));
        if (channel == null) return;
        channel.setDistance((float) reach);
        api.createAudioPlayer(channel, api.createEncoder(), pcm).startPlaying();
    }

    /** A line said from a place rather than by someone there: whoever said it may be gone by the time it ends. */
    public static void sayAt(ServerLevel level, net.minecraft.world.phys.Vec3 at, Component who, String line, double reach, float pitch) {
        short[] pcm = recording(level, line, pitch);
        if (pcm != null) {
            var api = CommonRadioPlugin.serverApi;
            var channel = api.createLocationalAudioChannel(UUID.randomUUID(), api.fromServerLevel(level), api.createPosition(at.x, at.y, at.z));
            if (channel != null) {
                channel.setDistance((float) reach);
                api.createAudioPlayer(channel, api.createEncoder(), pcm).startPlaying();
            }
        }
        subtitle(level, at, who, line, reach);
    }

    /** One of a line's takes as a voice says it, at a pitch; null where it is not recorded or the voice is not up. */
    private static short[] recording(ServerLevel level, String line, float pitch) {
        String take = Lines.take(line, level);
        if (take == null || CommonRadioPlugin.serverApi == null) return null;
        List<short[]> frames = Clips.frames(take);
        if (frames.isEmpty()) return null;
        short[] pcm = new short[frames.size() * Clips.FRAME];
        for (int i = 0; i < frames.size(); i++) System.arraycopy(frames.get(i), 0, pcm, i * Clips.FRAME, Clips.FRAME);
        return pitch != 1f ? pitched(pcm, pitch) : pcm;
    }

    private static void subtitle(ServerLevel level, net.minecraft.world.phys.Vec3 at, Component who, String line, double reach) {
        Component text = who.copy().withStyle(ChatFormatting.GRAY)
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable("radio.fts_lostsignal.line." + line).withStyle(ChatFormatting.WHITE));
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceTo(at) <= reach) p.sendSystemMessage(text);
        }
    }

    /**
     * A voice lifted or lowered: played faster or slower, as a tape is. A few hundredths either way and it is someone
     * else's voice, not a cartoon's.
     */
    public static short[] pitched(short[] pcm, float pitch) {
        int n = (int) (pcm.length / pitch);
        n -= n % Clips.FRAME;
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            double at = i * (double) pitch;
            int a = (int) at;
            double f = at - a;
            short s0 = pcm[Math.min(a, pcm.length - 1)], s1 = pcm[Math.min(a + 1, pcm.length - 1)];
            out[i] = (short) Math.round(s0 + (s1 - s0) * f);
        }
        return out;
    }
}
