package com.jeladastudios.lostsignal.dev;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.director.Director;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Commands for trying the mod in the development game: {@code /ls_test watcher} puts the watcher for the player now,
 * if a spot is found. Registered only with {@code -Dfts_lostsignal.test=true}, which also logs what the mod does and
 * makes the director twenty times as eager.
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class TestCommands {

    private TestCommands() {}

    public static final boolean ON = Boolean.getBoolean("fts_lostsignal.test");

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (!ON) return;
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("ls_test").requires(s -> s.hasPermission(2))
                .then(Commands.literal("canopy").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayer();
                    if (p == null) return 0;
                    var level = p.serverLevel();
                    StringBuilder b = new StringBuilder("LS_TEST canopy");
                    for (int i = 0; i < 9; i++) {
                        double a = Math.PI * 2 * i / 9;
                        int x = p.getBlockX() + (int) Math.round(Math.cos(a) * 6), z = p.getBlockZ() + (int) Math.round(Math.sin(a) * 6);
                        b.append(" ").append(level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z))
                                .append("/").append(level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
                    }
                    c.getSource().sendSuccess(() -> Component.literal(b.toString()), false);
                    return 1;
                }))
                .then(Commands.literal("broadcast")
                        .then(Commands.argument("clip", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .then(Commands.argument("distance", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100000))
                                        .executes(c -> broadcast(c, com.mojang.brigadier.arguments.StringArgumentType.getString(c, "clip"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "distance"))))))
                .then(Commands.literal("hiker")
                        .then(Commands.literal("spawn").then(Commands.argument("distance", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 300))
                                .executes(c -> hikerSpawn(c, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "distance")))))
                        .then(Commands.literal("goto").then(Commands.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                .then(Commands.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                        .executes(c -> hikerGoTo(c, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "x"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "z"))))))
                        .then(Commands.literal("flare").executes(c -> hikerSignal(c, 0)))
                        .then(Commands.literal("smoke").executes(c -> hikerSignal(c, 1)))
                        .then(Commands.literal("lamp").executes(c -> hikerSignal(c, 2)))
                        .then(Commands.literal("state").executes(TestCommands::hikerState)))
                .then(Commands.literal("flare").then(Commands.argument("distance", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 500))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayer();
                            if (p == null) return 0;
                            var level = p.serverLevel();
                            int far = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "distance");
                            var look = p.getLookAngle();
                            int x = (int) Math.floor(p.getX() + look.x * far), z = (int) Math.floor(p.getZ() + look.z * far);
                            level.getChunk(x >> 4, z >> 4);
                            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                            com.jeladastudios.lostsignal.signal.Signals.flare(level, new net.minecraft.world.phys.Vec3(x + 0.5, y, z + 0.5));
                            com.jeladastudios.lostsignal.signal.Signals.smoke(level, new net.minecraft.world.phys.Vec3(x + 0.5, y, z + 0.5), 60);
                            c.getSource().sendSuccess(() -> Component.literal("LS_TEST flare at " + x + " " + y + " " + z), false);
                            return 1;
                        })))
                .then(Commands.literal("say").then(Commands.argument("order", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(c -> say(c, 0))
                        .then(Commands.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 359)).executes(c ->
                                say(c, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "value"))))))
                .then(Commands.literal("mission").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayer();
                    if (p == null) return 0;
                    var level = p.serverLevel();
                    var foot = com.jeladastudios.lostsignal.mission.Lookouts.near(level, p.blockPosition(), 1);
                    com.jeladastudios.lostsignal.LostSignal.LOGGER.info("LS_TEST lookout near: {}", foot);
                    var tower = com.jeladastudios.lostsignal.mission.Missions.towerAt(level, p);
                    if (tower == null) tower = p.blockPosition();
                    var m = com.jeladastudios.lostsignal.mission.Missions.begin(level, tower);
                    var t = tower;
                    c.getSource().sendSuccess(() -> Component.literal("LS_TEST mission " + (m == null ? "none" : "at " + t.toShortString())), false);
                    return m == null ? 0 : 1;
                }))
                .then(Commands.literal("sleep").executes(c -> {
                    // A bed set beside the player and the player put in it, to try the night going by.
                    ServerPlayer p = c.getSource().getPlayer();
                    if (p == null) return 0;
                    var level = p.serverLevel();
                    var foot = p.blockPosition().east(2);
                    var bed = net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState();
                    level.setBlock(foot, bed.setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST), 3);
                    level.setBlock(foot.east(), bed.setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.EAST)
                            .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3);
                    var r = p.startSleepInBed(foot.east());
                    c.getSource().sendSuccess(() -> Component.literal("LS_TEST sleep " + r.left().map(Object::toString).orElse("asleep") + " time " + level.getDayTime()), false);
                    return 1;
                }))
                .then(Commands.literal("time").executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal("LS_TEST time " + c.getSource().getLevel().getDayTime() + " sleeping "
                            + (c.getSource().getPlayer() != null && c.getSource().getPlayer().isSleeping())), false);
                    return 1;
                }))
                .then(Commands.literal("watcher").executes(c -> watcher(c, false))
                        .then(Commands.literal("anywhere").executes(c -> watcher(c, true)))));
    }

    private static int watcher(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, boolean anywhere) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) return 0;
        Director.MISSES.clear();
        var w = Director.put(p.serverLevel(), p, anywhere);
        c.getSource().sendSuccess(() -> Component.literal("LS_TEST watcher "
                + (w == null ? "none" : w.getBlockX() + " " + w.getBlockY() + " " + w.getBlockZ()) + " misses=" + Director.MISSES), false);
        return w == null ? 0 : 1;
    }

    /** A test listener's tally, reported when the broadcast it listens to has ended. */
    private record Trial(com.jeladastudios.lostsignal.radio.Broadcast broadcast, com.jeladastudios.lostsignal.radio.Ear ear,
                         java.util.concurrent.atomic.AtomicInteger frames, java.util.concurrent.atomic.AtomicInteger ends,
                         float[] signal, CommandSourceStack source) {}

    private static final java.util.List<Trial> TRIALS = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * {@code /ls_test broadcast <clip> <distance>}: the clip sent on the rescue frequency from that many blocks east
     * of the player, ten up, and listened to at the player's eyes; when it ends, how many frames arrived, how strong.
     */
    private static int broadcast(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, String clip, int distance) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) return 0;
        var level = p.serverLevel();
        var frames = new java.util.concurrent.atomic.AtomicInteger();
        var ends = new java.util.concurrent.atomic.AtomicInteger();
        float[] signal = {Float.NaN};
        var ear = new com.jeladastudios.lostsignal.radio.Ear(level, p.getEyePosition(), com.jeladastudios.lostsignal.radio.Broadcast.RESCUE, h -> {
            if (h.end()) ends.incrementAndGet();
            else {
                frames.incrementAndGet();
                signal[0] = h.signalDb();
            }
        });
        var b = com.jeladastudios.lostsignal.radio.Broadcast.send(level, p.position().add(distance, 10, 0),
                com.jeladastudios.lostsignal.radio.Broadcast.RESCUE, 1f, clip);
        if (b == null) {
            ear.close();
            c.getSource().sendFailure(Component.literal("LS_TEST broadcast none (no clip " + clip + " or no radio)"));
            return 0;
        }
        TRIALS.add(new Trial(b, ear, frames, ends, signal, c.getSource()));
        c.getSource().sendSuccess(() -> Component.literal("LS_TEST broadcast started " + clip + " " + distance), false);
        return 1;
    }

    @SubscribeEvent
    public static void trials(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        for (Trial t : TRIALS) {
            if (t.broadcast().onAir()) continue;
            TRIALS.remove(t);
            t.ear().close();
            t.source().sendSuccess(() -> Component.literal("LS_TEST broadcast heard frames=" + t.frames().get() + " ends="
                    + t.ends().get() + " signalDb=" + t.signal()[0]), false);
        }
    }

    /** {@code /ls_test hiker spawn <distance>}: a lost hiker that many blocks ahead of the player, on the ground. */
    private static int hikerSpawn(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, int distance) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) return 0;
        var level = p.serverLevel();
        var look = p.getLookAngle();
        int x = (int) Math.floor(p.getX() + look.x * distance), z = (int) Math.floor(p.getZ() + look.z * distance);
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        var hiker = com.jeladastudios.lostsignal.entity.ModEntities.HIKER.get().create(level);
        if (hiker == null) return 0;
        hiker.kit(level.random.nextInt(com.jeladastudios.lostsignal.entity.Hiker.KITS));
        hiker.moveTo(x + 0.5, y, z + 0.5, level.random.nextFloat() * 360f, 0f);
        level.addFreshEntity(hiker);
        c.getSource().sendSuccess(() -> Component.literal("LS_TEST hiker " + hiker.getId() + " at " + x + " " + y + " " + z), false);
        return 1;
    }

    private static java.util.List<com.jeladastudios.lostsignal.entity.Hiker> hikers(ServerPlayer p) {
        return new java.util.ArrayList<>(p.serverLevel().getEntities(com.jeladastudios.lostsignal.entity.ModEntities.HIKER.get(),
                h -> h.distanceToSqr(p) < 512 * 512));
    }

    /** {@code /ls_test hiker goto <x> <z>}: every hiker within 512 blocks sent there. */
    private static int hikerGoTo(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, int x, int z) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) return 0;
        var level = p.serverLevel();
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        // At a lookout, its foot: the heightmap there is its roof.
        var foot = com.jeladastudios.lostsignal.mission.Missions.towerAt(level, p);
        if (foot != null && Math.abs(foot.getX() - x) < 4 && Math.abs(foot.getZ() - z) < 4) y = foot.getY();
        var list = hikers(p);
        for (var h : list) h.follow(com.jeladastudios.lostsignal.entity.Hiker.Guide.TOWER, new net.minecraft.core.BlockPos(x, y, z));
        c.getSource().sendSuccess(() -> Component.literal("LS_TEST hiker goto " + list.size()), false);
        return list.size();
    }

    /** {@code /ls_test hiker state}: where each hiker within 512 blocks is and what they are doing. */
    private static int hikerState(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) return 0;
        StringBuilder b = new StringBuilder("LS_TEST hikers");
        for (var h : hikers(p)) {
            b.append(String.format(java.util.Locale.ROOT, " [%d %s %d %d %d fatigue %.2f goal %s]", h.getId(), h.state(),
                    h.getBlockX(), h.getBlockY(), h.getBlockZ(), h.fatigue(), h.goal() == null ? "-" : h.goal().toShortString()));
        }
        c.getSource().sendSuccess(() -> Component.literal(b.toString()), false);
        return 1;
    }

    /** {@code /ls_test hiker flare|smoke|lamp}: every hiker within 512 blocks shows that signal. */
    private static int hikerSignal(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, int which) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) return 0;
        var list = hikers(p);
        for (var h : list) {
            switch (which) {
                case 0 -> h.fireFlare();
                case 1 -> h.smoke();
                default -> h.waveLamp();
            }
        }
        c.getSource().sendSuccess(() -> Component.literal("LS_TEST hiker signal " + which + " " + list.size()), false);
        return list.size();
    }

    /** {@code /ls_test say <order> [value]}: as if pressed on the rescue panel. */
    private static int say(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, int value) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) return 0;
        var order = com.jeladastudios.lostsignal.rescue.Order.valueOf(
                com.mojang.brigadier.arguments.StringArgumentType.getString(c, "order").toUpperCase(java.util.Locale.ROOT));
        com.jeladastudios.lostsignal.rescue.Rescue.said(p, order, value);
        return 1;
    }
}
