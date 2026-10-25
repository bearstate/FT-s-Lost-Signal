package com.jeladastudios.lostsignal.tower;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.mission.Missions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Lightning on a lookout in a thunderstorm: a tower on a summit is struck, as the real ones were, through its rod. It
 * warns first: the radio crackles, the rod's tip glows blue and hums, hair stands on end; some seconds later it strikes.
 * On the glass-footed stool the keeper only sees the flash and hears the crack; standing on the floor they are thrown
 * and dazed and hurt, out on the walkway or the stair a little worse; never killed, never left with less than two
 * hearts. A storm strikes a lookout once or twice at most.
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class Lightning {

    private Lightning() {}

    /** The chance each second, in a thunderstorm, that a lookout with someone at it is about to be struck. */
    static final float CHANCE = 1f / 90f;
    /** How many times a storm may strike one lookout, and the least time between, in ticks. */
    static final int MOST = 2, BETWEEN = 20 * 60;
    /** How long it warns before it strikes, in ticks. */
    static final int WARNS = 20 * 7;
    /** What a strike does to someone on the floor of the cab, and outside it, in health; and what it never takes them below. */
    static final float CAB = 6, OUTSIDE = 8, LEAVES = 4;

    /** A lookout in this storm: how often struck, when it may be next, and when the one it is warning of comes. */
    private static final class Storm {
        int strikes;
        long next, strikeAt = -1;
        BlockPos foot;
    }

    private static final Map<Long, Storm> STORMS = new HashMap<>();
    /** Where each lookout's rod is, once looked for. */
    private static final Map<Long, BlockPos> ROD = new HashMap<>();

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        long now = level.getGameTime();
        // The warnings and strikes under way.
        for (Storm s : STORMS.values()) {
            if (s.strikeAt < 0) continue;
            if (now < s.strikeAt) {
                if (now % 5 == 0) warn(level, s.foot, s.strikeAt - now);
                continue;
            }
            s.strikeAt = -1;
            strike(level, s.foot);
        }
        if (now % 20 != 0) return;
        if (!level.isThundering() || !com.jeladastudios.lostsignal.Config.on(com.jeladastudios.lostsignal.Config.LIGHTNING)) {
            // The storm over: the next one starts afresh.
            STORMS.values().removeIf(s -> s.strikeAt < 0);
            return;
        }
        for (ServerPlayer p : level.players()) {
            BlockPos foot = Missions.towerAt(level, p);
            if (foot == null || p.isSpectator()) continue;
            Storm s = STORMS.computeIfAbsent(Tower.key(foot.getX(), foot.getZ()), k -> new Storm());
            s.foot = foot;
            if (s.strikeAt >= 0 || s.strikes >= MOST || now < s.next || level.random.nextFloat() >= CHANCE) continue;
            begin(level, s, now);
        }
    }

    /** A strike coming: its warning begun, and the strike set some seconds off. */
    static void begin(ServerLevel level, Storm s, long now) {
        s.strikes++;
        s.next = now + WARNS + BETWEEN;
        s.strikeAt = now + WARNS;
        for (ServerPlayer p : near(level, s.foot)) p.displayClientMessage(Component.translatable("lightning.fts_lostsignal.warning"), true);
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("LIGHTNING coming at {}", s.foot.toShortString());
    }

    /** For trying: the lookout a player is at struck after its warning, storm or no storm. */
    public static boolean force(ServerLevel level, ServerPlayer p) {
        BlockPos foot = Missions.towerAt(level, p);
        if (foot == null) return false;
        Storm s = STORMS.computeIfAbsent(Tower.key(foot.getX(), foot.getZ()), k -> new Storm());
        s.foot = foot;
        s.strikes = 0;
        begin(level, s, level.getGameTime());
        return true;
    }

    /** The warning: a blue glow and sparks at the rod's tip, a hum rising as it comes, a crackle on the radio. */
    static void warn(ServerLevel level, BlockPos foot, long left) {
        Vec3 tip = Vec3.atCenterOf(rod(level, foot)).add(0, 0.6, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y, tip.z, 4, 0.15, 0.25, 0.15, 0.05);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, tip.x, tip.y, tip.z, 1, 0.05, 0.1, 0.05, 0.0);
        if (left % 20 == 0) {
            float pitch = 0.9f + (WARNS - left) / (float) WARNS * 0.9f;
            level.playSound(null, tip.x, tip.y, tip.z, SoundEvents.BEACON_AMBIENT, SoundSource.WEATHER, 1.6f, pitch);
            level.playSound(null, tip.x, tip.y - 8, tip.z, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.WEATHER, 0.4f, 1.8f);
        }
    }

    /** The strike: the bolt on the rod (a flash and a crack, nothing set alight), and what it does to whoever is there. */
    static void strike(ServerLevel level, BlockPos foot) {
        BlockPos rod = rod(level, foot);
        var bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(Vec3.atBottomCenterOf(rod.above()));
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        level.playSound(null, rod, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 6f, 0.9f);
        Vec3 middle = Vec3.atCenterOf(foot).add(0, 21, 0);
        for (ServerPlayer p : near(level, foot)) {
            if (p.isCreative()) continue;
            boolean inCab = Math.abs(p.getX() - (foot.getX() + 0.5)) < 3.5 && Math.abs(p.getZ() - (foot.getZ() + 0.5)) < 3.5
                    && p.getY() > foot.getY() + 19 && p.getY() < foot.getY() + 25;
            boolean stool = com.jeladastudios.lostsignal.block.StoolBlock.on(p);
            if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("LIGHTNING struck {}: {} in cab {} on stool {} health {}",
                    foot.toShortString(), p.getName().getString(), inCab, stool, p.getHealth());
            if (inCab && stool) continue;
            float hurt = inCab ? CAB : OUTSIDE;
            // Never below two hearts: it hurts, and throws them, but it does not kill.
            float before = p.getHealth();
            hurt = Math.min(hurt, before - LEAVES);
            if (hurt > 0) {
                p.hurt(level.damageSources().lightningBolt(), hurt);
                // Whatever else was on them (a burn, a fall), not this that takes them below two hearts.
                if (p.isAlive() && p.getHealth() < LEAVES) p.setHealth(Math.min(before, LEAVES));
            }
            // Thrown: in the cab any way, out on the walkway or the stair in towards the tower, never off it.
            Vec3 push = inCab ? new Vec3(level.random.nextGaussian(), 0, level.random.nextGaussian()).normalize().scale(0.4)
                    : middle.subtract(p.position()).multiply(1, 0, 1).normalize().scale(0.6);
            p.push(push.x, 0.25, push.z);
            p.hurtMarked = true;
            p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1, false, false));
            p.displayClientMessage(Component.translatable("lightning.fts_lostsignal.struck"), true);
        }
    }

    /** Whoever is at a lookout: in the cab, on its walkway and stair, about its foot. */
    static java.util.List<ServerPlayer> near(ServerLevel level, BlockPos foot) {
        java.util.List<ServerPlayer> list = new java.util.ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            if (Math.hypot(p.getX() - foot.getX() - 0.5, p.getZ() - foot.getZ() - 0.5) < 8 && p.getY() > foot.getY() - 1 && p.getY() < foot.getY() + 32) list.add(p);
        }
        return list;
    }

    /** The lookout's lightning rod, looked for above its cab; its roof's peak where there is none. */
    static BlockPos rod(ServerLevel level, BlockPos foot) {
        long key = Tower.key(foot.getX(), foot.getZ());
        BlockPos known = ROD.get(key);
        if (known != null && level.getBlockState(known).is(Blocks.LIGHTNING_ROD)) return known;
        BlockPos found = null;
        if (level.isLoaded(foot)) {
            for (BlockPos p : BlockPos.betweenClosed(foot.offset(-5, 22, -5), foot.offset(5, 36, 5))) {
                if (level.getBlockState(p).is(Blocks.LIGHTNING_ROD) && (found == null || p.getY() > found.getY())) found = p.immutable();
            }
        }
        if (found == null) found = foot.above(31);
        ROD.put(key, found);
        return found;
    }

    @SubscribeEvent
    public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        STORMS.clear();
        ROD.clear();
    }
}
