package com.jeladastudios.lostsignal.entity;

import com.jeladastudios.lostsignal.LostSignal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * The watcher, as it is first met: a figure at the edge of the trees, forty to seventy blocks off, standing beside a
 * trunk and looking at the player. It does nothing but look. Looked at for long, walked towards, or struck at, it is not
 * there any more; left alone, it stays a while and then is gone too. It is never saved with the world: each sighting is
 * its own.
 *
 * <p>Put in the world by the {@link com.jeladastudios.lostsignal.director.Director}, for one player, whom it watches.</p>
 */
public class Watcher extends PathfinderMob {

    /** How long a steady look it bears, in ticks, before it is gone. */
    static final int BEARS_LOOK = 40;
    /** How near the watched player may come, in blocks. */
    static final double KEEPS_OFF = 24;
    /** How narrow a look counts as looking at it: within this angle of the line to its head, in degrees. */
    static final double LOOK_ANGLE = 9;

    private UUID watching;
    private int looked;
    private int lifetime = 20 * 120;

    public Watcher(EntityType<? extends Watcher> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 96.0);
    }

    /** Whom it watches, and for how long it stays if nothing sends it off. */
    public void watch(ServerPlayer player, int ticks) {
        watching = player.getUUID();
        lifetime = ticks;
    }

    public UUID watching() {
        return watching;
    }

    @Override
    protected void registerGoals() {
        // Nothing of its own to do: it stands and looks, which tick() sees to.
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        ServerPlayer player = watching == null ? null : (ServerPlayer) server.getPlayerByUUID(watching);
        if (player == null || player.level() != server || tickCount > lifetime) {
            vanish("left");
            return;
        }
        // Its eyes on the player, its body turned to them.
        getLookControl().setLookAt(player, 30f, 30f);
        Vec3 to = player.position().subtract(position());
        float yaw = (float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90.0);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        double distance = Math.sqrt(distanceToSqr(player));
        if (distance < KEEPS_OFF) {
            vanish("approached");
            return;
        }
        if (lookedAt(player)) {
            if (++looked > BEARS_LOOK) vanish("seen");
        } else if (looked > 0) {
            looked--;
        }
    }

    /** Whether the player looks straight at it, with nothing between. */
    boolean lookedAt(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 head = new Vec3(getX(), getEyeY(), getZ());
        Vec3 line = head.subtract(eye).normalize();
        double cos = player.getViewVector(1f).dot(line);
        return cos > Math.cos(Math.toRadians(LOOK_ANGLE)) && player.hasLineOfSight(this);
    }

    /** Gone, without a sound or a trace. */
    void vanish(String why) {
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) {
            LostSignal.LOGGER.info("WATCHER gone ({}) after {} ticks at {} {} {}", why, tickCount, getBlockX(), getBlockY(), getBlockZ());
        }
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide()) vanish("struck");
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        // Seen as far as it is ever put: the edge of a wood across a valley.
        return d < 160.0 * 160.0;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return null;
    }
}
