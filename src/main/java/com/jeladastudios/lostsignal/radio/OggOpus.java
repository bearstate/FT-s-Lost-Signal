package com.jeladastudios.lostsignal.radio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The Opus packets of an Ogg Opus file, in order, without its two header packets ({@code OpusHead}, {@code OpusTags}).
 * The voice lines are kept so: small in the jar, and decoded on the server by Simple Voice Chat's own Opus decoder,
 * which every side that runs the radio has. They are encoded at 48 kHz mono in 20 ms frames, as the radio carries
 * them, so each packet is one frame of 960 samples.
 */
public final class OggOpus {

    private OggOpus() {}

    public static List<byte[]> packets(InputStream in) throws IOException {
        byte[] all = in.readAllBytes();
        List<byte[]> packets = new ArrayList<>();
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        int at = 0;
        while (at + 27 <= all.length) {
            if (all[at] != 'O' || all[at + 1] != 'g' || all[at + 2] != 'g' || all[at + 3] != 'S') {
                throw new IOException("not an Ogg page at " + at);
            }
            int segments = all[at + 26] & 0xFF;
            int body = at + 27 + segments;
            for (int s = 0; s < segments; s++) {
                int len = all[at + 27 + s] & 0xFF;
                packet.write(all, body, len);
                body += len;
                // A lacing value under 255 ends the packet; 255 runs it on into the next segment, or the next page.
                if (len < 255) {
                    packets.add(packet.toByteArray());
                    packet.reset();
                }
            }
            at = body;
        }
        // The two header packets.
        if (packets.size() >= 2 && startsWith(packets.get(0), "OpusHead")) packets.remove(0);
        if (!packets.isEmpty() && startsWith(packets.get(0), "OpusTags")) packets.remove(0);
        return packets;
    }

    private static boolean startsWith(byte[] b, String s) {
        if (b.length < s.length()) return false;
        for (int i = 0; i < s.length(); i++) if (b[i] != s.charAt(i)) return false;
        return true;
    }
}
