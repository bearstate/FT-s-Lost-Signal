package com.jeladastudios.lostsignal.desk;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.rescue.Lines;
import com.jeladastudios.lostsignal.tower.Fires;
import com.jeladastudios.lostsignal.tower.Tower;
import com.jeladastudios.lostsignal.tower.Towers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The season's last night, the night of the fires (HIKAYE.md). A red flag warning in the morning; from dusk dry
 * lightning all round, and no rain; a big smoke far off in plain view of each lookout someone keeps. Marion is not on
 * the night desk: someone else is, who calls the keeper Lookout, and on the radio, from the foot of the tower, "the
 * District" says to let it go. Called in, the crews go and the campers in its way are brought out, and he comes to the
 * door angry. Let go, he thanks the keeper at dawn and leaves their lookout be, and a camper in its way is not found.
 * The District knows either way: the evaluation, Marion's word in the morning and the next season's first mail go by
 * it. Which is right is never said.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class LastNight {

    private LastNight() {}

    /** How it has gone for a keeper: warned that morning, the fire burning in sight of them, called in, let go. */
    static final int NONE = 0, WARNED = 1, BURNING = 2, REPORTED = 3, LET_GO = 4;
    /** What of it has happened for a keeper, as bits: the night desk's message, the morning's word. */
    static final int DESK_SAID = 1, TOLD = 2;
    /** When, in the day's ticks: the warning, the lightning, the smoke, the night desk, the call, the visit, dawn. */
    static final int WARNING = 500, LIGHTNING = 12500, SMOKE = 13500, NIGHT_DESK = 15000, CALL = 16500, VISIT = 19500, DAWN = 22800;
    /** Who writes in Marion's place. */
    static final String NIGHT_DESK_FROM = "desk.fts_lostsignal.from.nightdesk";

    /** The night's fire at each lookout, by its key; and what has happened there tonight (the call, the visit), as bits. */
    private static final Map<Long, Fires.Big> BIG = new HashMap<>();
    private static final Map<Long, Integer> AT = new HashMap<>();
    static final int CALLED = 1, VISITED = 2;
    /** The camper not found, at each lookout whose fire was let go. */
    private static final Map<Long, String> NAMES = new HashMap<>();
    private static long night = -1;

    /** A keeper's last night as it stands this season: NONE where it has not begun. */
    static int state(Desk desk, Post post) {
        return post.lastNightSeason == desk.season ? post.lastNight : NONE;
    }

    static void set(Desk desk, Post post, int state) {
        if (post.lastNightSeason != desk.season) {
            post.lastNightSeason = desk.season;
            post.lastNightDone = 0;
        }
        post.lastNight = state;
        desk.setDirty();
    }

    /** Whether the last night is under way for a keeper: Marion is not on the desk. */
    static boolean tonight(ServerLevel level, Desk desk, Post post) {
        return Costs.on() && Season.lastDay(level) && state(desk, post) >= WARNED;
    }

    /** Whether a lookout is to be left be tonight but as the last night has it: no visit but the one it sends. */
    public static boolean quiet(ServerLevel level, BlockPos foot) {
        if (!Costs.on() || !Season.lastDay(level)) return false;
        Desk desk = Desk.of(level);
        long key = Tower.key(foot.getX(), foot.getZ());
        for (Post post : desk.posts.values()) if (post.tower == key && state(desk, post) >= BURNING) return true;
        return false;
    }

    /** Every few seconds, on the season's last day: the night as it goes, for each keeper. */
    static void tick(ServerLevel level, Desk desk) {
        if (!Costs.on() || !Season.lastDay(level)) return;
        long day = level.getDayTime() / 24000, time = level.getDayTime() % 24000;
        if (night != day) {
            night = day;
            AT.clear();
            BIG.clear();
            NAMES.clear();
        }
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            Post post = Desk.posted(p);
            if (post == null) continue;
            Tower t = Towers.known(level, post.tower);
            if (t == null) continue;
            boolean at = Math.hypot(p.getX() - t.x, p.getZ() - t.z) < Desk.AT_TOWER;
            int s = state(desk, post);
            if (s == NONE && time >= WARNING) {
                set(desk, post, s = WARNED);
                desk.mail(level, post, Desk.DISTRICT, Mail.Line.of("lastnight.fts_lostsignal.warning.subject"),
                        List.of(Mail.Line.of("lastnight.fts_lostsignal.warning.1"), Mail.Line.of("lastnight.fts_lostsignal.warning.2", post.number)));
            }
            if (s == WARNED && at && time >= SMOKE && time < VISIT) {
                set(desk, post, s = BURNING);
                burn(level, t);
            }
            if (s < BURNING) continue;
            int done = AT.getOrDefault(t.key(), 0);
            if (at && time >= LIGHTNING && time < 18000) lightning(level, t);
            if ((post.lastNightDone & DESK_SAID) == 0 && time >= NIGHT_DESK) {
                post.lastNightDone |= DESK_SAID;
                desk.mail(level, post, NIGHT_DESK_FROM, Mail.Line.of("lastnight.fts_lostsignal.desk.subject"),
                        List.of(Mail.Line.of("lastnight.fts_lostsignal.desk.1"), Mail.Line.of("lastnight.fts_lostsignal.desk.2")));
            }
            // "The District" on the radio, from the foot of the tower: let it go.
            if (s == BURNING && at && time >= CALL && (done & CALLED) == 0) {
                AT.put(t.key(), done |= CALLED);
                Lines.sayAs(level, Vec3.atCenterOf(t.foot()).add(2, 1, 2), Component.translatable("radio.fts_lostsignal.speaker.district"),
                        "D_LAST_LETGO", 1f, 0.97f, 96);
                if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("LAST NIGHT let it go, from the foot of {}", t.number);
            }
            // Called in: he comes to the door.
            if (s == REPORTED && at && time >= VISIT && (done & VISITED) == 0) {
                AT.put(t.key(), done | VISITED);
                com.jeladastudios.lostsignal.visit.Visit.begin(level, t.foot());
            }
            if (time >= DAWN && (post.lastNightDone & TOLD) == 0) {
                post.lastNightDone |= TOLD;
                dawn(level, desk, post, t, s);
            }
        }
    }

    /** The night's fire, far off in sight of a lookout, and another smaller one besides: the country is going up. */
    static void burn(ServerLevel level, Tower t) {
        if (BIG.containsKey(t.key())) return;
        long key = t.key();
        BIG.put(key, Fires.big(level, t.foot(), () -> reported(level, key)));
        Fires.start(level, t.foot());
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("LAST NIGHT burning at {}", t.number);
    }

    /** Dry lightning, now and then, somewhere round the lookout: seen and heard, setting nothing alight. */
    static void lightning(ServerLevel level, Tower t) {
        if (level.random.nextFloat() > 0.45f) return;
        double a = level.random.nextDouble() * Math.PI * 2, d = 90 + level.random.nextDouble() * 80;
        int x = (int) Math.floor(t.x + Math.cos(a) * d), z = (int) Math.floor(t.z + Math.sin(a) * d);
        if (!level.isLoaded(new BlockPos(x, t.y, z))) return;
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
    }

    /** The night's fire called in, from the lookout it was seen from: he heard it on the radio, and he is angry. */
    static void reported(ServerLevel level, long key) {
        Desk desk = Desk.of(level);
        Tower t = Towers.known(level, key);
        if (t == null) return;
        boolean any = false;
        for (Post post : desk.posts.values()) {
            if (post.tower != key || state(desk, post) != BURNING) continue;
            set(desk, post, REPORTED);
            any = true;
        }
        if (!any) return;
        t.anger++;
        Towers.of(level).setDirty();
        com.jeladastudios.lostsignal.tower.Speech.after(level, 600, () -> Lines.sayAs(level, Vec3.atCenterOf(t.foot()).add(2, 1, 2),
                Component.translatable("radio.fts_lostsignal.speaker.unknown"), "S_LAST_CALLED", 1f, 1f, 96));
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("LAST NIGHT fire called in at {}", t.number);
    }

    /**
     * Dawn: called in, the District's word that the crews held it and brought the campers out. Let go, his thanks on
     * the radio from the foot of the tower, the camper in its way posted missing, a sapling at the planting, and the
     * District's word that the lookout saw it.
     */
    static void dawn(ServerLevel level, Desk desk, Post post, Tower t, int s) {
        if (s == REPORTED) {
            desk.mail(level, post, Desk.DISTRICT, Mail.Line.of("lastnight.fts_lostsignal.reported.subject"),
                    List.of(Mail.Line.of("lastnight.fts_lostsignal.reported.1"), Mail.Line.of("lastnight.fts_lostsignal.reported.2")));
            return;
        }
        if (s != BURNING) return;
        set(desk, post, LET_GO);
        // Once at a lookout, however many keep it: his thanks, and the one not found.
        String name = NAMES.computeIfAbsent(t.key(), k -> {
            String n = com.jeladastudios.lostsignal.remains.Journal.name(level.random.nextLong());
            Lines.sayAs(level, Vec3.atCenterOf(t.foot()).add(2, 1, 2), Component.translatable("radio.fts_lostsignal.speaker.unknown"),
                    "S_LAST_THANKS", 1f, 1f, 96);
            Towers.missing(level, t.foot(), n);
            com.jeladastudios.lostsignal.story.Story.given(level, n, "hiker");
            return n;
        });
        Fires.Big big = BIG.get(t.key());
        Vec3 at = big != null && big.at() != null ? big.at() : t.cab();
        int bearing = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(at.x - t.x, -(at.z - t.z)))), 360);
        desk.mail(level, post, Desk.DISTRICT, Mail.Line.of("lastnight.fts_lostsignal.letgo.subject"), List.of(
                Mail.Line.of("lastnight.fts_lostsignal.letgo.1", "#radio.fts_lostsignal.point." + com.jeladastudios.lostsignal.finder.FireFinder.point(bearing), post.number),
                Mail.Line.of("lastnight.fts_lostsignal.letgo.2", name)));
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("LAST NIGHT let go at {}: {} not found", t.number, name);
    }

    /** What the season's evaluation says of the last night, and how it weighs: a line's key, and what it adds to the share. */
    static String evaluated(Desk desk, Post post) {
        return switch (state(desk, post)) {
            case REPORTED -> "reported";
            case LET_GO -> "letgo";
            case WARNED -> "away";
            default -> null;
        };
    }

    static double weighs(Desk desk, Post post) {
        return switch (state(desk, post)) {
            case REPORTED -> 0.1;
            case LET_GO -> -0.3;
            case WARNED -> -0.1;
            default -> 0;
        };
    }

    /** Marion's word the morning after, as the night went. */
    static String marion(Desk desk, Post post) {
        return switch (state(desk, post)) {
            case REPORTED -> "last_r";
            case LET_GO -> "last_l";
            default -> "last";
        };
    }

    /** The next season's first mail to a keeper, as their last night went. */
    static void opening(ServerLevel level, Desk desk, Post post) {
        if (post.lastNightSeason != desk.season - 1) return;
        String which = post.lastNight == REPORTED ? "reported" : post.lastNight == LET_GO ? "letgo" : null;
        if (which == null) return;
        desk.mail(level, post, Desk.DISTRICT, Mail.Line.of("lastnight.fts_lostsignal.next." + which + ".subject"),
                List.of(Mail.Line.of("lastnight.fts_lostsignal.next." + which + ".body", post.number)));
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        BIG.clear();
        AT.clear();
        NAMES.clear();
        night = -1;
    }
}
