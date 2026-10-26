package com.jeladastudios.lostsignal.visit;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.entity.ModEntities;
import com.jeladastudios.lostsignal.entity.Stranger;
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
 * The stranger comes up to the lookout in the night, masked. Steps on the stair, slow, all the way up; three knocks;
 * the mask at a window; then at the door, his voice through it, patient, and the door tried, again and again. Whoever
 * is in the cab has the length of that to get under the bed. If they are all hidden when he is done trying, he goes
 * back down the stair and away. If not, the door gives, and he is in.
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class Visit {

    private static final List<Visit> UNDER_WAY = new CopyOnWriteArrayList<>();

    /** The beats of a visit, in ticks from its start. */
    static final int KNOCK = 320, WINDOW = 420, RATTLE = 600, RATTLE_END = 780, DECIDE = 800, GONE = 1000;

    /** How likely a night brings it up the stair of a lookout someone keeps: more so when a hiker is out. */
    static final float CHANCE = 0.12f, CHANCE_MISSION = 0.3f;
    /**
     * The rules, as they hold: a lookout with its lamp lit is one someone is up in, and he is less sure of it; a dark one
     * is an empty one to him, and he comes to see. Given water by day, he takes it as an invitation, that night and the next.
     */
    static final float LIT = 0.7f, DARK = 1.6f, WATERED = 3f;

    /** When the small hours were last looked at, on the day's clock. */
    private static long lastClock = Long.MAX_VALUE;

    /** The night each lookout was last looked at for a visit, by its foot: one look a night. */
    private static final java.util.Map<BlockPos, Long> LOOKED = new java.util.HashMap<>();

    final ServerLevel level;
    public final BlockPos foot, door;
    final Direction out;
    int t, shutAt = -1;
    boolean broke;
    /** Whether he is still on his way up, walking, and for how long he has been. */
    boolean approaching;
    int approachTicks;
    /** How long he may take over it before he is simply there: a minute and a half. */
    static final int APPROACH_MAX = 20 * 90;
    /** How far down the trail he is first seen coming. */
    static final int COMES_FROM = 32;
    /** Whether he went back down the stair on foot, rather than out of sight. */
    boolean walkedOff;

    private Visit(ServerLevel level, BlockPos foot, BlockPos door, Direction out) {
        this.level = level;
        this.foot = foot;
        this.door = door;
        this.out = out;
    }

    /** A cab's door: its lower half, and the way out through it. */
    public record Door(BlockPos at, Direction out) {}

    /** The door of the cab of the lookout standing at a foot, or null where there is none. */
    public static Door doorOf(ServerLevel level, BlockPos foot) {
        for (int dy = 19; dy <= 23; dy++) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                for (int side = -1; side <= 1; side++) {
                    BlockPos p = foot.above(dy).relative(d, 3).relative(d.getClockWise(), side);
                    BlockState s = level.getBlockState(p);
                    if (s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) return new Door(p, d);
                }
            }
        }
        return null;
    }

    /** A visit begun at the lookout standing at a foot, if its cab door is found. */
    public static Visit begin(ServerLevel level, BlockPos foot) {
        for (Visit v : UNDER_WAY) if (v.foot.equals(foot)) return v;
        Door found = doorOf(level, foot);
        if (found == null) return null;
        BlockPos door = found.at();
        Direction out = found.out();
        Visit v = new Visit(level, foot, door, out);
        UNDER_WAY.add(v);
        v.comeUp();
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("VISIT begun at {} door {}{}", foot.toShortString(), door.toShortString(), v.approaching ? ", walking up" : "");
        return v;
    }

    /**
     * Him, masked, out on the trail below, walking up to it: along the trail to the foot, up the stair, along the
     * walkway; seen from the cab's windows by anyone looking out. Where the stair cannot be walked (a lookout built before
     * it came out onto the walkway) or the trail below is not loaded, he is only heard on the stair, as before.
     */
    void comeUp() {
        Vec3 middle = Vec3.atCenterOf(foot);
        ServerPlayer p = null;
        for (ServerPlayer q : level.players()) {
            if (q.isSpectator() || q.position().distanceTo(middle) > 96) continue;
            if (p == null || q.distanceToSqr(middle) < p.distanceToSqr(middle)) p = q;
        }
        if (p == null || !com.jeladastudios.lostsignal.tower.Stair.walkable(level, foot, out)) return;
        Vec3 down = com.jeladastudios.lostsignal.rescue.Sight.downhill(level, foot);
        double a = down != null ? Math.atan2(down.z, down.x) : level.random.nextDouble() * Math.PI * 2;
        int x = (int) Math.floor(foot.getX() + 0.5 + Math.cos(a) * COMES_FROM), z = (int) Math.floor(foot.getZ() + 0.5 + Math.sin(a) * COMES_FROM);
        if (!level.isLoaded(new BlockPos(x, foot.getY(), z))) return;
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        Stranger s = ModEntities.STRANGER.get().create(level);
        if (s == null) return;
        s.moveTo(x + 0.5, y, z + 0.5, (float) Math.toDegrees(Math.atan2(-(foot.getX() - x), foot.getZ() - z)), 0f);
        s.masked(true);
        s.approach(p, foot, com.jeladastudios.lostsignal.tower.Stair.up(foot, out));
        level.addFreshEntity(s);
        at = s;
        approaching = true;
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

    /** Whom he has turned to, out in the night rather than behind the door; and for how long. */
    ServerPlayer hunted;
    int huntTicks;
    /** How near the lookout someone out of its cab must be for him to come for them; how long he keeps at it. */
    static final double HUNT_REACH = 64;
    static final int HUNTS = 20 * 180;

    /** One beat of it. False when it is over. */
    boolean tick() {
        // No one behind the door, the one he came for out in the night: he comes for them where they are.
        if (hunted == null && !broke && t < DECIDE && at != null && at.isAlive() && inCab().isEmpty()) {
            ServerPlayer out = outside();
            if (out != null) hunt(out);
        }
        if (hunted != null) return hunting();
        if (approaching) {
            // Driven off on the way, hurt, or gone: no knock tonight.
            if (at == null || !at.isAlive()) {
                if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("VISIT at {} ended on the way up", foot.toShortString());
                return false;
            }
            if (!at.arrived() && ++approachTicks < APPROACH_MAX) return true;
            // At the door: he knocks.
            BlockPos outside = door.relative(out);
            if (at.distanceToSqr(Vec3.atBottomCenterOf(outside)) > 4) at.moveTo(outside.getX() + 0.5, outside.getY(), outside.getZ() + 0.5, out.getOpposite().toYRot(), 0f);
            at.stand(Integer.MAX_VALUE);
            approaching = false;
            t = KNOCK - 1;
        }
        t++;
        Vec3 middle = Vec3.atBottomCenterOf(foot);
        // The stair heard, a step at a time, where he is not seen to come up it.
        if (at == null && t < KNOCK && t % 14 == 0) {
            // The stair, a step at a time, up through the frame: slow, unhurried.
            double up = (double) t / KNOCK * 20;
            double sway = (t / 14) % 2 == 0 ? 1.5 : -1.5;
            sound(middle.add(sway, up, 0), SoundEvents.WOOD_STEP, 0.9f, 0.6f);
        }
        // The door left open: no knock. He is in.
        if (t == KNOCK && doorOpen()) {
            walkIn(inCab());
            t = DECIDE;
            return true;
        }
        if (t == KNOCK || t == KNOCK + 18 || t == KNOCK + 36) {
            sound(Vec3.atCenterOf(door), SoundEvents.WOODEN_DOOR_CLOSE, 1.2f, 0.5f);
            FlowingSleep.wake(level, Vec3.atCenterOf(door), 10, "knock");
        }
        // A nervous keeper knows that knock: under the bed at once. The others stay up, unless told.
        if (t == KNOCK + 40) {
            var tower = com.jeladastudios.lostsignal.tower.Towers.at(level, foot);
            if (tower.temper == com.jeladastudios.lostsignal.tower.Tower.Temper.NERVOUS) for (var k : keepersIn()) k.hide();
        }
        if (t == WINDOW) peek();
        if (t == RATTLE - 10) atDoor();
        // His voice through the door: asking, then waiting.
        if (t == RATTLE + 10) say(pick("S_DOOR_01", "S_DOOR_02", "S_DOOR_03"));
        if (t == RATTLE + 130) say("S_DOOR_04");
        if (t >= RATTLE && t <= RATTLE_END && t % 30 == 0) rattle();
        if (t == shutAt) slam();
        if (t == DECIDE) {
            List<ServerPlayer> in = inCab();
            boolean allHidden = in.stream().allMatch(Hiding::hidden);
            // A keeper up and about is someone for him too, where a world lets him take keepers.
            List<com.jeladastudios.lostsignal.entity.Keeper> exposed = new ArrayList<>();
            if (com.jeladastudios.lostsignal.Config.stranger(com.jeladastudios.lostsignal.Config.TAKES_KEEPERS)) {
                for (var k : keepersIn()) if (!k.hidden()) exposed.add(k);
            }
            if (com.jeladastudios.lostsignal.dev.TestCommands.ON) {
                for (ServerPlayer p : in) LostSignal.LOGGER.info("VISIT finds {} hidden {} at {}", p.getScoreboardName(), Hiding.hidden(p), p.position());
                LostSignal.LOGGER.info("VISIT finds {} keeper(s) not hidden", exposed.size());
            }
            if (allHidden && exposed.isEmpty()) {
                say("S_DOOR_LEAVE");
                for (ServerPlayer p : in) {
                    com.jeladastudios.lostsignal.desk.Marks.award(p, "closed_door");
                    com.jeladastudios.lostsignal.desk.Desk.count(p, "nights");
                }
                if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("VISIT went away ({})", in.isEmpty() ? "no one in" : "hidden");
            } else {
                breakIn(in);
                for (var k : exposed) take(k);
            }
        }
        if (t == DECIDE + 2 && !broke && at != null && at.isAlive() && com.jeladastudios.lostsignal.tower.Stair.walkable(level, foot, out)) {
            // Back down the stair, and off down the trail, walking.
            at.walkTo(com.jeladastudios.lostsignal.tower.Stair.down(foot, out), true, 0);
            walkedOff = true;
        }
        if (t == DECIDE + 40 && !broke && !walkedOff && at != null) at.discard();
        if (t >= GONE && at != null && !walkedOff) at.discard();
        if (t >= GONE) settle();
        if (t > DECIDE && t < GONE && !broke && !walkedOff && t % 16 == 0) {
            // Back down the stair and away.
            double up = 20 - (double) (t - DECIDE) / (GONE - DECIDE) * 20;
            sound(middle.add(0, up, 0), SoundEvents.WOOD_STEP, 0.7f, 0.6f);
        }
        return t < GONE;
    }

    /** The lookout as he leaves it: broken in on, he has had what he came for, and the anger of a hurt one is spent. */
    void settle() {
        var t = com.jeladastudios.lostsignal.tower.Towers.at(level, foot);
        if (broke && t.anger > 0) {
            t.anger = 0;
            com.jeladastudios.lostsignal.tower.Towers.of(level).setDirty();
        }
    }

    /**
     * The mask at a window: he is on the walkway outside the cab, looking in at whoever is there. Walked round to it
     * from the door if he came up the stair; else there, of a sudden.
     */
    void peek() {
        List<ServerPlayer> in = inCab();
        if (in.isEmpty()) return;
        ServerPlayer p = in.get(0);
        // The window on the side the player faces, or failing that the one opposite the door.
        Direction side = Direction.fromYRot(p.getYRot());
        if (side == out) side = side.getClockWise();
        BlockPos spot = foot.above(door.getY() - foot.getY()).relative(side, 4);
        // Somewhere on the walkway he can stand: not over the stairwell.
        for (int k : new int[]{0, 1, -1, 2, -2}) {
            BlockPos q = spot.relative(side.getClockWise(), k);
            if (level.getBlockState(q).isAir() && level.getBlockState(q.below()).isFaceSturdy(level, q.below(), Direction.UP)) {
                spot = q;
                break;
            }
        }
        if (at != null && at.isAlive()) {
            at.walkTo(List.of(spot), false, 12);
            return;
        }
        Stranger s = masked(p, spot, side.getOpposite(), 160);
        if (s != null) s.close(12);
    }

    /** Him, masked, set down somewhere about the cab, facing a way, watching a player for a while. */
    Stranger masked(ServerPlayer p, BlockPos spot, Direction facing, int ticks) {
        Stranger s = ModEntities.STRANGER.get().create(level);
        if (s == null) return null;
        s.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, facing.toYRot(), 0f);
        s.masked(true);
        s.watch(p, ticks);
        level.addFreshEntity(s);
        return s;
    }

    /** Outside the door, on the walkway: the one who talks through it, and comes through it if it gives. */
    Stranger at;

    void atDoor() {
        BlockPos outside = door.relative(out);
        // Back round from the window.
        if (at != null && at.isAlive()) {
            at.moveTo(outside.getX() + 0.5, outside.getY(), outside.getZ() + 0.5, out.getOpposite().toYRot(), 0f);
            at.stand(Integer.MAX_VALUE);
            return;
        }
        List<ServerPlayer> in = inCab();
        if (in.isEmpty()) return;
        at = masked(in.get(0), outside, out.getOpposite(), GONE);
        // Through the door's window he may be seen: it does not send him off.
        if (at != null) at.close(Integer.MAX_VALUE);
    }

    void say(String line) {
        if (at != null && at.isAlive()) at.say(line);
    }

    String pick(String... lines) {
        return lines[level.random.nextInt(lines.length)];
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

    /** The player nearest the lookout and near enough to it, out of its cab; or null. */
    ServerPlayer outside() {
        Vec3 middle = Vec3.atBottomCenterOf(foot);
        ServerPlayer best = null;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive() || p.position().distanceTo(middle) > HUNT_REACH) continue;
            if (best == null || p.distanceToSqr(middle) < best.distanceToSqr(middle)) best = p;
        }
        return best;
    }

    /** Turned from the door to someone out in the night: down or up the stair first if that is where they are. */
    void hunt(ServerPlayer p) {
        hunted = p;
        huntTicks = 0;
        approaching = false;
        at.hunt(p, between(at.getY(), p.getY()));
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("VISIT at {}: no one in, he goes for {}", foot.toShortString(), p.getScoreboardName());
    }

    /** The places on the stair between one height and another, in the order walked; none where they are level. */
    List<BlockPos> between(double from, double to) {
        List<BlockPos> way = new ArrayList<>();
        if (Math.abs(from - to) < 4 || !com.jeladastudios.lostsignal.tower.Stair.walkable(level, foot, out)) return way;
        if (to < from) {
            for (BlockPos p : com.jeladastudios.lostsignal.tower.Stair.down(foot, out)) if (p.getY() < from - 1 && p.getY() >= to - 2) way.add(p);
        } else {
            for (BlockPos p : com.jeladastudios.lostsignal.tower.Stair.up(foot, out)) if (p.getY() > from + 1 && p.getY() <= to + 1) way.add(p);
        }
        return way;
    }

    /** After them: until he has them, they get back behind the door, or they are gone from round the lookout. */
    boolean hunting() {
        if (at == null || !at.isAlive()) return false;
        huntTicks++;
        // Back in the cab: to the door again, and the night goes on there.
        if (!inCab().isEmpty()) {
            hunted = null;
            List<BlockPos> back = new ArrayList<>();
            for (BlockPos p : com.jeladastudios.lostsignal.tower.Stair.up(foot, out)) if (p.getY() > at.getY() - 2) back.add(p);
            at.walkTo(back, false, 0);
            approaching = true;
            approachTicks = 0;
            t = 0;
            return true;
        }
        if (!hunted.isAlive() || hunted.level() != level || hunted.isSpectator()
                || hunted.position().distanceTo(Vec3.atBottomCenterOf(foot)) > HUNT_REACH * 2 || huntTicks > HUNTS) {
            // Got away: he goes back into the dark.
            at.leave(hunted);
            if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("VISIT at {}: {} got away", foot.toShortString(), hunted.getScoreboardName());
            return false;
        }
        if (at.distanceToSqr(hunted) < 1.8 * 1.8) {
            strike(hunted);
            return false;
        }
        return true;
    }

    /** Caught out in the night: as the door giving would have been, and then he is not there. */
    void strike(ServerPlayer p) {
        broke = true;
        at.say("S_DOOR_IN");
        p.hurt(level.damageSources().magic(), 10f + 4f * com.jeladastudios.lostsignal.tower.Towers.at(level, foot).anger);
        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0));
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
        Stranger s = at;
        com.jeladastudios.lostsignal.tower.Speech.after(level, 30, () -> {
            if (!s.isAlive()) return;
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, s.getX(), s.getY() + 1, s.getZ(), 30, 0.3, 0.6, 0.3, 0.01);
            s.discard();
        });
        settle();
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("VISIT at {}: caught {} outside", foot.toShortString(), p.getScoreboardName());
    }

    /** The keepers of this lookout in its cab now. */
    List<com.jeladastudios.lostsignal.entity.Keeper> keepersIn() {
        long key = com.jeladastudios.lostsignal.tower.Towers.at(level, foot).key();
        return level.getEntitiesOfClass(com.jeladastudios.lostsignal.entity.Keeper.class,
                new net.minecraft.world.phys.AABB(door).inflate(5, 3, 5), k -> k.tower() == key);
    }

    /** The player nearest the lookout, within earshot of it; or null. */
    ServerPlayer nearest() {
        ServerPlayer best = null;
        Vec3 middle = Vec3.atCenterOf(door);
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.position().distanceTo(middle) > 96) continue;
            if (best == null || p.distanceToSqr(middle) < best.distanceToSqr(middle)) best = p;
        }
        return best;
    }

    /**
     * A keeper who did not hide, taken: a scream, cut off; the dark on whoever is in the cab; and in the morning the
     * lookout as one left in a hurry, the door open, the marks by it, what is left of them out on the mountain.
     */
    void take(com.jeladastudios.lostsignal.entity.Keeper k) {
        var tower = com.jeladastudios.lostsignal.tower.Towers.at(level, foot);
        com.jeladastudios.lostsignal.radio.Voice.say(k, net.minecraft.network.chat.Component.literal(tower.keeper), "X_SCREAM_CUT", 48, tower.pitch);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, k.getX(), k.getY() + 1, k.getZ(), 30, 0.3, 0.6, 0.3, 0.01);
        for (ServerPlayer p : inCab()) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200, 0));
        k.discard();
        com.jeladastudios.lostsignal.story.Story.given(level, tower.keeper, "keeper");
        com.jeladastudios.lostsignal.tower.Towers.force(level, tower, "abandoned");
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("VISIT took keeper {} of {}", tower.keeper, tower.number);
    }

    /** Whether the cab's door stands open. */
    boolean doorOpen() {
        BlockState s = level.getBlockState(door);
        return s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.OPEN);
    }

    /** The door left open: a creak, and he is in the doorway, among whoever did not hide; with all hidden, he goes. */
    void walkIn(List<ServerPlayer> in) {
        sound(Vec3.atCenterOf(door), SoundEvents.WOODEN_DOOR_OPEN, 0.5f, 0.55f);
        if (in.stream().allMatch(Hiding::hidden)) {
            broke = true;
            ServerPlayer watched = in.isEmpty() ? nearest() : in.get(0);
            if ((at == null || !at.isAlive()) && watched != null) at = masked(watched, door, out.getOpposite(), GONE);
            say("S_DOOR_LEAVE");
            if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("VISIT walked in at an open door, all hidden");
            return;
        }
        enter(in);
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("VISIT walked in at an open door on {}", in.size());
    }

    /** The door gives, and it is in among whoever did not hide. */
    void breakIn(List<ServerPlayer> in) {
        sound(Vec3.atCenterOf(door), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 1.5f, 0.7f);
        level.destroyBlock(door, false);
        if (level.getBlockState(door.above()).getBlock() instanceof DoorBlock) level.destroyBlock(door.above(), false);
        enter(in);
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("VISIT broke in on {}", in.size());
    }

    /** In the doorway, and on whoever did not hide. */
    void enter(List<ServerPlayer> in) {
        broke = true;
        // In the doorway.
        ServerPlayer watched = in.isEmpty() ? nearest() : in.get(0);
        if ((at == null || !at.isAlive()) && watched != null) at = masked(watched, door, out.getOpposite(), GONE);
        else if (at == null || !at.isAlive()) return;
        else at.moveTo(door.getX() + 0.5, door.getY(), door.getZ() + 0.5, out.getOpposite().toYRot(), 0f);
        say("S_DOOR_IN");
        for (ServerPlayer p : in) {
            if (Hiding.hidden(p)) continue;
            // The worse for each time he was driven off hurt.
            p.hurt(level.damageSources().magic(), 10f + 4f * com.jeladastudios.lostsignal.tower.Towers.at(level, foot).anger);
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0));
            p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
        }
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
        long clock = level.getDayTime(), day = clock % 24000;
        long since = lastClock;
        lastClock = clock;
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
            // Two in the cab, the keeper and a guest they let stay, and he is less likely to try it.
            if (com.jeladastudios.lostsignal.tower.Towers.company(level, tower)) chance *= 0.5f;
            // Driven off hurt before, he comes back sooner.
            var lookout = com.jeladastudios.lostsignal.tower.Towers.at(level, tower);
            chance *= 1 + 0.5f * lookout.anger;
            chance *= com.jeladastudios.lostsignal.finder.Lamps.lit(level, tower) ? LIT : DARK;
            if (lookout.wateredDay >= 0 && night - lookout.wateredDay <= 1) chance *= WATERED;
            if (com.jeladastudios.lostsignal.dev.TestCommands.ON) chance *= 2.5f;
            chance *= com.jeladastudios.lostsignal.Config.stranger(com.jeladastudios.lostsignal.Config.VISITS);
            // By how much of the small hours has gone by since the last look: a second, or many, a keeper asleep.
            float odds = chance * Math.min(2000, Math.max(0, clock - since)) / 4000f;
            if (level.random.nextFloat() >= odds) continue;
            LOOKED.put(tower, night);
            begin(level, tower);
        }
    }

    /** Whether a visit is under way at a lookout. */
    public static boolean at(BlockPos foot) {
        for (Visit v : UNDER_WAY) if (v.foot.equals(foot)) return true;
        return false;
    }

    /** A server stopped: nothing of its kept for the next world. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void forget(net.minecraftforge.event.server.ServerStoppedEvent event) {
        UNDER_WAY.clear();
        lastClock = Long.MAX_VALUE;
        LOOKED.clear();
    }
}
