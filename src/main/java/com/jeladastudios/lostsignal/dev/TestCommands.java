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
}
