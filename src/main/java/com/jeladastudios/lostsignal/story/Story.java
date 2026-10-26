package com.jeladastudios.lostsignal.story;

import com.jeladastudios.lostsignal.Config;
import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.tower.Tower;
import com.jeladastudios.lostsignal.tower.Towers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * What the story has left in this world: where Hollow Peak stands, the lookout closed in 1979 (its footings on the
 * highest summit some way from the first keeper's lookout), and where the planting is, below the ridge east of it, a
 * quarter mile out, as Arthur Lund's log has it; whether each has been set out yet; and those given in this world, by
 * name, each with a sapling there and a line in the ledger. See HIKAYE.md.
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class Story extends SavedData {

    /** How far from the first keeper's lookout Hollow Peak may stand. */
    static final int HOLLOW_FROM = 900, HOLLOW_TO = 1600;
    /** How far east of it the planting lies: a quarter mile. */
    static final int PLANTING_OUT = 400;
    /** How near a player must come for a place to be set out. */
    static final double NEAR = 80;
    /** No nearer one of Feel the Nature's own lookouts than this may Hollow Peak stand. */
    static final double APART = 300;

    BlockPos hollow, planting;
    boolean hollowBuilt, plantingBuilt;
    /** Those given in this world, in order: name, what they were (hiker or keeper) and the season, as "name|kind|season". */
    final List<String> given = new ArrayList<>();
    /** How many of them have their sapling at the planting. */
    int planted;
    /** Whether the places are being worked out on the siting's thread now. */
    transient boolean siting;

    public static Story of(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(Story::load, Story::new, "fts_lostsignal_story");
    }

    /** Where Hollow Peak stands, its footings' ground; null until worked out. */
    public static BlockPos hollow(ServerLevel level) {
        return of(level).hollow;
    }

    /** Someone given to the mountain: a line in the ledger, and a sapling at the planting when it is next seen. */
    public static void given(ServerLevel level, String name, String kind) {
        if (name == null || name.isEmpty() || name.equals("-")) return;
        Story s = of(level);
        s.given.add(name + "|" + kind + "|" + com.jeladastudios.lostsignal.desk.Desk.season(level));
        s.setDirty();
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("STORY given {} ({})", name, kind);
    }

    /** The names of those given in this world, by the season they were given in (the first, where it was not kept). */
    java.util.SortedMap<Integer, List<String>> bySeason() {
        java.util.SortedMap<Integer, List<String>> by = new java.util.TreeMap<>();
        for (String g : given) {
            String[] p = g.split(java.util.regex.Pattern.quote("|"));
            int season = 1;
            try {
                if (p.length > 2) season = Integer.parseInt(p[2]);
            } catch (NumberFormatException e) {
                // An old entry: the first season's.
            }
            by.computeIfAbsent(season, k -> new ArrayList<>()).add(p[0]);
        }
        return by;
    }

    /** The names of those given in a season of this world, in order. */
    public static List<String> givenIn(ServerLevel level, int season) {
        return of(level).bySeason().getOrDefault(season, List.of());
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (level.getGameTime() % 100 != 73 || !Config.on(Config.STRANGER)) return;
        Story s = of(level);
        if (s.hollow == null) {
            if (!s.siting) site(level, s);
            return;
        }
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            // Each set out where the ground will have it: off lava, fire and water, a few dozen blocks over if need be.
            if (!s.hollowBuilt && near(p, s.hollow) && Ruin.loaded(level, s.hollow)) {
                BlockPos spot = Ruin.near(level, s.hollow, 8);
                if (spot != null) {
                    s.hollow = spot;
                    s.hollowBuilt = true;
                    s.setDirty();
                    Ruin.build(level, s.hollow);
                }
            }
            if (s.planting != null && near(p, s.planting) && Ruin.loaded(level, s.planting)) {
                if (!s.plantingBuilt) {
                    BlockPos spot = Ruin.near(level, s.planting, 22);
                    if (spot != null) {
                        s.planting = spot;
                        s.plantingBuilt = true;
                        s.setDirty();
                        Planting.build(level, s);
                    }
                } else if (s.planted < s.given.size()) {
                    Planting.plant(level, s);
                }
            }
        }
    }

    static boolean near(ServerPlayer p, BlockPos at) {
        return Math.hypot(p.getX() - at.getX(), p.getZ() - at.getZ()) < NEAR;
    }

    /**
     * Hollow Peak and the planting worked out, once a world, from the first keeper's lookout (or, with no one posted,
     * the first lookout known): on the siting's own thread, where the ground model reads true.
     */
    static void site(ServerLevel level, Story s) {
        Tower from = com.jeladastudios.lostsignal.desk.Desk.firstPosted(level);
        if (from == null) {
            List<Tower> all = Towers.all(level);
            if (all.isEmpty()) return;
            from = all.get(0);
        }
        BlockPos foot = from.foot();
        s.siting = true;
        com.jeladastudios.lostsignal.mission.Lookouts.offThread(level, () -> Sites.find(level, foot), found -> {
            s.siting = false;
            if (found == null) return;
            s.hollow = found[0];
            s.planting = found[1];
            s.setDirty();
            LostSignal.LOGGER.info("Hollow Peak at {}, the planting at {}", s.hollow.toShortString(), s.planting.toShortString());
        });
    }

    // === Kept with the world ====================================================

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (hollow != null) tag.put("Hollow", NbtUtils.writeBlockPos(hollow));
        if (planting != null) tag.put("Planting", NbtUtils.writeBlockPos(planting));
        tag.putBoolean("HollowBuilt", hollowBuilt);
        tag.putBoolean("PlantingBuilt", plantingBuilt);
        ListTag list = new ListTag();
        for (String g : given) list.add(StringTag.valueOf(g));
        tag.put("Given", list);
        tag.putInt("Planted", planted);
        return tag;
    }

    static Story load(CompoundTag tag) {
        Story s = new Story();
        if (tag.contains("Hollow")) s.hollow = NbtUtils.readBlockPos(tag.getCompound("Hollow"));
        if (tag.contains("Planting")) s.planting = NbtUtils.readBlockPos(tag.getCompound("Planting"));
        s.hollowBuilt = tag.getBoolean("HollowBuilt");
        s.plantingBuilt = tag.getBoolean("PlantingBuilt");
        for (Tag g : tag.getList("Given", Tag.TAG_STRING)) s.given.add(g.getAsString());
        s.planted = tag.getInt("Planted");
        return s;
    }

    // === For trying =============================================================

    /** For trying: the places set out round a player now (the ruin where they stand, the planting a quarter mile east). */
    public static String test(ServerPlayer p, String what) {
        ServerLevel level = p.serverLevel();
        Story s = of(level);
        switch (what) {
            case "where" -> {
                return "hollow " + (s.hollow == null ? "none" : s.hollow.toShortString() + (s.hollowBuilt ? " built" : ""))
                        + " planting " + (s.planting == null ? "none" : s.planting.toShortString() + (s.plantingBuilt ? " built" : ""))
                        + " given " + s.given.size() + " planted " + s.planted;
            }
            case "here" -> {
                s.hollow = p.blockPosition();
                s.planting = p.blockPosition().offset(PLANTING_OUT, 0, 0);
                s.hollowBuilt = s.plantingBuilt = false;
                s.setDirty();
                return "set at " + s.hollow.toShortString();
            }
            case "ruin" -> {
                s.hollow = p.blockPosition().offset(8, 0, 0);
                s.hollowBuilt = true;
                s.setDirty();
                Ruin.build(level, s.hollow);
                return "ruin at " + s.hollow.toShortString();
            }
            case "planting" -> {
                s.planting = p.blockPosition().offset(18, 0, 0);
                s.plantingBuilt = true;
                s.setDirty();
                Planting.build(level, s);
                return "planting at " + s.planting.toShortString();
            }
            case "give" -> {
                given(level, "Hiker " + (s.given.size() + 1), "hiker");
                return "given " + s.given.size();
            }
            case "resite" -> {
                s.hollow = s.planting = null;
                s.hollowBuilt = s.plantingBuilt = false;
                s.setDirty();
                return "resiting";
            }
            default -> {
                return "unknown";
            }
        }
    }
}
