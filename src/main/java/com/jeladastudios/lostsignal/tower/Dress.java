package com.jeladastudios.lostsignal.tower;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.block.ModBlocks;
import com.jeladastudios.lostsignal.block.SignalLampBlock;
import com.jeladastudios.lostsignal.entity.Keeper;
import com.jeladastudios.lostsignal.entity.ModEntities;
import com.jeladastudios.lostsignal.mission.Missions;
import com.jeladastudios.lostsignal.remains.StainBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A lookout made to look as it is, when someone comes near enough to see: its keeper in the cab; the door of one left in
 * a hurry standing open, dark marks by it, and what is left of its keeper out on the mountain; black candles on the
 * floor of one he has been living in, lit at night, and its lamp burning.
 */
final class Dress {

    private Dress() {}

    /** How near a player must be for a lookout to be set as it is. */
    static final double NEAR = 96;

    /** A player at a lookout: its height as built, known now. */
    static void near(ServerLevel level, Tower t, BlockPos standing) {
        if (t.y != standing.getY()) {
            t.y = standing.getY();
            Towers.of(level).setDirty();
        }
    }

    static void ifLoaded(ServerLevel level, Tower t) {
        if (!someoneNear(level, t) || !level.isLoaded(t.foot())) return;
        BlockPos foot = Missions.standing(level, t.foot());
        if (foot == null) return;
        Towers towers = Towers.of(level);
        if (foot.getY() != t.y) {
            t.y = foot.getY();
            towers.setDirty();
        }
        BlockPos floor = foot.above(20);
        if (!t.dressed) {
            dress(level, t, foot, floor);
            t.dressed = true;
            towers.setDirty();
        }
        if (t.remainsPending) {
            remains(level, t, foot);
            t.remainsPending = false;
            towers.setDirty();
        }
        if (t.supplyPending && supply(level, t, foot)) {
            t.supplyPending = false;
            t.lastSupply = level.getGameTime();
            towers.setDirty();
        }
        if (t.state == Tower.State.KEPT) keeper(level, t, floor);
        if (t.state == Tower.State.STRANGER) burning(level, t, foot, floor);
    }

    static boolean someoneNear(ServerLevel level, Tower t) {
        for (ServerPlayer p : level.players()) if (Math.hypot(p.getX() - t.x, p.getZ() - t.z) < NEAR) return true;
        return false;
    }

