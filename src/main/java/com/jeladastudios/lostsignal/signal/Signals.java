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

    /** A fire's smoke, under way: put out by the crew, or burnt out. */
    public interface Burning {
        void out();

        boolean burning();
    }

    /**
     * A fire in the forest, for some seconds: a broad column of smoke from a few points across it, thick and rising
     * high, seen from any lookout in reach; and by night a dull glow at its foot.
     */
    public static Burning fire(ServerLevel level, Vec3 at, int seconds) {
        Signal s = new Signal(level) {
            boolean out;

            @Override
            boolean tick() {
                age++;
                if (out) return false;
                if (age % 3 == 0) {
                    boolean night = level.getDayTime() % 24000 > 13000 && level.getDayTime() % 24000 < 23000;
                    for (ServerPlayer p : level.players()) {
                        double d = p.position().distanceTo(at);
                        if (d < 48 || d > REACH) continue;
                        boolean far = d > 64;
                        for (int i = 0; i < (far ? 2 : 3); i++) {
                            level.sendParticles(p, far ? ModParticles.SMOKE.get() : ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, at.x + level.random.nextGaussian() * 4,
                                    at.y + 1 + level.random.nextDouble() * 3, at.z + level.random.nextGaussian() * 4, 0, 0, far ? 0.25 : 0.09, 0, 1);
                        }
                        if (night && age % 12 == 0) {
                            level.sendParticles(p, ModParticles.FLARE.get(), true, at.x + level.random.nextGaussian() * 3, at.y + 1,
                                    at.z + level.random.nextGaussian() * 3, 1, 0, 0, 0, 0);
                        }
                    }
                }
                return age < seconds * 20;
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

    /** A lamp waved by someone, for some seconds: a small warm light swinging at their shoulder. */
    public static void lamp(ServerLevel level, Entity holder, int seconds) {
        ACTIVE.add(new Signal(level) {
            @Override
            boolean tick() {
                age++;
                if (!holder.isAlive() || holder.level() != level) return false;
                double swing = Math.sin(age * 0.35) * 0.6;
                double yaw = Math.toRadians(holder.getYRot() + 90);
                Vec3 at = holder.position().add(Math.cos(yaw) * 0.4 + Math.sin(yaw) * swing, 1.6 + Math.abs(swing) * 0.4,
                        Math.sin(yaw) * 0.4 - Math.cos(yaw) * swing);
                send(level, ModParticles.LAMP.get(), at, 1, 0, 0);
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
