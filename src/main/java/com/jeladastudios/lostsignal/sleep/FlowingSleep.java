package com.jeladastudios.lostsignal.sleep;

import com.jeladastudios.lostsignal.LostSignal;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;

/**
 * Sleep that lets the night go by instead of cutting it out: in bed, time runs faster and faster -- the moon crossing
 * the sky, the stars wheeling, the weather going on as it was -- until morning, when the sleeper wakes as anyone does
 * at daybreak. The vanilla jump to morning never comes. Whatever happens in the night may wake them: the radio, a knock
 * at the door; time then goes at its own pace again.
 *
 * <p>Vanilla jumps to morning once enough players have slept a hundred ticks. Each sleeper's count is held just under
 * that; enough of them in bed, the night runs on fast instead.</p>
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class FlowingSleep {

    private FlowingSleep() {}

    /** How many times faster than its own pace the night goes when everyone needed is asleep. */
    static final int FASTEST = 25;
    /** How many ticks it takes to come up to that pace, and back down. */
    static final int EASE = 60;

    private static final Field SLEEP_COUNTER = ObfuscationReflectionHelper.findField(Player.class, "f_36110_");
    private static float pace = 1f;

    @SubscribeEvent
    public static void player(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || !p.isSleeping()
                || !com.jeladastudios.lostsignal.Config.on(com.jeladastudios.lostsignal.Config.FLOWING_SLEEP)) return;
        try {
            int counter = SLEEP_COUNTER.getInt(p);
            if (counter > 99) SLEEP_COUNTER.setInt(p, 99);
        } catch (IllegalAccessException e) {
            LostSignal.LOGGER.warn("Flowing sleep could not hold the sleep counter: {}", e.toString());
        }
    }

    @SubscribeEvent
    public static void level(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (!com.jeladastudios.lostsignal.Config.on(com.jeladastudios.lostsignal.Config.FLOWING_SLEEP)) {
            pace = 1f;
            return;
        }
        boolean flowing = enoughAsleep(level);
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON && level.getGameTime() % 40 == 0 && (flowing || pace > 1.01f || level.players().stream().anyMatch(Player::isSleeping))) {
            LostSignal.LOGGER.info("SLEEP time {} pace {} flowing {} asleep {}", level.getDayTime(), String.format(java.util.Locale.ROOT, "%.1f", pace), flowing,
                    level.players().stream().filter(Player::isSleeping).count());
        }
        float target = flowing ? FASTEST : 1f;
        pace += Math.signum(target - pace) * Math.min(Math.abs(target - pace), (FASTEST - 1f) / EASE);
        if (pace > 1.01f && level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
            // The extra ticks of the day this tick, carried over as fractions.
            carry += pace - 1f;
            int whole = (int) carry;
            if (whole > 0) {
                carry -= whole;
                level.setDayTime(level.getDayTime() + whole);
                // Told to every client each tick, so the sky turns smoothly rather than a second at a time.
                level.getServer().getPlayerList().broadcastAll(new net.minecraft.network.protocol.game.ClientboundSetTimePacket(
                        level.getGameTime(), level.getDayTime(), true), level.dimension());
            }
        }
    }

    private static float carry;

    /** Whether as many players are asleep as the world's rule asks for skipping a night. */
    static boolean enoughAsleep(ServerLevel level) {
        int players = 0, asleep = 0;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            players++;
            if (p.isSleeping()) asleep++;
        }
        if (players == 0 || asleep == 0) return false;
        int percent = level.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
        return asleep >= Math.max(1, (int) Math.ceil(players * percent / 100.0));
    }

    /** Whoever sleeps within some blocks of a place is woken: something happened there. */
    public static void wake(ServerLevel level, Vec3 near, double within, String why) {
        for (ServerPlayer p : level.players()) {
            if (!p.isSleeping() || p.position().distanceTo(near) > within) continue;
            p.stopSleepInBed(true, true);
            p.displayClientMessage(Component.translatable("sleep.fts_lostsignal.woken." + why), true);
        }
    }
}