    static void dress(ServerLevel level, Tower t, BlockPos foot, BlockPos floor) {
        var random = level.random;
        switch (t.state) {
            case ABANDONED -> {
                // The door left open; dark marks by it, inside and out.
                var door = com.jeladastudios.lostsignal.visit.Visit.doorOf(level, foot);
                if (door != null) {
                    BlockState s = level.getBlockState(door.at());
                    if (s.getBlock() instanceof DoorBlock d && !s.getValue(DoorBlock.OPEN)) d.setOpen(null, level, s, door.at(), true);
                    stain(level, door.at().relative(door.out()), random.nextInt(StainBlock.SPLASHES));
                    stain(level, door.at().relative(door.out().getOpposite()), random.nextInt(StainBlock.SPLASHES));
                }
            }
            case STRANGER -> {
                // Black candles about the floor, where there is room for them.
                List<BlockPos> room = free(level, floor);
                java.util.Collections.shuffle(room, new java.util.Random(random.nextLong()));
                for (int i = 0; i < Math.min(5, room.size()); i++) {
                    level.setBlock(room.get(i), Blocks.BLACK_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 1 + random.nextInt(3)), 3);
                }
                if (room.size() > 5) stain(level, room.get(5), random.nextInt(StainBlock.SPLASHES));
            }
            default -> {
            }
        }
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("TOWER {} dressed as {}", t.number, t.state);
    }

    /** The spots on the cab's floor with nothing on them. */
    static List<BlockPos> free(ServerLevel level, BlockPos floor) {
        List<BlockPos> room = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos p = floor.offset(dx, 1, dz);
                if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) room.add(p);
            }
        }
        return room;
    }

    static void stain(ServerLevel level, BlockPos p, int kind) {
        if (!com.jeladastudios.lostsignal.Config.on(com.jeladastudios.lostsignal.Config.STAINS)) return;
        BlockState s = ModBlocks.STAIN.get().defaultBlockState().setValue(StainBlock.KIND, kind)
                .setValue(StainBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(level.random));
        if (level.getBlockState(p).canBeReplaced() && s.canSurvive(level, p)) level.setBlock(p, s, 3);
    }

    /** What is left of a keeper who was not heard from again, somewhere on the slopes below their lookout. */
    static void remains(ServerLevel level, Tower t, BlockPos foot) {
        var random = level.random;
        double a = random.nextDouble() * Math.PI * 2, d = 24 + random.nextDouble() * 20;
        BlockPos at = BlockPos.containing(foot.getX() + Math.cos(a) * d, foot.getY(), foot.getZ() + Math.sin(a) * d);
        level.getChunk(at.getX() >> 4, at.getZ() >> 4);
        com.jeladastudios.lostsignal.remains.Scene.make(level, at, null, random.nextInt(com.jeladastudios.lostsignal.entity.Hiker.KITS),
                t.keeper.isEmpty() ? "-" : t.keeper, null, com.jeladastudios.lostsignal.remains.Journal.KEEPER,
                com.jeladastudios.lostsignal.Config.on(com.jeladastudios.lostsignal.Config.BODIES) && random.nextBoolean(), random);
    }

    /**
     * The District's supply crate, set down where the trail starts up to the lookout -- some way down the slope below
     * it -- and said on the radio, with where it is. False where its ground is not loaded yet.
     */
    static boolean supply(ServerLevel level, Tower t, BlockPos foot) {
        Vec3 down = com.jeladastudios.lostsignal.rescue.Sight.downhill(level, foot);
        double a = down != null ? Math.atan2(down.z, down.x) : level.random.nextDouble() * Math.PI * 2;
        int x = (int) Math.floor(foot.getX() + 0.5 + Math.cos(a) * 26), z = (int) Math.floor(foot.getZ() + 0.5 + Math.sin(a) * 26);
        if (!level.isLoaded(new BlockPos(x, foot.getY(), z))) return false;
        BlockPos at = new BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        if (!level.getBlockState(at).canBeReplaced() || !level.getFluidState(at.below()).isEmpty()) return false;
        level.setBlock(at, Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP), 3);
        if (level.getBlockEntity(at) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity barrel) {
            barrel.setCustomName(net.minecraft.network.chat.Component.translatable("block.fts_lostsignal.supply_crate"));
            var random = level.random;
            int slot = 0;
            barrel.setItem(slot++, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL, 10 + random.nextInt(9)));
            barrel.setItem(slot++, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 4 + random.nextInt(4)));
            barrel.setItem(slot++, new net.minecraft.world.item.ItemStack(random.nextBoolean() ? net.minecraft.world.item.Items.COOKED_SALMON
                    : net.minecraft.world.item.Items.BAKED_POTATO, 3 + random.nextInt(4)));
            barrel.setItem(slot++, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FIREWORK_ROCKET, 2)
                    .setHoverName(net.minecraft.network.chat.Component.translatable("item.fts_lostsignal.flare")));
            barrel.setItem(slot++, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CANDLE, 4));
            if (random.nextBoolean()) barrel.setItem(slot, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER, 6));
        }
        // On the radio, to whoever is at the lookout's set: and where it is from the tower's foot.
        Vec3 from = Vec3.atCenterOf(foot.above(22));
        com.jeladastudios.lostsignal.rescue.Lines.say(level, from, com.jeladastudios.lostsignal.rescue.Lines.Speaker.DISTRICT, "D_SUPPLY", 1f);
        int bearing = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(x - foot.getX(), -(z - foot.getZ())))), 360);
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceTo(from) > 64) continue;
            p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("supply.fts_lostsignal.where", 26,
                    net.minecraft.network.chat.Component.translatable("radio.fts_lostsignal.point." + com.jeladastudios.lostsignal.finder.FireFinder.point(bearing)))
                    .withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC));
        }
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("SUPPLY at {}", at.toShortString());
        return true;
    }

    /** The keeper in their cab: put there if they are not. */
    static void keeper(ServerLevel level, Tower t, BlockPos floor) {
        AABB cab = new AABB(floor).inflate(8);
        if (!level.getEntitiesOfClass(Keeper.class, cab, k -> k.tower() == t.key()).isEmpty()) return;
        List<BlockPos> room = free(level, floor);
        if (room.isEmpty()) return;
        BlockPos at = room.get(level.random.nextInt(room.size()));
        Keeper k = ModEntities.KEEPER.get().create(level);
        if (k == null) return;
        k.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        k.post(t, floor);
        level.addFreshEntity(k);
    }

    /** His lookout by night: the candles lit, the lamp burning; by day both out. */
    static void burning(ServerLevel level, Tower t, BlockPos foot, BlockPos floor) {
        boolean night = level.getDayTime() % 24000 > 13000 && level.getDayTime() % 24000 < 23000;
        for (BlockPos p : BlockPos.betweenClosed(floor.offset(-2, 1, -2), floor.offset(2, 1, 2))) {
            BlockState s = level.getBlockState(p);
            if (s.is(Blocks.BLACK_CANDLE) && s.getValue(CandleBlock.LIT) != night) level.setBlock(p, s.setValue(CandleBlock.LIT, night), 3);
        }
        BlockPos lamp = com.jeladastudios.lostsignal.finder.Lamps.find(level, foot);
        if (lamp != null) {
            BlockState s = level.getBlockState(lamp);
            if (s.getValue(SignalLampBlock.LIT) != night) {
                level.setBlock(lamp, s.setValue(SignalLampBlock.LIT, night), 3);
                com.jeladastudios.lostsignal.finder.Lamps.switched(level, lamp, night);
            }
        }
    }
}
