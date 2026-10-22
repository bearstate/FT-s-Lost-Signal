package com.jeladastudios.lostsignal.client;

import com.jeladastudios.lostsignal.LostSignal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The client's side of hiding: its own player held flat on the floor, the camera with them, until told to get up; and
 * the view from under a bed. The underside of its boards dark across the top of the sight, its legs and the shadow
 * closing in from the sides, the room out there at floor height in the strip between; no hands, nothing carried, no
 * bars at the foot of the screen.
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID, value = Dist.CLIENT)
public final class ClientHiding {

    private ClientHiding() {}

    private static boolean down;

    public static void lie(boolean lying) {
        down = lying;
        var p = Minecraft.getInstance().player;
        if (p != null) p.setForcedPose(lying ? Pose.SWIMMING : null);
    }

    @SubscribeEvent
    public static void hand(RenderHandEvent event) {
        if (down) event.setCanceled(true);
    }

    /** The game's own bars and the crosshair not drawn while under the bed. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void overlay(RenderGuiOverlayEvent.Pre event) {
        if (!down) return;
        var id = event.getOverlay().id();
        if (id.equals(VanillaGuiOverlay.HOTBAR.id()) || id.equals(VanillaGuiOverlay.CROSSHAIR.id())
                || id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id()) || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
                || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id()) || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())
                || id.equals(VanillaGuiOverlay.AIR_LEVEL.id()) || id.equals(VanillaGuiOverlay.ITEM_NAME.id())) {
            event.setCanceled(true);
        }
    }

    /** The bed above and about, while lying under it: drawn first of the overlays (see ClientSetup). */
    static void draw(GuiGraphics g, int w, int h) {
        if (down) underBed(g, w, h);
    }

    static void underBed(GuiGraphics g, int w, int h) {
        int edge = (int) (h * 0.36);
        // Its boards: dark, the slats across with a thin gap of a little less dark between, nearer ones wider apart.
        g.fill(0, 0, w, edge, 0xF8120D09);
        for (int y = edge - 6, gap = 5; y > 0; y -= gap, gap += 3) g.fill(0, y, w, y + 1, 0xF81C150F);
        // The side rail of the frame, nearest, at the bottom edge of the boards; the shadow under it.
        g.fill(0, edge - 4, w, edge, 0xFA070504);
        g.fillGradient(0, edge, w, edge + h / 6, 0xC0000000, 0x00000000);
        // The legs and the dark under the rest of the bed, closing in from either side.
        int side = w / 5;
        for (int i = 0; i < side; i += 2) {
            int a = (int) (0xE8 * Math.pow(1 - (double) i / side, 1.6));
            g.fill(i, 0, i + 2, h, a << 24);
            g.fill(w - i - 2, 0, w - i, h, a << 24);
        }
        g.fill(side / 3, edge, side / 3 + Math.max(4, w / 60), h, 0xF0080605);
        g.fill(w - side / 3 - Math.max(4, w / 60), edge, w - side / 3, h, 0xF0080605);
        // And the floor nearest, in its own shadow.
        g.fillGradient(0, h - h / 8, w, h, 0x00000000, 0x90000000);
    }
}
