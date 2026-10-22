package com.jeladastudios.lostsignal.remains;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.block.ModBlocks;
import com.jeladastudios.lostsignal.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * What the morning finds where a hiker was taken in the night. Their fire out and cold, still smoking a little; their
 * tent down, the cloth of it on the ground and one pole left standing; their things thrown about, the lamp left
 * burning in the grass; dark splashes on the ground, and a smear leading off from the tent into the trees, footprints
 * beside it, and where the smear ends, twenty to sixty blocks off, the hiker, face down. Or, one time in three, no
 * one: the smear goes to the edge of a drop, or into the thick of the trees, and stops.
 */
public final class Scene {

    private Scene() {}

    /** The tents' cloth: bright, as a tent bought for a weekend is. */
    static final Block[] CLOTH = {Blocks.ORANGE_CARPET, Blocks.LIME_CARPET, Blocks.LIGHT_BLUE_CARPET, Blocks.YELLOW_CARPET, Blocks.RED_CARPET};

    /** Where it was set, and where the trail ends: at the body, or where there is none. */
    public record Made(BlockPos camp, BlockPos end, boolean body) {}

    /**
     * Set out round a place: the hiker's camp. Their fire, if they had one, is put out where it is; else one is put
     * beside them.
     */
    public static Made make(ServerLevel level, BlockPos at, BlockPos fire, int kit, String name, BlockPos tower, int journal,
                            boolean withBody, RandomSource random) {
        return make(level, at, fire, kit, name, tower, journal, withBody, random, null);
    }

