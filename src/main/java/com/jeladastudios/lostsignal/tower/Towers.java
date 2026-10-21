package com.jeladastudios.lostsignal.tower;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.mission.Lookouts;
import com.jeladastudios.lostsignal.mission.Missions;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The lookouts as the world goes on, each as it is found: empty, kept by someone, left in a hurry, or -- no one posted
 * there, and its lamp lit at night. Found as players come near them, and the lookouts round those too, for the radio.
 * Each dawn, a few change: a keeper comes to an empty one, or one is not heard from again; he moves into one no one
 * keeps, or moves on.
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class Towers extends SavedData {

    /** How a lookout is first found: empty, kept, left, his. */
    static final float FOUND_KEPT = 0.35f, FOUND_ABANDONED = 0.12f, FOUND_STRANGER = 0.08f;
    /** What may change at dawn, each day, at each. */
    static final float KEPT_TAKEN = 0.04f, EMPTY_KEPT = 0.06f, EMPTY_STRANGER = 0.03f, STRANGER_GONE = 0.15f, ABANDONED_CLEARED = 0.08f;
    static final float NEEDS_COAL = 0.15f, NEEDS_CHECK = 0.3f;

    static final String[] NAMES = {"Maggie", "Tom", "Ruth", "Eli", "Nora", "Walt", "June", "Hal", "Bea", "Cal", "Iris", "Dale",
            "Gus", "Pearl", "Vern", "Ada"};

    final Map<Long, Tower> towers = new HashMap<>();
    /** The day last looked at for its changes. */
    long lastDawn = -1;
    /** What the District has to tell the keepers round a lookout: by kind, the lookout's place, and when. */
    final List<Notice> notices = new ArrayList<>();

    public record Notice(String kind, long tower, long at) {}

    /** The lookout each player has been asked to look in on, by its place. */
    final Map<java.util.UUID, Long> checking = new HashMap<>();

    public static Towers of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Towers::load, Towers::new, "fts_lostsignal_towers");
    }

    /** The lookout at a foot, found now if it was not before. */
    public static Tower at(ServerLevel level, BlockPos foot) {
        Towers t = of(level);
        Tower tower = t.towers.get(Tower.key(foot.getX(), foot.getZ()));
        if (tower == null) {
            tower = t.found(level, foot);
            t.towers.put(tower.key(), tower);
            t.setDirty();
        }
        return tower;
    }

    /** The lookout known at a place, or null. */
    public static Tower known(ServerLevel level, long key) {
        return of(level).towers.get(key);
    }

    /** A lookout found: as it is, by chance; and who keeps it, if anyone. */
    Tower found(ServerLevel level, BlockPos foot) {
        RandomSource random = level.random;
        Tower tower = new Tower(foot.getX(), foot.getY(), foot.getZ());
        tower.number = 1 + Math.floorMod(foot.getX() * 31 + foot.getZ() * 17, 40);
        tower.since = level.getGameTime();
        float roll = random.nextFloat();
        if (roll < FOUND_KEPT) keep(tower, random);
        else if (roll < FOUND_KEPT + FOUND_ABANDONED) tower.state = Tower.State.ABANDONED;
        else if (roll < FOUND_KEPT + FOUND_ABANDONED + FOUND_STRANGER) tower.state = Tower.State.STRANGER;
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("TOWER {} found at {}: {} {}", tower.number,
                foot.toShortString(), tower.state, tower.keeper);
        return tower;
    }

    /** Someone posted there. */
    static void keep(Tower tower, RandomSource random) {
        tower.state = Tower.State.KEPT;
        tower.keeper = NAMES[random.nextInt(NAMES.length)];
        tower.temper = Tower.Temper.values()[random.nextInt(Tower.Temper.values().length)];
        tower.pitch = 0.92f + random.nextFloat() * 0.18f;
        tower.kit = random.nextInt(3);
        tower.trust = 0;
        tower.need = Tower.Need.NONE;
        tower.dressed = false;
    }

    /**
     * For trying: a lookout made kept, empty, abandoned or his; or "coal" or "check", its keeper needing that; or
     * "silent", a neighbour of it gone quiet for the District to tell of; or "dawn", the night's changes now.
     */
    public static void force(ServerLevel level, Tower t, String what) {
        Towers towers = of(level);
        switch (what) {
            case "kept" -> keep(t, level.random);
            case "empty" -> t.state = Tower.State.EMPTY;
            case "abandoned" -> {
                t.state = Tower.State.ABANDONED;
                t.remainsPending = true;
            }
            case "stranger" -> t.state = Tower.State.STRANGER;
            case "coal" -> t.need = Tower.Need.COAL;
            case "check" -> {
                for (Tower other : around(level, t)) {
                    t.need = Tower.Need.CHECK;
                    t.checkOn = other.key();
                    break;
                }
            }
            case "met" -> t.metStranger = level.getGameTime();
            case "silent" -> {
                for (Tower other : around(level, t)) {
                    other.state = Tower.State.ABANDONED;
                    other.remainsPending = true;
                    other.dressed = false;
                    notice(level, "silent", other);
                    break;
                }
            }
            case "dawn" -> towers.dawn(level);
            default -> {
            }
        }
        if (!what.equals("coal") && !what.equals("check") && !what.equals("met") && !what.equals("silent")) t.dressed = false;
        towers.setDirty();
    }

    /** The lookouts round one -- in the cells about it -- found now if they were not, for the radio. */
    public static List<Tower> around(ServerLevel level, Tower tower) {
        List<Tower> found = new ArrayList<>();
        for (BlockPos foot : NEAR.computeIfAbsent(tower.key(), k -> Lookouts.around(level, tower.foot(), 2))) {
            Tower t = at(level, foot);
            if (t.key() != tower.key()) found.add(t);
        }
        found.sort(java.util.Comparator.comparingDouble(t -> Math.hypot(t.x - tower.x, t.z - tower.z)));
        return found;
    }

    /** The lookouts round each, as found: worked out once a run, the siting being the same each time. */
    private static final Map<Long, List<BlockPos>> NEAR = new HashMap<>();

    /** Something the District will tell the keepers round a lookout when next called. */
    public static void notice(ServerLevel level, String kind, Tower tower) {
        Towers t = of(level);
        t.notices.add(new Notice(kind, tower.key(), level.getGameTime()));
        if (t.notices.size() > 32) t.notices.remove(0);
        t.setDirty();
    }

    /** The newest notice about the lookouts round one, not older than two days; taken, so it is told once. */
    public static Notice takeNotice(ServerLevel level, Tower tower) {
        Towers t = of(level);
        java.util.Set<Long> round = new java.util.HashSet<>();
        round.add(tower.key());
        for (Tower n : around(level, tower)) round.add(n.key());
        for (int i = t.notices.size() - 1; i >= 0; i--) {
            Notice n = t.notices.get(i);
            if (round.contains(n.tower()) && level.getGameTime() - n.at() < 48_000) {
                t.notices.remove(i);
                t.setDirty();
                return n;
            }
        }
        return null;
    }

    // === The days ===============================================================

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        Towers t = of(level);
        long now = level.getGameTime();
        long day = level.getDayTime() / 24000, time = level.getDayTime() % 24000;
        if (now % 100 == 13) {
            for (ServerPlayer p : level.players()) {
                BlockPos foot = Missions.towerAt(level, p);
                if (foot != null) Dress.near(level, at(level, foot), foot);
                lookedIn(level, t, p);
            }
            // The keepers and the dressing of the lookouts round the players, when their ground is loaded.
            for (Tower tower : t.towers.values()) Dress.ifLoaded(level, tower);
        }
        if (time >= 0 && time < 400 && t.lastDawn != day) {
            t.lastDawn = day;
            t.setDirty();
            t.dawn(level);
        }
        // His lamp in the lookouts he is in, at night: seen across the valley from the lookouts round.
        if (now % 4 == 0 && (time > 13000 && time < 23000)) {
            for (Tower tower : t.towers.values()) {
                if (tower.state != Tower.State.STRANGER) continue;
                Vec3 at = tower.cab();
                for (ServerPlayer p : level.players()) {
                    double d = p.position().distanceTo(at);
                    if (d < 48 || d > 512) continue;
                    level.sendParticles(p, com.jeladastudios.lostsignal.signal.ModParticles.LAMP.get(), true, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    /**
     * A player asked to look in on a lookout, come there: what they found is the District's to hear, and the keeper who
     * asked it of them is grateful.
     */
    static void lookedIn(ServerLevel level, Towers t, ServerPlayer p) {
        Long key = t.checking.get(p.getUUID());
        if (key == null) return;
        Tower there = t.towers.get(key);
        if (there == null) {
            t.checking.remove(p.getUUID());
            return;
        }
        if (Math.hypot(p.getX() - there.x, p.getZ() - there.z) > 24) return;
        t.checking.remove(p.getUUID());
        if (there.state != Tower.State.KEPT) notice(level, "checked", there);
        for (Tower asked : t.towers.values()) {
            if (asked.need == Tower.Need.CHECK && asked.checkOn == key) {
                asked.need = Tower.Need.NONE;
                asked.trust++;
            }
        }
        t.setDirty();
        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("tower.fts_lostsignal.looked_in", there.number,
                net.minecraft.network.chat.Component.translatable("tower.fts_lostsignal.state." + there.state.name().toLowerCase(java.util.Locale.ROOT))), true);
    }

    /** Dawn: what changed in the night, at each lookout no one is at. */
    void dawn(ServerLevel level) {
        RandomSource random = level.random;
        for (Tower tower : new ArrayList<>(towers.values())) {
            if (someoneAt(level, tower)) continue;
            Tower.State was = tower.state;
            float r = random.nextFloat();
            switch (tower.state) {
                case KEPT -> {
                    if (r < KEPT_TAKEN) {
                        // Not heard from again.
                        tower.state = Tower.State.ABANDONED;
                        tower.remainsPending = true;
                        tower.dressed = false;
                        notice(level, "silent", tower);
                    } else {
                        // Now and then they see him: on the trail below, at the foot of the stair.
                        if (random.nextFloat() < 0.1f) tower.metStranger = level.getGameTime();
                    }
                    if (tower.state == Tower.State.KEPT && tower.need == Tower.Need.NONE) {
                        float n = random.nextFloat();
                        if (n < NEEDS_COAL) tower.need = Tower.Need.COAL;
                        else if (n < NEEDS_COAL + NEEDS_CHECK) {
                            // A neighbour gone quiet, or with a light in it no one should have lit.
                            for (Tower other : around(level, tower)) {
                                if (other.state == Tower.State.ABANDONED || other.state == Tower.State.STRANGER) {
                                    tower.need = Tower.Need.CHECK;
                                    tower.checkOn = other.key();
                                    break;
                                }
                            }
                        }
                    }
                }
                case EMPTY -> {
                    if (r < EMPTY_KEPT) {
                        keep(tower, random);
                        notice(level, "new_keeper", tower);
                    } else if (r < EMPTY_KEPT + EMPTY_STRANGER) {
                        tower.state = Tower.State.STRANGER;
                        tower.dressed = false;
                    }
                }
                case STRANGER -> {
                    // He moves on; what he left there stays.
                    if (r < STRANGER_GONE) tower.state = Tower.State.EMPTY;
                }
                case ABANDONED -> {
                    if (r < ABANDONED_CLEARED) tower.state = Tower.State.EMPTY;
                }
            }
            if (tower.state != was) {
                tower.since = level.getGameTime();
                setDirty();
                if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("TOWER {}: {} -> {}", tower.number, was, tower.state);
            }
        }
        setDirty();
    }

    /** Whether a player is at a lookout now: within its reach. */
    static boolean someoneAt(ServerLevel level, Tower tower) {
        for (ServerPlayer p : level.players()) {
            if (Math.hypot(p.getX() - tower.x, p.getZ() - tower.z) < 96) return true;
        }
        return false;
    }

    // === Saving =================================================================

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Tower tower : towers.values()) list.add(tower.save());
        tag.put("Towers", list);
        tag.putLong("LastDawn", lastDawn);
        ListTag ns = new ListTag();
        for (Notice n : notices) {
            CompoundTag c = new CompoundTag();
            c.putString("Kind", n.kind());
            c.putLong("Tower", n.tower());
            c.putLong("At", n.at());
            ns.add(c);
        }
        tag.put("Notices", ns);
        return tag;
    }

    static Towers load(CompoundTag tag) {
        Towers t = new Towers();
        for (Tag e : tag.getList("Towers", Tag.TAG_COMPOUND)) {
            Tower tower = Tower.load((CompoundTag) e);
            t.towers.put(tower.key(), tower);
        }
        t.lastDawn = tag.getLong("LastDawn");
        for (Tag e : tag.getList("Notices", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            t.notices.add(new Notice(c.getString("Kind"), c.getLong("Tower"), c.getLong("At")));
        }
        return t;
    }

    @SubscribeEvent
    public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        NEAR.clear();
    }
}
