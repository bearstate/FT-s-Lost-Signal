package com.jeladastudios.lostsignal.tower;

import com.jeladastudios.lostsignal.LostSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;

import java.lang.reflect.Method;

/**
 * FT's Geology's earthquakes and the lookouts. Its API 5 lets a box be left alone by them: a lookout of plain timber is
 * otherwise a building to them, and a tall thin one comes down. Feel the Nature asks it for the towers it builds now;
 * this asks it for the ones built before, once each, as they are come to. And a quake felt round the lookouts is the
 * District's business: once the ground is still, it asks after them on the radio. Both looked up by name, so an older
 * FT's Geology without them is simply done without.
 */
public final class Quakes {

    private Quakes() {}

    /** How far from a quake a lookout is asked after, in blocks; and how strong it must be to be. */
    static final double ASKS_WITHIN = 3000, FELT = 4.0;
    /** How long after the waves leave the District calls: the shaking over, the dust down. */
    static final int AFTER = 20 * 40;

    private static Method protect;
    private static boolean looked;

    /** A lookout left alone by the quakes, from its footings to its roof, the generator at its leg; true where asked. */
    static boolean protect(ServerLevel level, BlockPos foot) {
        if (!looked) {
            looked = true;
            try {
                protect = com.jeladastudios.ftsgeology.api.FtsGeologyApi.class.getMethod("protectFromQuakes",
                        net.minecraft.world.level.LevelAccessor.class, BoundingBox.class);
            } catch (NoSuchMethodException | LinkageError e) {
                protect = null;
            }
        }
        if (protect == null) return false;
        try {
            protect.invoke(null, level, new BoundingBox(foot.getX() - 7, foot.getY() - 13, foot.getZ() - 7,
                    foot.getX() + 7, foot.getY() + 33, foot.getZ() + 7));
            if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("QUAKES leave the lookout at {} alone", foot.toShortString());
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Listening for FT's Geology's quakes, where it tells of them. Called once, as the mod is set up. */
    @SuppressWarnings("unchecked")
    public static void listen() {
        try {
            Class<? extends Event> type = (Class<? extends Event>) Class.forName("com.jeladastudios.ftsgeology.api.QuakeEvent");
            Method level = type.getMethod("level"), epicentre = type.getMethod("epicentre"), magnitude = type.getMethod("magnitude"),
                    start = type.getMethod("startTick"), aftershock = type.getMethod("aftershock");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, (Class<Event>) type, e -> {
                try {
                    quake((ServerLevel) level.invoke(e), (BlockPos) epicentre.invoke(e), (double) magnitude.invoke(e),
                            (long) start.invoke(e), (boolean) aftershock.invoke(e));
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    LostSignal.LOGGER.warn("A quake FT's Geology told of could not be read: {}", ex.toString());
                }
            });
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError e) {
            // An FT's Geology that does not tell of its quakes: the District does not hear of them either.
        }
    }

    /** A quake: if the lookouts round felt it, the District asks after them once it is over. Not for every aftershock. */
    static void quake(ServerLevel level, BlockPos epicentre, double magnitude, long start, boolean aftershock) {
        if (magnitude < FELT || aftershock && magnitude < FELT + 1) return;
        boolean near = false;
        for (ServerPlayer p : level.players()) {
            BlockPos tower = com.jeladastudios.lostsignal.mission.Missions.towerAt(level, p);
            if (tower != null && Math.hypot(tower.getX() - epicentre.getX(), tower.getZ() - epicentre.getZ()) < ASKS_WITHIN) near = true;
        }
        if (!near) return;
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("QUAKE M{} at {}: the District will ask", magnitude, epicentre.toShortString());
        int wait = (int) Math.max(0, start - level.getGameTime()) + AFTER;
        Speech.after(level, wait, () -> District.call(level, "D_QUAKE"));
    }
}
