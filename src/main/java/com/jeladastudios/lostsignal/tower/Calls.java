package com.jeladastudios.lostsignal.tower;

import com.jeladastudios.lostsignal.finder.FireFinder;
import com.jeladastudios.lostsignal.mission.Lookouts;
import com.jeladastudios.lostsignal.mission.Missions;
import com.jeladastudios.lostsignal.net.Net;
import com.jeladastudios.lostsignal.rescue.Lines;
import com.jeladastudios.lostsignal.rescue.Stations;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Calling out on the radio from a lookout's station: to the District, or to one of the lookouts round. Who answers is
 * how the world is -- a keeper with their news, or the District with what it has heard; or no one, only the hiss; or,
 * from a lookout no one is posted at, a voice that says everything is fine.
 */
public final class Calls {

    private Calls() {}

    /** How far a station's call carries, lookout to lookout, in blocks. */
    static final double REACH = 3500;
    /** How many of the lookouts round are on the panel. */
    static final int LISTED = 3;

    /** The lookout a player calls from: the one they are at, or the nearest. */
    static Tower home(ServerLevel level, ServerPlayer p) {
        BlockPos foot = Missions.towerAt(level, p);
        if (foot == null) foot = Lookouts.near(level, p.blockPosition(), 1);
        return foot == null ? null : Towers.at(level, foot);
    }

