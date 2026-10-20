package com.jeladastudios.lostsignal.rescue;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

/**
 * The radio fork's stations, as the story needs them: one within reach of a place, and whether it has power. A station
 * with no power hears nothing and sends nothing, so a keeper at a dead set is not on the air.
 */
public final class Stations {

    private Stations() {}

    /** A station within some blocks of a place, powered or not; null where there is none. */
    public static BlockPos near(ServerLevel level, BlockPos at, int within) {
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-within, -3, -within), at.offset(within, 3, within))) {
            var id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(level.getBlockState(p).getBlock());
            if (id != null && id.getNamespace().equals("simpleradio") && id.getPath().equals("station")) return p.immutable();
        }
        return null;
    }

    /** Whether the station there has power in it. */
    public static boolean powered(ServerLevel level, BlockPos station) {
        var be = level.getBlockEntity(station);
        if (be == null) return false;
        return be.getCapability(ForgeCapabilities.ENERGY).map(e -> e.getEnergyStored() > 0).orElse(false);
    }

    /** A powered station within some blocks of a place, or null. */
    public static BlockPos poweredNear(ServerLevel level, BlockPos at, int within) {
        BlockPos s = near(level, at, within);
        return s != null && powered(level, s) ? s : null;
    }
}
