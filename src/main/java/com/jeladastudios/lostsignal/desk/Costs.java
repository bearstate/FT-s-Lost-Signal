package com.jeladastudios.lostsignal.desk;

import com.jeladastudios.lostsignal.Config;
import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.rescue.Lines;
import com.jeladastudios.lostsignal.tower.Tower;
import com.jeladastudios.lostsignal.tower.Towers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * What a duty let go costs, on the horror side: not credit, but his attention. The end of day check missed, and at two
 * in the morning "the District" calls the lookout by its number and asks if they are alone (it is him, at the foot of
 * the tower, his handheld louder there than the District's far repeater); the one o'clock observation missed, and in
 * the morning an observation is on the terminal that the keeper never sent; a fire let burn out, and someone thanks
 * them for it on the radio. And now and then, at night, a message from no one. All of it as HIKAYE.md has it.
 */
public final class Costs {

    private Costs() {}

    /** When the false call comes: two in the morning. When the false observation is found: the early morning. */
    static final int FALSE_CALL = 20000, FALSE_OBS = 300;
    /** The chance of a message from no one, on a night one may come; and the least nights between them. */
    static final float NO_ONE = 0.3f;
    static final int NO_ONE_BETWEEN = 3;

    /** For trying: the next night's message from no one comes, whatever the odds. */
    static boolean sure;

    static boolean on() {
        return Config.on(Config.DESK) && Config.on(Config.HORROR) && Config.on(Config.STRANGER);
    }

    /** A duty missed by a keeper who was at their lookout: what it costs, set going. */
    static void missed(ServerLevel level, Post post, Duty d) {
        if (!on()) return;
        long day = level.getDayTime() / 24000;
        if (d == Duty.EVENING) post.falseCall = day;
        if (d == Duty.WEATHER) post.falseObs = day + 1;
    }

    /** At a keeper's lookout through the night: the false call and the false observation, when their time comes. */
    static void night(ServerLevel level, ServerPlayer p, Post post, Desk desk, Tower t) {
        if (!on()) return;
        long day = level.getDayTime() / 24000, time = level.getDayTime() % 24000;
        if (post.falseCall == day && time >= FALSE_CALL && time < FALSE_CALL + 2000) {
            post.falseCall = -1;
            post.fakeNight = day;
            desk.setDirty();
            // From the foot of the tower: heard on the lookout's own set, and nowhere further.
            Vec3 from = Vec3.atCenterOf(t.foot()).add(2, 1, 2);
            Lines.sayAs(level, from, Component.translatable("radio.fts_lostsignal.speaker.district"), "D_FALSE_ALONE", 1f, 0.97f, 96);
            Marion.heard(level, desk, post);
            if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("COSTS false call at {}", t.number);
        }
        if (post.falseObs >= 0 && post.falseObs <= day && time >= FALSE_OBS && time < 6000) {
            post.falseObs = -1;
            desk.setDirty();
            post.mail("", Mail.Line.of("desk.fts_lostsignal.obs.taken.subject", "13:00"), List.of(Mail.Line.of("costs.fts_lostsignal.false_obs")),
                    day - 1, 7000);
            desk.told(level, post);
            if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("COSTS false observation for {}", post.name);
        }
        // A message from no one, now and then, once the nights have started to go wrong.
        if (time > 15000 && time < 22000 && post.noOneNight != day && (post.stat("nights") > 0 || post.fakeNight >= 0)
                && (post.noOneLast < 0 || day - post.noOneLast >= NO_ONE_BETWEEN)) {
            post.noOneNight = day;
            if (sure || level.random.nextFloat() < NO_ONE) {
                sure = false;
                post.noOneLast = day;
                boolean lit = com.jeladastudios.lostsignal.finder.Lamps.lit(level, t.foot());
                String which = lit ? (level.random.nextBoolean() ? "lit" : "see") : "dark";
                post.mail("", Mail.Line.of("costs.fts_lostsignal.no_one.subject"), List.of(Mail.Line.of("costs.fts_lostsignal.no_one." + which)),
                        day, (int) time);
                desk.told(level, post);
                Marion.heard(level, desk, post);
                if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("COSTS message from no one to {}: {}", post.name, which);
            }
            desk.setDirty();
        }
    }

    /**
     * A fire let burn itself out, never called in: whoever was on duty at a lookout in sight of it is thanked for it on
     * the radio, by someone at the foot of their tower; and the District asks after it, with a little off the next pay.
     */
    public static void burnedOut(ServerLevel level, Vec3 fire) {
        if (!Config.on(Config.DESK)) return;
        Desk desk = Desk.of(level);
        for (ServerPlayer p : level.players()) {
            Post post = Desk.posted(p);
            if (post == null) continue;
            Tower t = Towers.known(level, post.tower);
            if (t == null || t.cab().distanceTo(fire) > 600 || Math.hypot(p.getX() - t.x, p.getZ() - t.z) > 96) continue;
            if (on()) {
                Vec3 from = Vec3.atCenterOf(t.foot()).add(2, 1, 2);
                Lines.sayAs(level, from, Component.translatable("radio.fts_lostsignal.speaker.unknown"), "S_THANK_BURN", 1f, 1f, 96);
            }
            com.jeladastudios.lostsignal.tower.Speech.after(level, 400, () -> {
                desk.mail(level, post, Desk.DISTRICT, Mail.Line.of("costs.fts_lostsignal.burned.subject"), List.of(Mail.Line.of("costs.fts_lostsignal.burned.body")));
                post.bonuses.add(new Post.Bonus("costs.fts_lostsignal.burned.cut", -Desk.FIRE_PAY));
            });
            if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("COSTS fire let burn near {}", t.number);
        }
    }
}
