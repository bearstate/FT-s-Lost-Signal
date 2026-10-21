package com.jeladastudios.lostsignal.tower;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.finder.FireFinder;
import com.jeladastudios.lostsignal.mission.Lookouts;
import com.jeladastudios.lostsignal.mission.Missions;
import com.jeladastudios.lostsignal.rescue.Lines;
import com.jeladastudios.lostsignal.signal.Signals;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fires in the forest: what the lookouts are for. A lightning strike in a storm (now and then on a dry afternoon
 * too) and a column of smoke stands up somewhere a few hundred blocks off. The keeper who sees it takes its bearing
 * with the fire finder and calls it in; another lookout's keeper crosses it with their own, the District has a fix,
 * and a crew goes out. Called in, it is out within a few minutes; not, it burns itself out in a while.
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class Fires {

    private Fires() {}

    /** The chance, every half minute in a thunderstorm, of a strike setting a fire within sight of a kept lookout; and on a dry afternoon. */
    static final float STORM = 0.12f, DRY = 0.004f;
    /**
     * How far off a fire starts, in blocks (within the furthest the game sends a particle, with room for the column);
     * how long it burns uncalled, and once a crew is on it, in seconds.
     */
    static final int NEAR = 200, FAR = 450, BURNS = 15 * 60, CREW = 3 * 60;

    /** A fire: where, when it started, whether it has been called in. */
    static final class Fire {
        final Vec3 at;
        final Signals.Burning smoke;
        boolean called;

        Fire(Vec3 at, Signals.Burning smoke) {
            this.at = at;
            this.smoke = smoke;
        }
    }

    private static final List<Fire> FIRES = new CopyOnWriteArrayList<>();
    private static boolean starting;

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        for (Fire f : FIRES) if (!f.smoke.burning()) FIRES.remove(f);
        if (level.getGameTime() % 600 != 77 || starting || !com.jeladastudios.lostsignal.Config.on(com.jeladastudios.lostsignal.Config.FIRES)) return;
        long time = level.getDayTime() % 24000;
        float chance = level.isThundering() ? STORM : !level.isRaining() && time > 6000 && time < 11000 ? DRY : 0;
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) chance *= 2;
        chance *= com.jeladastudios.lostsignal.Config.scale(com.jeladastudios.lostsignal.Config.FIRE_CHANCE);
        if (chance == 0) return;
        for (ServerPlayer p : level.players()) {
            BlockPos tower = Missions.towerAt(level, p);
            if (tower == null || level.random.nextFloat() >= chance) continue;
            boolean near = false;
            for (Fire f : FIRES) if (f.at.distanceTo(Vec3.atCenterOf(tower)) < FAR * 2) near = true;
            if (!near) start(level, tower);
            return;
        }
    }

    /** A fire started somewhere a few hundred blocks from a lookout: its ground worked out off the server's thread. */
    public static void start(ServerLevel level, BlockPos tower) {
        long seed = level.random.nextLong();
        starting = true;
        Lookouts.offThread(level, () -> spots(level, tower, new java.util.Random(seed)), found -> {
            starting = false;
            if (found == null || found.isEmpty()) return;
            // One the lookout can see the smoke of: what a lookout is there for. None, and no fire this time.
            Vec3 cab = Vec3.atCenterOf(tower.above(22));
            Vec3 at = null;
            for (Vec3 s : found) {
                if (com.jeladastudios.lostsignal.signal.Sightline.clear(level, cab, s.add(0, 20, 0))) {
                    at = s;
                    break;
                }
            }
            if (at == null) return;
            FIRES.add(new Fire(at, Signals.fire(level, at, BURNS)));
            if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("FIRE at {}", BlockPos.containing(at).toShortString());
        });
    }

    /**
     * Where a fire may start: a few hundred blocks from the lookout, on ground well over the sea and any lake: spots
     * in forest or meadow, not in water. Empty where none is found.
     */
    static List<Vec3> spots(ServerLevel level, BlockPos tower, java.util.Random random) {
        List<Vec3> found = new java.util.ArrayList<>();
        int sea = level.getSeaLevel();
        for (int tries = 0; tries < 24; tries++) {
            double a = random.nextDouble() * Math.PI * 2, d = NEAR + random.nextDouble() * (FAR - NEAR);
            int x = (int) Math.floor(tower.getX() + Math.cos(a) * d), z = (int) Math.floor(tower.getZ() + Math.sin(a) * d);
            Integer y = ground(level, x, z);
            if (y == null || y < sea + 4) continue;
            found.add(new Vec3(x + 0.5, y, z + 0.5));
        }
        return found;
    }

    /** The ground's height at a column, as the world is made: FT's Geology's model where it gives it, else the generator's. */
    public static Integer ground(ServerLevel level, int x, int z) {
        try {
            double h = com.jeladastudios.feelthenature.GeologyBridge.groundHeight(level, x, z);
            if (!Double.isNaN(h)) return (int) Math.round(h);
        } catch (LinkageError e) {
            // Not there: the generator's own.
        }
        var source = level.getChunkSource();
        return source.getGenerator().getBaseHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level, source.randomState());
    }

    /**
     * A fire called in from the panel, by its bearing off the fire finder: crossed with another keeper's bearing where
     * one is posted near, a crew sent, and it is out in a few minutes. Told to read it first, where there is no reading.
     */
    public static void report(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        FireFinder.Reading r = FireFinder.last(p);
        Fire fire = null;
        if (r != null && r.kind().equals("fire")) {
            for (Fire f : FIRES) if (f.at.distanceTo(r.at()) < 20 && !f.called) fire = f;
        }
        if (fire == null) {
            p.displayClientMessage(Component.translatable("fire.fts_lostsignal.read_first"), true);
            return;
        }
        fire.called = true;
        Fire called = fire;
        Vec3 from = p.position();
        BlockPos foot = Missions.towerAt(level, p);
        Tower home = foot == null ? null : Towers.at(level, foot);
        // Another keeper who can see it, to cross the bearing with.
        Tower crossing = null;
        if (home != null) {
            for (Tower t : Towers.around(level, home)) {
                if (t.state == Tower.State.KEPT && t.cab().distanceTo(called.at) < 3000) {
                    crossing = t;
                    break;
                }
            }
        }
        p.displayClientMessage(Component.translatable("call.fts_lostsignal.calling_district"), true);
        int at = 40;
        Speech.after(level, at, () -> Lines.say(level, from, Lines.Speaker.DISTRICT, "D_ANSWER", 1f));
        at += Speech.length("D_ANSWER");
        if (crossing != null) {
            Tower t = crossing;
            Speech.after(level, at, () -> {
                Lines.sayAs(level, t.cab(), Calls.lookout(t, t.keeper), "K_SMOKE", 1f, t.pitch, Calls.REACH);
                Vec3 to = called.at.subtract(t.cab());
                int bearing = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(to.x, -to.z))), 360);
                p.sendSystemMessage(Component.translatable("fire.fts_lostsignal.crossed", t.number, bearing).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                t.trust++;
                Towers.of(level).setDirty();
            });
            at += Speech.length("K_SMOKE");
        }
        String done = crossing != null ? "D_SMOKE_CROSS" : "D_SMOKE_01";
        Speech.after(level, at, () -> Lines.say(level, from, Lines.Speaker.DISTRICT, done, 1f));
        // The crew on it: out in a few minutes. And the District remembers good eyes: supplies come up, once it is said.
        Speech.after(level, CREW * 20, called.smoke::out);
        if (home != null && com.jeladastudios.lostsignal.Config.on(com.jeladastudios.lostsignal.Config.SUPPLIES)) {
            Tower h = home;
            Speech.after(level, at + Speech.length(done) + 60, () -> {
                h.supplyPending = true;
                Towers.of(level).setDirty();
            });
        }
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("FIRE called in, crossed by {}", crossing == null ? "no one" : crossing.number);
    }

    @SubscribeEvent
    public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        FIRES.clear();
        starting = false;
    }
}
