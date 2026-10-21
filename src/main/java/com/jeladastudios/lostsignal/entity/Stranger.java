package com.jeladastudios.lostsignal.entity;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.radio.Voice;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * The stranger. A man, and only a man: an ordinary walker by day, in a flannel shirt and no pack, who stands too still
 * and smiles too long, who says things about the forest that sound like wisdom until they don't; by night the same
 * man in a black horned mask and a robe, at the edge of the trees, on the stair, at the door.
 *
 * <p>He is put in the world for one player, whom he watches or talks to, and he is never saved with it: each meeting
 * is its own. He does nothing a man could not: looked at too long or come at, he turns and walks off into the trees,
 * and is gone once no one can see him. He is not hurt by being struck -- he walks away from it.</p>
 */
public class Stranger extends PathfinderMob {

    private static final EntityDataAccessor<Boolean> MASKED = SynchedEntityData.defineId(Stranger.class, EntityDataSerializers.BOOLEAN);

    /** What he is out for: to watch from off, to come and talk, or at a lookout in the night. */
    public enum Mode { WATCH, TALK, VISIT }

    /** How long a steady look he bears, in ticks, before he goes. */
    static final int BEARS_LOOK = 40;
    /** How near the watched player may come, in blocks, while he only watches. */
    static final double KEEPS_OFF = 24;
    /** How narrow a look counts as looking at him: within this angle of the line to his head, in degrees. */
    static final double LOOK_ANGLE = 9;
    /** How far his voice carries. */
    static final double VOICE = 20;

    private UUID watching;
    private Mode mode = Mode.WATCH;
    private int looked;
    private int lifetime = 20 * 120;
    private double keepsOff = KEEPS_OFF;
    private int bearsLook = BEARS_LOOK;
    private boolean leaving;
    private int unseen;
    private int step, nextLine;
    private final java.util.Set<String> said = new java.util.HashSet<>();

    public Stranger(EntityType<? extends Stranger> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 96.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(MASKED, false);
    }

    public boolean masked() {
        return entityData.get(MASKED);
    }

    public void masked(boolean masked) {
        entityData.set(MASKED, masked);
    }

    /** Whom he watches, and for how long he stays if nothing sends him off. */
    public void watch(ServerPlayer player, int ticks) {
        watching = player.getUUID();
        lifetime = ticks;
    }

    /**
     * Close, as at a window or a broken door: he keeps his place however near they are, and bears only a short look.
     */
    public void close(int bears) {
        mode = Mode.VISIT;
        keepsOff = 0;
        bearsLook = bears;
    }

    /** Come up to a player and talk, a few things said, and go. */
    public void talk(ServerPlayer player) {
        watching = player.getUUID();
        mode = Mode.TALK;
        lifetime = 20 * 150;
        step = 0;
        nextLine = 0;
    }

    public UUID watching() {
        return watching;
    }

    @Override
    protected void registerGoals() {
        // Nothing of his own to do: what he does, tick() sees to.
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        ServerPlayer player = watching == null ? null : (ServerPlayer) server.getPlayerByUUID(watching);
        if (player == null || player.level() != server) {
            gone("no one");
            return;
        }
        if (leaving) {
            walkingOff(server, player);
            return;
        }
        if (tickCount > lifetime) {
            leave(player);
            return;
        }
        double distance = Math.sqrt(distanceToSqr(player));
        switch (mode) {
            case WATCH, VISIT -> {
                face(player);
                if (distance < keepsOff) {
                    leave(player);
                    return;
                }
                if (lookedAt(player)) {
                    if (++looked > bearsLook) {
                        // At a window or the door he steps back out of sight; out in the open he walks off.
                        if (mode == Mode.VISIT) gone("stepped back");
                        else leave(player);
                    }
                } else if (looked > 0) {
                    looked--;
                }
            }
            case TALK -> {
                if (step > 0 && distance > 24) {
                    leave(player);
                    return;
                }
                if (distance > 3.5) {
                    if (!getNavigation().isInProgress() || tickCount % 20 == 0) getNavigation().moveTo(player, 0.8);
                } else {
                    getNavigation().stop();
                    face(player);
                }
                if (distance < 6 && --nextLine <= 0) speak(player);
            }
        }
    }

