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
        // Kept off by FT's Geology's earthquakes, where it can be: once, whenever it was built.
        if (!t.sheltered && Quakes.protect(level, foot)) {
            t.sheltered = true;
            towers.setDirty();
        }
        if (!t.fitted) {
            var door = com.jeladastudios.lostsignal.visit.Visit.doorOf(level, foot);
            if (door != null) {
                fit(level, foot, door);
                t.fitted = true;
                towers.setDirty();
            }
        }
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
        boolean parcels = !t.parcels.isEmpty() && t.parcelDue >= 0 && level.getDayTime() / 24000 >= t.parcelDue;
        if ((t.supplyPending || parcels) && supply(level, t, foot, parcels)) {
            if (t.supplyPending) t.lastSupply = level.getGameTime();
            t.supplyPending = false;
            if (t.parcels.isEmpty()) t.parcelDue = -1;
            towers.setDirty();
        }
        if (!t.posters.isEmpty() && poster(level, foot, t.posters.get(0))) {
            t.posters.remove(0);
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

    /**
     * A lookout built before its stair was carried out onto the walkway: its last flight still comes up through a hole
     * in the cab's floor, and whatever climbs the stair in the night is in the room. A hatch over the hole, shut, which a
     * hand opens from below and nothing else will; and the lanterns the new ones are built with hung under the tower.
     * Its blocks as Feel the Nature lays them out, from the door: x the way out through it, z to its right.
     */
    static void fit(ServerLevel level, BlockPos foot, com.jeladastudios.lostsignal.visit.Visit.Door door) {
        Direction east = door.out(), south = east.getClockWise();
        // The old hole: three open cells along the north wall, the last step at their end.
        boolean old = level.getBlockState(Stair.at(foot, east, 1, 20, -2)).getBlock() instanceof net.minecraft.world.level.block.StairBlock;
        for (int x = -2; x <= 0 && old; x++) old = level.getBlockState(Stair.at(foot, east, x, 20, -2)).isAir();
        if (old) {
            // Up against the wall when they are opened, so the flight is clear.
            BlockState hatch = Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP)
                    .setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, south);
            for (int x = -2; x <= 0; x++) level.setBlock(Stair.at(foot, east, x, 20, -2), hatch, 3);
        }
        BlockState hung = Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true);
        for (int[] l : LIGHTS) {
            BlockPos p = Stair.at(foot, east, l[0], l[1], l[2]);
            if (level.getBlockState(p).isAir() && hung.canSurvive(level, p)) level.setBlock(p, hung, 3);
        }
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("TOWER at {} fitted{}", foot.toShortString(), old ? " with a hatch" : "");
    }

    /** Where Feel the Nature hangs the lanterns under a lookout: under two landings and under the cab's floor. */
    static final int[][] LIGHTS = {{2, 1, 0}, {-2, 10, 0}, {1, 19, 2}};

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
     * The District's supply crate, set down where the trail starts up to the lookout (some way down the slope below
     * it) and said on the radio, with where it is: the supplies, if they are due, and what has been sent up (as much of
     * it as fits; the rest with the next). Put into the last crate where it still stands there with room in it, else
     * set down beside it. False where its ground is not loaded yet, or there is nowhere to set one down.
     */
    static boolean supply(ServerLevel level, Tower t, BlockPos foot, boolean parcels) {
        Vec3 down = com.jeladastudios.lostsignal.rescue.Sight.downhill(level, foot);
        double a = down != null ? Math.atan2(down.z, down.x) : level.random.nextDouble() * Math.PI * 2;
        int x0 = (int) Math.floor(foot.getX() + 0.5 + Math.cos(a) * 26), z0 = (int) Math.floor(foot.getZ() + 0.5 + Math.sin(a) * 26);
        if (!level.isLoaded(new BlockPos(x0, foot.getY(), z0))) return false;
        net.minecraft.world.level.block.entity.BarrelBlockEntity barrel = crate(level, x0, z0);
        if (barrel == null) return false;
        BlockPos at = barrel.getBlockPos();
        int x = at.getX(), z = at.getZ();
        {
            var random = level.random;
            int slot = next(barrel, -1);
            List<net.minecraft.world.item.ItemStack> put = new ArrayList<>();
            if (t.supplyPending) {
                put.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL, 10 + random.nextInt(9)));
                put.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 4 + random.nextInt(4)));
                put.add(new net.minecraft.world.item.ItemStack(random.nextBoolean() ? net.minecraft.world.item.Items.COOKED_SALMON
                        : net.minecraft.world.item.Items.BAKED_POTATO, 3 + random.nextInt(4)));
                put.add(new net.minecraft.world.item.ItemStack(com.jeladastudios.lostsignal.block.ModBlocks.FLARE.get(), 2));
                put.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CANDLE, 4));
                if (random.nextBoolean()) put.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER, 6));
            }
            for (var s : put) {
                if (slot < 0) break;
                barrel.setItem(slot, s);
                slot = next(barrel, slot);
            }
            while (parcels && slot >= 0 && !t.parcels.isEmpty()) {
                barrel.setItem(slot, t.parcels.remove(0));
                slot = next(barrel, slot);
            }
            barrel.setChanged();
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

    /**
     * A missing hiker posted up at the foot of the trail, a few steps from where the crates are set down: a sign facing
     * down the slope, the way anyone coming up comes, MISSING, the name, the day they were last heard, the frequency.
     * Waxed, so no one writes over it. False where its ground is not loaded, or there is nowhere to stand it.
     */
    static boolean poster(ServerLevel level, BlockPos foot, String entry) {
        String[] parts = entry.split(java.util.regex.Pattern.quote("|"));
        String name = parts[0], day = parts.length > 1 ? parts[1] : "?";
        Vec3 down = com.jeladastudios.lostsignal.rescue.Sight.downhill(level, foot);
        double a = down != null ? Math.atan2(down.z, down.x) : 0;
        // Beside the crates' spot, a little to one side of the trail.
        double side = a + Math.PI / 2;
        int x0 = (int) Math.floor(foot.getX() + 0.5 + Math.cos(a) * 26 + Math.cos(side) * 3), z0 = (int) Math.floor(foot.getZ() + 0.5 + Math.sin(a) * 26 + Math.sin(side) * 3);
        if (!level.isLoaded(new BlockPos(x0, foot.getY(), z0))) return false;
        float facing = (float) Math.toDegrees(Math.atan2(-Math.cos(a), Math.sin(a)));
        for (int r = 0; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = x0 + dx, z = z0 + dz;
                    BlockPos at = new BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                    if (!level.getBlockState(at).canBeReplaced() || !level.getFluidState(at).isEmpty() || !level.getBlockState(at.below()).isSolid()) continue;
                    level.setBlock(at, Blocks.SPRUCE_SIGN.defaultBlockState().setValue(net.minecraft.world.level.block.StandingSignBlock.ROTATION,
                            net.minecraft.world.level.block.state.properties.RotationSegment.convertToSegment(facing)), 3);
                    if (level.getBlockEntity(at) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
                        var text = new net.minecraft.world.level.block.entity.SignText()
                                .setMessage(0, net.minecraft.network.chat.Component.translatable("poster.fts_lostsignal.missing")
                                        .withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.BOLD))
                                .setMessage(1, net.minecraft.network.chat.Component.literal(name))
                                .setMessage(2, net.minecraft.network.chat.Component.translatable("poster.fts_lostsignal.since", day))
                                .setMessage(3, net.minecraft.network.chat.Component.literal(com.jeladastudios.lostsignal.radio.Broadcast.RESCUE));
                        sign.setText(text, true);
                        sign.setWaxed(true);
                        sign.setChanged();
                        level.sendBlockUpdated(at, level.getBlockState(at), level.getBlockState(at), 3);
                    }
                    if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("POSTER for {} at {}", name, at.toShortString());
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The crate at the foot of the trail: the last one, where it still stands at the spot or a step from it with room
     * in it, else a new one set down at the nearest ground there is; null where there is none within a few steps.
     */
    static net.minecraft.world.level.block.entity.BarrelBlockEntity crate(ServerLevel level, int x0, int z0) {
        for (int r = 0; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = x0 + dx, z = z0 + dz;
                    BlockPos top = new BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                    if (level.getBlockEntity(top.below()) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity old && old.hasCustomName()
                            && old.getCustomName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents name
                            && name.getKey().equals("block.fts_lostsignal.supply_crate")) {
                        if (next(old, -1) >= 0 && old.getContainerSize() - filled(old) >= 6) return old;
                        continue;
                    }
                    if (!level.getBlockState(top).canBeReplaced() || !level.getFluidState(top.below()).isEmpty()) continue;
                    level.setBlock(top, Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP), 3);
                    if (level.getBlockEntity(top) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity made) {
                        made.setCustomName(net.minecraft.network.chat.Component.translatable("block.fts_lostsignal.supply_crate"));
                        return made;
                    }
                    return null;
                }
            }
        }
        return null;
    }

    /** The next empty slot of a crate after one, or -1 where it is full. */
    static int next(net.minecraft.world.level.block.entity.BarrelBlockEntity barrel, int after) {
        for (int i = after + 1; i < barrel.getContainerSize(); i++) if (barrel.getItem(i).isEmpty()) return i;
        return -1;
    }

    static int filled(net.minecraft.world.level.block.entity.BarrelBlockEntity barrel) {
        int n = 0;
        for (int i = 0; i < barrel.getContainerSize(); i++) if (!barrel.getItem(i).isEmpty()) n++;
        return n;
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
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("KEEPER {} put at {} (floor {})", t.keeper, at.toShortString(), floor.toShortString());
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
