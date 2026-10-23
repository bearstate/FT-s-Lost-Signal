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
 * and is gone once no one can see him. He is not hurt by being struck: he walks away from it.</p>
 */
public class Stranger extends PathfinderMob {

    private static final EntityDataAccessor<Boolean> MASKED = SynchedEntityData.defineId(Stranger.class, EntityDataSerializers.BOOLEAN);

    /** What he is out for: to watch from off, to come and talk, at a lookout in the night, or on his way up to one. */
    public enum Mode { WATCH, TALK, VISIT, APPROACH, HUNT }

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
                .add(Attributes.MAX_HEALTH, 400.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
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

    /** The places he is walking through, in order, on his way up to a lookout; not kept. */
    private final java.util.ArrayDeque<net.minecraft.core.BlockPos> way = new java.util.ArrayDeque<>();
    private int wayTries, stood, stops;
    /** The lookout he came up to, by its foot: where he is the angrier for having been driven off. */
    private net.minecraft.core.BlockPos visiting;

    /**
     * Up to a lookout in the night, at a walk: along the trail to its foot, up the stair landing by landing, along the
     * walkway to the door. Seen, he stops and looks back up at whoever is looking; then comes on.
     */
    public void approach(ServerPlayer player, net.minecraft.core.BlockPos foot, java.util.List<net.minecraft.core.BlockPos> path) {
        watching = player.getUUID();
        visiting = foot;
        mode = Mode.APPROACH;
        lifetime = Integer.MAX_VALUE;
        keepsOff = 0;
        way.clear();
        way.addAll(path);
    }

    /**
     * On to somewhere else about the lookout: round the walkway to a window, there to watch, bearing a look so long; or
     * down the stair, and then off into the trees.
     */
    public void walkTo(java.util.List<net.minecraft.core.BlockPos> path, boolean thenLeave, int thenBears) {
        mode = Mode.APPROACH;
        way.clear();
        way.addAll(path);
        wayTries = 0;
        stops = 3;
        this.thenLeave = thenLeave;
        this.thenBears = thenBears;
    }

    private boolean thenLeave;
    private int thenBears;

    /**
     * After someone out in the night, not behind a door: by a way first where there is one (down or up the lookout's
     * stair), then straight for them. Slow while he is looked at; quicker when he is not.
     */
    public void hunt(ServerPlayer player, java.util.List<net.minecraft.core.BlockPos> first) {
        watching = player.getUUID();
        mode = Mode.HUNT;
        lifetime = Integer.MAX_VALUE;
        keepsOff = 0;
        stops = 3;
        thenLeave = false;
        thenBears = 0;
        way.clear();
        way.addAll(first);
        wayTries = 0;
        closest = Double.MAX_VALUE;
        stuck = 0;
    }

    /** Closing on whoever he is after. */
    void chase(ServerPlayer player) {
        if (!way.isEmpty()) {
            onTheWay(player);
            return;
        }
        boolean seen = lookedAt(player);
        double pace = seen ? 0.6 : 1.05;
        if (tickCount % 10 == 0 || !getNavigation().isInProgress()) getNavigation().moveTo(player, pace);
        // No way to them he can walk (a drop, a stream): no nearer for a while, and yet, the next time they look round,
        // he is, behind them.
        double gap = distanceTo(player);
        if (gap < closest - 1) {
            closest = gap;
            stuck = 0;
        } else if (++stuck > 80 && !seen && gap > 6) {
            Vec3 spot = behind(player, 7 + random.nextInt(4));
            if (spot != null) {
                getNavigation().stop();
                moveTo(spot.x, spot.y, spot.z, getYRot(), 0f);
                closest = distanceTo(player);
            }
            stuck = 0;
        }
    }

    private double closest = Double.MAX_VALUE;
    private int stuck;

    /** Somewhere to stand some blocks behind a player, out of their sight; or null. */
    Vec3 behind(ServerPlayer player, int far) {
        Vec3 look = player.getLookAngle();
        double a0 = Math.atan2(-look.z, -look.x);
        for (int k : new int[]{0, 1, -1, 2, -2}) {
            double a = a0 + k * 0.5;
            int x = (int) Math.floor(player.getX() + Math.cos(a) * far), z = (int) Math.floor(player.getZ() + Math.sin(a) * far);
            net.minecraft.core.BlockPos p = new net.minecraft.core.BlockPos(x, level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (Math.abs(p.getY() - player.getY()) > 4 || !level().getFluidState(p.below()).isEmpty() || !level().getBlockState(p).isAir()) continue;
            Vec3 at = Vec3.atBottomCenterOf(p);
            Vec3 to = at.add(0, 1.6, 0).subtract(player.getEyePosition()).normalize();
            if (player.getViewVector(1f).dot(to) > 0.3) continue;
            return at;
        }
        return null;
    }

    /** Where he was making for, reached. */
    public boolean arrived() {
        return way.isEmpty();
    }

    /** Standing where he is, now, as at the door or a window: watching, bearing a look as long as given. */
    public void stand(int bears) {
        way.clear();
        getNavigation().stop();
        close(bears);
    }

    /** A leg of his way; where it cannot be walked, he is simply there, the next time no one is looking. */
    void onTheWay(ServerPlayer player) {
        net.minecraft.core.BlockPos next = way.peek();
        if (next == null) {
            if (thenLeave) leave(player);
            else if (thenBears > 0) stand(thenBears);
            else face(player);
            return;
        }
        // Looked at from up there, he stops and looks back, a while; a few times on the way, no more.
        if (stood > 0) {
            stood--;
            getNavigation().stop();
            face(player);
            return;
        }
        if (stops < 3 && distanceToSqr(player) > 12 * 12 && lookedAt(player)) {
            stops++;
            stood = 60 + random.nextInt(60);
            return;
        }
        if (distanceToSqr(Vec3.atBottomCenterOf(next)) < 2.0) {
            way.poll();
            wayTries = 0;
            return;
        }
        if (getNavigation().isInProgress() || tickCount % 5 != 0) return;
        var path = getNavigation().createPath(next, 0);
        if (path != null && path.canReach()) {
            getNavigation().moveTo(path, 0.6);
            wayTries = 0;
        } else if (++wayTries > 4 && !lookedAt(player)) {
            moveTo(next.getX() + 0.5, next.getY(), next.getZ() + 0.5, getYRot(), 0f);
            way.poll();
            wayTries = 0;
        }
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
            case APPROACH -> onTheWay(player);
            case HUNT -> chase(player);
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
        else if (step == 1) line = pick("S_ASK_01", "S_ASK_02", "S_ASK_03", "S_KNOWS_01", "S_KNOWS_02", "S_WATER");
        else if (step == 2 || step == 3 && random.nextBoolean()) line = saying();
        else line = null;
        if (line == null) {
            say(pick("S_BYE_01", "S_BYE_02"));
            leave(player);
            return;
        }
        say(line);
        // Asked for water: he waits for it, a little longer, for whoever has some.
        if (line.equals("S_WATER")) askedWater = true;
        step++;
        // Time to say it, and a while after: he is in no hurry.
        nextLine = com.jeladastudios.lostsignal.tower.Speech.length(line) + 160 + random.nextInt(140) + (askedWater ? 100 : 0);
    }

    /** One of his sayings he has not said yet in this meeting: of those with his voice, once any are recorded. */
    String saying() {
        java.util.List<String> all = new java.util.ArrayList<>(), heard = new java.util.ArrayList<>();
        for (int i = 1; i <= 16; i++) {
            String s = String.format("S_SAY_%02d", i);
            all.add(s);
            if (!com.jeladastudios.lostsignal.rescue.Lines.takes(s).isEmpty()) heard.add(s);
        }
        java.util.List<String> pool = heard.isEmpty() ? all : heard;
        for (int tries = 0; tries < 20; tries++) {
            String s = pool.get(random.nextInt(pool.size()));
            if (said.add(s)) return s;
        }
        return pool.get(0);
    }

    /** Whether he has asked for water and not been given any. */
    private boolean askedWater;

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
        // Water, as he asked: he takes the bottle, thanks them, and holds it. He never drinks.
        net.minecraft.world.item.ItemStack held = p.getItemInHand(hand);
        if (askedWater && held.is(net.minecraft.world.item.Items.POTION)
                && net.minecraft.world.item.alchemy.PotionUtils.getPotion(held) == net.minecraft.world.item.alchemy.Potions.WATER) {
            askedWater = false;
            setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, held.copyWithCount(1));
            if (!p.getAbilities().instabuild) held.shrink(1);
            say("S_WATER_THANKS");
            nextLine = 120 + random.nextInt(60);
            return InteractionResult.CONSUME;
        }
        // Spoken to, he talks: or, already talking, goes on to the next thing at once.
        if (mode != Mode.TALK) talk(p);
        else nextLine = 0;
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide()) return false;
        // By day, a man struck at steps back from it and goes; it does him no harm the game can see.
        if (!masked()) {
            if (source.getEntity() instanceof ServerPlayer p) leave(p);
            return false;
        }
        // Masked, in the night, it does; and he does not run from it. Brought to the end of it he does not fall: he
        // steps back into the dark and is gone, and some other night he is back, and worse.
        if (!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) && getHealth() - amount <= 1f) {
            withdraw();
            return true;
        }
        return super.hurt(source, amount);
    }

    /** Gone back into the dark, hurt: the lookout he came to is the worse for it. */
    void withdraw() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 40, 0.4, 0.8, 0.4, 0.02);
            server.playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.SOUL_ESCAPE, net.minecraft.sounds.SoundSource.HOSTILE, 2f, 0.5f);
            if (visiting != null) com.jeladastudios.lostsignal.tower.Towers.angered(server, visiting);
        }
        gone("withdrew, hurt");
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
