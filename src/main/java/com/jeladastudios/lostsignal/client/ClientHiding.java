package com.jeladastudios.lostsignal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Pose;

/** The client's side of hiding: its own player held flat on the floor, the camera with them, until told to get up. */
public final class ClientHiding {

    private ClientHiding() {}

    public static void lie(boolean down) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.setForcedPose(down ? Pose.SWIMMING : null);
    }
}
