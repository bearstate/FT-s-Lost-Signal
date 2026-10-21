package com.jeladastudios.lostsignal.remains;

import com.jeladastudios.lostsignal.LostSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;

/**
 * What is left of a hiker the thing in the trees took: the body, face down at the end of the drag marks -- or, where
 * there is no body to find, nothing to see at all, only the keeper of the scene: the camp's fire smoking a while
 * after, and the scene cleared away in the end.
 *
 * <p>Found -- the body turned over, its journal taken -- the night's story is told to the District, and in a day, once
 * no one is looking, the body is gone (taken down off the mountain).</p>
 */
public class Remains extends Entity {

    private static final EntityDataAccessor<Integer> KIT = SynchedEntityData.defineId(Remains.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> BODY = SynchedEntityData.defineId(Remains.class, EntityDataSerializers.BOOLEAN);

    /** How long the put-out fire smokes, in ticks: a few hours of the morning. */
    static final long SMOKES = 6_000;
    /** How long a found body stays, and an unfound scene's keeper. */
    static final long STAYS_FOUND = 24_000, STAYS = 24_000 * 5;

    private String name = "";
    private BlockPos fire, tower;
    private long since, foundAt = -1;
    /** The journal's pages: what they wrote, as they wrote it. */
    private int journal;

    public Remains(EntityType<? extends Remains> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(KIT, 0);
        entityData.define(BODY, true);
    }

    public int kit() {
        return entityData.get(KIT);
    }

    public boolean body() {
        return entityData.get(BODY);
    }

    /** Set out once, by the scene. */
    void set(int kit, boolean body, String name, BlockPos fire, BlockPos tower, int journal) {
        entityData.set(KIT, kit);
        entityData.set(BODY, body);
        this.name = name;
        this.fire = fire;
        this.tower = tower;
        this.journal = journal;
        this.since = level().getGameTime();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        long now = server.getGameTime();
        // The put-out fire: a thread of smoke going up from it a while.
        if (fire != null && now - since < SMOKES && tickCount % 25 == 0 && server.getBlockState(fire).is(Blocks.CAMPFIRE)
                && !server.getBlockState(fire).getValue(CampfireBlock.LIT)) {
            server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, fire.getX() + 0.5, fire.getY() + 0.4, fire.getZ() + 0.5, 0, 0, 0.06, 0, 1);
        }
        if (tickCount % 200 != 0) return;
        boolean due = foundAt >= 0 ? now - foundAt > STAYS_FOUND : now - since > STAYS;
        if (due && server.getNearestPlayer(this, 48) == null) {
            if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("REMAINS cleared at {}", blockPosition().toShortString());
            discard();
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!body()) return InteractionResult.PASS;
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (foundAt < 0 && player instanceof ServerPlayer p) {
            foundAt = level().getGameTime();
            var book = Journal.of(name, journal);
            if (!p.getInventory().add(book)) p.drop(book, false);
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("remains.fts_lostsignal.found", name), true);
            if (tower != null) com.jeladastudios.lostsignal.mission.Missions.bodyFound((ServerLevel) level(), tower);
        } else {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("remains.fts_lostsignal.again", name), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isPickable() {
        return body() && !isRemoved();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return body() && d < 96.0 * 96.0;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Kit", kit());
        tag.putBoolean("Body", body());
        tag.putString("Name", name);
        if (fire != null) tag.put("Fire", NbtUtils.writeBlockPos(fire));
        if (tower != null) tag.put("Tower", NbtUtils.writeBlockPos(tower));
        tag.putLong("Since", since);
        tag.putLong("FoundAt", foundAt);
        tag.putInt("Journal", journal);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(KIT, tag.getInt("Kit"));
        entityData.set(BODY, tag.getBoolean("Body"));
        name = tag.getString("Name");
        fire = tag.contains("Fire") ? NbtUtils.readBlockPos(tag.getCompound("Fire")) : null;
        tower = tag.contains("Tower") ? NbtUtils.readBlockPos(tag.getCompound("Tower")) : null;
        since = tag.getLong("Since");
        foundAt = tag.getLong("FoundAt");
        journal = tag.getInt("Journal");
    }
}
