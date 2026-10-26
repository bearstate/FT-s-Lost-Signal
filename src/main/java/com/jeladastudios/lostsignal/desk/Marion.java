package com.jeladastudios.lostsignal.desk;

import com.jeladastudios.lostsignal.LostSignal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/**
 * Marion Teague on the District's night desk, writing to a keeper's terminal after dark: a night at a time, once they
 * are posted, what she has to say as the season goes (who she is, what they did that day, the knock, Corinne, Hollow
 * Peak); some of it to be written back to, one of two ways, and she answers. The words are keys of the language files
 * (marion.fts_lostsignal.*); see HIKAYE.md for who she is.
 */
public final class Marion {

    private Marion() {}

    public static final String FROM = "desk.fts_lostsignal.from.marion";
    /** When she writes: from the evening to the small hours. */
    static final int FROM_TIME = 14500, TO_TIME = 22000;

    /**
     * One thing she has to say: its name, how many lines, how many replies (and how many lines each answer has), when
     * it may be said (by the keeper's post and the day), how many nights after the one before it waits at least, and
     * what it says beyond the keeper's number and name, if anything.
     */
    record Beat(String id, int lines, int[] answers, BiPredicate<ServerLevel, Post> when, int after, BiFunction<ServerLevel, Post, Object[]> args) {

        Beat(String id, int lines, int[] answers, BiPredicate<ServerLevel, Post> when, int after) {
            this(id, lines, answers, when, after, null);
        }

        Beat(String id, int lines, BiPredicate<ServerLevel, Post> when) {
            this(id, lines, new int[0], when, 0);
        }
    }

    static boolean done(Post p, String id) {
        return p.marion.containsKey(id);
    }

    /** Nights since a beat was said; -1 where it has not been, so nothing waiting on it comes first. */
    static long since(ServerLevel level, Post p, String id) {
        Long day = p.marion.get(id);
        return day == null ? -1 : level.getDayTime() / 24000 - day;
    }

    static final List<Beat> BEATS = List.of(
            new Beat("intro", 2, new int[]{1, 1}, (l, p) -> true, 0),
            new Beat("fake", 2, new int[]{1, 1}, (l, p) -> p.fakeNight >= 0 && l.getDayTime() / 24000 > p.fakeNight, 1),
            new Beat("obs", 1, new int[0], (l, p) -> p.stat("good_observations") > 0, 1),
            new Beat("fire", 1, new int[]{1, 1}, (l, p) -> p.stat("fires") > 0, 1),
            new Beat("rescue", 2, new int[0], (l, p) -> p.stat("rescued") > 0, 1),
            new Beat("knock", 1, new int[]{3, 1}, (l, p) -> p.stat("nights") > 0, 1),
            new Beat("corinne1", 1, new int[]{1, 1}, (l, p) -> since(l, p, "intro") >= 3 || Season.phase(l) != Season.Phase.OPENING, 1),
            new Beat("corinne2", 2, new int[]{2, 2}, (l, p) -> since(l, p, "corinne1") >= 2, 2),
            new Beat("page", 2, new int[0], (l, p) -> since(l, p, "corinne2") >= 1, 1),
            new Beat("hollow1", 1, new int[]{1, 1}, (l, p) -> since(l, p, "corinne2") >= 3 && Season.phase(l) != Season.Phase.OPENING, 2),
            new Beat("hollow2", 2, new int[]{2, 2}, (l, p) -> since(l, p, "hollow1") >= 2 && Season.phase(l) == Season.Phase.CLOSING, 2),
            new Beat("hollow3", 2, new int[]{1, 1}, (l, p) -> since(l, p, "hollow2") >= 1 && com.jeladastudios.lostsignal.story.Story.hollow(l) != null, 1,
                    Marion::hollow),
            // Hollow Peak gone up to: she asks what is left there, the more so where they said they would go; and where
            // they said so and never went, she is glad of it.
            new Beat("went_said", 1, new int[]{2, 1}, (l, p) -> answered(p, "hollow3") == 1 && !done(p, "nogo") && p.stat("hollow_seen") > 0, 1),
            new Beat("went", 1, new int[]{2, 1}, (l, p) -> done(p, "hollow1") && !done(p, "went_said")
                    && (answered(p, "hollow3") != 1 || done(p, "nogo")) && p.stat("hollow_seen") > 0, 1),
            new Beat("nogo", 1, (l, p) -> answered(p, "hollow3") == 1 && since(l, p, "hollow3") >= 4 && p.stat("hollow_seen") == 0),
            // The planting walked into: the crate driver saw their tracks.
            new Beat("trees", 1, new int[]{2, 1}, (l, p) -> done(p, "hollow1") && p.stat("planting_seen") > 0, 1));