    /** The lookouts round the player's, for the panel: number, bearing, distance. */
    public static List<Net.Neighbour> neighbours(ServerPlayer p) {
        List<Net.Neighbour> list = new ArrayList<>();
        ServerLevel level = p.serverLevel();
        Tower home = home(level, p);
        if (home == null) return list;
        for (Tower t : Towers.around(level, home)) {
            if (list.size() >= LISTED) break;
            int dx = t.x - home.x, dz = t.z - home.z;
            int bearing = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz))), 360);
            list.add(new Net.Neighbour(t.key(), t.number, bearing, (int) Math.round(Math.hypot(dx, dz) / 10.0) * 10));
        }
        return list;
    }

    /** A call made from the panel: to the District (key 0), or to a lookout. */
    public static void call(ServerPlayer p, long key) {
        ServerLevel level = p.serverLevel();
        BlockPos station = Stations.near(level, p.blockPosition(), 6);
        if (station == null || !Stations.powered(level, station)) {
            p.displayClientMessage(Component.translatable(station == null ? "rescue.fts_lostsignal.no_station" : "rescue.fts_lostsignal.no_power"), true);
            return;
        }
        Tower home = home(level, p);
        if (key == 0) {
            district(level, p, home);
            return;
        }
        Tower t = Towers.known(level, key);
        if (t == null) return;
        p.displayClientMessage(Component.translatable("call.fts_lostsignal.calling", t.number), true);
        switch (t.state) {
            case KEPT -> keeper(level, p, t);
            case STRANGER -> {
                // No one is posted there. Someone answers.
                Speech.after(level, 70 + level.random.nextInt(60), () -> Lines.sayAs(level, t.cab(), lookout(t, null),
                        "S_RADIO_0" + (1 + level.random.nextInt(3)), 1f, 1f, REACH));
                if (home != null) Towers.notice(level, "stranger_answered", home);
            }
            default -> Speech.after(level, 80, () -> p.displayClientMessage(Component.translatable("call.fts_lostsignal.no_answer", t.number), true));
        }
    }

    /** A keeper answering: hello, what is on their mind, goodbye. */
    static void keeper(ServerLevel level, ServerPlayer p, Tower t) {
        Component who = lookout(t, t.keeper);
        Vec3 from = t.cab();
        String hello = level.random.nextBoolean() ? "K_ANSWER_01" : "K_ANSWER_02";
        // What they say: what they need first, then anything they have seen, else their news.
        Talk.Topic topic = t.need != Tower.Need.NONE ? Talk.Topic.HELP : notable(level, t) ? Talk.Topic.SEEN : Talk.Topic.NEWS;
        String news = Talk.answer(level, t, topic, true);
        int at = 40;
        Speech.after(level, at, () -> Lines.sayAs(level, from, who, hello, 1f, t.pitch, REACH));
        at += Speech.length(hello);
        Speech.after(level, at, () -> {
            Lines.sayAs(level, from, who, news, 1f, t.pitch, REACH);
            if (topic == Talk.Topic.HELP && t.need == Tower.Need.CHECK) {
                Talk.where(level, p, t);
                Towers.of(level).checking.put(p.getUUID(), t.checkOn);
            }
        });
        at += Speech.length(news);
        Speech.after(level, at, () -> Lines.sayAs(level, from, who, "K_BYE", 1f, t.pitch, REACH));
    }

    /** Whether a keeper has seen something worth telling: the stranger lately, or a light where there should be none. */
    static boolean notable(ServerLevel level, Tower t) {
        if (t.metStranger >= 0 && level.getGameTime() - t.metStranger < Talk.ON_MIND) return true;
        for (Tower other : Towers.around(level, t)) if (other.state == Tower.State.STRANGER) return true;
        return false;
    }

    /** The District answering, and telling what it has to tell about the lookouts round. */
    static void district(ServerLevel level, ServerPlayer p, Tower home) {
        p.displayClientMessage(Component.translatable("call.fts_lostsignal.calling_district"), true);
        long time = level.getDayTime() % 24000;
        boolean night = time > 13000;
        Vec3 from = p.position();
        Speech.after(level, 40, () -> Lines.say(level, from, Lines.Speaker.DISTRICT, night ? "D_ANSWER_NIGHT" : "D_ANSWER", 1f));
        // While he is on the radio: the District has heard no one, and sent no one.
        String his = com.jeladastudios.lostsignal.visit.Mimic.on(Missions.towerAt(level, p));
        if (his != null) {
            String who = his.equals("call") ? "D_WHO_01" : "D_WHO_02";
            Speech.after(level, 40 + Speech.length(night ? "D_ANSWER_NIGHT" : "D_ANSWER"), () -> Lines.say(level, from, Lines.Speaker.DISTRICT, who, 1f));
            return;
        }
        // Called back after the District's own call: that is what it answers.
        String reply = District.reply(level);
        if (reply != null) {
            Speech.after(level, 40 + Speech.length(night ? "D_ANSWER_NIGHT" : "D_ANSWER"), () -> Lines.say(level, from, Lines.Speaker.DISTRICT, reply, 1f));
            return;
        }
        Towers.Notice n = home == null ? null : Towers.takeNotice(level, home);
        String line = n == null ? null : switch (n.kind()) {
            case "silent" -> "D_CHECK_TOWER";
            case "checked" -> "D_TOWER_EMPTY";
            case "stranger_answered" -> "D_NOBODY_POSTED";
            case "new_keeper" -> "D_NEW_KEEPER";
            default -> null;
        };
        if (line == null) return;
        Speech.after(level, 40 + Speech.length(night ? "D_ANSWER_NIGHT" : "D_ANSWER"), () -> {
            Lines.say(level, from, Lines.Speaker.DISTRICT, line, 1f);
            Tower about = Towers.known(level, n == null ? 0 : n.tower());
            if (about != null && ("silent".equals(n.kind()) || "new_keeper".equals(n.kind()))) {
                note(p, home, about);
                if ("silent".equals(n.kind())) Towers.of(level).checking.put(p.getUUID(), about.key());
            }
        });
    }

    /** A lookout as the radio names it: Lookout 12, or Lookout 12 (Ruth). */
    static Component lookout(Tower t, String keeper) {
        return keeper == null || keeper.isEmpty() ? Component.translatable("call.fts_lostsignal.lookout", t.number)
                : Component.translatable("call.fts_lostsignal.lookout_kept", t.number, keeper);
    }

    /** Where a lookout is from the player's: a note under the words. */
    static void note(ServerPlayer p, Tower home, Tower about) {
        if (home == null) return;
        int dx = about.x - home.x, dz = about.z - home.z;
        int bearing = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz))), 360);
        p.sendSystemMessage(Component.translatable("tower.fts_lostsignal.check_on", about.number, (int) Math.round(Math.hypot(dx, dz) / 10.0) * 10,
                Component.translatable("radio.fts_lostsignal.point." + FireFinder.point(bearing)), bearing).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
