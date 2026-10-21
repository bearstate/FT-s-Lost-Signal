package com.jeladastudios.lostsignal.finder;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.mission.Missions;
import com.jeladastudios.lostsignal.signal.Signals;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The fire finder on its stand in the middle of the cab: a sight turning over a ring marked in degrees from true
 * north. The keeper steps up to it (a right click on the stand; crouched, it is a cartography table as ever) and looks
 * along the sight: the ring reads the bearing they look on. Held steady on a flare, a column of smoke, a lamp waved in
 * the dark, it gives that signal's bearing and, by the keeper's eye, how far off it is -- and the way back from there
 * to the lookout, the bearing a hiker with a compass is told to walk, goes into the rescue panel ready to send.
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class FireFinder {

    private FireFinder() {}

    /** How near the stand the keeper stays to look through the sight. */
    static final double AT_STAND = 1.6;
    /** How steady the sight must be held, in degrees, and for how long, in ticks, to take a reading. */
    static final double STEADY = 1.2;
    static final int HOLD = 24;
    /** How near the sight's line a signal must be, in degrees, to be read. */
    static final double ON_LINE = 2.5;

    private static final class Sighting {
        final BlockPos stand, tower;
        float yaw, pitch;
        int held;
        boolean read;

        Sighting(BlockPos stand, BlockPos tower) {
            this.stand = stand;
            this.tower = tower;
        }
    }

    private static final Map<UUID, Sighting> SIGHTING = new HashMap<>();

    /** A reading taken: what, its bearing, its distance by eye, where it truly was, and when. */
    public record Reading(String kind, int bearing, int distance, Vec3 at, long when) {}

    private static final Map<UUID, Reading> LAST = new HashMap<>();
    /** When each player last pressed on the stand. */
    private static final Map<UUID, Long> PRESSED = new HashMap<>();

    /** A player's last reading off the fire finder, if it is not more than ten minutes old. */
    public static Reading last(ServerPlayer p) {
        Reading r = LAST.get(p.getUUID());
        return r != null && p.level().getGameTime() - r.when() < 20 * 60 * 10 ? r : null;
    }

    /** A compass bearing from a look: 0 north, 90 east. */
    public static int bearing(float yaw) {
        return Math.floorMod(Math.round(yaw + 180f), 360);
    }

    /** A bearing's compass point, for the language files: n, ne, e, ... */
    public static String point(int bearing) {
        String[] points = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
        return points[Math.floorMod(Math.round(bearing / 45f), 8)];
    }

    @SubscribeEvent
    public static void click(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.isShiftKeyDown()) return;
        ServerLevel level = p.serverLevel();
        BlockPos pos = event.getPos();
        if (!level.getBlockState(pos).is(Blocks.CARTOGRAPHY_TABLE)) return;
        BlockPos tower = Missions.towerAt(level, p);
        if (tower == null || Math.abs(pos.getX() - tower.getX()) > 1 || Math.abs(pos.getZ() - tower.getZ()) > 1
                || pos.getY() < tower.getY() + 18 || pos.getY() > tower.getY() + 24) return;
        event.setCanceled(true);
        // Once a press: a hand held on the stand, as the game repeats it, does not step up and away again.
        Long last = PRESSED.get(p.getUUID());
        // Every press marks the time, those let through and those not, so a held button -- repeated every few ticks --
        // never gets through again until it is let go.
        PRESSED.put(p.getUUID(), level.getGameTime());
        if (last != null && level.getGameTime() - last < 10 && last <= level.getGameTime()) return;
        if (SIGHTING.containsKey(p.getUUID())) {
            SIGHTING.remove(p.getUUID());
            p.displayClientMessage(Component.translatable("finder.fts_lostsignal.away"), true);
            return;
        }
        SIGHTING.put(p.getUUID(), new Sighting(pos.immutable(), tower));
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("FINDER sighting at {}", pos.toShortString());
        level.playSound(null, pos, SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 0.8f, 0.9f);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        Sighting s = SIGHTING.get(p.getUUID());
        if (s == null) return;
        ServerLevel level = p.serverLevel();
        // Stepped away, or crouched: done with the sight.
        if (p.isShiftKeyDown() || p.position().distanceTo(Vec3.atBottomCenterOf(s.stand).add(0, 1, 0)) > AT_STAND + 1
                || !level.getBlockState(s.stand).is(Blocks.CARTOGRAPHY_TABLE)) {
            SIGHTING.remove(p.getUUID());
            p.displayClientMessage(Component.translatable("finder.fts_lostsignal.away"), true);
            return;
        }
        float yaw = p.getYRot(), pitch = p.getXRot();
        boolean steady = Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - s.yaw)) < STEADY && Math.abs(pitch - s.pitch) < STEADY;
        if (steady) s.held++;
        else {
            s.held = 0;
            s.read = false;
            s.yaw = yaw;
            s.pitch = pitch;
        }
        int bearing = bearing(yaw);
        if (s.held == HOLD && !s.read) {
            s.read = true;
            read(level, p, s, bearing);
            return;
        }
        if (p.tickCount % 2 == 0 && !s.read) {
            p.displayClientMessage(Component.translatable("finder.fts_lostsignal.ring", bearing,
                    Component.translatable("radio.fts_lostsignal.point." + point(bearing))), true);
        }
    }

    /** The reading off the ring, held steady: what is on the line, if anything, how far by eye, and the way back. */
    static void read(ServerLevel level, ServerPlayer p, Sighting s, int bearing) {
        var seen = Signals.onLine(level, p.getEyePosition(), p.getViewVector(1f), ON_LINE, p);
        level.playSound(null, s.stand, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 0.7f, 1.2f);
        if (seen == null) {
            p.displayClientMessage(Component.translatable("finder.fts_lostsignal.nothing", bearing), true);
            if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("FINDER nothing at {}", bearing);
            return;
        }
        Vec3 to = seen.at().subtract(p.getEyePosition());
        // The true bearing of it, to a degree; its distance as a practised eye puts it, to ten blocks, give or take.
        int exact = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(to.x, -to.z))), 360);
        double flat = Math.hypot(to.x, to.z);
        int far = (int) Math.round(flat * (0.88 + level.random.nextDouble() * 0.24) / 10.0) * 10;
        int back = Math.floorMod(exact + 180, 360);
        Component text = Component.translatable("finder.fts_lostsignal.reading",
                Component.translatable("finder.fts_lostsignal.kind." + seen.kind()), exact,
                Component.translatable("radio.fts_lostsignal.point." + point(exact)), far, back).withStyle(ChatFormatting.GOLD);
        p.sendSystemMessage(text);
        p.displayClientMessage(Component.translatable("finder.fts_lostsignal.read"), true);
        com.jeladastudios.lostsignal.net.Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                new com.jeladastudios.lostsignal.net.Net.Reading(seen.kind(), exact, far));
        LAST.put(p.getUUID(), new Reading(seen.kind(), exact, far, seen.at(), level.getGameTime()));
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("FINDER {} at {} ~{} blocks, back {}", seen.kind(), exact, far, back);
    }

    /** A server stopped: nothing of its kept for the next world. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void forget(net.minecraftforge.event.server.ServerStoppedEvent event) {
        SIGHTING.clear();
        LAST.clear();
        PRESSED.clear();
    }
}
