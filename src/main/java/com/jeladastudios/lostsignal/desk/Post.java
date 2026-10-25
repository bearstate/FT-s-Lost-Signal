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
    /** Their pay credit. */
    public int credit;

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
    /** Whether today's observation has been sent and taken, and earned its bonus for being right. */
    public boolean observed, observedRight;

    /** Their record, by what: fires reported, hikers brought in, and so on. */
    public final Map<String, Integer> stats = new TreeMap<>();

    public final List<Mail> inbox = new ArrayList<>();
    long nextMail = 1;

    /** Something earned beyond the day's pay: its line on the slip (a key) and how much. */
    public record Bonus(String key, int amount) {}

    public Post(UUID id) {
        this.id = id;
    }

    public void count(String what) {
        stats.merge(what, 1, Integer::sum);
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
        t.putBoolean("Observed", observed);
        t.putBoolean("ObservedRight", observedRight);
        CompoundTag st = new CompoundTag();
        for (var e : stats.entrySet()) st.putInt(e.getKey(), e.getValue());
        t.put("Stats", st);
        ListTag mails = new ListTag();
        for (Mail m : inbox) mails.add(m.save());
        t.put("Inbox", mails);
        t.putLong("NextMail", nextMail);
        return t;
    }

    static Post load(CompoundTag t) {
        Post p = new Post(t.getUUID("Id"));
        p.name = t.getString("Name");
        p.tower = t.getLong("Tower");
        p.number = t.contains("Number") ? t.getInt("Number") : -1;
        p.credit = t.getInt("Credit");
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
        p.observed = t.getBoolean("Observed");
        p.observedRight = t.getBoolean("ObservedRight");
        CompoundTag st = t.getCompound("Stats");
        for (String k : st.getAllKeys()) p.stats.put(k, st.getInt(k));
        for (Tag m : t.getList("Inbox", Tag.TAG_COMPOUND)) p.inbox.add(Mail.load((CompoundTag) m));
        p.nextMail = Math.max(1, t.getLong("NextMail"));
        return p;
    }
}
