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
 * so it is seen whether or not the hiker -- or the ground under them -- is loaded where the player is.
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
                        level.sendParticles(p, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, fire.x + level.random.nextGaussian() * 0.3,
                                fire.y + 0.5, fire.z + level.random.nextGaussian() * 0.3, 0, 0, 0.07, 0, 1);
                    }
                }
                return age < seconds * 20;
            }
        });
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
