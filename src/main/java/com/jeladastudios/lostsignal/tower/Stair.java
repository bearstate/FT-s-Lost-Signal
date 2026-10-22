package com.jeladastudios.lostsignal.tower;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * A lookout's stair as Feel the Nature lays it out, for whoever walks it: its blocks from the foot, x out through the
 * cab's door and z to the door's right as one goes out; and the places to make for on the way up and down. A walker's
 * path up twenty blocks of switchback is too long to be found in one go; landing to landing, it is not.
 */
public final class Stair {

    private Stair() {}

    /** Where to stand on the way up: the bottom landing, two more, the head of the stair on the walkway, round its corner. */
    static final int[][] UP = {{-2, 0, -1}, {2, 9, -1}, {-2, 18, -1}, {2, 21, -4}, {4, 21, -4}};

    /** A block of the lookout, from its foot and the way out through its door. */
    public static BlockPos at(BlockPos foot, Direction out, int x, int y, int z) {
        return foot.relative(out, x).above(y).relative(out.getClockWise(), z);
    }

    /**
     * Whether the lookout's stair comes up outside the cab onto the walkway: the new way, which a walker can take to
     * the door. One built before, its stair still into the cab under a hatch, cannot be walked up by them.
     */
    public static boolean walkable(ServerLevel level, BlockPos foot, Direction out) {
        return level.getBlockState(at(foot, out, 0, 20, -4)).isAir()
                && level.getBlockState(at(foot, out, 1, 20, -4)).getBlock() instanceof net.minecraft.world.level.block.StairBlock;
    }

    /** The way up from the foot to the walkway, ending outside the door. */
    public static List<BlockPos> up(BlockPos foot, Direction out) {
        List<BlockPos> way = new ArrayList<>();
        for (int[] p : UP) way.add(at(foot, out, p[0], p[1], p[2]));
        way.add(outside(foot, out));
        return way;
    }

    /** The way down from outside the door to the foot. */
    public static List<BlockPos> down(BlockPos foot, Direction out) {
        List<BlockPos> way = new ArrayList<>();
        for (int i = UP.length - 1; i >= 0; i--) way.add(at(foot, out, UP[i][0], UP[i][1], UP[i][2]));
        return way;
    }

    /** On the walkway before the cab's door. */
    public static BlockPos outside(BlockPos foot, Direction out) {
        return at(foot, out, 4, 21, 0);
    }
}
