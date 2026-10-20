package com.jeladastudios.lostsignal.visit;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.entity.ModEntities;
import com.jeladastudios.lostsignal.entity.Watcher;
import com.jeladastudios.lostsignal.sleep.FlowingSleep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Something comes up to the lookout in the night. Steps on the stair, slow, all the way up; three knocks at the door;
 * a face at a window; the door tried, again and again. Whoever is in the cab has the length of that to get under the
 * bed. If they are all hidden when it is done trying, it goes back down the stair and away. If not, the door gives,
 * and it is in.
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class Visit {

    private static final List<Visit> UNDER_WAY = new CopyOnWriteArrayList<>();

    /** The beats of a visit, in ticks from its start. */
    static final int KNOCK = 320, WINDOW = 420, RATTLE = 600, RATTLE_END = 780, DECIDE = 800, GONE = 1000;

    /** How likely a night brings it up the stair of a lookout someone keeps: more so when a hiker is out. */
    static final float CHANCE = 0.12f, CHANCE_MISSION = 0.3f;

    /** The night each lookout was last looked at for a visit, by its foot: one look a night. */
    private static final java.util.Map<BlockPos, Long> LOOKED = new java.util.HashMap<>();

    final ServerLevel level;
    public final BlockPos foot, door;
    final Direction out;
    int t, shutAt = -1;
    boolean broke;

    private Visit(ServerLevel level, BlockPos foot, BlockPos door, Direction out) {
        this.level = level;
        this.foot = foot;
        this.door = door;
        this.out = out;
    }

    /** A visit begun at the lookout standing at a foot, if its cab door is found. */
    public static Visit begin(ServerLevel level, BlockPos foot) {
        for (Visit v : UNDER_WAY) if (v.foot.equals(foot)) return v;
        BlockPos door = null;
        Direction out = null;
        for (int dy = 19; dy <= 23 && door == null; dy++) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                for (int side = -1; side <= 1; side++) {
                    BlockPos p = foot.above(dy).relative(d, 3).relative(d.getClockWise(), side);
                    BlockState s = level.getBlockState(p);
                    if (s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                        door = p;
                        out = d;
                    }
                }
            }
        }
        if (door == null) return null;
        Visit v = new Visit(level, foot, door, out);
        UNDER_WAY.add(v);
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("VISIT begun at {} door {}", foot.toShortString(), door.toShortString());
        return v;
    }

    /** The players in the cab: within its walls, at its floor. */
    List<ServerPlayer> inCab() {
        List<ServerPlayer> in = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            Vec3 at = p.position();
            if (Math.abs(at.x - (foot.getX() + 0.5)) < 3.5 && Math.abs(at.z - (foot.getZ() + 0.5)) < 3.5
                    && at.y > door.getY() - 2 && at.y < door.getY() + 4) in.add(p);
        }
        return in;
    }

    void sound(Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    /** One beat of it. False when it is over. */
    boolean tick() {
        t++;
        Vec3 middle = Vec3.atBottomCenterOf(foot);
        if (t < KNOCK && t % 14 == 0) {
            // The stair, a step at a time, up through the frame: slow, and heavier than a person.
            double up = (double) t / KNOCK * 20;
            double sway = (t / 14) % 2 == 0 ? 1.5 : -1.5;
            sound(middle.add(sway, up, 0), SoundEvents.WOOD_STEP, 0.9f, 0.6f);
        }
        if (t == KNOCK || t == KNOCK + 18 || t == KNOCK + 36) {
            sound(Vec3.atCenterOf(door), SoundEvents.WOODEN_DOOR_CLOSE, 1.2f, 0.5f);
            FlowingSleep.wake(level, Vec3.atCenterOf(door), 10, "knock");
        }
        if (t == WINDOW) peek();
        if (t >= RATTLE && t <= RATTLE_END && t % 30 == 0) rattle();
        if (t == shutAt) slam();
        if (t == DECIDE) {
            List<ServerPlayer> in = inCab();
            boolean allHidden = !in.isEmpty() && in.stream().allMatch(Hiding::hidden);
            if (com.jeladastudios.lostsignal.dev.TestCommands.ON) for (ServerPlayer p : in) LostSignal.LOGGER.info("VISIT finds {} hidden {} at {}", p.getScoreboardName(), Hiding.hidden(p), p.position());
            if (in.isEmpty() || allHidden) {
                if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("VISIT went away ({})", in.isEmpty() ? "no one in" : "hidden");
            } else {
                breakIn(in);
            }
        }
        if (t > DECIDE && t < GONE && !broke && t % 16 == 0) {
            // Back down the stair and away.
            double up = 20 - (double) (t - DECIDE) / (GONE - DECIDE) * 20;
            sound(middle.add(0, up, 0), SoundEvents.WOOD_STEP, 0.7f, 0.6f);
        }
        return t < GONE;
    }

    /** A face at a window: the watcher on the walkway outside the cab, looking in at whoever is there. */
    void peek() {
        List<ServerPlayer> in = inCab();
        if (in.isEmpty()) return;
        ServerPlayer p = in.get(0);
        // The window on the side the player faces, or failing that the one opposite the door.
        Direction side = Direction.fromYRot(p.getYRot());
        if (side == out) side = side.getClockWise();
        BlockPos spot = foot.above(door.getY() - foot.getY()).relative(side, 4);
        Watcher w = ModEntities.WATCHER.get().create(level);
        if (w == null) return;
        w.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, side.getOpposite().toYRot(), 0f);
        w.watch(p, 160);
        w.close(12);
        level.addFreshEntity(w);
    }

    /** The door tried: open a crack and slammed, or shaken in its frame. */
    void rattle() {
        BlockState s = level.getBlockState(door);
        if (!(s.getBlock() instanceof DoorBlock d)) return;
        boolean open = s.getValue(DoorBlock.OPEN);
        if (!open && level.random.nextInt(3) == 0) {
            d.setOpen(null, level, s, door, true);
            shutAt = t + 6;
            sound(Vec3.atCenterOf(door), SoundEvents.WOODEN_DOOR_OPEN, 1f, 0.7f);
        } else {
            if (open) d.setOpen(null, level, s, door, false);
            sound(Vec3.atCenterOf(door), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 0.6f, 0.8f);
        }
    }

    /** Pulled shut again, hard, after the crack it was opened. */
    void slam() {
        BlockState s = level.getBlockState(door);
        if (s.getBlock() instanceof DoorBlock d && s.getValue(DoorBlock.OPEN)) {
            d.setOpen(null, level, s, door, false);
            sound(Vec3.atCenterOf(door), SoundEvents.WOODEN_DOOR_CLOSE, 1.3f, 0.6f);
        }
    }

    /** The door gives, and it is in among whoever did not hide. */
    void breakIn(List<ServerPlayer> in) {
        broke = true;
        sound(Vec3.atCenterOf(door), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 1.5f, 0.7f);
        level.destroyBlock(door, false);
        if (level.getBlockState(door.above()).getBlock() instanceof DoorBlock) level.destroyBlock(door.above(), false);
        Watcher w = ModEntities.WATCHER.get().create(level);
        if (w != null) {
            w.moveTo(door.getX() + 0.5, door.getY(), door.getZ() + 0.5, out.getOpposite().toYRot(), 0f);
            w.watch(in.get(0), 40);
            w.close(40);
            level.addFreshEntity(w);
        }
        for (ServerPlayer p : in) {
            if (Hiding.hidden(p)) continue;
            p.hurt(level.damageSources().magic(), 10f);
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0));
            p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
        }
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("VISIT broke in on {}", in.size());
    }

    @SubscribeEvent
    public static void tickAll(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Visit v : UNDER_WAY) if (!v.tick()) UNDER_WAY.remove(v);
    }

    /** In the small hours, at a lookout where someone is in the cab: once a night it may come. */
    @SubscribeEvent
    public static void night(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || level.dimension() != net.minecraft.world.level.Level.OVERWORLD || level.getGameTime() % 20 != 0) return;
        long day = level.getDayTime() % 24000;
        if (day < 18000 || day > 22000) return;
        long night = level.getDayTime() / 24000;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            BlockPos tower = com.jeladastudios.lostsignal.mission.Missions.towerAt(level, p);
            if (tower == null || at(tower) || LOOKED.getOrDefault(tower, -1L) == night) continue;
            // Only for someone up in the cab, not passing at the foot.
            if (p.getY() < tower.getY() + 19) continue;
            // The moment within the small hours it comes, drawn as the night goes: a look every second, at odds
            // that add up to the night's chance over the four hours.
            float chance = com.jeladastudios.lostsignal.mission.Missions.at(level, tower) != null ? CHANCE_MISSION : CHANCE;
            if (com.jeladastudios.lostsignal.dev.TestCommands.ON) chance *= 2.5f;
            if (level.random.nextFloat() >= chance / 200f) continue;
            LOOKED.put(tower, night);
            begin(level, tower);
        }
    }

    /** Whether a visit is under way at a lookout. */
    public static boolean at(BlockPos foot) {
        for (Visit v : UNDER_WAY) if (v.foot.equals(foot)) return true;
        return false;
    }
}
