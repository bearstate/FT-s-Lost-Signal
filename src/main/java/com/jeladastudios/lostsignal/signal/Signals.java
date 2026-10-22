package com.jeladastudios.lostsignal.signal;

import com.jeladastudios.lostsignal.LostSignal;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What a lost hiker shows to be found by, seen from a lookout far further off than the hiker is: a red flare going up
 * and coming down slowly under its little parachute, a column of smoke from a fire built to smoke, a lamp waved in the
 * dark. Each is drawn on the players' screens from the server, as points of light and smoke sent up to 512 blocks out,
 * so it is seen whether or not the hiker (or the ground under them) is loaded where the player is.
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class Signals {

    private Signals() {}

    /** How far off a signal is sent: the furthest the game sends a particle. */
    static final double REACH = 512;

    private abstract static class Signal {
        final ServerLevel level;
        int age;

        Signal(ServerLevel level) {
            this.level = level;
        }

        /** False when it is over. */
        abstract boolean tick();

        /** Where it is to be seen now. */
        abstract Vec3 at();

        /** What it is: "flare", "smoke", "lamp". */
        abstract String kind();
    }

    /** A signal on a line of sight: what it is and where. */
    public record Seen(String kind, Vec3 at) {}

    /**
     * The signal nearest a line of sight from an eye, within some degrees of it and in plain view, as a fire finder's
     * sight finds it; or null.
     */
    public static Seen onLine(ServerLevel level, Vec3 eye, Vec3 look, double degrees, net.minecraft.world.entity.Entity looker) {
        Seen best = null;
        double bestCos = Math.cos(Math.toRadians(degrees));
        for (Signal s : ACTIVE) {
            if (s.level != level) continue;
            Vec3 at = s.at();
            Vec3 to = at.subtract(eye);
            double far = to.length();
            if (far < 8 || far > REACH) continue;
            double cos = to.normalize().dot(look.normalize());
            if (cos < bestCos) continue;
            // The line to it clear, from a little way out (the looker's own walls and windows are not in the way); for a
            // fire's smoke, to anywhere up its column.
            Vec3 from = eye.add(to.normalize().scale(6));
            boolean visible = Sightline.clear(level, from, at);
            if (!visible && s.kind().equals("fire")) visible = Sightline.clear(level, from, at.add(0, 12, 0)) || Sightline.clear(level, from, at.add(0, 26, 0));
            if (!visible) continue;
            bestCos = cos;
            best = new Seen(s.kind(), at);
        }
        return best;
    }

    private static final List<Signal> ACTIVE = new CopyOnWriteArrayList<>();

    /**
     * A flare fired straight up from a place: it climbs some forty blocks in two seconds, bursts, and comes down under its
     * parachute over twenty-five seconds, burning red, drifting with the wind.
     */
    public static void flare(ServerLevel level, Vec3 from) {
        ACTIVE.add(new Signal(level) {
            Vec3 at = from.add(0, 1.5, 0);
            final double drift = (level.random.nextDouble() - 0.5) * 0.04;

            @Override
            boolean tick() {
                age++;
                if (age <= 40) {
                    // The climb: fast, slowing towards the top; a thin trail.
                    at = at.add(0, 2.0 * (1.0 - age / 45.0), 0);
                    send(level, ModParticles.FLARE.get(), at, 1, 0.02, 0);
                } else {
                    at = at.add(drift, -0.06, drift * 0.5);
                    send(level, ModParticles.FLARE.get(), at, 2, 0.08, 0);
                }
                return age < 40 + 500;
            }

            @Override
            Vec3 at() {
                return at;
            }

            @Override
            String kind() {
                return "flare";
            }
        });
    }

    /** A column of smoke from a fire, for some seconds: the smoke a signal fire sends up, seen across a valley. */
    public static void smoke(ServerLevel level, Vec3 fire, int seconds) {
        ACTIVE.add(new Signal(level) {
            @Override
            boolean tick() {
                age++;
                if (age % 4 == 0) {
                    for (ServerPlayer p : level.players()) {
                        double d = p.position().distanceTo(fire);
                        // Near the fire its own smoke is seen; this is for the far side of the valley.
                        if (d < 48 || d > REACH) continue;
                        // Further off than the game draws its own smoke clearly, the column drawn large and through the haze.
                        boolean far = d > 64;
                        level.sendParticles(p, far ? ModParticles.SMOKE.get() : ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, fire.x + level.random.nextGaussian() * 0.3,
                                fire.y + 0.5, fire.z + level.random.nextGaussian() * 0.3, 0, 0, far ? 0.2 : 0.07, 0, 1);
                    }
                }
                return age < seconds * 20;
            }

            @Override
            Vec3 at() {
                // The column, a little way up it, where it stands clear of the trees.
                return fire.add(0, 10, 0);
            }

            @Override
            String kind() {
                return "smoke";
            }
        });
    }

    /** The wind at a place, in blocks a second the way it blows: FT's Geology's, else a light westerly. */
    static Vec3 wind(ServerLevel level, Vec3 at) {
        try {
            Vec3 w = com.jeladastudios.ftsgeology.api.FtsGeologyApi.windAt(level, at.x, at.z);
            if (w != null && w.lengthSqr() > 0) return new Vec3(w.x, 0, w.z);
        } catch (LinkageError e) {
            // No such thing in this version of it: the westerly.
        }
        return new Vec3(1.5, 0, 0.3);
    }

    /** A fire's smoke, under way: put out by the crew, or burnt out. */
    public interface Burning {
        void out();

        boolean burning();
    }

    /**
     * A fire in the forest, for some seconds: smoke from a few places across it, dark off the burning and greying as it
     * rises, leaning and drifting downwind (FT's Geology's wind where there is one), seen from any lookout in reach; by
     * night an orange glow under it; and close to, flames in among the trees that burn nothing.
     */
    public static Burning fire(ServerLevel level, Vec3 at, int seconds) {
        // Where it burns: a few seats across a stretch of forest, one bigger than the rest.
        List<Vec3> seats = new java.util.ArrayList<>();
        seats.add(at);
        for (int i = 0, n = 1 + level.random.nextInt(3); i < n; i++) {
            seats.add(at.add(level.random.nextGaussian() * 9, level.random.nextGaussian() * 1.5, level.random.nextGaussian() * 9));
        }
        Signal s = new Signal(level) {
            boolean out;
            Vec3 drift = Vec3.ZERO;

            @Override
            boolean tick() {
                age++;
                if (out) return false;
                // The wind, looked at now and then: a breeze leans the smoke; a gale lays it along the ground.
                if (age % 100 == 1) drift = wind(level, at).scale(0.03);
                if (age % 3 == 0) {
                    boolean night = level.getDayTime() % 24000 > 13000 && level.getDayTime() % 24000 < 23000;
                    for (ServerPlayer p : level.players()) {
                        double d = p.position().distanceTo(at);
                        if (d > REACH) continue;
                        if (d < 64) {
                            near(p, night);
                            continue;
                        }
                        for (int i = 0; i < seats.size(); i++) {
                            Vec3 seat = seats.get(i);
                            int puffs = i == 0 ? 3 : 2;
                            for (int k = 0; k < puffs; k++) {
                                level.sendParticles(p, ModParticles.FIRE_SMOKE.get(), true, seat.x + level.random.nextGaussian() * 3,
                                        seat.y + 1 + level.random.nextDouble() * 3, seat.z + level.random.nextGaussian() * 3, 0,
                                        drift.x, 0.22 + level.random.nextDouble() * 0.06, drift.z, 1);
                            }
                            if (night && age % 6 == 0) {
                                level.sendParticles(p, ModParticles.FIRE_GLOW.get(), true, seat.x + level.random.nextGaussian() * 3, seat.y + 1.5,
                                        seat.z + level.random.nextGaussian() * 3, 1, 0, 0, 0, 0);
                            }
                        }
                    }
                }
                return age < seconds * 20;
            }

            /** Close to: flames low in the trees and on the ground, sparks, and the smoke as the game draws it. */
            void near(ServerPlayer p, boolean night) {
                for (Vec3 seat : seats) {
                    for (int k = 0; k < 3; k++) {
                        double x = seat.x + level.random.nextGaussian() * 4, z = seat.z + level.random.nextGaussian() * 4;
                        double y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z)) + 0.1;
                        level.sendParticles(p, ParticleTypes.FLAME, true, x, y + level.random.nextDouble() * 1.5, z, 3, 0.3, 0.4, 0.3, 0.01);
                        level.sendParticles(p, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, x, y + 1, z, 0, drift.x * 0.4, 0.08, drift.z * 0.4, 1);
                        if (level.random.nextInt(5) == 0) level.sendParticles(p, ParticleTypes.LAVA, true, x, y + 0.5, z, 1, 0, 0, 0, 0);
                    }
                }
            }

            @Override
            Vec3 at() {
                return at.add(0, 14, 0);
            }

            @Override
            String kind() {
                return "fire";
            }
        };
        ACTIVE.add(s);
        return new Burning() {
            @Override
            public void out() {
                ACTIVE.remove(s);
            }

            @Override
            public boolean burning() {
                return ACTIVE.contains(s);
            }
        };
    }

    /**
     * A lamp waved by someone, for some seconds: a warm light swung side to side over their head, about once a second,
     * highest in the middle of the swing. Drawn three points a tick along the arc, so from far off it is a short bright
     * streak going to and fro rather than a single point.
     */
    public static void lamp(ServerLevel level, Entity holder, int seconds) {
        ACTIVE.add(new Signal(level) {
            @Override
            boolean tick() {
                age++;
                if (!holder.isAlive() || holder.level() != level) return false;
                double yaw = Math.toRadians(holder.getYRot());
                for (int k = 0; k < 3; k++) {
                    double phase = (age + k / 3.0) * 0.3, side = Math.sin(phase) * 0.9;
                    // Across their front: to their left and right as they face.
                    Vec3 at = holder.position().add(Math.cos(yaw) * side, 2.0 + (1 - Math.abs(Math.sin(phase))) * 0.35, Math.sin(yaw) * side);
                    send(level, ModParticles.LAMP.get(), at, 1, 0, 0);
                }
                return age < seconds * 20;
            }

            @Override
            Vec3 at() {
                return holder.position().add(0, 1.6, 0);
            }

            @Override
            String kind() {
                return "lamp";
            }
        });
    }

    static void send(ServerLevel level, ParticleOptions particle, Vec3 at, int count, double spread, double speed) {
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(at) > REACH * REACH) continue;
            level.sendParticles(p, particle, true, at.x, at.y, at.z, count, spread, spread, spread, speed);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Signal s : ACTIVE) if (!s.tick()) ACTIVE.remove(s);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }
}
