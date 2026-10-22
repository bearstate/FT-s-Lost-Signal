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
 * The lookouts as the world goes on, each as it is found: empty, kept by someone, left in a hurry, or, with no one posted
 * there, its lamp lit at night. Found as players come near them, and the lookouts round those too, for the radio.
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
    /** The chance each dawn, once two days have gone by since the last, that supplies are sent up to a kept lookout. */
    static final float SUPPLIES = 0.35f;

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
        tower.number = number(foot);
        tower.since = level.getGameTime();
        float roll = random.nextFloat();
        if (roll < FOUND_KEPT) keep(tower, random);
        else if (roll < FOUND_KEPT + FOUND_ABANDONED) tower.state = Tower.State.ABANDONED;
        else if (roll < FOUND_KEPT + FOUND_ABANDONED + FOUND_STRANGER
                && com.jeladastudios.lostsignal.Config.stranger(com.jeladastudios.lostsignal.Config.KEEPS_LOOKOUTS)) tower.state = Tower.State.STRANGER;
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("TOWER {} found at {}: {} {}", tower.number,
                foot.toShortString(), tower.state, tower.keeper);
        return tower;
    }

    /** A number for a lookout found, as the District calls it: from its place, and not one another has round here. */
    int number(BlockPos foot) {
        java.util.Set<Integer> taken = new java.util.HashSet<>();
        for (Tower t : towers.values()) taken.add(t.number);
        int n = 1 + Math.floorMod(foot.getX() * 31 + foot.getZ() * 17, 900);
        for (int i = 0; i < 999 && taken.contains(n); i++) n = n % 999 + 1;
        return n;
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
            case "refit" -> t.fitted = false;
            case "check" -> {
                for (Tower other : around(level, t)) {
                    t.need = Tower.Need.CHECK;
                    t.checkOn = other.key();
                    break;
                }
            }
            case "met" -> t.metStranger = level.getGameTime();
            case "supply" -> t.supplyPending = true;
            case "cross" -> {
                for (Tower other : around(level, t)) {
                    keep(other, level.random);
                    break;
                }
            }
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
        if (!what.equals("coal") && !what.equals("check") && !what.equals("met") && !what.equals("silent") && !what.equals("supply") && !what.equals("cross") && !what.equals("refit")) t.dressed = false;
        towers.setDirty();
    }

    /** The lookouts round one (in the cells about it) found now if they were not, for the radio. */
    public static List<Tower> around(ServerLevel level, Tower tower) {
        List<Tower> found = new ArrayList<>();
        if (tower.near == null) {
            // Not worked out yet: worked out off the server's thread, and none known meanwhile.
            find(level, tower, null);
            return found;
        }
        for (BlockPos foot : tower.near) {
            Tower t = at(level, foot);
            if (t.key() != tower.key()) found.add(t);
        }
        found.sort(java.util.Comparator.comparingDouble(t -> Math.hypot(t.x - tower.x, t.z - tower.z)));
        return found;
    }

    /**
     * The lookouts round one already known, and only those: what the dawn goes by, so that lookouts no one has come
     * near are not found (and theirs round them, and so on) of themselves, day after day.
     */
    List<Tower> knownAround(Tower tower) {
        List<Tower> found = new ArrayList<>();
        if (tower.near == null) return found;
        for (BlockPos foot : tower.near) {
            Tower t = towers.get(Tower.key(foot.getX(), foot.getZ()));
            if (t != null && t.key() != tower.key()) found.add(t);
        }
        return found;
    }

    /** The one thread the lookouts round are worked out on, one lookout at a time. */
    private static final java.util.concurrent.ExecutorService FINDER = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Lost Signal lookout finder");
        t.setDaemon(true);
        return t;
    });
    private static final Map<Long, List<Runnable>> FINDING = new HashMap<>();

    /**
     * The lookouts round one worked out on the finder's thread (the siting takes seconds the first time) and kept
     * with the lookout, so it is done once a world; then, back on the server's thread, whatever was waiting on it.
     */
    public static void find(ServerLevel level, Tower tower, Runnable then) {
        if (tower.near != null) {
            if (then != null) then.run();
            return;
        }
        List<Runnable> waiting = FINDING.get(tower.key());
        if (waiting != null) {
            if (then != null) waiting.add(then);
            return;
        }
        waiting = new ArrayList<>();
        if (then != null) waiting.add(then);
        FINDING.put(tower.key(), waiting);
        BlockPos foot = tower.foot();
        long started = System.nanoTime();
        int gen = Lookouts.generation();
        var server = level.getServer();
        FINDER.submit(() -> {
            List<BlockPos> feet;
            try {
                feet = Lookouts.aroundOffThread(level, foot, 2);
            } catch (RuntimeException e) {
                LostSignal.LOGGER.warn("The lookouts round {} could not be worked out: {}", foot.toShortString(), e.toString());
                feet = List.of();
            }
            List<BlockPos> result = feet;
            Lookouts.back(server, gen, () -> {
                tower.near = result;
                of(level).setDirty();
                List<Runnable> done = FINDING.remove(tower.key());
                if (com.jeladastudios.lostsignal.dev.TestCommands.ON) {
                    LostSignal.LOGGER.info("TOWER {}: {} round, found in {} ms", tower.number, result.size(), (System.nanoTime() - started) / 1_000_000);
                }
                if (done != null) for (Runnable r : done) r.run();
            });
        });
    }

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
                if (foot != null) {
                    Tower here = at(level, foot);
                    Dress.near(level, here, foot);
                    nightfall(level, here, foot, p);
                }
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
     * Nightfall at a kept lookout with a player in its cab: the keeper tells a guest they let stay to lock up, and one
     * they did not let stay that they cannot stay.
     */
    static void nightfall(ServerLevel level, Tower t, BlockPos foot, ServerPlayer p) {
        long time = level.getDayTime() % 24000, day = level.getDayTime() / 24000;
        if (t.state != Tower.State.KEPT || time < 13000 || time > 15000 || t.nightSaid == day || p.getY() < foot.getY() + 18) return;
        t.nightSaid = day;
        boolean guest = p.getUUID().equals(t.guest) && t.guestNight == day;
        for (var k : level.getEntitiesOfClass(com.jeladastudios.lostsignal.entity.Keeper.class, p.getBoundingBox().inflate(10), k -> k.tower() == t.key())) {
            com.jeladastudios.lostsignal.radio.Voice.say(k, net.minecraft.network.chat.Component.literal(t.keeper), guest ? "K_NIGHT" : "K_STAY_NO", 16, t.pitch);
            break;
        }
    }

    /** The stranger driven off from a lookout, hurt: he will be back for it, sooner, and worse. */
    public static void angered(ServerLevel level, BlockPos foot) {
        Tower t = at(level, foot);
        t.anger = Math.min(3, t.anger + 1);
        of(level).setDirty();
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("TOWER {} anger {}", t.number, t.anger);
    }

    /** The spots on a cab's floor with nothing on them, to stand on. */
    public static java.util.List<BlockPos> room(ServerLevel level, BlockPos floor) {
        return Dress.free(level, floor);
    }

    /** Whether a keeper has company tonight: someone they let stay. */
    public static boolean company(ServerLevel level, BlockPos foot) {
        Tower t = of(level).towers.get(Tower.key(foot.getX(), foot.getZ()));
        return t != null && t.state == Tower.State.KEPT && t.guest != null && t.guestNight == level.getDayTime() / 24000;
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
            if (someoneAt(level, tower)) {
                // A guest who stayed the night with its keeper, the keeper more at ease with them for it.
                if (tower.guest != null && tower.guestNight == level.getDayTime() / 24000 - 1) {
                    tower.trust++;
                    tower.guest = null;
                    setDirty();
                }
                // A lookout someone keeps: now and then, every few days, the District sends supplies up.
                if (!tower.supplyPending && level.getGameTime() - tower.lastSupply > 2 * 24_000 && random.nextFloat() < SUPPLIES
                        && com.jeladastudios.lostsignal.Config.on(com.jeladastudios.lostsignal.Config.SUPPLIES)) {
                    tower.supplyPending = true;
                    setDirty();
                }
                continue;
            }
            Tower.State was = tower.state;
            float r = random.nextFloat();
            switch (tower.state) {
                case KEPT -> {
                    if (r < KEPT_TAKEN && com.jeladastudios.lostsignal.Config.stranger(com.jeladastudios.lostsignal.Config.TAKES_KEEPERS)) {
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
                            for (Tower other : knownAround(tower)) {
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
                    } else if (r < EMPTY_KEPT + EMPTY_STRANGER && com.jeladastudios.lostsignal.Config.stranger(com.jeladastudios.lostsignal.Config.KEEPS_LOOKOUTS)) {
                        tower.state = Tower.State.STRANGER;
                        tower.dressed = false;
                    }
                }
                case STRANGER -> {
                    // He moves on; what he left there stays. No longer let keep one, he is gone by the next dawn.
                    if (r < STRANGER_GONE || !com.jeladastudios.lostsignal.Config.stranger(com.jeladastudios.lostsignal.Config.KEEPS_LOOKOUTS)) tower.state = Tower.State.EMPTY;
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
        CompoundTag asked = new CompoundTag();
        for (var e : checking.entrySet()) asked.putLong(e.getKey().toString(), e.getValue());
        tag.put("Checking", asked);
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
        // Two with the same number (an older save numbered them from fewer): all but one of them renumbered.
        java.util.Set<Integer> numbers = new java.util.HashSet<>();
        List<Tower> byKey = new ArrayList<>(t.towers.values());
        byKey.sort(java.util.Comparator.comparingLong(Tower::key));
        for (Tower tower : byKey) {
            if (!numbers.add(tower.number)) {
                tower.number = 0;
                tower.number = t.number(tower.foot());
                numbers.add(tower.number);
            }
        }
        t.lastDawn = tag.getLong("LastDawn");
        CompoundTag asked = tag.getCompound("Checking");
        for (String k : asked.getAllKeys()) {
            try {
                t.checking.put(java.util.UUID.fromString(k), asked.getLong(k));
            } catch (IllegalArgumentException e) {
                // Not a player's: skipped.
            }
        }
        for (Tag e : tag.getList("Notices", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            t.notices.add(new Notice(c.getString("Kind"), c.getLong("Tower"), c.getLong("At")));
        }
        return t;
    }

    @SubscribeEvent
    public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        FINDING.clear();
    }
}
