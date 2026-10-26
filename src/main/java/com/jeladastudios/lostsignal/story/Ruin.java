package com.jeladastudios.lostsignal.story;

import com.jeladastudios.lostsignal.LostSignal;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Hollow Peak as it is now, forty-odd years closed: four stone footings on the summit, what is left of the tower's
 * spruce legs on three of them and the fourth lying on the ground where it fell, the cab come down in pieces between
 * them; under its floor, a chest with Arthur Lund's log of 1979 in it; and the District's notice nailed to the one leg
 * still standing tall. Set out only over the mountain's own ground, never over anything built.
 */
final class Ruin {

    private Ruin() {}

    /** Half the width between the footings. */
    static final int HALF = 3;
    /** How much of each leg still stands on its footing; the last one fell. */
    static final int[] LEGS = {4, 2, 1, 0};

    /** Whether the ground round a place is all loaded, a couple of chunks out. */
    static boolean loaded(ServerLevel level, BlockPos at) {
        for (int cx = (at.getX() - 24) >> 4; cx <= (at.getX() + 24) >> 4; cx++) {
            for (int cz = (at.getZ() - 24) >> 4; cz <= (at.getZ() + 24) >> 4; cz++) {
                if (!level.getChunkSource().hasChunk(cx, cz)) return false;
            }
        }
        return true;
    }

