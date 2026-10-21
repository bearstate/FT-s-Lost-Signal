package com.jeladastudios.lostsignal.signal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Whether one place can be seen from another across the country (a flare from a lookout, a lookout's lamp from a
 * lost hiker) without loading the ground between. Where the ground is loaded, by its blocks; where it is not, by FT's
 * Geology's model of it: a ridge in the way hides what is behind it, whether or not anyone has been there.
 * Minecraft's own ray would load, or make, every chunk along a line hundreds of blocks long.
 */
public final class Sightline {

    private Sightline() {}

    /** Whether the line between two places is clear: of blocks where loaded, of ground everywhere. */
    public static boolean clear(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double length = d.length();
        if (length < 1) return true;
        Vec3 step = d.scale(1 / length);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        // Not the last few blocks at either end: the looker's own walls, and what is looked at.
        for (double t = 2; t < length - 2; t += 1) {
            double x = from.x + step.x * t, y = from.y + step.y * t, z = from.z + step.z * t;
            p.set(x, y, z);
            if (level.hasChunkAt(p)) {
                var s = level.getBlockState(p);
                if (!s.isAir() && !s.getVisualShape(level, p, net.minecraft.world.phys.shapes.CollisionContext.empty()).isEmpty()
                        && s.canOcclude()) return false;
            } else if (((int) t) % 8 == 0 && y < ground(level, (int) Math.floor(x), (int) Math.floor(z))) {
                return false;
            }
        }
        return true;
    }

    /** The ground's height by FT's Geology's model, or no height at all where it gives none. */
    static double ground(ServerLevel level, int x, int z) {
        try {
            double h = com.jeladastudios.feelthenature.GeologyBridge.groundHeight(level, x, z);
            return Double.isNaN(h) ? Double.NEGATIVE_INFINITY : h;
        } catch (LinkageError e) {
            return Double.NEGATIVE_INFINITY;
        }
    }
}
