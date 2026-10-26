package com.jeladastudios.lostsignal.story;

import com.jeladastudios.lostsignal.LostSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

import java.util.List;

/**
 * The planting, below the ridge east of Hollow Peak: the clearing where the three boys of 1934 fell and the biggest
 * trees grew, as the Lund family has it, and where the family has planted ever since. A row of trees for each who were
 * given, oldest first, a post by each with something of theirs hung on it under their initials; a sapling for each
 * given in this world, with their name; and at the head of the rows a lectern with Silas's ledger on it, his black
 * candles round it. See HIKAYE.md.
 */
final class Planting {

    private Planting() {}

    /**
     * The old rows, as the ledger has them: 1934 (the boys' trees, giants now), 1979 (the three men of the Hollow
     * Fire, planted the spring after), and those Silas gave since. Each with what hangs on its post, if anything, and
     * its initials.
     */
    record Old(String tree, Item item, String initials) {}

    static final List<Old> OLD = List.of(
            new Old("mega", Items.IRON_SHOVEL, ""), new Old("mega", Items.IRON_SHOVEL, ""), new Old("mega", Items.IRON_SHOVEL, ""),
            new Old("spruce", null, ""), new Old("spruce", null, ""), new Old("spruce", null, ""),
            new Old("spruce", Items.COMPASS, "A.L."), new Old("spruce", Items.LEATHER_HELMET, "R.H."), new Old("spruce", Items.LANTERN, "D.W."),
            new Old("spruce", Items.SPYGLASS, "C."), new Old("spruce", Items.COMPASS, "T."), new Old("spruce", Items.LEATHER_BOOTS, "J."),
            new Old("spruce", Items.LANTERN, "M.K."), new Old("spruce", Items.SPYGLASS, "S.B."), new Old("sapling", null, "L."));

    /** The old rows, east of the head: x of each row, z of each tree along it. */
    static final int[] OLD_ROWS = {-1, 4, 9}, OLD_ALONG = {-10, -5, 0, 5, 10};
    /** The rows of saplings for this world's, nearer the head; and how many go in a row. */
    static final int[] NEW_ROWS = {-6, -9};
    static final int ALONG = 11;
    /** Where the lectern stands, from the middle: the head of the rows, west. */
    static final int HEAD = -13;

    static void build(ServerLevel level, Story s) {
        BlockPos c = s.planting;
        RandomSource random = RandomSource.create(c.asLong());
        // The clearing: what grew there cut back, the ground the needles and bare earth of under spruce; where snow
        // lies, the snow left lying.
        for (int dx = -17; dx <= 15; dx++) {
            for (int dz = -15; dz <= 15; dz++) {
                double e = Math.pow((dx + 1) / 15.5, 2) + Math.pow(dz / 13.5, 2);
                if (e > 1 + random.nextFloat() * 0.12) continue;
                int x = c.getX() + dx, z = c.getZ() + dz, gy = Ruin.surface(level, x, z);
                boolean snow = false;
                for (int y = gy + 1; y <= gy + 36; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState b = level.getBlockState(p);
                    if (b.is(Blocks.SNOW)) {
                        snow = true;
                        continue;
                    }
                    if (!b.isAir() && b.getFluidState().isEmpty() && !Ruin.built(b)) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
                BlockPos ground = new BlockPos(x, gy, z);
                if (!snow && level.getBlockState(ground).is(BlockTags.DIRT)) {
                    float r = random.nextFloat();
                    if (r < 0.85f) level.setBlock(ground, (r < 0.6f ? Blocks.PODZOL : Blocks.COARSE_DIRT).defaultBlockState(), 3);
                }
            }
        }
        // The old rows.
        for (int i = 0; i < OLD.size(); i++) {
            Old o = OLD.get(i);
            int x = c.getX() + OLD_ROWS[i / OLD_ALONG.length], z = c.getZ() + OLD_ALONG[i % OLD_ALONG.length];
            BlockPos at = ready(level, x, z);
            boolean mega = o.tree().equals("mega");
            if (mega) {
                // A giant's trunk is four wide, and it wants room round it: all four spots made ready, the snow
                // cleared from a ring round them.
                for (int[] d : new int[][]{{1, 0}, {0, 1}, {1, 1}}) ready(level, x + d[0], z + d[1]);
                for (BlockPos p : BlockPos.betweenClosed(at.offset(-2, 0, -2), at.offset(3, 2, 3))) {
                    if (level.getBlockState(p).is(Blocks.SNOW)) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
            }
            // Where a giant will not grow, a tree as old as the rest.
            boolean grown = !o.tree().equals("sapling") && (mega && tree(level, TreeFeatures.MEGA_SPRUCE, at, random)
                    || tree(level, TreeFeatures.SPRUCE, at, random));
            if (!grown) level.setBlock(at, Blocks.SPRUCE_SAPLING.defaultBlockState(), 3);
            post(level, x - 2, z, o.item() == null ? ItemStack.EMPTY : named(new ItemStack(o.item()), o.initials()));
        }
        // The head of the rows: the lectern, the ledger on it, his candles round it.
        int hx = c.getX() + HEAD, hz = c.getZ();
        BlockPos lectern = new BlockPos(hx, Ruin.surface(level, hx, hz) + 1, hz);
        if (Ruin.set(level, lectern, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.WEST).setValue(LecternBlock.HAS_BOOK, true))
                && level.getBlockEntity(lectern) instanceof LecternBlockEntity l) {
            l.setBook(Papers.ledger(s.bySeason(), com.jeladastudios.lostsignal.desk.Desk.season(level), fires(level)));
            l.setChanged();
        }
        for (int[] o : new int[][]{{0, 2}, {0, -2}, {1, 1}}) {
            int x = hx + o[0], z = hz + o[1];
            BlockPos p = new BlockPos(x, Ruin.surface(level, x, z) + 1, z);
            Ruin.set(level, p, Blocks.BLACK_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 1 + random.nextInt(3)));
        }
        LostSignal.LOGGER.info("The planting set out at {}", c.toShortString());
        plant(level, s);
    }