    /** The same, with their last night for the journal's page of it. */
    public static Made make(ServerLevel level, BlockPos at, BlockPos fire, int kit, String name, BlockPos tower, int journal,
                            boolean withBody, RandomSource random, Journal.Night night) {
        BlockPos camp = ground(level, at.getX(), at.getZ());
        if (camp == null) camp = at;
        // The fire, out.
        if (fire != null && level.getBlockState(fire).is(Blocks.CAMPFIRE)) {
            level.setBlock(fire, level.getBlockState(fire).setValue(CampfireBlock.LIT, false).setValue(CampfireBlock.SIGNAL_FIRE, false), 3);
        } else {
            fire = free(level, camp, 1, 2, random);
            if (fire != null) level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), 3);
        }
        // The tent: its cloth down, on the far side of the camp from the fire.
        Direction way = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        BlockPos tent = camp.relative(way, 2);
        BlockState cloth = CLOTH[random.nextInt(CLOTH.length)].defaultBlockState();
        Direction side = way.getClockWise();
        for (int a = 0; a < 3; a++) {
            for (int b = 0; b < 2; b++) {
                if (random.nextFloat() < 0.2f) continue;
                BlockPos p = tent.relative(way, a).relative(side, b);
                // Some of it pulled off askew.
                if (random.nextFloat() < 0.2f) p = p.relative(Direction.Plane.HORIZONTAL.getRandomDirection(random));
                put(level, p, cloth);
            }
        }
        BlockPos pole = tent.relative(way, random.nextBoolean() ? 0 : 2).relative(side, random.nextBoolean() ? -1 : 2);
        put(level, pole, Blocks.SPRUCE_FENCE.defaultBlockState());
        // The lamp set down in the grass, still lit; their things about.
        BlockPos lamp = free(level, camp, 2, 4, random);
        if (lamp != null) level.setBlock(lamp, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), 3);
        drop(level, camp, new ItemStack(Items.FIREWORK_ROCKET).setHoverName(Component.translatable("item.fts_lostsignal.flare")), random);
        drop(level, camp, new ItemStack(Items.BREAD), random);
        drop(level, camp, net.minecraft.world.item.alchemy.PotionUtils.setPotion(new ItemStack(Items.POTION),
                net.minecraft.world.item.alchemy.Potions.WATER), random);
        if (random.nextBoolean()) drop(level, camp, new ItemStack(Items.COMPASS), random);
        // Dark splashes about the tent.
        int splashes = 4 + random.nextInt(4);
        for (int i = 0; i < splashes; i++) {
            BlockPos p = free(level, tent, 0, 3, random);
            if (p != null) stain(level, p, random.nextInt(StainBlock.SPLASHES), Direction.Plane.HORIZONTAL.getRandomDirection(random), false);
        }
        // His mark, some of the time: black candles set in a ring by the fire, burnt down, out.
        if (random.nextFloat() < 0.6f) {
            int candles = 4 + random.nextInt(3);
            double turn = random.nextDouble() * Math.PI * 2;
            for (int i = 0; i < candles; i++) {
                double a = turn + Math.PI * 2 * i / candles;
                BlockPos p = ground(level, (int) Math.floor(camp.getX() + 0.5 + Math.cos(a) * 3), (int) Math.floor(camp.getZ() + 0.5 + Math.sin(a) * 3));
                if (p != null && level.getBlockState(p).canBeReplaced()) {
                    level.setBlock(p, Blocks.BLACK_CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 1 + random.nextInt(2)), 3);
                }
            }
        }
        // The smear: off into the thickest trees within reach, or down to a drop. It goes block to block, each piece
        // of it straight on, turning, or ending, so that it runs on unbroken from one block into the next.
        double heading = towardTrees(level, camp, random);
        int length = 20 + random.nextInt(41);
        boolean body = withBody;
        BlockPos start = ground(level, tent.getX(), tent.getZ());
        java.util.List<BlockPos> path = new java.util.ArrayList<>();
        java.util.List<Direction> moves = new java.util.ArrayList<>();
        if (start != null) {
            path.add(start);
            BlockPos cell = start;
            double fx = start.getX() + 0.5, fz = start.getZ() + 0.5;
            walk:
            while (path.size() < length) {
                // It wanders a little, as a thing dragged does.
                heading += (random.nextDouble() - 0.5) * 0.25;
                fx += Math.cos(heading);
                fz += Math.sin(heading);
                int tx = (int) Math.floor(fx), tz = (int) Math.floor(fz);
                while (cell.getX() != tx || cell.getZ() != tz) {
                    int dx = tx - cell.getX(), dz = tz - cell.getZ();
                    Direction d = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
                    BlockPos next = ground(level, cell.getX() + d.getStepX(), cell.getZ() + d.getStepZ());
                    if (next == null || next.getY() > cell.getY() + 2) break walk;
                    // Over an edge: the smear stops at the top, and there is no one to find.
                    if (next.getY() < cell.getY() - 4) {
                        body = false;
                        break walk;
                    }
                    moves.add(d);
                    path.add(next);
                    cell = next;
                }
            }
        }
        java.util.Set<BlockPos> onPath = new java.util.HashSet<>(path);
        for (int i = 0; i < path.size() && !moves.isEmpty(); i++) {
            int kind;
            Direction facing;
            if (i == 0) {
                // Its start, thin at the tent and full where it leaves.
                kind = StainBlock.END;
                facing = moves.get(0).getOpposite();
            } else if (i == path.size() - 1) {
                kind = StainBlock.END;
                facing = moves.get(i - 1);
            } else {
                Direction in = moves.get(i - 1), out = moves.get(i);
                facing = in;
                kind = out == in.getClockWise() ? StainBlock.RIGHT : out == in.getCounterClockWise() ? StainBlock.LEFT
                        : StainBlock.STRAIGHT + random.nextInt(2);
            }
            stain(level, path.get(i), kind, facing, true);
            // The steps of whoever dragged it beside it, now one side, now the other, every few blocks.
            if (i > 0 && i % 3 == 0) {
                Direction along = moves.get(i - 1);
                Direction aside = (i / 3) % 2 == 0 ? along.getClockWise() : along.getCounterClockWise();
                BlockPos foot = ground(level, path.get(i).getX() + aside.getStepX(), path.get(i).getZ() + aside.getStepZ());
                if (foot != null && !onPath.contains(foot)) stain(level, foot, StainBlock.PRINT, along, false);
            }
        }
        BlockPos end = path.isEmpty() ? tent : path.get(path.size() - 1);
        // Steps at the edge of the camp, coming in from the trees.
        double in = heading + Math.PI + (random.nextDouble() - 0.5);
        for (int i = 0; i < 4; i++) {
            double far = 10 - i * 2.6;
            BlockPos p = ground(level, (int) Math.floor(camp.getX() + Math.cos(in) * far), (int) Math.floor(camp.getZ() + Math.sin(in) * far));
            if (p != null && !onPath.contains(p)) stain(level, p, StainBlock.PRINT, Direction.fromYRot(Math.toDegrees(in + Math.PI) - 90), false);
        }
        // Who is left: the body at the smear's end, face down, head on; or the scene's keeper by the fire.
        Direction lies = moves.isEmpty() ? Direction.fromYRot(Math.toDegrees(heading) - 90) : moves.get(moves.size() - 1);
        BlockPos lying = body ? level(level, end, lies) : camp;
        Remains r = ModEntities.REMAINS.get().create(level);
        if (r != null) {
            Vec3 place = Vec3.atBottomCenterOf(lying);
            // Along the way it was dragged, a little askew.
            r.moveTo(place.x, place.y, place.z, lies.toYRot() + (random.nextFloat() - 0.5f) * 30f, 0f);
            r.set(kit, body, name, fire, tower, journal, night);
            level.addFreshEntity(r);
        }
        if (body) {
            // Dark on the ground under and about them.
            for (BlockPos p : new BlockPos[]{lying, lying.relative(lies), lying.relative(lies.getOpposite()),
                    lying.relative(lies.getClockWise()), lying.relative(lies.getCounterClockWise())}) {
                if (p != lying && random.nextFloat() < 0.4f) continue;
                BlockPos g = ground(level, p.getX(), p.getZ());
                if (g != null) stain(level, g, random.nextInt(StainBlock.SPLASHES), Direction.Plane.HORIZONTAL.getRandomDirection(random), true);
            }
        }
        if (!body) {
            // The journal lies in the tent, then.
            drop(level, tent, Journal.of(name, journal, night), random);
        }
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) {
            LostSignal.LOGGER.info("REMAINS scene at {} smear to {} body {}", camp.toShortString(), end.toShortString(), body);
        }
        return new Made(camp, end, body);
    }

    /** The heading, in radians, towards the most leaves within some thirty blocks; a random one where there are none. */
    static double towardTrees(ServerLevel level, BlockPos from, RandomSource random) {
        double best = random.nextDouble() * Math.PI * 2;
        int most = 0;
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6;
            int leaves = 0;
            for (int d = 8; d <= 32; d += 6) {
                int x = (int) Math.floor(from.getX() + Math.cos(a) * d), z = (int) Math.floor(from.getZ() + Math.sin(a) * d);
                if (!level.isLoaded(new BlockPos(x, 0, z))) continue;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                if (level.getBlockState(new BlockPos(x, top, z)).is(BlockTags.LEAVES)) leaves++;
            }
            if (leaves > most) {
                most = leaves;
                best = a;
            }
        }
        return best;
    }

    /**
     * A spot near a place where a body lies along a way on level ground (the ground the same height a block either
     * side of it, nothing standing there) so it does not lie into a step of the hill; the place itself where none is.
     */
    static BlockPos level(ServerLevel level, BlockPos near, Direction along) {
        for (int r = 0; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    BlockPos p = ground(level, near.getX() + dx, near.getZ() + dz);
                    if (p == null) continue;
                    BlockPos a = ground(level, p.relative(along).getX(), p.relative(along).getZ());
                    BlockPos b = ground(level, p.relative(along.getOpposite()).getX(), p.relative(along.getOpposite()).getZ());
                    if (a == null || b == null || a.getY() != p.getY() || b.getY() != p.getY()) continue;
                    if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) continue;
                    return p;
                }
            }
        }
        return near;
    }

    /** The spot over the ground at a column, where it is open ground, not water; or null. */
    static BlockPos ground(ServerLevel level, int x, int z) {
        // Not where the ground is not loaded: it would be loaded, or made, for this.
        if (!level.isLoaded(new BlockPos(x, 0, z))) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos p = new BlockPos(x, y, z);
        BlockState below = level.getBlockState(p.below());
        if (!level.getFluidState(p.below()).isEmpty() || !level.getFluidState(p).isEmpty()) return null;
        if (!below.isFaceSturdy(level, p.below(), Direction.UP)) return null;
        return p;
    }

    /** An open spot on the ground some blocks from a place, or null. */
    static BlockPos free(ServerLevel level, BlockPos near, int min, int max, RandomSource random) {
        for (int tries = 0; tries < 12; tries++) {
            int dx = random.nextInt(max * 2 + 1) - max, dz = random.nextInt(max * 2 + 1) - max;
            if (Math.max(Math.abs(dx), Math.abs(dz)) < min) continue;
            BlockPos p = ground(level, near.getX() + dx, near.getZ() + dz);
            if (p != null && level.getBlockState(p).canBeReplaced()) return p;
        }
        return null;
    }

    static void put(ServerLevel level, BlockPos at, BlockState s) {
        BlockPos p = ground(level, at.getX(), at.getZ());
        if (p != null && level.getBlockState(p).canBeReplaced() && s.canSurvive(level, p)) level.setBlock(p, s, 3);
    }

    /** A mark on the ground there; over one already there only if told to (the smear goes over splashes). */
    static void stain(ServerLevel level, BlockPos p, int kind, Direction facing, boolean over) {
        // The stains and the smear may be turned off; the footprints are only footprints.
        if (kind != StainBlock.PRINT && !com.jeladastudios.lostsignal.Config.on(com.jeladastudios.lostsignal.Config.STAINS)) return;
        BlockState s = ModBlocks.STAIN.get().defaultBlockState().setValue(StainBlock.KIND, kind).setValue(StainBlock.FACING, facing);
        BlockState there = level.getBlockState(p);
        if (!there.canBeReplaced() || there.is(ModBlocks.STAIN.get()) && !over) return;
        if (!s.canSurvive(level, p)) return;
        level.setBlock(p, s, 3);
    }

    /** Something of theirs on the ground, there until someone picks it up. */
    static void drop(ServerLevel level, BlockPos near, ItemStack stack, RandomSource random) {
        BlockPos p = free(level, near, 0, 3, random);
        if (p == null) p = near;
        ItemEntity item = new ItemEntity(level, p.getX() + 0.5, p.getY() + 0.05, p.getZ() + 0.5, stack, 0, 0, 0);
        item.setUnlimitedLifetime();
        level.addFreshEntity(item);
    }
}