    /** How a keeper wrote back to one of hers: 0 the first way, 1 the second, -1 not at all. */
    static int answered(Post p, String id) {
        return p.answers.getOrDefault(id, -1);
    }

    /** Whether she is keeping an ear on their channel: told of the call at two in the morning. */
    static boolean listens(Post p) {
        return answered(p, "fake") == 0;
    }

    /** Whether she has warned them against whoever sits at her desk some night: told of the call, or of the trees. */
    static boolean warns(Post p) {
        return listens(p) || answered(p, "trees") == 0;
    }

    /** Listening on their channel, she heard what came over it, and says so: it was not her. */
    static void heard(ServerLevel level, Desk desk, Post post) {
        if (!listens(post)) return;
        com.jeladastudios.lostsignal.tower.Speech.after(level, 900, () -> desk.mail(level, post, FROM, Mail.Line.of(key("listen", "subject")),
                List.of(Mail.Line.of(key("listen", "1"), post.number, post.name))));
    }

    /** The season's last morning, to a keeper she has warned: she is on tonight, and anyone else on her desk is no one from here. */
    static void warn(ServerLevel level, Desk desk, Post post) {
        if (!warns(post)) return;
        desk.mail(level, post, FROM, Mail.Line.of(key("warn", "subject")), List.of(Mail.Line.of(key("warn", "1"), post.number, post.name),
                Mail.Line.of(key("warn", "2"), post.number, post.name)));
    }

    /** The morning after she made her call about the east ridge: the District's word on it. */
    static void morning(ServerLevel level, Desk desk, Post post) {
        long day = level.getDayTime() / 24000;
        if (post.rangerDay < 0 || day < post.rangerDay || level.getDayTime() % 24000 < 1500) return;
        post.rangerDay = -1;
        desk.mail(level, post, Desk.DISTRICT, Mail.Line.of("desk.fts_lostsignal.mail.ranger.subject"),
                List.of(Mail.Line.of("desk.fts_lostsignal.mail.ranger.1"), Mail.Line.of("desk.fts_lostsignal.mail.ranger.2")));
    }