    /** The next thing he says, and when the one after. */
    void speak(ServerPlayer player) {
        String line;
        // "Afternoon" only of an afternoon.
        long day = level().getDayTime() % 24000;
        if (step == 0) line = day >= 6000 && day < 12000 && random.nextBoolean() ? "S_HELLO_01" : "S_HELLO_02";
        else if (step == 1) line = pick("S_ASK_01", "S_ASK_02", "S_ASK_03", "S_KNOWS_01");
        else if (step == 2 || step == 3 && random.nextBoolean()) line = saying();
        else line = null;
        if (line == null) {
            say(pick("S_BYE_01", "S_BYE_02"));
            leave(player);
            return;
        }
        say(line);
        step++;
        nextLine = 120 + random.nextInt(60);
    }

    /** One of his sayings he has not said yet in this meeting. */
    String saying() {
        for (int tries = 0; tries < 20; tries++) {
            String s = String.format("S_SAY_%02d", 1 + random.nextInt(16));
            if (said.add(s)) return s;
        }
        return "S_SAY_07";
    }

    String pick(String... lines) {
        return lines[random.nextInt(lines.length)];
    }

    public void say(String line) {
        Voice.say(this, Component.translatable("voice.fts_lostsignal.speaker.stranger"), line, VOICE, 1f);
    }

    void face(ServerPlayer player) {
        getLookControl().setLookAt(player, 30f, 30f);
        Vec3 to = player.position().subtract(position());
        float yaw = (float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90.0);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    /** Off into the trees, away from whoever he was with: thirty to forty blocks, somewhere they are not looking. */
    public void leave(ServerPlayer player) {
        if (leaving) return;
        leaving = true;
        unseen = 0;
        Vec3 away = position().subtract(player.position());
        double a = Math.atan2(away.z, away.x) + (random.nextDouble() - 0.5) * 1.6;
        double far = 30 + random.nextDouble() * 10;
        int x = (int) Math.floor(getX() + Math.cos(a) * far), z = (int) Math.floor(getZ() + Math.sin(a) * far);
        int y = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        getNavigation().moveTo(x + 0.5, y, z + 0.5, 0.75);
    }

    /** Walking off; gone once no one has seen him for a few seconds, or after a while whatever happens. */
    void walkingOff(ServerLevel server, ServerPlayer player) {
        boolean seen = distanceToSqr(player) < 64 * 64 && player.hasLineOfSight(this);
        unseen = seen ? 0 : unseen + 1;
        if (unseen > 60 || tickCount > lifetime + 20 * 60) gone("walked off");
        else if (!getNavigation().isInProgress() && tickCount % 40 == 0) {
            leaving = false;
            leave(player);
        }
    }

    /** Whether the player looks straight at him, with nothing between. */
    boolean lookedAt(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 head = new Vec3(getX(), getEyeY(), getZ());
        Vec3 line = head.subtract(eye).normalize();
        double cos = player.getViewVector(1f).dot(line);
        return cos > Math.cos(Math.toRadians(LOOK_ANGLE)) && player.hasLineOfSight(this);
    }

    void gone(String why) {
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) {
            LostSignal.LOGGER.info("STRANGER gone ({}) after {} ticks at {} {} {}", why, tickCount, getBlockX(), getBlockY(), getBlockZ());
        }
        discard();
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (masked() || mode == Mode.VISIT || leaving || !(player instanceof ServerPlayer p)) return InteractionResult.PASS;
        // Spoken to, he talks: or, already talking, goes on to the next thing at once.
        if (mode != Mode.TALK) talk(p);
        else nextLine = 0;
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // A man struck at steps back from it and goes; it does him no harm the game can see.
        if (!level().isClientSide() && source.getEntity() instanceof ServerPlayer p) leave(p);
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
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        // Seen as far as he is ever put: the edge of a wood across a valley.
        return d < 160.0 * 160.0;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return null;
    }
}
