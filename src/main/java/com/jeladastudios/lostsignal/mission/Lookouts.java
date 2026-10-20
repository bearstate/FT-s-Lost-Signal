package com.jeladastudios.lostsignal.mission;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Feel the Nature's lookouts, found by its own siting -- from the ground model, as the world was made with -- and
 * read with the true ground. FT's Geology answers on the server's own thread from a blunter ground unless a search
 * for the true one is under way, and a lookout sited on the blunt ground is not where the world built it.
 */
public final class Lookouts {

    private Lookouts() {}

    /** The nearest lookout's foot within some cells of a place, or null. */
    public static BlockPos near(ServerLevel level, BlockPos at, int cells) {
        boolean exact = false;
        try {
            com.jeladastudios.ftsgeology.worldgen.relief.ServerThreadGround.enterExact();
            exact = true;
        } catch (LinkageError e) {
            // An FT's Geology without it: its ground is exact everywhere.
        }
        try {
            var found = com.jeladastudios.feelthenature.world.LookoutTowerFeature.locate(level, at, cells);
            return found == null ? null : found.foot();
        } catch (LinkageError e) {
            return null;
        } finally {
            if (exact) com.jeladastudios.ftsgeology.worldgen.relief.ServerThreadGround.exitExact();
        }
    }
}
