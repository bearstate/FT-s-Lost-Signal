package com.jeladastudios.lostsignal.mission;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Feel the Nature's lookouts, found by its own siting -- from the ground model, as the world was made with -- and
 * read with the true ground. FT's Geology answers on the server's own thread from a blunter ground unless a search
 * for the true one is under way, and a lookout sited on the blunt ground is not where the world built it.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.jeladastudios.lostsignal.LostSignal.MODID)
public final class Lookouts {

    private Lookouts() {}

    /**
     * Every lookout in the cells round a place, a ring of them out: their feet, as the siting puts them. Feel the
     * Nature puts at most one in each of its cells.
     *
     * <p>For a worker thread, not the server's: the siting reads only the generator's ground model and FT's Geology's,
     * as world generation does from its own threads -- where the ground is exact without asking -- and working out a
     * ring of cells the first time takes seconds.</p>
     */
    public static java.util.List<BlockPos> aroundOffThread(ServerLevel level, BlockPos at, int ring) {
        java.util.List<BlockPos> feet = new java.util.ArrayList<>();
        int cell;
        try {
            cell = com.jeladastudios.feelthenature.world.LookoutTowerFeature.CELL * 16;
        } catch (LinkageError e) {
            return feet;
        }
        int cx = Math.floorDiv(at.getX(), cell), cz = Math.floorDiv(at.getZ(), cell);
        for (int dx = -ring; dx <= ring; dx++) {
            for (int dz = -ring; dz <= ring; dz++) {
                BlockPos middle = new BlockPos((cx + dx) * cell + cell / 2, at.getY(), (cz + dz) * cell + cell / 2);
                try {
                    var found = com.jeladastudios.feelthenature.world.LookoutTowerFeature.locate(level, middle, 0);
                    if (found != null && !feet.contains(found.foot())) feet.add(found.foot());
                } catch (LinkageError e) {
                    return feet;
                }
            }
        }
        return feet;
    }

    /** The one thread the siting is worked out on, off the server's. */
    private static final java.util.concurrent.ExecutorService SITING = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Lost Signal siting");
        t.setDaemon(true);
        return t;
    });

    /**
     * Something of the siting worked out on its own thread -- where it reads the ground exactly, as world generation
     * does, and where the first time over new cells, seconds long, holds up no one -- and its answer handed back on the
     * server's thread.
     */
    public static <T> void offThread(ServerLevel level, java.util.function.Supplier<T> work, java.util.function.Consumer<T> then) {
        int gen = generation;
        var server = level.getServer();
        SITING.submit(() -> {
            T result;
            try {
                result = work.get();
            } catch (RuntimeException | LinkageError e) {
                com.jeladastudios.lostsignal.LostSignal.LOGGER.warn("Siting failed: {}", e.toString());
                result = null;
            }
            T r = result;
            back(server, gen, () -> then.accept(r));
        });
    }

    /**
     * Something worked out off the server's thread handed back to it -- but not to a server that has stopped since, nor
     * into the next world: a stopped server runs what it is handed there and then, on the worker's thread.
     */
    public static void back(net.minecraft.server.MinecraftServer server, int gen, Runnable then) {
        if (gen != generation || !server.isRunning()) return;
        server.execute(() -> {
            if (gen == generation && server.isRunning()) then.run();
        });
    }

    /** Which run of a server this is: counted up as each stops. */
    private static volatile int generation;

    public static int generation() {
        return generation;
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        generation++;
    }

    /** The nearest lookout's foot within some cells of a place, or null: for the siting's own thread. */
    public static BlockPos nearOffThread(ServerLevel level, BlockPos at, int cells) {
        try {
            var found = com.jeladastudios.feelthenature.world.LookoutTowerFeature.locate(level, at, cells);
            return found == null ? null : found.foot();
        } catch (LinkageError e) {
            return null;
        }
    }

    /** The nearest lookout's foot within some cells of a place, or null. */
    public static BlockPos near(ServerLevel level, BlockPos at, int cells) {
        boolean exact = false;
        try {
            com.jeladastudios.ftsgeology.worldgen.relief.ServerThreadGround.enterExact();
            exact = true;
        } catch (LinkageError e) {
            // An FT's Geology without it: its ground is exact everywhere.
        }
        try {
            var found = com.jeladastudios.feelthenature.world.LookoutTowerFeature.locate(level, at, cells);
            return found == null ? null : found.foot();
        } catch (LinkageError e) {
            return null;
        } finally {
            if (exact) com.jeladastudios.ftsgeology.worldgen.relief.ServerThreadGround.exitExact();
        }
    }
}
