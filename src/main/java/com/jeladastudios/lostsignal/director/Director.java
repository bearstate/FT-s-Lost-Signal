package com.jeladastudios.lostsignal.director;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.entity.ModEntities;
import com.jeladastudios.lostsignal.entity.Stranger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What happens to whom, and when: when the stranger is seen, and when he comes to talk. Once a second, for each player
 * out under the sky in the overworld:
 * <ul>
 *   <li>at dusk and by night, a small chance -- none for a while after the last sighting -- that he is put at the edge
 *   of the trees somewhere ahead of them, masked, where they could see him if they looked;</li>
 *   <li>by day, a smaller one that he stands watching from a wood's edge, as an ordinary man would not;</li>
 *   <li>by day, away from the lookouts, a smaller one still that he comes up out of the trees behind them to talk.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class Director {

    private Director() {}

    /** The chance each second of a sighting, once one may come at all: by night, and by day. */
    static final float CHANCE = 1f / 240f, CHANCE_DAY = 1f / 900f;
    /** The quiet after a sighting, in ticks: twenty minutes at least. */
    static final int REST = 20 * 60 * 20;
    /** The chance each second that he comes to talk, by day, and the quiet after: a day and a half. */
    static final float CHANCE_TALK = 1f / 1800f;
    static final int REST_TALK = 36_000;
    /** How far from the player he is put, in blocks: to be seen, and to come up from. */
    static final int NEAR = 40, FAR = 70, COMES_FROM = 28;

    private static final Map<UUID, Long> NEXT = new HashMap<>(), NEXT_TALK = new HashMap<>();

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 != 0 || player.isSpectator()) return;
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD) return;
        long now = level.getGameTime();
        if (!level.canSeeSky(player.blockPosition()) || strangerOf(level, player) != null) return;
        if (!com.jeladastudios.lostsignal.Config.on(com.jeladastudios.lostsignal.Config.STRANGER)) return;
        float boost = com.jeladastudios.lostsignal.dev.TestCommands.ON ? 20 : 1;
        boolean dark = dark(level);
        if (!dark && com.jeladastudios.lostsignal.Config.on(com.jeladastudios.lostsignal.Config.TALKS) && now >= NEXT_TALK.getOrDefault(player.getUUID(), 0L) && level.random.nextFloat() < CHANCE_TALK * boost
                && com.jeladastudios.lostsignal.mission.Missions.towerAt(level, player) == null) {
            if (comeUp(level, player) != null) NEXT_TALK.put(player.getUUID(), now + REST_TALK);
            return;
        }
        if (now < NEXT.getOrDefault(player.getUUID(), 0L)) return;
        if (level.random.nextFloat() >= (dark ? CHANCE : CHANCE_DAY) * boost * com.jeladastudios.lostsignal.Config.scale(com.jeladastudios.lostsignal.Config.SIGHTINGS)) return;
        if (put(level, player) != null) NEXT.put(player.getUUID(), now + REST);
    }

    /** Dusk, night, and the dark before dawn. */
    static boolean dark(ServerLevel level) {
        long t = level.getDayTime() % 24000;
        return t > 12500 && t < 23300;
    }

    /** The stranger already out for a player, if any. */
    static Stranger strangerOf(ServerLevel level, ServerPlayer player) {
        for (Stranger s : level.getEntities(ModEntities.STRANGER.get(), e -> player.getUUID().equals(e.watching()))) return s;
        return null;
    }

    /**
     * Him come up to talk: put out of their sight some way behind them, among trees if there are any, and walking up.
     * Null where no such spot is found.
     */
    public static Stranger comeUp(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.random;
        Vec3 eye = player.getEyePosition();
        double behind = Math.toRadians(player.getYRot() + 270.0);
        for (int tries = 0; tries < 24; tries++) {
            double angle = behind + Math.toRadians((random.nextDouble() * 2 - 1) * 80);
            double distance = COMES_FROM + random.nextDouble() * 8;
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos foot = new BlockPos(x, y, z);
            if (!level.getFluidState(foot.below()).isEmpty() || !level.getBlockState(foot.below()).isSolid()) continue;
            if (!level.getBlockState(foot).getCollisionShape(level, foot).isEmpty()
                    || !level.getBlockState(foot.above()).getCollisionShape(level, foot.above()).isEmpty()) continue;
            if (tries < 16 && seen(level, player, eye, x + 0.5, y, z + 0.5)) continue;
            Stranger s = ModEntities.STRANGER.get().create(level);
            if (s == null) return null;
            s.moveTo(x + 0.5, y, z + 0.5, 0f, 0f);
            s.talk(player);
            level.addFreshEntity(s);
            if (com.jeladastudios.lostsignal.dev.TestCommands.ON) {
                LostSignal.LOGGER.info("STRANGER comes up from {} {} {}, {} blocks from {}", x, y, z, (int) distance, player.getName().getString());
            }
            return s;
        }
        return null;
    }

    /**
     * Him put to watch a player: at a spot in a wood ahead of them, within seventy degrees either side of where they
     * face, beside a trunk, where a line from their eyes reaches his head; masked by night. Null where no such spot is found.
     */
    public static Stranger put(ServerLevel level, ServerPlayer player) {
        return put(level, player, false);
    }

    /** The same, or (for the test command) anywhere it can be seen, wood or no wood. */
    public static Stranger put(ServerLevel level, ServerPlayer player, boolean anywhere) {
        RandomSource random = level.random;
        Vec3 eye = player.getEyePosition();
        double facing = Math.toRadians(player.getYRot() + 90.0);
        for (int tries = 0; tries < 24; tries++) {
            double angle = facing + Math.toRadians((random.nextDouble() * 2 - 1) * 70);
            double distance = NEAR + random.nextDouble() * (FAR - NEAR);
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) { miss("unloaded"); continue; }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos foot = new BlockPos(x, y, z);
            if (!level.getFluidState(foot.below()).isEmpty() || !level.getBlockState(foot.below()).isSolid()) { miss("ground"); continue; }
            if (!level.getBlockState(foot).getCollisionShape(level, foot).isEmpty()
                    || !level.getBlockState(foot.above()).getCollisionShape(level, foot.above()).isEmpty()) { miss("room"); continue; }
            if (!anywhere && !wooded(level, foot)) { miss("open"); continue; }
            if (!anywhere && !besideTrunk(level, foot)) { miss("trunk"); continue; }
            if (!seen(level, player, eye, x + 0.5, y, z + 0.5)) { miss("hidden"); continue; }
            Stranger s = ModEntities.STRANGER.get().create(level);
            if (s == null) return null;
            s.moveTo(x + 0.5, y, z + 0.5, 0f, 0f);
            s.watch(player, 20 * (60 + random.nextInt(90)));
            s.masked(dark(level));
            level.addFreshEntity(s);
            if (com.jeladastudios.lostsignal.dev.TestCommands.ON) {
                LostSignal.LOGGER.info("STRANGER put at {} {} {}, {} blocks from {}", x, y, z, (int) distance, player.getName().getString());
            }
            return s;
        }
        return null;
    }

    /**
     * Whether any of him can be seen from the player's eyes: his head, his shoulders either side, or his middle -- half
     * hidden behind a trunk is how he should first be seen.
     */
    static boolean seen(ServerLevel level, ServerPlayer player, Vec3 eye, double x, double y, double z) {
        for (double[] p : new double[][]{{0, 1.9, 0}, {0.3, 1.6, 0}, {-0.3, 1.6, 0}, {0, 1.6, 0.3}, {0, 1.6, -0.3}, {0, 1.1, 0}}) {
            Vec3 to = new Vec3(x + p[0], y + p[1], z + p[2]);
            if (level.clip(new ClipContext(eye, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }

    /** Why spots were turned down, for the test command. */
    public static final Map<String, Integer> MISSES = new java.util.TreeMap<>();

    private static void miss(String why) {
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) MISSES.merge(why, 1, Integer::sum);
    }

    /**
     * In a wood: of eighteen points round it, four and eight blocks out, at least three under leaves standing two
     * blocks or more over the ground: the open spruce wood of the subalpine belt will do.
     */
    static boolean wooded(ServerLevel level, BlockPos at) {
        int under = 0;
        for (int ring = 4; ring <= 8; ring += 4) {
            for (int i = 0; i < 9; i++) {
                double a = Math.PI * 2 * (i + ring / 8.0) / 9;
                int x = at.getX() + (int) Math.round(Math.cos(a) * ring), z = at.getZ() + (int) Math.round(Math.sin(a) * ring);
                if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 2) under++;
            }
        }
        return under >= 3;
    }

    /** A trunk within three blocks of the spot, standing as high as his head. */
    static boolean besideTrunk(ServerLevel level, BlockPos at) {
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-3, 0, -3), at.offset(3, 2, 3))) {
            if (level.getBlockState(p).is(BlockTags.LOGS)) return true;
        }
        return false;
    }

    /** A server stopped: nothing of its kept for the next world. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void forget(net.minecraftforge.event.server.ServerStoppedEvent event) {
        NEXT.clear();
        NEXT_TALK.clear();
    }
}
