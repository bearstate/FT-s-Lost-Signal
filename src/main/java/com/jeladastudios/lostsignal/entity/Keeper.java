package com.jeladastudios.lostsignal.entity;

import com.jeladastudios.lostsignal.tower.Tower;
import com.jeladastudios.lostsignal.tower.Towers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A lookout's keeper: someone posted there for the season, who lives in the cab (at the fire finder, at the stove,
 * at the windows) and answers the radio. Talked to face to face, they say what they say on the radio, and more.
 *
 * <p>Put in the cab when a player comes near a kept lookout, and gone when they are all far off; what they are
 * (name, voice, temper, what they need, how far they trust the player) is the lookout's, in {@link Towers}.</p>
 */
public class Keeper extends PathfinderMob {

    private static final EntityDataAccessor<Integer> KIT = SynchedEntityData.defineId(Keeper.class, EntityDataSerializers.INT);

    /** The kits the textures come in (tools/textures/Keeper.java). */
    public static final int KITS = 3;

    private long tower;
    /** The cab's floor, where they go about. */
    private BlockPos floor;
    private int idle;

    public Keeper(EntityType<? extends Keeper> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(KIT, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8f));
    }

    public int kit() {
        return entityData.get(KIT);
    }

    /** Theirs: a lookout, and its cab's floor. */
    public void post(Tower t, BlockPos floor) {
        this.tower = t.key();
        this.floor = floor;
        entityData.set(KIT, Math.floorMod(t.kit, KITS));
    }

    public long tower() {
        return tower;
    }

    /** Where they lie under the bed; null while they do not. */
    private Vec3 hidden;

    public boolean hidden() {
        return hidden != null;
    }

    /** Down under the cab's bed, tucked against it: true where there is a bed and room by it. */
    public boolean hide() {
        if (hidden != null) return true;
        if (floor == null) return false;
        for (BlockPos p : BlockPos.betweenClosed(floor.offset(-3, 1, -3), floor.offset(3, 1, 3))) {
            var s = level().getBlockState(p);
            if (!(s.getBlock() instanceof net.minecraft.world.level.block.BedBlock)) continue;
            var under = com.jeladastudios.lostsignal.visit.Hiding.under(level(), p.immutable(), s);
            if (under == null) continue;
            hidden = under.at();
            getNavigation().stop();
            moveTo(hidden.x, hidden.y, hidden.z, under.out().toYRot(), 0f);
            setYHeadRot(under.out().toYRot());
            setPose(net.minecraft.world.entity.Pose.SWIMMING);
            return true;
        }
        return false;
    }

    public void unhide() {
        hidden = null;
        setPose(net.minecraft.world.entity.Pose.STANDING);
    }

    public Tower record() {
        return level() instanceof ServerLevel server ? Towers.known(server, tower) : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || floor == null) return;
        Tower t = record();
        // No longer kept (gone down, or worse) or no one near to see them: they are not here.
        if (t == null || t.state != Tower.State.KEPT || tickCount % 100 == 0 && server.getNearestPlayer(this, 128) == null) {
            discard();
            return;
        }
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON && tickCount % 20 == 0 && getY() < floor.getY()) {
            com.jeladastudios.lostsignal.LostSignal.LOGGER.info("KEEPER below the cab at {} {} {}", (int) getX(), (int) getY(), (int) getZ());
        }
        // Under the bed, as still as they can be, until it is light.
        if (hidden != null) {
            getNavigation().stop();
            if (position().distanceToSqr(hidden) > 0.04) moveTo(hidden.x, hidden.y, hidden.z, getYRot(), 0f);
            setPose(net.minecraft.world.entity.Pose.SWIMMING);
            if (server.isDay()) unhide();
            return;
        }
        // About the cab: a few steps to somewhere else in it now and then, and a while there.
        if (!getNavigation().isInProgress() && --idle <= 0) {
            idle = 100 + random.nextInt(200);
            int dx = random.nextInt(5) - 2, dz = random.nextInt(5) - 2;
            BlockPos to = floor.offset(dx, 1, dz);
            if (server.getBlockState(to).getCollisionShape(server, to).isEmpty()) getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 0.6);
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer p)) return InteractionResult.PASS;
        Tower t = record();
        if (t == null) return InteractionResult.PASS;
        getNavigation().stop();
        getLookControl().setLookAt(p);
        // Coal for a keeper who asked for it: taken, and thanked for.
        if (t.need == Tower.Need.COAL && com.jeladastudios.lostsignal.tower.Talk.takeCoal(this, p, t, p.getItemInHand(hand))) {
            return InteractionResult.CONSUME;
        }
        com.jeladastudios.lostsignal.net.Net.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                new com.jeladastudios.lostsignal.net.Net.Talk(getId(), t.keeper, t.number));
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Not someone the game lets a player hurt.
        return source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(source, amount);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("Tower", tower);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        tower = tag.getLong("Tower");
    }
}
