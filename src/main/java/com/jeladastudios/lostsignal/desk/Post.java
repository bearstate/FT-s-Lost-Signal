package com.jeladastudios.lostsignal.desk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * One player's season at the lookouts, as the District keeps it: where they are posted, their pay credit, the day's
 * duties, the week's work since the last pay, their record, and the messages on their terminal.
 */
public final class Post {

    /** How many messages a terminal keeps: the oldest go. */
    static final int KEEPS = 40;

    public final UUID id;
    public String name = "";
    /** The lookout they are posted at, by its place, and its number; 0 and -1 where not posted. */
    public long tower;
    public int number = -1;
    /** Their pay credit, and their rank (0 seasonal lookout to 3 district lookout). */
    public int credit;
    public int rank;

    /** The day the duties below are for; which they were at their lookout for while open, did, and missed. */
    public long day = -1;
    public int present, done, missed;

    /** Since the last pay: days on duty, duties done, and what else was earned, as lines of the pay slip. */
    public int daysOnDuty, dutiesDone;
    public final List<Bonus> bonuses = new ArrayList<>();
    /** The day of the last pay; -1 before the first. */
    public long lastPay = -1;

    /** The season they were last welcomed in; whether their terminal and handbook have been sent. */
    public int welcomed;
    public boolean kit;
    /** The day they last set their lookout's fire danger sign to the day's rating; whether the sign and stool were sent. */
    public long signDay = -1;
    public boolean kit2;
    /** Whether today's observation has been sent and taken, and earned its bonus for being right. */
    public boolean observed, observedRight;

    /** Their record, by what: fires reported, hikers brought in, and so on; and the same for this season alone. */
    public final Map<String, Integer> stats = new TreeMap<>();
    public final Map<String, Integer> season = new TreeMap<>();

    /** What Marion has said to them, each by the day she said it; and the last night she wrote. */
    public final Map<String, Long> marion = new TreeMap<>();
    public long marionNight = -1;
    /**
     * What letting a duty go costs: the night of the false call still to come, the morning of the false observation, the
     * night the false call came; the last night looked at for a message from no one, and the last one that brought one.
     */
    public long falseCall = -1, falseObs = -1, fakeNight = -1, noOneNight = -1, noOneLast = -1;
    /** The season's last night: which season it was last kept for, how it went (see LastNight), and what of it has happened. */
    int lastNightSeason = -1, lastNight, lastNightDone;

    public final List<Mail> inbox = new ArrayList<>();
    long nextMail = 1;

    /** Something earned beyond the day's pay: its line on the slip (a key) and how much. */
    public record Bonus(String key, int amount) {}

    public Post(UUID id) {
        this.id = id;
    }

    public void count(String what) {
        stats.merge(what, 1, Integer::sum);
        season.merge(what, 1, Integer::sum);
    }

    public int seasonStat(String what) {
        return season.getOrDefault(what, 0);
    }

    public int stat(String what) {
        return stats.getOrDefault(what, 0);
    }

    /** A message put on their terminal; the oldest dropped past what it keeps. */
    public Mail mail(String from, Mail.Line subject, List<Mail.Line> body, long day, int time) {
        Mail m = new Mail(nextMail++, from, subject, body, day, time, false);
        inbox.add(m);
        while (inbox.size() > KEEPS) inbox.remove(0);
        return m;
    }

    /** A message that can be written back to: its replies, and the thread it belongs to (what answers it). */
    public Mail mail(String from, Mail.Line subject, List<Mail.Line> body, long day, int time, List<Mail.Line> replies, String thread) {
        Mail m = new Mail(nextMail++, from, subject, body, day, time, false, replies, -1, thread);
        inbox.add(m);
        while (inbox.size() > KEEPS) inbox.remove(0);
        return m;
    }

