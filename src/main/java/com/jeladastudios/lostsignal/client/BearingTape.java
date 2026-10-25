package com.jeladastudios.lostsignal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A compass reticle in the spyglass, as lookouts' binoculars have: a tape of bearings across the top of the view, a
 * tick a degree, a number every five, the compass points, and a mark in the middle over the bearing looked along. Its
 * degrees as wide as the view's own, so a mark on the tape is over what lies on that bearing.
 */
public final class BearingTape {

    private BearingTape() {}

    static final int GREEN = 0xE0A8FFB8, DIM = 0x9068C080;

    public static void draw(GuiGraphics g, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isScoping() || !mc.options.getCameraType().isFirstPerson()) return;
        // The view's width in degrees: the spyglass's field, a tenth of the usual, as wide as the screen is.
        double vertical = mc.options.fov().get() * 0.1;
        double horizontal = Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(vertical / 2)) * width / (double) height));
        double perDegree = width / horizontal;
        double bearing = Math.floorMod(Math.round((mc.player.getYRot() + 180f) * 10), 3600) / 10.0;
        int y = 14, middle = width / 2;
        var font = mc.font;
        int span = (int) Math.ceil(horizontal / 2) + 1;
        for (int d = (int) Math.floor(bearing) - span; d <= (int) Math.ceil(bearing) + span; d++) {
            int x = (int) Math.round(middle + (d - bearing) * perDegree);
            if (x < 4 || x > width - 4) continue;
            int deg = Math.floorMod(d, 360);
            boolean five = deg % 5 == 0;
            g.fill(x, y, x + 1, y + (five ? 6 : 3), five ? GREEN : DIM);
            if (five) {
                String label = deg % 45 == 0 ? point(deg) + " " + deg : String.valueOf(deg);
                g.drawString(font, label, x - font.width(label) / 2, y + 8, deg % 45 == 0 ? GREEN : DIM, false);
            }
        }
        // The middle: a mark down onto the tape, and the bearing read off it.
        g.fill(middle, y - 6, middle + 1, y + 8, 0xF0FFE070);
        Component read = Component.translatable("spyglass.fts_lostsignal.bearing", String.format(java.util.Locale.ROOT, "%03d", (int) Math.round(bearing) % 360));
        g.drawString(font, read, middle - font.width(read) / 2, y + 22, 0xF0FFE070, false);
    }

    static String point(int deg) {
        return Component.translatable("radio.fts_lostsignal.point_short." + com.jeladastudios.lostsignal.finder.FireFinder.point(deg)).getString();
    }
}