    /** How far Hollow Peak is from a keeper's lookout, to the hundred blocks, and which way. */
    static Object[] hollow(ServerLevel level, Post post) {
        var t = com.jeladastudios.lostsignal.tower.Towers.known(level, post.tower);
        var h = com.jeladastudios.lostsignal.story.Story.hollow(level);
        if (t == null || h == null) return new Object[]{"?", "?"};
        double dx = h.getX() - t.x, dz = h.getZ() - t.z;
        int bearing = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz))), 360);
        return new Object[]{Math.round(Math.hypot(dx, dz) / 100) * 100,
                "#radio.fts_lostsignal.point." + com.jeladastudios.lostsignal.finder.FireFinder.point(bearing)};
    }

    /**
     * At night, a keeper at their lookout: the next thing she has to say to them, if anything is due, and at most one
     * a night. Waits for the evening's own line to be the last one before it: a beat said waits its nights out.
     */
    static void night(ServerLevel level, ServerPlayer player, Post post, Desk desk) {
        long time = level.getDayTime() % 24000, day = level.getDayTime() / 24000;
        if (time < FROM_TIME || time > TO_TIME || post.marionNight == day) return;
        long last = -1;
        for (Long d : post.marion.values()) last = Math.max(last, d);
        for (Beat b : BEATS) {
            if (done(post, b.id())) continue;
            if (last >= 0 && day - last < Math.max(1, b.after())) continue;
            if (!b.when().test(level, post)) continue;
            say(level, desk, post, b);
            return;
        }
    }

    /** For trying: the next beat whose time has come said now, whatever the hour and however lately the last. */
    static String force(ServerLevel level, Post post, Desk desk) {
        for (Beat b : BEATS) {
            if (done(post, b.id()) || !b.when().test(level, post)) continue;
            say(level, desk, post, b);
            return b.id();
        }
        return "none";
    }

    /** A beat said: its lines to the keeper's terminal, with what can be written back. */
    static void say(ServerLevel level, Desk desk, Post post, Beat b) {
        long day = level.getDayTime() / 24000;
        post.marion.put(b.id(), day);
        post.marionNight = day;
        List<Mail.Line> lines = new ArrayList<>();
        Object[] args = {post.number, post.name};
        if (b.args() != null) {
            Object[] more = b.args().apply(level, post);
            args = java.util.Arrays.copyOf(args, 2 + more.length);
            System.arraycopy(more, 0, args, 2, more.length);
        }
        for (int i = 1; i <= b.lines(); i++) lines.add(Mail.Line.of(key(b.id(), String.valueOf(i)), args));
        List<Mail.Line> replies = new ArrayList<>();
        for (int i = 1; i <= b.answers().length; i++) replies.add(Mail.Line.of(key(b.id(), "r" + i)));
        post.mail(FROM, Mail.Line.of(key(b.id(), "subject")), lines, day, (int) (level.getDayTime() % 24000), replies, b.id());
        desk.setDirty();
        desk.told(level, post);
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) {
            StringBuilder said = new StringBuilder();
            for (Mail.Line l : lines) said.append(" | ").append(l.text().getString());
            LostSignal.LOGGER.info("MARION to {}: {}{}", post.name, b.id(), said);
        }
    }

    static String key(String id, String part) {
        return "marion.fts_lostsignal." + id + "." + part;
    }

    /** A keeper writing back to one of hers: the reply kept, and her answer a few seconds on, as if typed. */
    public static void reply(ServerPlayer p, long id, int choice) {
        ServerLevel level = p.serverLevel();
        Desk desk = Desk.of(level);
        Post post = desk.post(p);
        Mail m = null;
        for (Mail x : post.inbox) if (x.id == id) m = x;
        if (m == null || m.replied >= 0 || choice < 0 || choice >= m.replies.size()) return;
        m.replied = choice;
        if (m.thread != null && !m.thread.isEmpty()) post.answers.put(m.thread, choice);
        desk.setDirty();
        // What she does about it: a pup sent up to one who told her of the knock; a call made about the east ridge.
        if ("knock".equals(m.thread) && choice == 0) {
            var t = com.jeladastudios.lostsignal.tower.Towers.known(level, post.tower);
            if (t != null) {
                t.pups.add(post.id);
                com.jeladastudios.lostsignal.tower.Towers.of(level).setDirty();
            }
        }
        if ("trees".equals(m.thread) && choice == 0) post.rangerDay = level.getDayTime() / 24000 + 1;
        Beat beat = null;
        for (Beat b : BEATS) if (b.id().equals(m.thread)) beat = b;
        if (beat == null) return;
        Beat b = beat;
        int lines = b.answers()[choice];
        com.jeladastudios.lostsignal.tower.Speech.after(level, 60 + level.random.nextInt(80), () -> {
            List<Mail.Line> body = new ArrayList<>();
            for (int i = 1; i <= lines; i++) body.add(Mail.Line.of(key(b.id(), "a" + (choice + 1) + "." + i), post.number, post.name));
            post.mail(FROM, Mail.Line.of("marion.fts_lostsignal.re", "#" + key(b.id(), "subject")), body, level.getDayTime() / 24000,
                    (int) (level.getDayTime() % 24000));
            desk.setDirty();
            desk.told(level, post);
            if (com.jeladastudios.lostsignal.dev.TestCommands.ON) {
                StringBuilder said = new StringBuilder();
                for (Mail.Line l : body) said.append(" | ").append(l.text().getString());
                LostSignal.LOGGER.info("MARION answers {} on {}{}", post.name, b.id(), said);
            }
            // Hollow Peak: what the Board of Review found, out of the archive and up with the next crate.
            if (b.id().equals("hollow2")) {
                var t = com.jeladastudios.lostsignal.tower.Towers.known(level, post.tower);
                if (t != null) Desk.send(level, t, List.of(com.jeladastudios.lostsignal.story.Papers.report()), level.getDayTime() / 24000);
            }
            ServerPlayer now = level.getServer().getPlayerList().getPlayer(post.id);
            if (now != null) Terminal.send(now, false);
        });
    }

    /** The season's last night: what she says as the lookouts close. */
    static void close(ServerLevel level, Desk desk, Post post) {
        // As the last night went for them.
        String last = LastNight.marion(desk, post);
        desk.mail(level, post, FROM, Mail.Line.of("marion.fts_lostsignal." + last + ".subject"), List.of(Mail.Line.of("marion.fts_lostsignal." + last + ".1"),
                Mail.Line.of("marion.fts_lostsignal." + last + ".2")));
    }
}