    /** This world's given, each a sapling in the rows near the head with their name hung by it; and the ledger brought up to them. */
    static void plant(ServerLevel level, Story s) {
        BlockPos c = s.planting;
        RandomSource random = level.random;
        boolean any = false;
        while (s.planted < s.given.size()) {
            int k = s.planted++;
            any = true;
            if (k >= NEW_ROWS.length * ALONG) continue;
            String[] g = s.given.get(k).split(java.util.regex.Pattern.quote("|"));
            int x = c.getX() + NEW_ROWS[k / ALONG], z = c.getZ() - 10 + (k % ALONG) * 2;
            level.setBlock(ready(level, x, z), Blocks.SPRUCE_SAPLING.defaultBlockState(), 3);
            Item item = g.length > 1 && g[1].equals("keeper") ? Items.SPYGLASS
                    : List.of(Items.COMPASS, Items.LANTERN, Items.LEATHER_HELMET, transceiver()).get(random.nextInt(4));
            post(level, x - 1, z, named(new ItemStack(item), g[0]));
        }
        if (!any) return;
        s.setDirty();
        // The ledger on the lectern, where it still is, brought up to this year.
        int hx = c.getX() + HEAD, hz = c.getZ(), top = Ruin.surface(level, hx, hz);
        for (int y = top - 4; y <= top + 4; y++) {
            BlockPos p = new BlockPos(hx, y, hz);
            if (level.getBlockEntity(p) instanceof LecternBlockEntity l && l.hasBook()) {
                l.setBook(Papers.ledger(s.bySeason(), com.jeladastudios.lostsignal.desk.Desk.season(level), fires(level)));
                l.setChanged();
            }
        }
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("PLANTING now {} planted", s.planted);
    }

    /** A spot made ready to plant: its ground good earth, the block over it clear; that block. */
    static BlockPos ready(ServerLevel level, int x, int z) {
        int gy = Ruin.surface(level, x, z);
        BlockPos ground = new BlockPos(x, gy, z);
        if (!level.getBlockState(ground).is(BlockTags.DIRT)) Ruin.set(level, ground, Blocks.PODZOL.defaultBlockState());
        BlockPos at = ground.above();
        Ruin.set(level, at, Blocks.AIR.defaultBlockState());
        return at;
    }

    /** A tree grown at a spot, as the world grows them; false where it would not grow there. */
    static boolean tree(ServerLevel level, ResourceKey<ConfiguredFeature<?, ?>> which, BlockPos at, RandomSource random) {
        var feature = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(which);
        return feature.isPresent() && feature.get().value().place(level, level.getChunkSource().getGenerator(), random, at);
    }

    /** A post by a tree, and what of theirs hangs on it, facing the head of the rows. */
    static void post(ServerLevel level, int x, int z, ItemStack hung) {
        int gy = Ruin.surface(level, x, z);
        BlockPos post = new BlockPos(x, gy + 1, z), frame = post.west();
        Ruin.set(level, post, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
        if (hung.isEmpty()) return;
        // Room for it: no needles in the way.
        if (level.getBlockState(frame).is(BlockTags.LEAVES)) level.setBlock(frame, Blocks.AIR.defaultBlockState(), 3);
        ItemFrame f = new ItemFrame(level, frame, Direction.WEST);
        f.setItem(hung, false);
        if (f.survives()) level.addFreshEntity(f);
    }

    static ItemStack named(ItemStack s, String name) {
        if (!name.isEmpty()) s.setHoverName(Component.literal(name));
        return s;
    }

    /** The radio fork's transceiver; a compass where there is none. */
    static Item transceiver() {
        Item i = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("simpleradio", "transceiver"));
        return i == null || i == Items.AIR ? Items.COMPASS : i;
    }

    /** The fires put out this season, all the District's lookouts together: the ledger's other column. */
    static int fires(ServerLevel level) {
        return com.jeladastudios.lostsignal.desk.Desk.seasonFires(level);
    }
}
