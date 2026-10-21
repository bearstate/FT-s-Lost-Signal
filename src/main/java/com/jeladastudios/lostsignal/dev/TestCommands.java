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
 * Commands for trying the mod in the development game: {@code /ls_test stranger [anywhere|talk]} puts the stranger for the player now,
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
                .then(Commands.literal("visit").executes(c -> {
                    // Something up the stair of the lookout the player is at, now.
                    ServerPlayer p = c.getSource().getPlayer();
                    if (p == null) return 0;
                    var tower = com.jeladastudios.lostsignal.mission.Missions.towerAt(p.serverLevel(), p);
                    var v = tower == null ? null : com.jeladastudios.lostsignal.visit.Visit.begin(p.serverLevel(), tower);
                    c.getSource().sendSuccess(() -> Component.literal("LS_TEST visit " + (v == null ? "none" : "at " + v.foot.toShortString() + " door " + v.door.toShortString())), false);
                    return v == null ? 0 : 1;
                }))
                .then(Commands.literal("remains").executes(c -> remains(c, true))
                        .then(Commands.literal("nobody").executes(c -> remains(c, false))))
                .then(Commands.literal("offer").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayer();
                    if (p == null) return 0;
                    var tower = com.jeladastudios.lostsignal.mission.Missions.towerAt(p.serverLevel(), p);
                    boolean ok = tower != null && com.jeladastudios.lostsignal.mission.Missions.takeNow(p.serverLevel(), tower, true);
                    c.getSource().sendSuccess(() -> Component.literal("LS_TEST offer " + ok), false);
                    return ok ? 1 : 0;
                }))
                .then(Commands.literal("take").executes(c -> {
                    // What comes for the hiker of the lookout's night, in ten seconds.
                    ServerPlayer p = c.getSource().getPlayer();
                    if (p == null) return 0;
                    var tower = com.jeladastudios.lostsignal.mission.Missions.towerAt(p.serverLevel(), p);
                    boolean ok = tower != null && com.jeladastudios.lostsignal.mission.Missions.takeNow(p.serverLevel(), tower, false);
                    c.getSource().sendSuccess(() -> Component.literal("LS_TEST take " + ok), false);
                    return ok ? 1 : 0;
                }))
                .then(Commands.literal("talk").executes(c -> keeperTalk(c, null))
                        .then(Commands.argument("topic", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(c -> keeperTalk(c, com.mojang.brigadier.arguments.StringArgumentType.getString(c, "topic")))))
                .then(Commands.literal("call").then(Commands.argument("which", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 8)).executes(c -> {
                    // A call from the panel: 0 the District, 1.. the lookouts round, nearest first.
                    ServerPlayer p = c.getSource().getPlayer();
                    if (p == null) return 0;
                    int which = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "which");
                    var list = com.jeladastudios.lostsignal.tower.Calls.neighbours(p);
                    long key = which == 0 || which > list.size() ? 0 : list.get(which - 1).key();
                    com.jeladastudios.lostsignal.tower.Calls.call(p, key);
                    c.getSource().sendSuccess(() -> Component.literal("LS_TEST call " + which + " of " + list.size()), false);
                    return 1;
                })))
                .then(Commands.literal("mimic").then(Commands.argument("how", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                    // Him on the radio now: "echo" (the player's own voice off his tape) or "call" (lost, as he plays it).
                    ServerPlayer p = c.getSource().getPlayer();
                    if (p == null) return 0;
                    var tower = com.jeladastudios.lostsignal.mission.Missions.towerAt(p.serverLevel(), p);
                    boolean ok = tower != null && com.jeladastudios.lostsignal.visit.Mimic.begin(p.serverLevel(), p, tower,
                            "echo".equals(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "how")));
                    c.getSource().sendSuccess(() -> Component.literal("LS_TEST mimic " + ok), false);
                    return ok ? 1 : 0;
                })))
                .then(Commands.literal("towers").executes(c -> {
                    // The lookout the player is at and the ones round it, as they are.
                    ServerPlayer p = c.getSource().getPlayer();
                    if (p == null) return 0;
                    var level = p.serverLevel();
                    var foot = com.jeladastudios.lostsignal.mission.Missions.towerAt(level, p);
                    if (foot == null) foot = com.jeladastudios.lostsignal.mission.Lookouts.near(level, p.blockPosition(), 1);
                    if (foot == null) return 0;
                    var home = com.jeladastudios.lostsignal.tower.Towers.at(level, foot);
                    StringBuilder s = new StringBuilder("LS_TEST towers: " + home.number + " " + home.state + " " + home.keeper + " " + home.need
                            + " ring2=" + com.jeladastudios.lostsignal.mission.Lookouts.around(level, foot, 2));
                    for (var t : com.jeladastudios.lostsignal.tower.Towers.around(level, home)) {
                        s.append(" | ").append(t.number).append(" ").append(t.state).append(" ").append(t.keeper).append(" @").append(t.x).append(",").append(t.z);
                    }
                    c.getSource().sendSuccess(() -> Component.literal(s.toString()), false);
                    return 1;
                })
                        .then(Commands.argument("state", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                            // The lookout the player is at, made as asked: kept, empty, abandoned, stranger.
                            ServerPlayer p = c.getSource().getPlayer();
                            if (p == null) return 0;
                            var level = p.serverLevel();
                            var foot = com.jeladastudios.lostsignal.mission.Missions.towerAt(level, p);
                            if (foot == null) return 0;
                            var t = com.jeladastudios.lostsignal.tower.Towers.at(level, foot);
                            String state = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "state");
                            com.jeladastudios.lostsignal.tower.Towers.force(level, t, state);
                            c.getSource().sendSuccess(() -> Component.literal("LS_TEST tower " + t.number + " " + t.state + " " + t.keeper + " " + t.need), false);
                            return 1;
                        })))
                .then(Commands.literal("hide").executes(c -> {
                    // Under the nearest bed, as a crouched click on it does.
                    ServerPlayer p = c.getSource().getPlayer();
                    if (p == null) return 0;
                    for (var q : net.minecraft.core.BlockPos.betweenClosed(p.blockPosition().offset(-5, -1, -5), p.blockPosition().offset(5, 1, 5))) {
                        var s = p.level().getBlockState(q);
                        if (s.getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
                            com.jeladastudios.lostsignal.visit.Hiding.hide(p, q.immutable(), s);
                            return 1;
                        }
                    }
                    return 0;
                }))
                .then(Commands.literal("time").executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal("LS_TEST time " + c.getSource().getLevel().getDayTime() + " sleeping "
                            + (c.getSource().getPlayer() != null && c.getSource().getPlayer().isSleeping())), false);
                    return 1;
                }))
                .then(Commands.literal("stranger").executes(c -> watcher(c, false))
                        .then(Commands.literal("talk").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayer();
                            var s = p == null ? null : Director.comeUp(p.serverLevel(), p);
                            c.getSource().sendSuccess(() -> Component.literal("LS_TEST stranger talk " + (s == null ? "none" : s.blockPosition().toShortString())), false);
                            return s == null ? 0 : 1;
                        }))
                        .then(Commands.literal("anywhere").executes(c -> watcher(c, true)))));
    }

    /** The nearest keeper talked to: their talk opened, or a topic asked of them. */
    private static int keeperTalk(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, String topic) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) return 0;
        var keepers = p.serverLevel().getEntitiesOfClass(com.jeladastudios.lostsignal.entity.Keeper.class, p.getBoundingBox().inflate(16));
        if (keepers.isEmpty()) return 0;
        var k = keepers.get(0);
        var t = k.record();
        if (t == null) return 0;
        if (topic == null) {
            com.jeladastudios.lostsignal.net.Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                    new com.jeladastudios.lostsignal.net.Net.Talk(k.getId(), t.keeper, t.number));
        } else {
            com.jeladastudios.lostsignal.tower.Talk.asked(p, k, com.jeladastudios.lostsignal.tower.Talk.Topic.valueOf(topic.toUpperCase(java.util.Locale.ROOT)));
        }
        return 1;
    }

    /** A taken hiker's scene twelve blocks ahead of the player, with or without the body. */
    private static int remains(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, boolean body) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) return 0;
        var level = p.serverLevel();
        var look = p.getLookAngle();
        var at = net.minecraft.core.BlockPos.containing(p.getX() + look.x * 12, p.getY(), p.getZ() + look.z * 12);
        var made = com.jeladastudios.lostsignal.remains.Scene.make(level, at, null, level.random.nextInt(com.jeladastudios.lostsignal.entity.Hiker.KITS),
                com.jeladastudios.lostsignal.remains.Journal.name(level.random.nextLong()), null, level.random.nextInt(2), body, level.random);
        c.getSource().sendSuccess(() -> Component.literal("LS_TEST remains at " + made.camp().toShortString() + " end " + made.end().toShortString()
                + " body " + made.body()), false);
        return 1;
    }

    private static int watcher(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, boolean anywhere) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) return 0;
        Director.MISSES.clear();
        var w = Director.put(p.serverLevel(), p, anywhere);
        c.getSource().sendSuccess(() -> Component.literal("LS_TEST stranger "
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
