package com.jeladastudios.lostsignal.desk;

import com.jeladastudios.lostsignal.Config;
import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.mission.Missions;
import com.jeladastudios.lostsignal.tower.Tower;
import com.jeladastudios.lostsignal.tower.Towers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The District's books on its lookouts' keepers: who is posted where, the season, each day's duties, the week's pay and
 * the messages sent to each terminal. Kept with the world, so a world that had lookouts before simply starts a season
 * the day it first runs this.
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class Desk extends SavedData {

    /** A day on duty, and each duty done, in pay credit. */
    public static final int DAY_PAY = 10, DUTY_PAY = 5;
    /**
     * What else is earned: a hiker brought in; a fire called in, from the panel or reported in full from the terminal;
     * a report right to a degree; an observation right in every reading.
     */
    public static final int RESCUE_PAY = 40, FIRE_PAY = 15, REPORT_PAY = 25, PRECISE_PAY = 10, RIGHT_PAY = 5, SIGN_PAY = 2, RELAY_PAY = 5;
    /** How many days between pay. */
    static final int PAY_EVERY = 7;
    /** How near their lookout a keeper must be to be at it, in blocks. */
    static final double AT_TOWER = 96;

    public static final String DISTRICT = "desk.fts_lostsignal.from.district";

    final Map<UUID, Post> posts = new HashMap<>();
    /** The day our own calendar's first season began; -1 before it has. */
    long seasonStart = -1;
    /** Which season it is, counted from the first. */
    public int season = 1;
    /** The phase last seen, to tell when it changes. */
    String lastPhase = "";
    /** The last day looked at. */
    long lastDay = -1;
    /** Today's fire danger (a Danger.Rating's place, -1 before the first morning) and the day it was rated. */
    int rating = -1;
    long ratingDay = -1;
    /** The last day it rained, and the last observation sent in (its figures, for the morning's rating). */
    long lastRain = -1;
    CompoundTag lastObs;
    /** The hikers' letters on their way up. */
    final List<Letters.Letter> letters = new ArrayList<>();

    public static Desk of(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(Desk::load, Desk::new, "fts_lostsignal_desk");
    }

    /** A player's post, made now if they have none, their day brought up to today. */
    public Post post(ServerPlayer p) {
        Post post = posts.computeIfAbsent(p.getUUID(), Post::new);
        if (!post.name.equals(p.getName().getString())) {
            post.name = p.getName().getString();
            setDirty();
        }
        today(post, p.serverLevel().getDayTime() / 24000);
        return post;
    }

    /** A player's post if they are posted at a lookout, else null. */
    public static Post posted(ServerPlayer p) {
        if (!Config.on(Config.DESK)) return null;
        Post post = of(p.serverLevel()).post(p);
        return post.tower == 0 ? null : post;
    }

    /** A day gone by for a post: counted to the week if they were on duty in it; the new day's duties not yet begun. */
    void today(Post post, long day) {
        if (post.day == day) return;
        if (post.day >= 0 && post.present != 0) {
            post.daysOnDuty++;
            post.count("days");
        }
        post.day = day;
        post.present = post.done = post.missed = 0;
        post.observed = post.observedRight = false;
        setDirty();
    }

    // === The day ================================================================

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (level.getGameTime() % 100 != 41 || !Config.on(Config.DESK)) return;
        Desk desk = of(level);
        long day = level.getDayTime() / 24000, time = level.getDayTime() % 24000;
        if (desk.seasonStart < 0) {
            desk.seasonStart = day;
            desk.setDirty();
        }
        Season.Phase phase = Season.phase(level);
        if (!phase.name().equals(desk.lastPhase)) {
            boolean first = desk.lastPhase.isEmpty();
            String was = desk.lastPhase;
            desk.lastPhase = phase.name();
            desk.setDirty();
            if (!first) desk.turned(level, was, phase);
        }
        // Rain, as the District hears of it: the days since it last rained go into the fire danger.
        if (level.isRaining() && desk.lastRain != day) {
            desk.lastRain = day;
            desk.setDirty();
        }
        if (time >= 500 && desk.ratingDay != day) Danger.morning(level, desk, phase != Season.Phase.CLOSED);
        if (day != desk.lastDay) {
            desk.lastDay = day;
            desk.setDirty();
            Letters.send(level, desk);
            for (Post post : desk.posts.values()) {
                desk.today(post, day);
                if (post.tower != 0 && post.lastPay >= 0 && day - post.lastPay >= PAY_EVERY && phase != Season.Phase.CLOSED) desk.pay(level, post);
            }
        }
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            Post post = desk.post(p);
            if (post.tower == 0) {
                // At a lookout with no post yet: posted there, in a season the lookouts are open.
                BlockPos foot = Missions.towerAt(level, p);
                if (foot != null && phase != Season.Phase.CLOSED) desk.postAt(level, p, post, Towers.at(level, foot));
                continue;
            }
            // The fire danger sign and the lightning stool, sent once to every keeper (those posted before they were, too).
            if (post.kit && !post.kit2) {
                post.kit2 = true;
                Tower home = Towers.known(level, post.tower);
                if (home != null) {
                    send(level, home, List.of(new ItemStack(com.jeladastudios.lostsignal.block.ModBlocks.DANGER_SIGN_ITEM.get()),
                            new ItemStack(com.jeladastudios.lostsignal.block.ModBlocks.STOOL_ITEM.get()), Handbook.guide()), day);
                    desk.mail(level, post, DISTRICT, Mail.Line.of("desk.fts_lostsignal.mail.gear.subject"), List.of(
                            Mail.Line.of("desk.fts_lostsignal.mail.gear.1"), Mail.Line.of("desk.fts_lostsignal.mail.gear.2")));
                }
                desk.setDirty();
            }
            // Promoted, as the record grows.
            int rank = rankOf(post);
            if (rank > post.rank) desk.promote(level, p, post, rank);
            if (phase == Season.Phase.CLOSED) continue;
            Tower t = Towers.known(level, post.tower);
            if (t == null) continue;
            boolean at = Math.hypot(p.getX() - t.x, p.getZ() - t.z) < AT_TOWER;
            for (Duty d : Duty.values()) {
                if (!applies(d)) continue;
                // A radio check is there to be answered only once the District has made it today.
                boolean called = d == Duty.MORNING ? com.jeladastudios.lostsignal.tower.District.called(level, "D_MORNING_01")
                        : d != Duty.EVENING || com.jeladastudios.lostsignal.tower.District.called(level, "D_EVENING_01");
                if (d.open(time) && at && called && (post.present & d.bit()) == 0) {
                    post.present |= d.bit();
                    desk.setDirty();
                }
                if (d == Duty.LAMP && d.open(time) && at && com.jeladastudios.lostsignal.finder.Lamps.lit(level, t.foot())) done(p, Duty.LAMP);
                if (time >= d.closes && (post.present & d.bit()) != 0 && (post.done & d.bit()) == 0 && (post.missed & d.bit()) == 0) {
                    post.missed |= d.bit();
                    desk.setDirty();
                    desk.missedDuty(level, p, post, d);
                }
            }
        }
    }

    /** Whether a duty is one to be done in this world: the District's checks only where the District calls. */
    static boolean applies(Duty d) {
        return d != Duty.MORNING && d != Duty.EVENING || Config.on(Config.DISTRICT);
    }

    /** A duty done by a player, while it is open: counted for their pay. */
    public static void done(ServerPlayer p, Duty d) {
        Post post = posted(p);
        if (post == null || Season.phase(p.serverLevel()) == Season.Phase.CLOSED) return;
        long time = p.serverLevel().getDayTime() % 24000;
        if (!d.open(time) || (post.done & d.bit()) != 0) return;
        post.done |= d.bit();
        post.present |= d.bit();
        post.missed &= ~d.bit();
        post.dutiesDone++;
        post.count("duties");
        Desk desk = of(p.serverLevel());
        desk.setDirty();
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("DESK {} did {}", p.getName().getString(), d);
        // Every duty of the day done: a full day.
        int all = 0;
        for (Duty x : Duty.values()) if (applies(x)) all |= x.bit();
        if ((post.done & all) == all) Marks.award(p, "full_day");
    }

    /** Something earned beyond the day's pay, on the next pay slip; and counted in the record. */
    public static void earn(ServerPlayer p, String key, int amount) {
        Post post = posted(p);
        if (post == null) return;
        post.bonuses.add(new Post.Bonus(key, amount));
        of(p.serverLevel()).setDirty();
    }

    /** Counted in a player's record. */
    public static void count(ServerPlayer p, String what) {
        if (!Config.on(Config.DESK)) return;
        of(p.serverLevel()).post(p).count(what);
        of(p.serverLevel()).setDirty();
    }

    /** A duty missed by someone who was at their lookout while it was open. */
    void missedDuty(ServerLevel level, ServerPlayer p, Post post, Duty d) {
        if (d == Duty.WEATHER) {
            mail(level, post, DISTRICT, Mail.Line.of("desk.fts_lostsignal.mail.missed_weather.subject"),
                    List.of(Mail.Line.of("desk.fts_lostsignal.mail.missed_weather.body", post.number)));
        }
    }

    /** A message put on a player's terminal; told to them if they are at their lookout. */
    public void mail(ServerLevel level, Post post, String from, Mail.Line subject, List<Mail.Line> body) {
        post.mail(from, subject, body, level.getDayTime() / 24000, (int) (level.getDayTime() % 24000));
        setDirty();
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(post.id);
        if (p == null || post.tower == 0) return;
        Tower t = Towers.known(level, post.tower);
        if (t != null && Math.hypot(p.getX() - t.x, p.getZ() - t.z) < 32) {
            p.displayClientMessage(Component.translatable("desk.fts_lostsignal.new_mail", post.unread()), true);
        }
    }

    // === Posting ================================================================

    /**
     * A player posted at a lookout: told on the radio and on their terminal, and their terminal, handbook and the page
     * in it sent up with the next crate (once; a later posting sends nothing again).
     */
    void postAt(ServerLevel level, ServerPlayer p, Post post, Tower t) {
        boolean moving = post.tower != 0;
        post.tower = t.key();
        post.number = t.number;
        long day = level.getDayTime() / 24000;
        if (post.lastPay < 0) post.lastPay = day;
        post.welcomed = season;
        setDirty();
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("DESK {} posted at {}", p.getName().getString(), t.number);
        if (moving) {
            mail(level, post, DISTRICT, Mail.Line.of("desk.fts_lostsignal.mail.moved.subject", t.number),
                    List.of(Mail.Line.of("desk.fts_lostsignal.mail.moved.body", t.number)));
            return;
        }
        com.jeladastudios.lostsignal.rescue.Lines.say(level, t.cab(), com.jeladastudios.lostsignal.rescue.Lines.Speaker.DISTRICT, "D_POSTED", 1f);
        p.sendSystemMessage(Component.translatable("desk.fts_lostsignal.posted", t.number).withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC));
        mail(level, post, DISTRICT, Mail.Line.of("desk.fts_lostsignal.mail.welcome.subject", t.number), List.of(
                Mail.Line.of("desk.fts_lostsignal.mail.welcome.1", p.getName().getString(), t.number),
                Mail.Line.of("desk.fts_lostsignal.mail.welcome.2"),
                Mail.Line.of("desk.fts_lostsignal.mail.welcome.3", DAY_PAY, DUTY_PAY),
                Mail.Line.of("desk.fts_lostsignal.mail.welcome.4")));
        if (!post.kit) {
            post.kit = true;
            List<ItemStack> kit = new ArrayList<>();
            kit.add(new ItemStack(com.jeladastudios.lostsignal.block.ModBlocks.TERMINAL_ITEM.get()));
            kit.add(Handbook.guide());
            kit.add(Handbook.page());
            send(level, t, kit, day);
        }
        Marks.award(p, "root");
    }

    /** Things sent up to a lookout, set down with a crate at the foot of its trail from a day on. */
    public static void send(ServerLevel level, Tower t, List<ItemStack> items, long due) {
        t.parcels.addAll(items);
        t.parcelDue = t.parcelDue < 0 ? due : Math.max(t.parcelDue, due);
        Towers.of(level).setDirty();
    }

    // === Pay ====================================================================

    /** The week's pay: days on duty, duties done, what else was earned; onto their credit, and the slip to their terminal. */
    void pay(ServerLevel level, Post post) {
        long day = level.getDayTime() / 24000;
        int days = post.daysOnDuty, dayPay = dayPay(post);
        int amount = days * dayPay + post.dutiesDone * DUTY_PAY;
        List<Mail.Line> lines = new ArrayList<>();
        lines.add(Mail.Line.of("desk.fts_lostsignal.pay.days", days, days * dayPay));
        lines.add(Mail.Line.of("desk.fts_lostsignal.pay.duties", post.dutiesDone, post.dutiesDone * DUTY_PAY));
        for (Post.Bonus b : post.bonuses) {
            amount += b.amount();
            lines.add(Mail.Line.of("desk.fts_lostsignal.pay.bonus", "#" + b.key(), b.amount()));
        }
        post.credit += amount;
        lines.add(Mail.Line.of("desk.fts_lostsignal.pay.total", amount, post.credit));
        if (amount == 0) lines.add(Mail.Line.of("desk.fts_lostsignal.pay.nothing"));
        mail(level, post, DISTRICT, Mail.Line.of("desk.fts_lostsignal.pay.subject"), lines);
        // Today, if it is not over, goes into the next week.
        post.daysOnDuty = 0;
        post.dutiesDone = 0;
        post.bonuses.clear();
        post.lastPay = day;
        setDirty();
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(post.id);
        if (p != null && amount > 0) Marks.award(p, "payday");
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("DESK paid {} {} credit, now {}", post.name, amount, post.credit);
    }

    // === Rank ===================================================================

    /** The service a rank asks for, and the day's pay at each. */
    static final int[] RANKS = {0, 60, 160, 320};
    static final int[] RANK_DAY_PAY = {10, 12, 14, 16};

    /** A keeper's service: duties done, and more for fires called in, hikers brought in, right observations, nights kept. */
    public static int service(Post post) {
        return post.stat("duties") + 3 * post.stat("fires") + 5 * post.stat("rescued") + post.stat("good_observations") + 2 * post.stat("nights");
    }

    public static int rankOf(Post post) {
        int s = service(post), rank = 0;
        while (rank < RANKS.length - 1 && s >= RANKS[rank + 1]) rank++;
        return rank;
    }

    static int dayPay(Post post) {
        return RANK_DAY_PAY[Math.max(0, Math.min(RANK_DAY_PAY.length - 1, post.rank))];
    }

    /** Promoted: told on the terminal, with the raise and what they may order now. */
    void promote(ServerLevel level, ServerPlayer p, Post post, int rank) {
        post.rank = rank;
        setDirty();
        mail(level, post, DISTRICT, Mail.Line.of("desk.fts_lostsignal.rank.subject", "#desk.fts_lostsignal.rank." + rank), List.of(
                Mail.Line.of("desk.fts_lostsignal.rank.body", post.name, "#desk.fts_lostsignal.rank." + rank, RANK_DAY_PAY[rank]),
                Mail.Line.of("desk.fts_lostsignal.rank.orders")));
        if (rank >= 2) Marks.award(p, "senior");
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("DESK {} promoted to {}", post.name, rank);
    }

    // === The season =============================================================

    /** The season turned: told to every keeper; at its close, the last of the pay. */
    void turned(ServerLevel level, String was, Season.Phase now) {
        if (now == Season.Phase.OPENING && Season.Phase.CLOSED.name().equals(was)) season++;
        setDirty();
        for (Post post : posts.values()) {
            if (post.tower == 0) continue;
            if (now == Season.Phase.CLOSED) {
                pay(level, post);
                evaluate(level, post);
            }
            if (now == Season.Phase.OPENING) post.season.clear();
            String key = "desk.fts_lostsignal.mail.season." + now.name().toLowerCase(java.util.Locale.ROOT);
            mail(level, post, DISTRICT, Mail.Line.of(key + ".subject", season), List.of(Mail.Line.of(key + ".body", season)));
        }
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("DESK season {}: {} -> {}", season, was, now);
    }

    /** A grade's least share of the season's duties done, and its bonus. */
    static final double[] GRADES = {0.8, 0.6, 0.4, 0};
    static final int[] GRADE_PAY = {100, 60, 30, 0};

    /**
     * The season's evaluation, to each keeper as it closes: days on duty, the share of the duties done, fires called in,
     * hikers brought in, a grade by them and a bonus for a good one, and whether they are wanted back.
     */
    void evaluate(ServerLevel level, Post post) {
        int days = post.seasonStat("days"), duties = post.seasonStat("duties");
        int all = 0;
        for (Duty d : Duty.values()) if (applies(d)) all++;
        double share = days == 0 ? 0 : Math.min(1, duties / (double) (days * all));
        // Fires and the found count for something besides: each lifts the share a little.
        share = Math.min(1, share + 0.03 * post.seasonStat("fires") + 0.05 * post.seasonStat("rescued"));
        int grade = 0;
        while (grade < GRADES.length - 1 && share < GRADES[grade]) grade++;
        String letter = String.valueOf((char) ('A' + grade));
        post.credit += GRADE_PAY[grade];
        mail(level, post, DISTRICT, Mail.Line.of("desk.fts_lostsignal.eval.subject", season), List.of(
                Mail.Line.of("desk.fts_lostsignal.eval.days", days, Math.round(share * 100)),
                Mail.Line.of("desk.fts_lostsignal.eval.work", post.seasonStat("fires"), post.seasonStat("rescued"), post.seasonStat("good_observations")),
                Mail.Line.of("desk.fts_lostsignal.eval.grade", letter, GRADE_PAY[grade]),
                Mail.Line.of("desk.fts_lostsignal.eval.close." + letter.toLowerCase(java.util.Locale.ROOT))));
        setDirty();
    }

    // === Trying =================================================================

    /**
     * For trying (/ls_test desk): "pay" pays the week now; "credit" sets the credit; "parcel" makes what was sent up due
     * today; "season" moves our own calendar to a day of its cycle; anything else just tells the post as it is.
     */
    public static String test(ServerPlayer p, String what, int value) {
        ServerLevel level = p.serverLevel();
        Desk desk = of(level);
        Post post = desk.post(p);
        long day = level.getDayTime() / 24000;
        switch (what) {
            case "pay" -> desk.pay(level, post);
            case "credit" -> post.credit = value;
            case "parcel" -> {
                Tower t = Towers.known(level, post.tower);
                if (t != null && !t.parcels.isEmpty()) {
                    t.parcelDue = day;
                    Towers.of(level).setDirty();
                }
            }
            case "season" -> desk.seasonStart = day - value;
            case "danger" -> Danger.morning(level, desk, true);
            case "evaluate" -> desk.evaluate(level, post);
            case "letter" -> {
                // A letter from a hiker due today, written the given way, with the given gift.
                desk.letters.add(new Letters.Letter(p.getUUID(), "Dana", day, Math.floorMod(value, Letters.WAYS), Math.floorMod(value, Letters.GIFTS.length)));
                Letters.send(level, desk);
            }
            case "service" -> post.stats.put("duties", value);
            case "power" -> {
                // The nearest station's battery filled, as a running generator would keep it.
                BlockPos st = com.jeladastudios.lostsignal.rescue.Stations.near(level, p.blockPosition(), 12);
                if (st != null) level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(),
                        "data merge block " + st.getX() + " " + st.getY() + " " + st.getZ() + " {energy:10000d}");
                return "station " + st;
            }
            default -> {
            }
        }
        desk.setDirty();
        return "post " + post.number + " credit " + post.credit + " present " + post.present + " done " + post.done + " missed " + post.missed
                + " days " + post.daysOnDuty + " duties " + post.dutiesDone + " bonuses " + post.bonuses.size() + " mail " + post.inbox.size()
                + " phase " + Season.phase(level) + " season " + desk.season + " stats " + post.stats;
    }

    // === Saving =================================================================

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Post p : posts.values()) list.add(p.save());
        tag.put("Posts", list);
        tag.putLong("SeasonStart", seasonStart);
        tag.putInt("Season", season);
        tag.putString("LastPhase", lastPhase);
        tag.putLong("LastDay", lastDay);
        tag.putInt("Rating", rating);
        tag.putLong("RatingDay", ratingDay);
        tag.putLong("LastRain", lastRain);
        if (lastObs != null) tag.put("LastObs", lastObs);
        ListTag ls = new ListTag();
        for (Letters.Letter l : letters) ls.add(l.save());
        tag.put("Letters", ls);
        return tag;
    }

    static Desk load(CompoundTag tag) {
        Desk d = new Desk();
        for (Tag t : tag.getList("Posts", Tag.TAG_COMPOUND)) {
            Post p = Post.load((CompoundTag) t);
            d.posts.put(p.id, p);
        }
        d.seasonStart = tag.contains("SeasonStart") ? tag.getLong("SeasonStart") : -1;
        d.season = Math.max(1, tag.getInt("Season"));
        d.lastPhase = tag.getString("LastPhase");
        d.lastDay = tag.contains("LastDay") ? tag.getLong("LastDay") : -1;
        d.rating = tag.contains("Rating") ? tag.getInt("Rating") : -1;
        d.ratingDay = tag.contains("RatingDay") ? tag.getLong("RatingDay") : -1;
        d.lastRain = tag.contains("LastRain") ? tag.getLong("LastRain") : -1;
        d.lastObs = tag.contains("LastObs") ? tag.getCompound("LastObs") : null;
        for (Tag l : tag.getList("Letters", Tag.TAG_COMPOUND)) d.letters.add(Letters.Letter.load((CompoundTag) l));
        return d;
    }
}
