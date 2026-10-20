package com.jeladastudios.lostsignal.entity;

import com.jeladastudios.lostsignal.LostSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A hiker lost on the mountain: a walker of the ordinary kind who left the trail and cannot find it. Walks as a lost
 * person does -- on in roughly the way they were going, downhill more often than up, round what is too steep or too
 * deep -- tires and rests, and when night comes stops and makes camp with a fire. Told where to go (by the lookout's
 * keeper, over the radio), walks there instead, as far as the ground lets them.
 *
 * <p>Saved with the world: a hiker is someone, and does not vanish when the player looks away.</p>
 */
public class Hiker extends PathfinderMob {

    public enum State { LOST, GOING, RESTING, CAMPING, RESCUED }

    private static final EntityDataAccessor<Integer> KIT = SynchedEntityData.defineId(Hiker.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(Hiker.class, EntityDataSerializers.INT);

    /** The kits the textures come in (tools/textures/Hiker.java). */
    public static final int KITS = 4;
    /** How tired walking makes them, a tick, and how rested a tick of rest. */
    static final float TIRES = 1f / (20 * 240), RESTS = 1f / (20 * 40);
    /** Their walking pace, as a share of the attribute's: an unhurried walk over rough ground. */
    static final double PACE = 0.55;

    private float fatigue;
    private double heading = Double.NaN;
    private BlockPos goal;
    private BlockPos fire;

    public Hiker(EntityType<? extends Hiker> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setMaxUpStep(1.0f);
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(KIT, 0);
        entityData.define(STATE, State.LOST.ordinal());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new Walk());
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 16f));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    public int kit() {
        return entityData.get(KIT);
    }

    public void kit(int kit) {
        entityData.set(KIT, Math.floorMod(kit, KITS));
    }

    public State state() {
        return State.values()[entityData.get(STATE)];
    }

    void state(State s) {
        if (s == state()) return;
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) {
            LostSignal.LOGGER.info("HIKER {} {} -> {} at {} {} {} fatigue {}", getId(), state(), s, getBlockX(), getBlockY(), getBlockZ(),
                    String.format(java.util.Locale.ROOT, "%.2f", fatigue));
        }
        if (state() == State.CAMPING && s != State.CAMPING) breakCamp();
        entityData.set(STATE, s.ordinal());
    }

    /** Sent somewhere: walks there, as near as the ground lets, and stops. */
    public void goTo(BlockPos where) {
        goal = where;
        getNavigation().stop();
        state(State.GOING);
    }

    /** Asked to stay where they are: they rest. */
    public void stay() {
        goal = null;
        getNavigation().stop();
        state(State.RESTING);
    }

    public float fatigue() {
        return fatigue;
    }

    public BlockPos goal() {
        return goal;
    }

    // === Walking ================================================================

    /** The whole of their walking: lost, sent, resting, camping. Holds the movement while it runs. */
    class Walk extends Goal {

        private int pause;

        Walk() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Level level = level();
            boolean night = !level.isDay() && level.dimensionType().hasSkyLight();
            boolean moving = getNavigation().isInProgress();
            fatigue = Mth.clamp(fatigue + (moving ? TIRES : -RESTS), 0f, 1.2f);
            switch (state()) {
                case CAMPING -> {
                    if (!night) state(State.LOST);
                }
                case RESTING -> {
                    if (night && fire == null) makeCamp();
                    else if (fatigue <= 0.05f && goal == null && !night) state(State.LOST);
                    else if (fatigue <= 0.05f && goal != null) state(State.GOING);
                }
                case GOING -> {
                    if (goal == null) {
                        state(State.LOST);
                    } else if (fatigue >= 1f) {
                        getNavigation().stop();
                        state(State.RESTING);
                    } else if (distanceToSqr(Vec3.atBottomCenterOf(goal)) < 9) {
                        goal = null;
                        getNavigation().stop();
                        state(State.RESTING);
                    } else if (!moving && --pause <= 0) {
                        if (!walkToward(goal)) pause = 40;
                    }
                }
                case LOST -> {
                    if (night) {
                        getNavigation().stop();
                        makeCamp();
                    } else if (fatigue >= 1f) {
                        getNavigation().stop();
                        state(State.RESTING);
                    } else if (!moving && --pause <= 0) {
                        // A lost walker stops, looks round, and goes on.
                        pause = 40 + getRandom().nextInt(160);
                        wander();
                    }
                }
                case RESCUED -> {
                    // Followed home: for now they stand where they were found.
                }
            }
        }
    }

    /**
     * On in roughly the way they were going: of a handful of spots ten to twenty blocks out within sixty degrees of
     * that way, the lowest that a path reaches -- the lost go down, towards water and valleys.
     */
    void wander() {
        if (Double.isNaN(heading)) heading = getRandom().nextDouble() * Math.PI * 2;
        Level level = level();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE, bestHeading = heading;
        for (int i = 0; i < 6; i++) {
            double a = heading + Math.toRadians((getRandom().nextDouble() * 2 - 1) * 60);
            double d = 10 + getRandom().nextDouble() * 10;
            int x = (int) Math.floor(getX() + Math.cos(a) * d), z = (int) Math.floor(getZ() + Math.sin(a) * d);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos at = new BlockPos(x, y, z);
            if (!level.getFluidState(at.below()).isEmpty() || !level.getFluidState(at).isEmpty()) continue;
            double score = y + getRandom().nextDouble() * 4;
            if (score < bestScore && getNavigation().createPath(at, 1) != null) {
                bestScore = score;
                best = at;
                bestHeading = a;
            }
        }
        if (best == null) {
            // Nowhere on that way: they turn.
            heading += Math.PI * (0.5 + getRandom().nextDouble());
            return;
        }
        heading = bestHeading;
        getNavigation().moveTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5, PACE);
    }

    /** A leg of the way to somewhere further off than a path is found to: twenty-four blocks along the line, on the ground. */
    boolean walkToward(BlockPos to) {
        Vec3 from = position();
        Vec3 line = Vec3.atBottomCenterOf(to).subtract(from);
        double length = Math.sqrt(line.x * line.x + line.z * line.z);
        BlockPos step = to;
        if (length > 24) {
            int x = (int) Math.floor(from.x + line.x / length * 24), z = (int) Math.floor(from.z + line.z / length * 24);
            step = new BlockPos(x, level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        }
        heading = Math.atan2(line.z, line.x);
        return getNavigation().moveTo(step.getX() + 0.5, step.getY(), step.getZ() + 0.5, PACE);
    }

    // === Camp ===================================================================

    /** A fire on a free spot beside them, for the night; put out and gone when they move on. */
    void makeCamp() {
        state(State.CAMPING);
        if (fire != null) return;
        Level level = level();
        for (BlockPos p : BlockPos.betweenClosed(blockPosition().offset(-2, -1, -2), blockPosition().offset(2, 1, 2))) {
            if (p.closerThan(blockPosition(), 1.5)) continue;
            if (!level.getBlockState(p).isAir() || !level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)) continue;
            if (!level.getFluidState(p.below()).isEmpty()) continue;
            level.setBlock(p, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), 3);
            fire = p.immutable();
            return;
        }
    }

    void breakCamp() {
        if (fire == null) return;
        BlockState s = level().getBlockState(fire);
        if (s.is(Blocks.CAMPFIRE)) level().removeBlock(fire, false);
        fire = null;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide() && reason.shouldDestroy()) breakCamp();
        super.remove(reason);
    }

    // === Saving =================================================================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Kit", kit());
        tag.putString("State", state().name());
        tag.putFloat("Fatigue", fatigue);
        if (!Double.isNaN(heading)) tag.putDouble("Heading", heading);
        if (goal != null) tag.put("Goal", NbtUtils.writeBlockPos(goal));
        if (fire != null) tag.put("Fire", NbtUtils.writeBlockPos(fire));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        kit(tag.getInt("Kit"));
        try {
            entityData.set(STATE, State.valueOf(tag.getString("State")).ordinal());
        } catch (IllegalArgumentException e) {
            entityData.set(STATE, State.LOST.ordinal());
        }
        fatigue = tag.getFloat("Fatigue");
        heading = tag.contains("Heading") ? tag.getDouble("Heading") : Double.NaN;
        goal = tag.contains("Goal") ? NbtUtils.readBlockPos(tag.getCompound("Goal")) : null;
        fire = tag.contains("Fire") ? NbtUtils.readBlockPos(tag.getCompound("Fire")) : null;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        // A bright jacket seen from a lookout across a valley.
        return d < 192.0 * 192.0;
    }
}
