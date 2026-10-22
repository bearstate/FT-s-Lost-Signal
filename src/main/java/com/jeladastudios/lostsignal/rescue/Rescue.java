package com.jeladastudios.lostsignal.rescue;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.entity.Hiker;
import com.jeladastudios.lostsignal.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The keeper's words to a lost hiker, and the hiker's answers: what each order means to someone on the ground who can
 * see only what is round them, and whether they can do it. The answer comes a moment later, as it would over a radio.
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class Rescue {

    private Rescue() {}

    /** How near a radio station the keeper must stand to be on the air, in blocks. */
    static final int AT_STATION = 6;
    /** How far a hiker's handheld is heard: the subtitle's reach. */
    static final double IN_CONTACT = Lines.HEARD;
    /** A hiker's handheld, as a share of a station's power. */
    static final float HANDHELD = 0.15f;
    /** How far a leg of following goes, in blocks. */
    static final int LEG = 40;

    private record Pending(ServerLevel level, Hiker hiker, String line, long at) {}

    private static final List<Pending> PENDING = new CopyOnWriteArrayList<>();

    /** The keeper said something from the panel. */
    public static void said(ServerPlayer keeper, Order order, int value) {
        ServerLevel level = keeper.serverLevel();
        BlockPos station = Stations.near(level, keeper.blockPosition(), AT_STATION);
        if (station == null) {
            keeper.displayClientMessage(net.minecraft.network.chat.Component.translatable("rescue.fts_lostsignal.no_station"), true);
            return;
        }
        if (!Stations.powered(level, station)) {
            keeper.displayClientMessage(net.minecraft.network.chat.Component.translatable("rescue.fts_lostsignal.no_power"), true);
            return;
        }
        Hiker h = inContact(level, keeper.position());
        if (h == null) {
            // No one lost out there to hear it, but someone may be listening who is not lost.
            if (com.jeladastudios.lostsignal.visit.Mimic.answered(level, keeper)) return;
            if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("RESCUE {} unanswered", order);
            return;
        }
        // Told what to do about the man who offered to walk them down: that is their answer.
        if (com.jeladastudios.lostsignal.mission.Missions.offerAnswered(level, h, order)) {
            com.jeladastudios.lostsignal.mission.Missions.heard(level, h);
            return;
        }
        long at = level.getGameTime() + 30 + level.random.nextInt(30);
        // Far off, now and then, what was said does not get through: they ask for it again, and do nothing yet.
        if (h.distanceTo(keeper) > BREAKS_UP && level.random.nextFloat() < BREAKING) {
            PENDING.add(new Pending(level, h, "H_SAY_AGAIN", at));
            com.jeladastudios.lostsignal.mission.Missions.heard(level, h);
            return;
        }
        // Answered for the first time: relief before anything else.
        if (com.jeladastudios.lostsignal.mission.Missions.firstContact(level, h)) {
            String reply = level.random.nextBoolean() ? "H_REPLY_01" : "H_REPLY_02";
            PENDING.add(new Pending(level, h, reply, at));
            at += com.jeladastudios.lostsignal.tower.Speech.length(reply);
        }
        String line = answer(level, h, order, value);
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) {
            LostSignal.LOGGER.info("RESCUE {} {} -> {} ({})", order, value, line, h.state());
        }
        PENDING.add(new Pending(level, h, line, at));
        com.jeladastudios.lostsignal.mission.Missions.heard(level, h);
    }

    /** Beyond how far, in blocks, a keeper's words may break up on a hiker's handheld, and how often then. */
    static final double BREAKS_UP = 200;
    static final float BREAKING = 0.08f;

    /** What the hiker does with an order, and the line they answer with. */
    public static String answer(ServerLevel level, Hiker h, Order order, int value) {
        BlockPos at = h.blockPosition();
        // A broken ankle: told to go anywhere, they cannot.
        boolean goes = order == Order.BEARING || order == Order.FOLLOW_WATER || order == Order.FOLLOW_TRAIL || order == Order.FOLLOW_SIGN
                || order == Order.TOWARD_LIGHT;
        if (goes && h.hurt() >= 2) return "H_HURT_02";
        return switch (order) {
            case WHERE -> Sight.describe(level, h);
            case HOW -> h.hurt() >= 2 ? "H_HURT_02" : h.hurt() == 1 ? "H_HURT_01"
                    : !level.isDay() && level.isRaining() && h.state() != Hiker.State.CAMPING ? (level.random.nextBoolean() ? "H_COLD" : "H_FREEZING")
                    : h.fatigue() > 0.7f ? "H_TIRED" : !level.isDay() ? "H_DARK" : "H_HOW_OK";
            case COMPASS -> h.hasCompass() ? "H_COMPASS_YES" : "H_COMPASS_NO";
            case BEARING -> {
                if (!h.hasCompass()) yield "H_WHICH_WAY";
                // A bearing as a compass reads it: 0 north, 90 east.
                double a = Math.toRadians(value);
                h.follow(Hiker.Guide.NONE, ground(level, at.getX() + Math.sin(a) * LEG * 1.5, at.getZ() - Math.cos(a) * LEG * 1.5));
                yield ok(level);
            }
            case FOLLOW_WATER -> {
                BlockPos next = nextLeg(level, h, Hiker.Guide.WATER);
                if (next == null) yield "H_NO_WATER";
                h.follow(Hiker.Guide.WATER, next);
                yield "H_FOLLOW_CREEK";
            }
            case FOLLOW_TRAIL -> {
                BlockPos next = nextLeg(level, h, Hiker.Guide.TRAIL);
                if (next == null) yield "H_NO_TRAIL";
                h.follow(Hiker.Guide.TRAIL, next);
                yield "H_FOLLOW_TRAIL";
            }
            case FOLLOW_SIGN -> {
                BlockPos tower = h.tower();
                if (Sight.sign(level, at, 12) == null || tower == null) yield "H_NO_SIGN";
                // The board says which way and how far the lookout is: they go that way.
                h.follow(Hiker.Guide.TOWER, toward(level, at, tower, LEG));
                yield "H_FOLLOW_SIGN";
            }
            case TOWARD_LIGHT -> {
                BlockPos tower = h.tower();
                // Only the lamp is seen at night, and only lit.
                if (tower == null || level.isDay() || !com.jeladastudios.lostsignal.finder.Lamps.lit(level, tower)
                        || !Sight.seesTower(level, h, tower)) yield "H_NO_LIGHT";
                h.follow(Hiker.Guide.TOWER, toward(level, at, tower, LEG));
                yield ok(level);
            }
            case STAY -> {
                h.stay();
                yield "H_STAY";
            }
            case FLARE -> h.fireFlare() ? "H_FLARE" : "H_FLARE_NONE";
            case SMOKE -> {
                h.smoke();
                yield "H_FIRE";
            }
            case LAMP -> {
                // A lamp is not seen by day from a lookout miles off: they say so, and offer smoke.
                if (level.isDay() && !level.isThundering()) yield "H_LAMP_DAY";
                h.waveLamp();
                yield "H_LAMP";
            }
        };
    }

    /** Their word that they are going: one way of saying it or another. */
    static String ok(ServerLevel level) {
        return level.random.nextBoolean() ? "H_OK_01" : "H_OK_02";
    }

    /**
     * The next leg of what a hiker follows: down the water (the way FT's Geology's river runs where there is one,
     * else downhill to where water gathers) or up the trail, the trail's next stretch nearer the lookout. Null where
     * there is nothing to follow from here.
     */
    public static BlockPos nextLeg(ServerLevel level, Hiker h, Hiker.Guide guide) {
        BlockPos at = h.blockPosition();
        if (guide == Hiker.Guide.WATER) {
            var river = Sight.river(at, 20);
            if (river.isPresent() && !river.get().lake() && (river.get().flowX() != 0 || river.get().flowZ() != 0)) {
                var r = river.get();
                // Along the bank, downstream, a little to the side of the water.
                return ground(level, at.getX() + r.flowX() * LEG, at.getZ() + r.flowZ() * LEG);
            }
            if (!Sight.water(level, at, 12)) return null;
            Vec3 down = Sight.downhill(level, at);
            return down == null ? null : ground(level, at.getX() + down.x * LEG, at.getZ() + down.z * LEG);
        }
        if (guide == Hiker.Guide.TOWER) {
            // On towards the lookout, a leg at a time, as long as it is not reached.
            BlockPos tower = h.tower();
            if (tower == null || at.distSqr(tower) < 14 * 14) return null;
            return toward(level, at, tower, LEG);
        }
        if (guide == Hiker.Guide.TRAIL) {
            BlockPos tower = h.tower();
            BlockPos best = null;
            double bestScore = Double.MAX_VALUE;
            // Of the trail's worn ground in reach, the stretch nearest the lookout (or, with none known, the highest).
            for (int r = 6; r <= 24; r += 6) {
                for (int i = 0; i < 16; i++) {
                    double a = Math.PI * 2 * i / 16;
                    BlockPos p = Sight.trail(level, new BlockPos(at.getX() + (int) (Math.cos(a) * r), at.getY(), at.getZ() + (int) (Math.sin(a) * r)), 2);
                    if (p == null) continue;
                    double score = tower != null ? p.distSqr(tower) : -p.getY();
                    if (score < bestScore) {
                        bestScore = score;
                        best = p;
                    }
                }
            }
            if (best == null) return null;
            if (tower != null && best.distSqr(tower) >= at.distSqr(tower)) return null;
            return best;
        }
        return null;
    }

    /** A spot on the ground some way from a place towards another. */
    static BlockPos toward(ServerLevel level, BlockPos from, BlockPos to, int far) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ(), d = Math.sqrt(dx * dx + dz * dz);
        // Near enough: the lookout's foot itself, on the ground, not its roof.
        if (d <= far) return to;
        return ground(level, from.getX() + dx / d * far, from.getZ() + dz / d * far);
    }

    public static BlockPos ground(ServerLevel level, double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        // The ground there as it is, if loaded; else as the ground model has it, not loaded, and not made, for this.
        if (level.isLoaded(new BlockPos(bx, 0, bz))) return new BlockPos(bx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz), bz);
        Integer y = com.jeladastudios.lostsignal.tower.Fires.ground(level, bx, bz);
        return new BlockPos(bx, y == null ? level.getSeaLevel() : y, bz);
    }

    /** Whether a radio station of the ham fork stands within reach of a place. */
    public static boolean atStation(ServerLevel level, BlockPos at) {
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-AT_STATION, -3, -AT_STATION), at.offset(AT_STATION, 3, AT_STATION))) {
            var id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(level.getBlockState(p).getBlock());
            if (id != null && id.getNamespace().equals("simpleradio") && id.getPath().equals("station")) return true;
        }
        return false;
    }

    /** The lost hiker on the air nearest a place, if any is within a handheld's reach. */
    static Hiker inContact(ServerLevel level, Vec3 from) {
        // The hiker of the night at the lookout the keeper is at, if there is one: not another lookout's.
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(from) > 1) continue;
            BlockPos tower = com.jeladastudios.lostsignal.mission.Missions.towerAt(level, p);
            var mission = tower == null ? null : com.jeladastudios.lostsignal.mission.Missions.at(level, tower);
            if (mission != null && mission.hiker != null) {
                return level.getEntity(mission.hiker) instanceof Hiker h && h.state() != Hiker.State.RESCUED
                        && h.position().distanceToSqr(from) < IN_CONTACT * IN_CONTACT ? h : null;
            }
        }
        // Else the nearest hiker who is no lookout's (one put out by hand).
        Hiker best = null;
        double nearest = IN_CONTACT * IN_CONTACT;
        for (Hiker h : level.getEntities(ModEntities.HIKER.get(), h -> h.state() != Hiker.State.RESCUED
                && !com.jeladastudios.lostsignal.mission.Missions.onNight(level, h.getUUID()))) {
            double d = h.position().distanceToSqr(from);
            if (d < nearest) {
                nearest = d;
                best = h;
            }
        }
        return best;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Pending p : PENDING) {
            if (p.level().getGameTime() < p.at()) continue;
            PENDING.remove(p);
            if (!p.hiker().isAlive()) continue;
            Lines.say(p.level(), p.hiker().getEyePosition(), Lines.Speaker.HIKER, p.line(), HANDHELD);
        }
    }

    /** A server stopped: nothing of its kept for the next world. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void forget(net.minecraftforge.event.server.ServerStoppedEvent event) {
        PENDING.clear();
    }
}
