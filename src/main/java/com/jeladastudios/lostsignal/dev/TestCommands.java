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
                        .then(Commands.literal("state").executes(TestCommands::hikerState)))
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
        var list = hikers(p);
        for (var h : list) h.goTo(new net.minecraft.core.BlockPos(x, y, z));
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
}
