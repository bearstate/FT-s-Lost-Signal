package com.jeladastudios.lostsignal.story;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the story's places are, worked out from the ground model on the siting's thread: Hollow Peak on the highest
 * summit in a ring round the first keeper's lookout, away from Feel the Nature's own; the planting a quarter mile east
 * of it, on the gentlest ground below the ridge.
 */
final class Sites {

    private Sites() {}

    /** The coarse grid the summits are looked for on, and how many of the highest are tried. */
    static final int STEP = 64, TRIED = 16;

    /** Hollow Peak's ground and the planting's, or null where there is no ground to read. */
    static BlockPos[] find(ServerLevel level, BlockPos from) {
        Map<Long, Integer> h = new HashMap<>();
        int reach = Story.HOLLOW_TO + STEP;
        for (int dx = -reach; dx <= reach; dx += STEP) {
            for (int dz = -reach; dz <= reach; dz += STEP) {
                double d = Math.hypot(dx, dz);
                if (d < Story.HOLLOW_FROM - STEP || d > reach) continue;
                Integer y = ground(level, from.getX() + dx, from.getZ() + dz);
                if (y != null) h.put(key(dx, dz), y);
            }
        }
        // The summits: higher than all eight round them on the grid.
        List<int[]> peaks = new ArrayList<>();
        int[] highest = null;
        for (var e : h.entrySet()) {
            int dx = (int) (e.getKey() >> 32), dz = e.getKey().intValue(), y = e.getValue();
            double d = Math.hypot(dx, dz);
            if (d < Story.HOLLOW_FROM || d > Story.HOLLOW_TO) continue;
            if (highest == null || y > highest[2]) highest = new int[]{dx, dz, y};
            boolean top = true;
            for (int i = -1; i <= 1 && top; i++) {
                for (int j = -1; j <= 1 && top; j++) {
                    if (i == 0 && j == 0) continue;
                    Integer n = h.get(key(dx + i * STEP, dz + j * STEP));
                    if (n == null || n > y) top = false;
                }
            }
            if (top) peaks.add(new int[]{dx, dz, y});
        }
        if (highest == null) return null;
        peaks.sort((a, b) -> Integer.compare(b[2], a[2]));
        BlockPos hollow = null;
        for (int i = 0; i < Math.min(TRIED, peaks.size()) && hollow == null; i++) {
            BlockPos top = climb(level, from.getX() + peaks.get(i)[0], from.getZ() + peaks.get(i)[1], peaks.get(i)[2]);
            BlockPos lookout = com.jeladastudios.lostsignal.mission.Lookouts.nearOffThread(level, top, 1);
            if (lookout == null || Math.hypot(lookout.getX() - top.getX(), lookout.getZ() - top.getZ()) > Story.APART) hollow = top;
        }
        if (hollow == null) hollow = climb(level, from.getX() + highest[0], from.getZ() + highest[1], highest[2]);
        return new BlockPos[]{hollow, planting(level, hollow)};
    }

    /**
     * The top round a point of the coarse grid (a finer search, and a finer one again), and on it the seat a tower
     * was built on: where the ground under all four footings stands highest, not the needle of rock at the very top.
     */
    static BlockPos climb(ServerLevel level, int x, int z, int y) {
        int[] best = {x, z, y};
        for (int[] pass : new int[][]{{STEP, 8}, {8, 1}}) {
            int cx = best[0], cz = best[1];
            for (int dx = -pass[0]; dx <= pass[0]; dx += pass[1]) {
                for (int dz = -pass[0]; dz <= pass[0]; dz += pass[1]) {
                    Integer h = ground(level, cx + dx, cz + dz);
                    if (h != null && h > best[2]) best = new int[]{cx + dx, cz + dz, h};
                }
            }
        }
        int cx = best[0], cz = best[1], seat = Integer.MIN_VALUE;
        int[] at = best;
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                int low = Integer.MAX_VALUE;
                for (int i = -Ruin.HALF; i <= Ruin.HALF && low > seat; i += Ruin.HALF) {
                    for (int j = -Ruin.HALF; j <= Ruin.HALF && low > seat; j += Ruin.HALF) {
                        Integer h = ground(level, cx + dx + i, cz + dz + j);
                        low = h == null ? Integer.MIN_VALUE : Math.min(low, h);
                    }
                }
                if (low > seat) {
                    seat = low;
                    at = new int[]{cx + dx, cz + dz, low};
                }
            }
        }
        return new BlockPos(at[0], at[2], at[1]);
    }

    /**
     * The planting: east of Hollow Peak by a quarter mile or so, on the gentlest ground well below its top and above
     * the water; straight east where there is nothing better.
     */
    static BlockPos planting(ServerLevel level, BlockPos hollow) {
        int sea = level.getSeaLevel();
        BlockPos best = null;
        double score = Double.MAX_VALUE;
        for (int deg = -30; deg <= 30; deg += 6) {
            double a = Math.toRadians(deg);
            for (int d = Story.PLANTING_OUT - 80; d <= Story.PLANTING_OUT + 80; d += 16) {
                int x = hollow.getX() + (int) Math.round(Math.cos(a) * d), z = hollow.getZ() + (int) Math.round(Math.sin(a) * d);
                Integer y = ground(level, x, z);
                if (y == null || y > hollow.getY() - 15 || y < sea + 3) continue;
                int slope = 0;
                for (int[] o : new int[][]{{8, 0}, {-8, 0}, {0, 8}, {0, -8}}) {
                    Integer n = ground(level, x + o[0], z + o[1]);
                    slope = Math.max(slope, n == null ? 99 : Math.abs(n - y));
                }
                double s = slope + Math.abs(d - Story.PLANTING_OUT) / 40.0;
                if (s < score) {
                    score = s;
                    best = new BlockPos(x, y, z);
                }
            }
        }
        if (best != null) return best;
        Integer y = ground(level, hollow.getX() + Story.PLANTING_OUT, hollow.getZ());
        return new BlockPos(hollow.getX() + Story.PLANTING_OUT, y == null ? hollow.getY() : y, hollow.getZ());
    }

    static Integer ground(ServerLevel level, int x, int z) {
        return com.jeladastudios.lostsignal.tower.Fires.ground(level, x, z);
    }

    static long key(int dx, int dz) {
        return ((long) dx << 32) ^ (dz & 0xFFFFFFFFL);
    }
}
