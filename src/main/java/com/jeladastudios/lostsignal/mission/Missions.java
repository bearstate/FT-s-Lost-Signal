package com.jeladastudios.lostsignal.mission;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.entity.Hiker;
import com.jeladastudios.lostsignal.entity.ModEntities;
import com.jeladastudios.lostsignal.rescue.Lines;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The nights at the lookouts. Each lookout with a keeper in it -- a player within its reach -- may have, of an evening,
 * a hiker lost somewhere within a few hundred blocks, calling on the rescue frequency until someone answers. One night
 * at a lookout at a time, and none for a day or two after; what happens belongs to the lookout, not to each player in
 * it, so a lookout kept by three players has one lost hiker, not three.
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class Missions extends SavedData {

    /** How near a lookout's foot a player keeps it. */
    static final double KEEPS = 64;
    /** Where a hiker is lost: this far from the lookout. */
    static final int NEAR = 180, FAR = 300;
    /** The quiet after a night at a lookout, in ticks: a day and a half. */
    static final long REST = 36_000;
    /** How often a hiker no one has answered calls again, in ticks. */
    static final long CALL_AGAIN = 1_800;
    /** The chance, once an evening at a kept lookout, that someone is lost. */
    static final float CHANCE = 0.4f;
    /**
     * The chance something comes for a hiker still out in the small hours: some always; more if no one ever answered
     * them; more again if they are walking in the dark. And how often there is a body to find after.
     */
    static final float TAKEN_BASE = 0.08f, TAKEN_UNANSWERED = 0.3f, TAKEN_WALKING = 0.2f, WITH_BODY = 0.67f;

    final Map<BlockPos, Mission> missions = new HashMap<>();
    final Map<BlockPos, Long> lastNight = new HashMap<>();

    static Missions of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Missions::load, Missions::new, "fts_lostsignal_missions");
    }

    // === The lookouts players keep ================================================

    private record Seen(BlockPos tower, long until) {}

    private static final Map<UUID, Seen> SEEN = new HashMap<>();

    /** The lookout a player is at, if any: found by Feel the Nature's own siting, and checked for its cab's floor. */
    public static BlockPos towerAt(ServerLevel level, ServerPlayer p) {
        long now = level.getGameTime();
        Seen seen = SEEN.get(p.getUUID());
        if (seen == null || seen.until < now) {
            BlockPos tower = null;
            try {
                BlockPos foot = Lookouts.near(level, p.blockPosition(), 1);
                if (foot != null && level.hasChunkAt(foot)) tower = standing(level, foot);
            } catch (LinkageError e) {
                tower = null;
            }
            seen = new Seen(tower, now + 200);
            SEEN.put(p.getUUID(), seen);
        }
        if (seen.tower == null || p.position().distanceTo(Vec3.atCenterOf(seen.tower)) > KEEPS) return null;
        return seen.tower;
    }

    /**
     * The lookout standing at a foot the siting gives, its foot set at the height the world built it: its cab floor
     * looked for up the middle -- the ground model may lie a few blocks off the ground -- twenty blocks over its foot.
     * Null where no lookout stands there.
     */
    public static BlockPos standing(ServerLevel level, BlockPos foot) {
        for (int dy = 40; dy >= -12; dy--) {
            BlockPos p = foot.above(dy);
            if (level.getBlockState(p).is(BlockTags.PLANKS) && level.getBlockState(p.above()).is(net.minecraft.world.level.block.Blocks.CARTOGRAPHY_TABLE)) {
                return p.below(20);
            }
        }
        return null;
    }

    // === The nights =============================================================

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (level.getGameTime() % 20 != 0) return;
        Missions m = of(level);
        long day = level.getDayTime() % 24000;
        // The kept lookouts: a night may begin at one of them in the evening.
        for (ServerPlayer p : level.players()) {
            BlockPos tower = towerAt(level, p);
            if (tower == null || m.missions.containsKey(tower)) continue;
            if (day < 13000 || day > 13100) continue;
            if (level.getGameTime() - m.lastNight.getOrDefault(tower, -REST) < REST) continue;
            m.lastNight.put(tower, level.getGameTime());
            m.setDirty();
            if (level.random.nextFloat() < CHANCE * (com.jeladastudios.lostsignal.dev.TestCommands.ON ? 2.5f : 1f)) begin(level, tower);
        }
        for (Mission mission : new java.util.ArrayList<>(m.missions.values())) m.run(level, mission, day);
    }

    /** A hiker lost within a few hundred blocks of a lookout, from tonight. Null where no spot is found. */
    public static Mission begin(ServerLevel level, BlockPos tower) {
        Missions m = of(level);
        if (m.missions.containsKey(tower)) return m.missions.get(tower);
        for (int tries = 0; tries < 16; tries++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double d = NEAR + level.random.nextDouble() * (FAR - NEAR);
            int x = (int) Math.floor(tower.getX() + Math.cos(a) * d), z = (int) Math.floor(tower.getZ() + Math.sin(a) * d);
            level.getChunk(x >> 4, z >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos at = new BlockPos(x, y, z);
            if (!level.getFluidState(at.below()).isEmpty() || !level.getBlockState(at.below()).isSolid()) continue;
            Hiker h = ModEntities.HIKER.get().create(level);
            if (h == null) return null;
            h.kit(level.random.nextInt(Hiker.KITS));
            h.moveTo(x + 0.5, y, z + 0.5, level.random.nextFloat() * 360f, 0f);
            h.onMission(tower);
            level.addFreshEntity(h);
            keep(level, h.blockPosition());
            Mission mission = new Mission(tower);
            mission.hiker = h.getUUID();
            mission.started = level.getGameTime();
            mission.at = h.blockPosition();
            mission.lastCall = level.getGameTime() - CALL_AGAIN + 200;
            m.missions.put(tower, mission);
            m.setDirty();
            if (com.jeladastudios.lostsignal.dev.TestCommands.ON) {
                LostSignal.LOGGER.info("MISSION begun at lookout {} : hiker at {} {} {} ({} blocks)", tower.toShortString(), x, y, z, (int) d);
            }
            return mission;
        }
        return null;
    }

    void run(ServerLevel level, Mission mission, long day) {
        long now = level.getGameTime();
        if (mission.stage == Mission.Stage.TAKEN) {
            // The morning after: the District sends the keeper to where they were last heard from.
            if (!mission.told && day > 0 && day < 4000) {
                mission.told = true;
                setDirty();
                Lines.say(level, Vec3.atCenterOf(mission.tower), Lines.Speaker.DISTRICT, "D_GO_LOOK", 1f);
                where(level, mission);
            }
            // Never found: after three days the night is closed; what is out there stays a while yet.
            if (now - mission.takenAt > 3 * 24_000L) end(level, mission, "taken");
            return;
        }
        Hiker h = mission.hiker == null ? null : level.getEntity(mission.hiker) instanceof Hiker found ? found : null;
        if (h == null && mission.at != null) keep(level, mission.at);
        if (h == null && mission.stage == Mission.Stage.FOUND && mission.leaving) {
            // Gone home.
            end(level, mission, mission.ending);
            return;
        }
        if (h == null) {
            // Not loaded yet, or gone: a night without its hiker ends at dawn.
            if (day > 23500 || day < 12000) end(level, mission, "lost");
            return;
        }
        if (!h.blockPosition().equals(mission.at)) {
            mission.at = h.blockPosition();
            setDirty();
        }
        switch (mission.stage) {
            case CALLING -> {
                if (now - mission.lastCall >= CALL_AGAIN) {
                    mission.lastCall = now;
                    setDirty();
                    String[] calls = {"H_CALL_01", "H_CALL_02", "H_CALL_03", "H_CALL_04"};
                    Lines.say(level, h.getEyePosition(), Lines.Speaker.HIKER, calls[level.random.nextInt(calls.length)], 0.15f);
                }
            }
            case IN_CONTACT -> {
                // Come in to the lookout, or met on the mountain by a player.
                if (h.position().distanceTo(Vec3.atCenterOf(mission.tower)) < 14) {
                    Lines.say(level, h.getEyePosition(), Lines.Speaker.HIKER, "H_AT_TOWER", 0.15f);
                    found(level, mission, h, "came_in");
                } else {
                    for (ServerPlayer p : level.players()) {
                        if (p.distanceTo(h) < 5 && !p.isSpectator()) {
                            Lines.say(level, h.getEyePosition(), Lines.Speaker.HIKER, "H_SEE_YOU", 0.15f);
                            found(level, mission, h, "found");
                            break;
                        }
                    }
                }
            }
            case FOUND -> {
                // The morning after, they thank the keeper and go home: down off the mountain, away from the lookout.
                if (day > 0 && day < 6000 && !mission.leaving) {
                    mission.leaving = true;
                    setDirty();
                    Lines.say(level, h.getEyePosition(), Lines.Speaker.HIKER, level.random.nextBoolean() ? "H_THANKS_01" : "H_ON_TRAIL", 0.15f);
                    // From the lookout if they came in to it; from where they were found if met out on the mountain.
                    BlockPos from = "came_in".equals(mission.ending) ? mission.tower : h.blockPosition();
                    Vec3 down = com.jeladastudios.lostsignal.rescue.Sight.downhill(level, from);
                    if (down == null) {
                        double a = level.random.nextDouble() * Math.PI * 2;
                        down = new Vec3(Math.cos(a), 0, Math.sin(a));
                    }
                    BlockPos away = com.jeladastudios.lostsignal.rescue.Rescue.ground(level,
                            from.getX() + down.x * 110, from.getZ() + down.z * 110);
                    h.leave(away);
                }
            }
            case OVER -> {
            }
        }
        // The small hours, with them still out there: something may come for them. Likelier if no one ever answered,
        // likelier still if they are walking in the dark.
        boolean out = mission.stage == Mission.Stage.CALLING || mission.stage == Mission.Stage.IN_CONTACT;
        if (out && !mission.rolled && day >= 18000 && day < 22000) {
            mission.rolled = true;
            float chance = chance(mission, h);
            mission.takeChance = chance;
            // Not at once: a keeper who has not answered yet has a while to.
            if (level.random.nextFloat() < chance) mission.takeAt = now + 1200 + level.random.nextInt(Math.max(1, (int) (22500 - day) - 1400));
            setDirty();
            if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("MISSION at {}: taken {} (chance {})", mission.tower.toShortString(), mission.takeAt >= 0 ? "at " + mission.takeAt : "not", chance);
        }
        if (out && mission.takeAt >= 0) {
            if (!mission.warned && now >= mission.takeAt - 600) {
                mission.warned = true;
                setDirty();
                Lines.say(level, h.getEyePosition(), Lines.Speaker.HIKER, level.random.nextBoolean() ? "H_FEAR_TREES" : "H_FEAR_STANDING", 0.15f);
            }
            if (now >= mission.takeAt) {
                // As things stand now: answered since, or stopped walking in the dark, the odds are better.
                float current = chance(mission, h);
                if (mission.takeChance > 0 && level.random.nextFloat() >= current / mission.takeChance) {
                    mission.takeAt = -1;
                    setDirty();
                    if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("MISSION at {}: spared", mission.tower.toShortString());
                } else {
                    take(level, mission, h);
                    return;
                }
            }
        }
        // Dawn with the hiker still out there: lost contact.
        if ((mission.stage == Mission.Stage.CALLING || mission.stage == Mission.Stage.IN_CONTACT) && day > 23000) {
            Lines.say(level, Vec3.atCenterOf(mission.tower), Lines.Speaker.DISTRICT, "D_LOST_CONTACT", 1f);
            h.discard();
            end(level, mission, "lost");
        }
    }

    /** The chance something comes for a hiker out in the small hours, as things stand with them. */
    static float chance(Mission mission, Hiker h) {
        return TAKEN_BASE + (mission.answered ? 0 : TAKEN_UNANSWERED) + (h.state() == Hiker.State.GOING ? TAKEN_WALKING : 0);
    }

    /**
     * Taken: the last thing on the radio is them running, and a scream cut short. Where they were, the morning will
     * find what is left.
     */
    void take(ServerLevel level, Mission mission, Hiker h) {
        Lines.say(level, h.getEyePosition(), Lines.Speaker.HIKER, "H_FEAR_RUN", 0.15f);
        Lines.say(level, h.getEyePosition(), Lines.Speaker.HIKER, "X_SCREAM_CUT", 0.15f);
        String name = com.jeladastudios.lostsignal.remains.Journal.name(h.getUUID().getLeastSignificantBits());
        int journal = mission.answered ? com.jeladastudios.lostsignal.remains.Journal.ANSWERED : com.jeladastudios.lostsignal.remains.Journal.UNANSWERED;
        BlockPos fire = h.leaveCamp();
        var made = com.jeladastudios.lostsignal.remains.Scene.make(level, h.blockPosition(), fire, h.kit(), name, mission.tower, journal,
                level.random.nextFloat() < WITH_BODY, level.random);
        h.discard();
        mission.stage = Mission.Stage.TAKEN;
        mission.takenAt = level.getGameTime();
        mission.scene = made.camp();
        setDirty();
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("MISSION at {}: taken at {}", mission.tower.toShortString(), made.camp().toShortString());
    }

    /** Where to look: told to the keepers at the lookout as the District's note, a compass point and a distance. */
    void where(ServerLevel level, Mission mission) {
        if (mission.scene == null) return;
        int dx = mission.scene.getX() - mission.tower.getX(), dz = mission.scene.getZ() - mission.tower.getZ();
        int bearing = (int) Math.round(Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360;
        String[] points = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
        String point = points[Math.floorMod((int) Math.round(bearing / 45.0), 8)];
        int far = (int) Math.round(Math.hypot(dx, dz) / 10.0) * 10;
        var text = net.minecraft.network.chat.Component.translatable("radio.fts_lostsignal.note.where",
                net.minecraft.network.chat.Component.translatable("radio.fts_lostsignal.point." + point), far, bearing)
                .withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC);
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceTo(Vec3.atCenterOf(mission.tower)) > KEEPS) continue;
            if (com.jeladastudios.lostsignal.rescue.Stations.poweredNear(level, p.blockPosition(), 8) == null) continue;
            p.sendSystemMessage(text);
        }
    }

    /** The keeper found the body: the night is told to the District, and closed. */
    public static void bodyFound(ServerLevel level, BlockPos tower) {
        Missions m = of(level);
        Mission mission = m.missions.get(tower);
        if (mission == null || mission.stage != Mission.Stage.TAKEN) return;
        Lines.say(level, Vec3.atCenterOf(tower), Lines.Speaker.DISTRICT, "D_BODY_FOUND", 1f);
        m.end(level, mission, "taken_found");
    }

    /** Something come for the hiker of a night under way at a lookout, now: for trying it. */
    public static boolean takeNow(ServerLevel level, BlockPos tower) {
        Missions m = of(level);
        Mission mission = m.missions.get(tower);
        if (mission == null || (mission.stage != Mission.Stage.CALLING && mission.stage != Mission.Stage.IN_CONTACT)) return false;
        mission.rolled = true;
        mission.takeAt = level.getGameTime() + 200;
        mission.warned = false;
        m.setDirty();
        return true;
    }

    /** The ground round a lost hiker kept loaded and alive, so they live on with no player near: renewed every second. */
    static void keep(ServerLevel level, BlockPos at) {
        var cp = new net.minecraft.world.level.ChunkPos(at);
        level.getChunkSource().addRegionTicket(Hiker.KEPT, cp, 3, cp);
    }

    void found(ServerLevel level, Mission mission, Hiker h, String how) {
        mission.stage = Mission.Stage.FOUND;
        mission.ending = how;
        h.rescued(mission.tower);
        setDirty();
        Lines.say(level, Vec3.atCenterOf(mission.tower), Lines.Speaker.DISTRICT, "D_FOUND", 1f);
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("MISSION at {} {}", mission.tower.toShortString(), how);
    }

    void end(ServerLevel level, Mission mission, String how) {
        mission.stage = Mission.Stage.OVER;
        mission.ending = how;
        missions.remove(mission.tower);
        setDirty();
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("MISSION over at {}: {}", mission.tower.toShortString(), how);
    }

    /** The keeper answered: the hiker is in contact. */
    public static void heard(ServerLevel level, Hiker h) {
        Missions m = of(level);
        for (Mission mission : m.missions.values()) {
            if (h.getUUID().equals(mission.hiker) && mission.stage == Mission.Stage.CALLING) {
                mission.stage = Mission.Stage.IN_CONTACT;
                mission.answered = true;
                m.setDirty();
            }
        }
    }

    /** The night under way at a lookout, if any. */
    public static Mission at(ServerLevel level, BlockPos tower) {
        return of(level).missions.get(tower);
    }

    // === Saving =================================================================

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Mission mission : missions.values()) list.add(mission.save());
        tag.put("Missions", list);
        ListTag nights = new ListTag();
        for (var e : lastNight.entrySet()) {
            CompoundTag n = new CompoundTag();
            n.put("Tower", net.minecraft.nbt.NbtUtils.writeBlockPos(e.getKey()));
            n.putLong("At", e.getValue());
            nights.add(n);
        }
        tag.put("Nights", nights);
        return tag;
    }

    static Missions load(CompoundTag tag) {
        Missions m = new Missions();
        for (Tag t : tag.getList("Missions", Tag.TAG_COMPOUND)) {
            Mission mission = Mission.load((CompoundTag) t);
            if (mission.stage != Mission.Stage.OVER) m.missions.put(mission.tower, mission);
        }
        for (Tag t : tag.getList("Nights", Tag.TAG_COMPOUND)) {
            CompoundTag n = (CompoundTag) t;
            m.lastNight.put(net.minecraft.nbt.NbtUtils.readBlockPos(n.getCompound("Tower")), n.getLong("At"));
        }
        return m;
    }
}