    /**
     * Whether the ground round a place will do for one of the story's places: no lava or fire within a radius (a lava
     * lake beside the planting and its spruces go up), and not a tenth of it under water.
     */
    static boolean fit(ServerLevel level, BlockPos at, int r) {
        int wet = 0, n = 0;
        for (int dx = -r; dx <= r; dx += 2) {
            for (int dz = -r; dz <= r; dz += 2) {
                if (dx * dx + dz * dz > r * r) continue;
                n++;
                int x = at.getX() + dx, z = at.getZ() + dz, top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                for (int y = top; y >= top - 3; y--) {
                    BlockState s = level.getBlockState(new BlockPos(x, y, z));
                    if (s.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) || s.getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock) return false;
                }
                if (!level.getFluidState(new BlockPos(x, top, z)).isEmpty()) wet++;
            }
        }
        return wet * 10 < n;
    }

    /**
     * Where one of the story's places goes, near where it was meant to: there, or the nearest spot a few dozen blocks
     * off that will do and whose ground is loaded; null where none is yet.
     */
    static BlockPos near(ServerLevel level, BlockPos at, int r) {
        for (int ring = 0; ring <= 3; ring++) {
            for (int i = 0; i < (ring == 0 ? 1 : 8); i++) {
                double a = Math.PI / 4 * i;
                BlockPos p = at.offset((int) Math.round(Math.cos(a) * ring * 24), 0, (int) Math.round(Math.sin(a) * ring * 24));
                if (loaded(level, p) && fit(level, p, r)) return p;
            }
        }
        return null;
    }

    static void build(ServerLevel level, BlockPos at) {
        RandomSource random = RandomSource.create(at.asLong());
        int x = at.getX(), z = at.getZ();
        int[][] corners = {{HALF, HALF}, {-HALF, HALF}, {HALF, -HALF}, {-HALF, -HALF}};
        // The summit blasted flat for it, as the crews that built the lookouts did: down to the lowest ground under the
        // footings, a few blocks round.
        int g = surface(level, x, z);
        for (int[] c : corners) g = Math.min(g, surface(level, x + c[0], z + c[1]));
        for (int dx = -HALF - 2; dx <= HALF + 2; dx++) {
            for (int dz = -HALF - 2; dz <= HALF + 2; dz++) {
                if (dx * dx + dz * dz > (HALF + 2.5) * (HALF + 2.5)) continue;
                for (int y = g + 1; y <= g + 12; y++) {
                    BlockPos p = new BlockPos(x + dx, y, z + dz);
                    if (!level.getBlockState(p).isAir() && level.getFluidState(p).isEmpty()) set(level, p, Blocks.AIR.defaultBlockState());
                }
            }
        }
        // The footings, their tops level a block over the flat, and the legs on them; the fourth leg lying where it fell, west.
        BlockPos tall = null;
        int top = g + 1;
        for (int i = 0; i < 4; i++) {
            int fx = x + corners[i][0], fz = z + corners[i][1], gy = surface(level, fx, fz);
            for (int y = gy; y <= top; y++) {
                BlockState brick = random.nextFloat() < 0.3f ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                        : random.nextFloat() < 0.4f ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
                set(level, new BlockPos(fx, y, fz), brick);
            }
            for (int y = 1; y <= LEGS[i]; y++) set(level, new BlockPos(fx, top + y, fz), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
            if (i == 0) tall = new BlockPos(fx, top + 2, fz);
            if (LEGS[i] == 0) {
                for (int d = 1; d <= 6; d++) {
                    int lx = fx - d, ly = surface(level, lx, fz) + 1;
                    set(level, new BlockPos(lx, ly, fz), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
                }
            }
        }
        // Under where the cab's floor was: the chest, its place kept clear of what came down.
        BlockPos chest = new BlockPos(x, surface(level, x, z) + 1, z);
        // The cab come down between them: its floor in pieces, the boards and shutters about, a bar or two of its rail.
        for (int dx = -HALF - 2; dx <= HALF + 2; dx++) {
            for (int dz = -HALF - 2; dz <= HALF + 2; dz++) {
                if (Math.abs(dx) == HALF && Math.abs(dz) == HALF || dx == 0 && dz == 0 || dx == 1 && dz == 1) continue;
                boolean under = Math.abs(dx) < HALF && Math.abs(dz) < HALF;
                if (random.nextFloat() > (under ? 0.55f : 0.14f)) continue;
                int px = x + dx, pz = z + dz;
                BlockPos p = new BlockPos(px, surface(level, px, pz) + 1, pz);
                float r = random.nextFloat();
                BlockState s = r < 0.45f ? Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)
                        : r < 0.65f ? Blocks.SPRUCE_PLANKS.defaultBlockState()
                        : r < 0.88f ? Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random))
                        : Blocks.IRON_BARS.defaultBlockState();
                set(level, p, s);
            }
        }
        // The chest, with the log in it.
        if (set(level, chest, Blocks.CHEST.defaultBlockState()) && level.getBlockEntity(chest) instanceof ChestBlockEntity box) {
            box.setItem(4, Papers.log());
            box.setItem(11, new ItemStack(Items.PAPER, 3));
            box.setItem(15, new ItemStack(Items.CANDLE, 2));
            box.setItem(22, new ItemStack(Items.GLASS_BOTTLE));
            box.setChanged();
        }
        set(level, chest.offset(1, 0, 1), Blocks.COBWEB.defaultBlockState());
        set(level, chest.above(), Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        // The notice, on the leg still standing tall: who closed it, and when.
        if (tall != null && set(level, tall.south(), Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.SOUTH))
                && level.getBlockEntity(tall.south()) instanceof SignBlockEntity sign) {
            SignText text = new SignText();
            for (int i = 0; i < 4; i++) {
                Component line = Component.translatable("story.fts_lostsignal.sign." + (i + 1));
                text = text.setMessage(i, i == 0 ? line.copy().withStyle(ChatFormatting.BOLD) : line);
            }
            sign.setText(text, true);
            sign.setWaxed(true);
            sign.setChanged();
            level.sendBlockUpdated(tall.south(), level.getBlockState(tall.south()), level.getBlockState(tall.south()), 3);
        }
        LostSignal.LOGGER.info("Hollow Peak set out at {}", chest.toShortString());
    }

    /**
     * The mountain's own ground at a column: the top of it under any trees, plants or a dusting of snow. Deep snow,
     * lying in full layers, is the ground there.
     */
    static int surface(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
        while (m.getY() > level.getMinBuildHeight()) {
            BlockState s = level.getBlockState(m);
            if (s.is(Blocks.SNOW) && s.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) == 8) break;
            if (!(s.isAir() || s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(BlockTags.SAPLINGS)
                    || s.is(BlockTags.FLOWERS) || !s.getFluidState().isEmpty())) break;
            m.move(Direction.DOWN);
        }
        return m.getY();
    }

    /** A block set where the mountain's own ground or growth is; false where something built is in the way. */
    static boolean set(ServerLevel level, BlockPos p, BlockState s) {
        if (built(level.getBlockState(p))) return false;
        level.setBlock(p, s, 3);
        return true;
    }

    /** Whether a block is one someone made or put there. */
    static boolean built(BlockState s) {
        return s.hasBlockEntity() || s.is(BlockTags.PLANKS) || s.is(BlockTags.SLABS) || s.is(BlockTags.STAIRS) || s.is(BlockTags.FENCES)
                || s.is(BlockTags.FENCE_GATES) || s.is(BlockTags.WALLS) || s.is(BlockTags.DOORS) || s.is(BlockTags.TRAPDOORS)
                || s.is(BlockTags.WOOL) || s.is(BlockTags.BEDS) || s.is(BlockTags.IMPERMEABLE) || s.is(BlockTags.STONE_BRICKS)
                || s.is(BlockTags.RAILS) || s.is(BlockTags.CANDLES) || s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH)
                || s.is(Blocks.LANTERN) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.GLASS_PANE) || s.is(Blocks.IRON_BARS)
                || s.is(Blocks.LADDER) || s.is(Blocks.CHAIN) || s.is(Blocks.HAY_BLOCK);
    }
}
