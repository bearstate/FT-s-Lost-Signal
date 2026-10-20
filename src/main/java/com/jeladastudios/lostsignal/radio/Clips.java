package com.jeladastudios.lostsignal.radio;

import com.codinglitch.simpleradio.radio.CommonRadioPlugin;
import com.jeladastudios.lostsignal.LostSignal;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The recorded lines and sounds the radio carries, as frames of 960 samples (20 ms at 48 kHz), from
 * {@code /data/fts_lostsignal/radio/<name>.opus} in the jar. Decoded once, on first use, and kept.
 *
 * <p>The radio drops a frame as silence when it has almost nothing in it; a line's pauses are given a faint hiss so
 * they go out as the pauses of a live voice and do not cut the carrier.</p>
 */
public final class Clips {

    private Clips() {}

    public static final int FRAME = 960;
    /** The hiss in a pause: a few steps of a sixteen-bit sample, under anything heard. */
    private static final int HISS = 12;

    private static final Map<String, List<short[]>> KEPT = new ConcurrentHashMap<>();

    /** A clip's frames, or an empty list where there is no such clip or no decoder yet. */
    public static List<short[]> frames(String name) {
        List<short[]> kept = KEPT.get(name);
        if (kept != null) return kept;
        List<short[]> frames = decode(name);
        if (!frames.isEmpty()) KEPT.put(name, frames);
        return frames;
    }

    public static boolean exists(String name) {
        return Clips.class.getResource(path(name)) != null;
    }

    private static String path(String name) {
        return "/data/" + LostSignal.MODID + "/radio/" + name + ".opus";
    }

    private static List<short[]> decode(String name) {
        if (CommonRadioPlugin.serverApi == null) return List.of();
        try (InputStream in = Clips.class.getResourceAsStream(path(name))) {
            if (in == null) {
                LostSignal.LOGGER.warn("No radio clip {}", name);
                return List.of();
            }
            OpusDecoder decoder = CommonRadioPlugin.serverApi.createDecoder();
            List<short[]> frames = new ArrayList<>();
            java.util.Random hiss = new java.util.Random(name.hashCode());
            try {
                for (byte[] packet : OggOpus.packets(in)) {
                    short[] pcm = decoder.decode(packet);
                    if (pcm == null || pcm.length == 0) continue;
                    short[] frame = pcm.length == FRAME ? pcm : java.util.Arrays.copyOf(pcm, FRAME);
                    if (CommonRadioPlugin.analyzeActivity(frame) <= 10f) {
                        for (int i = 0; i < FRAME; i++) frame[i] += (short) (hiss.nextInt(2 * HISS + 1) - HISS);
                    }
                    frames.add(frame);
                }
            } finally {
                decoder.close();
            }
            return frames;
        } catch (Exception e) {
            LostSignal.LOGGER.warn("Radio clip {} could not be read: {}", name, e.toString());
            return List.of();
        }
    }
}
