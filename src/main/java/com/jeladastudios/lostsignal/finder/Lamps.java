package com.jeladastudios.lostsignal.finder;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.block.ModBlocks;
import com.jeladastudios.lostsignal.block.SignalLampBlock;
import com.jeladastudios.lostsignal.mission.Missions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * The lookouts' signal lamps. Feel the Nature hangs a lantern on a corner post of each walkway; the first time someone
 * keeps a lookout, that lantern is found and a signal lamp put in its place. Where the keeper has taken it down or
 * moved it, the lamp is looked for round the walkway.
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class Lamps {

    private Lamps() {}

    /** Where each kept lookout's lamp is, by its foot, once found; or the foot itself where there is none. */
    private static final Map<BlockPos, BlockPos> LAMP = new HashMap<>();

    /**
     * The lamps burning, wherever they are: their light is sent from here to whoever is far off, as the hikers'
     * signals are, so it is seen across the valley though the lamp's own ground is not being run there.
     */
    private static final java.util.Set<BlockPos> BURNING = new java.util.HashSet<>();

    /** A lamp switched on or off. */
    public static void switched(ServerLevel level, BlockPos lamp, boolean lit) {
        if (lit) BURNING.add(lamp.immutable());
        else BURNING.remove(lamp);
    }

    @SubscribeEvent
    public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        BURNING.clear();
        LAMP.clear();
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        long now = level.getGameTime();
        if (now % 4 == 0) {
            for (BlockPos lamp : BURNING) {
                // Where its ground is loaded, looked at again now and then: taken down, or put out by other means.
                if (now % 100 == 0 && level.isLoaded(lamp) && !(level.getBlockState(lamp).is(ModBlocks.SIGNAL_LAMP.get())
                        && level.getBlockState(lamp).getValue(SignalLampBlock.LIT))) {
                    BURNING.remove(lamp);
                    break;
                }
                var at = net.minecraft.world.phys.Vec3.atCenterOf(lamp);
                for (ServerPlayer p : level.players()) {
                    double d = p.position().distanceTo(at);
                    if (d < 24 || d > 512) continue;
                    level.sendParticles(p, com.jeladastudios.lostsignal.signal.ModParticles.LAMP.get(), true, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                }
            }
        }
        if (now % 100 != 7) return;
        for (ServerPlayer p : level.players()) {
            BlockPos tower = Missions.towerAt(level, p);
            if (tower != null && !LAMP.containsKey(tower)) install(level, tower);
        }
    }

    /** The lantern on the walkway's corner post made a signal lamp; or, where there already is one, found. */
    static void install(ServerLevel level, BlockPos tower) {
        BlockPos lamp = find(level, tower);
        if (lamp == null) {
            for (int dy = 20; dy <= 24 && lamp == null; dy++) {
                for (int dx = -5; dx <= 5; dx += 10) {
                    for (int dz = -5; dz <= 5; dz += 10) {
                        BlockPos p = tower.offset(dx, dy, dz);
                        if (level.getBlockState(p).is(Blocks.LANTERN)) {
                            level.setBlock(p, ModBlocks.SIGNAL_LAMP.get().defaultBlockState(), 3);
                            lamp = p;
                        }
                    }
                }
            }
            if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("LAMP at {}: {}", tower.toShortString(), lamp);
        }
        LAMP.put(tower, lamp == null ? tower : lamp);
        // One left burning when the world was last left.
        if (lamp != null && level.getBlockState(lamp).getValue(SignalLampBlock.LIT)) BURNING.add(lamp);
    }

    /** A signal lamp on or about the walkway of a lookout, or null. */
    static BlockPos find(ServerLevel level, BlockPos tower) {
        for (BlockPos p : BlockPos.betweenClosed(tower.offset(-6, 18, -6), tower.offset(6, 27, 6))) {
            if (level.getBlockState(p).is(ModBlocks.SIGNAL_LAMP.get())) return p.immutable();
        }
        return null;
    }

    /** Whether the lookout's signal lamp burns: looked at where it was, or looked for again where it is gone. */
    public static boolean lit(ServerLevel level, BlockPos tower) {
        BlockPos at = LAMP.get(tower);
        if (at == null || at.equals(tower) || !level.getBlockState(at).is(ModBlocks.SIGNAL_LAMP.get())) {
            if (!level.hasChunkAt(tower)) return false;
            at = find(level, tower);
            LAMP.put(tower, at == null ? tower : at);
            if (at == null) return false;
        }
        return level.getBlockState(at).getValue(SignalLampBlock.LIT);
    }
}