    public int unread() {
        int n = 0;
        for (Mail m : inbox) if (!m.read) n++;
        return n;
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("Id", id);
        t.putString("Name", name);
        t.putLong("Tower", tower);
        t.putInt("Number", number);
        t.putInt("Credit", credit);
        t.putInt("Rank", rank);
        t.putLong("Day", day);
        t.putInt("Present", present);
        t.putInt("Done", done);
        t.putInt("Missed", missed);
        t.putInt("DaysOnDuty", daysOnDuty);
        t.putInt("DutiesDone", dutiesDone);
        ListTag bs = new ListTag();
        for (Bonus b : bonuses) {
            CompoundTag c = new CompoundTag();
            c.putString("Key", b.key());
            c.putInt("Amount", b.amount());
            bs.add(c);
        }
        t.put("Bonuses", bs);
        t.putLong("LastPay", lastPay);
        t.putInt("Welcomed", welcomed);
        t.putBoolean("Kit", kit);
        t.putBoolean("Kit2", kit2);
        t.putLong("SignDay", signDay);
        t.putBoolean("Observed", observed);
        t.putBoolean("ObservedRight", observedRight);
        CompoundTag st = new CompoundTag();
        for (var e : stats.entrySet()) st.putInt(e.getKey(), e.getValue());
        t.put("Stats", st);
        CompoundTag ss = new CompoundTag();
        for (var e : season.entrySet()) ss.putInt(e.getKey(), e.getValue());
        t.put("Season", ss);
        ListTag mails = new ListTag();
        for (Mail m : inbox) mails.add(m.save());
        t.put("Inbox", mails);
        t.putLong("NextMail", nextMail);
        CompoundTag mar = new CompoundTag();
        for (var e : marion.entrySet()) mar.putLong(e.getKey(), e.getValue());
        t.put("Marion", mar);
        t.putLong("MarionNight", marionNight);
        t.putLong("FalseCall", falseCall);
        t.putLong("FalseObs", falseObs);
        t.putLong("FakeNight", fakeNight);
        t.putLong("NoOneNight", noOneNight);
        t.putLong("NoOneLast", noOneLast);
        t.putInt("LastNightSeason", lastNightSeason);
        t.putInt("LastNight", lastNight);
        t.putInt("LastNightDone", lastNightDone);
        return t;
    }

    static Post load(CompoundTag t) {
        Post p = new Post(t.getUUID("Id"));
        p.name = t.getString("Name");
        p.tower = t.getLong("Tower");
        p.number = t.contains("Number") ? t.getInt("Number") : -1;
        p.credit = t.getInt("Credit");
        p.rank = t.getInt("Rank");
        p.day = t.getLong("Day");
        p.present = t.getInt("Present");
        p.done = t.getInt("Done");
        p.missed = t.getInt("Missed");
        p.daysOnDuty = t.getInt("DaysOnDuty");
        p.dutiesDone = t.getInt("DutiesDone");
        for (Tag b : t.getList("Bonuses", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) b;
            p.bonuses.add(new Bonus(c.getString("Key"), c.getInt("Amount")));
        }
        p.lastPay = t.contains("LastPay") ? t.getLong("LastPay") : -1;
        p.welcomed = t.getInt("Welcomed");
        p.kit = t.getBoolean("Kit");
        p.kit2 = t.getBoolean("Kit2");
        p.signDay = t.contains("SignDay") ? t.getLong("SignDay") : -1;
        p.observed = t.getBoolean("Observed");
        p.observedRight = t.getBoolean("ObservedRight");
        CompoundTag st = t.getCompound("Stats");
        for (String k : st.getAllKeys()) p.stats.put(k, st.getInt(k));
        CompoundTag ss = t.getCompound("Season");
        for (String k : ss.getAllKeys()) p.season.put(k, ss.getInt(k));
        for (Tag m : t.getList("Inbox", Tag.TAG_COMPOUND)) p.inbox.add(Mail.load((CompoundTag) m));
        p.nextMail = Math.max(1, t.getLong("NextMail"));
        CompoundTag mar = t.getCompound("Marion");
        for (String k : mar.getAllKeys()) p.marion.put(k, mar.getLong(k));
        p.marionNight = t.contains("MarionNight") ? t.getLong("MarionNight") : -1;
        p.falseCall = t.contains("FalseCall") ? t.getLong("FalseCall") : -1;
        p.falseObs = t.contains("FalseObs") ? t.getLong("FalseObs") : -1;
        p.fakeNight = t.contains("FakeNight") ? t.getLong("FakeNight") : -1;
        p.noOneNight = t.contains("NoOneNight") ? t.getLong("NoOneNight") : -1;
        p.noOneLast = t.contains("NoOneLast") ? t.getLong("NoOneLast") : -1;
        p.lastNightSeason = t.contains("LastNightSeason") ? t.getInt("LastNightSeason") : -1;
        p.lastNight = t.getInt("LastNight");
        p.lastNightDone = t.getInt("LastNightDone");
        return p;
    }
}
