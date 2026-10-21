package com.jeladastudios.lostsignal.rescue;

import com.jeladastudios.lostsignal.entity.Hiker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * What a hiker can see from where they stand, as they would tell it over the radio: a rescue board on a tree, the
 * trail, the lookout or its light, a drop, water, snow, a ridge, the trees, rock, a meadow -- the first of these there
 * is, as a line of the voice script. And where the things they are told to follow lie.
 */
public final class Sight {

    private Sight() {}

    /** The line a hiker answers "where are you?" with. */
    public static String describe(ServerLevel level, Hiker h) {
        BlockPos at = h.blockPosition();
        if (sign(level, at, 10) != null) return "H_SEE_SIGN";
        if (trail(level, at, 4) != null) return "H_SEE_TRAIL";
        BlockPos tower = h.tower();
        // By day the tower; by night only its lamp, if it is lit.
        if (tower != null && (level.isDay() || com.jeladastudios.lostsignal.finder.Lamps.lit(level, tower)) && seesTower(level, h, tower)) {
            return level.isDay() ? "H_SEE_TOWER" : "H_SEE_LIGHT";
        }
        if (drop(level, at) >= 6) return "H_SEE_CLIFF";
        var river = river(at, 14);
        if (river.isPresent()) {
            var r = river.get();
            if (r.lake()) return "H_SEE_LAKE";
            return r.halfWidth() > 4 ? "H_SEE_RIVER" : "H_SEE_CREEK";
        }
        if (water(level, at, 8)) return "H_SEE_CREEK";
        if (level.getBlockState(at).is(Blocks.SNOW) || level.getBlockState(at.below()).is(Blocks.SNOW_BLOCK)) return "H_SEE_SNOW";
        if (ridge(level, at)) return "H_SEE_RIDGE";
        if (canopy(level, at) >= 6) return "H_SEE_TREES";
        var ground = level.getBlockState(at.below());
        if (ground.is(BlockTags.BASE_STONE_OVERWORLD) || ground.is(Blocks.GRAVEL) || ground.is(Blocks.COBBLESTONE)) return "H_SEE_ROCKS";
        if (ground.is(Blocks.GRASS_BLOCK)) return "H_SEE_MEADOW";
        return "H_SEE_NOTHING";
    }

    /** A rescue board within a distance: a sign with the rescue frequency on it. */
    public static BlockPos sign(ServerLevel level, BlockPos at, int within) {
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-within, -3, -within), at.offset(within, 5, within))) {
            if (level.getBlockEntity(p) instanceof SignBlockEntity sign) {
                for (var line : sign.getFrontText().getMessages(false)) {
                    if (line.getString().contains("145.500")) return p.immutable();
                }
            }
        }
        return null;
    }

    /** The nearest worn ground of a trail within a distance. */
    public static BlockPos trail(ServerLevel level, BlockPos at, int within) {
        BlockPos best = null;
        double nearest = Double.MAX_VALUE;
        for (int dx = -within; dx <= within; dx++) {
            for (int dz = -within; dz <= within; dz++) {
                int x = at.getX() + dx, z = at.getZ() + dz;
                BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
                if (!level.getBlockState(top).is(Blocks.DIRT_PATH)) continue;
                double d = top.distSqr(at);
                if (d < nearest) {
                    nearest = d;
                    best = top.above();
                }
            }
        }
        return best;
    }

    /** Whether the lookout's cab is in sight from the hiker's eyes, within a long walk. */
    public static boolean seesTower(ServerLevel level, Hiker h, BlockPos tower) {
        Vec3 cab = Vec3.atCenterOf(tower).add(0, 22, 0);
        Vec3 eye = h.getEyePosition();
        if (cab.distanceTo(eye) > 400) return false;
        return level.clip(new ClipContext(eye, cab, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, h)).getType() == HitResult.Type.MISS
                || level.clip(new ClipContext(eye, cab, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, h)).getLocation().distanceTo(cab) < 6;
    }

    /** The deepest drop at the hiker's feet, looking three blocks out each way. */
    public static int drop(ServerLevel level, BlockPos at) {
        int deepest = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = at.relative(d, 3);
            deepest = Math.max(deepest, at.getY() - level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()));
        }
        return deepest;
    }

    /** FT's Geology's river or lake within a distance. */
    public static Optional<com.jeladastudios.ftsgeology.api.FtsGeologyApi.River> river(BlockPos at, double within) {
        try {
            return com.jeladastudios.ftsgeology.api.FtsGeologyApi.river(at.getX(), at.getZ(), within);
        } catch (LinkageError e) {
            return Optional.empty();
        }
    }

    /** Open water at the surface within a distance. */
    static boolean water(ServerLevel level, BlockPos at, int within) {
        for (int dx = -within; dx <= within; dx += 2) {
            for (int dz = -within; dz <= within; dz += 2) {
                int x = at.getX() + dx, z = at.getZ() + dz;
                BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
                if (!level.getFluidState(top).isEmpty()) return true;
            }
        }
        return false;
    }

    /** Standing well over the ground round them, twenty-four blocks out. */
    static boolean ridge(ServerLevel level, BlockPos at) {
        double sum = 0;
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            int x = at.getX() + (int) Math.round(Math.cos(a) * 24), z = at.getZ() + (int) Math.round(Math.sin(a) * 24);
            sum += level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        }
        return at.getY() - sum / 8 > 8;
    }

    /** How many of twelve points round them stand under leaves. */
    static int canopy(ServerLevel level, BlockPos at) {
        int under = 0;
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            int r = i % 2 == 0 ? 4 : 7;
            int x = at.getX() + (int) Math.round(Math.cos(a) * r), z = at.getZ() + (int) Math.round(Math.sin(a) * r);
            if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 2) under++;
        }
        return under;
    }

    /** The way downhill from a place, as a unit vector, sixteen blocks out; null on the flat. */
    public static Vec3 downhill(ServerLevel level, BlockPos at) {
        Vec3 best = null;
        int lowest = at.getY() - 2;
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            int x = at.getX() + (int) Math.round(Math.cos(a) * 16), z = at.getZ() + (int) Math.round(Math.sin(a) * 16);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y < lowest) {
                lowest = y;
                best = new Vec3(Math.cos(a), 0, Math.sin(a));
            }
        }
        return best;
    }
}
