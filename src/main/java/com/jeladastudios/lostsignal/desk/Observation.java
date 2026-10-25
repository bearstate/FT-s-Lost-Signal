package com.jeladastudios.lostsignal.desk;

import com.jeladastudios.lostsignal.LostSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The one o'clock fire weather observation: what the keeper read off the instruments, typed into the terminal by hand
 * and sent. The District holds it against what the instruments of that cab truly read (FT's Geology's terminal, within
 * a few blocks of the terminal it is sent from), each reading within what a careful eye would give or take. Two
 * readings off and it is taken, with a word about them; more, and it is sent back to be read again.
 */
public final class Observation {

    private Observation() {}

    /** The form's fields, in the order the terminal sends them. */
    public static final int TEMPERATURE = 0, HUMIDITY = 1, PRESSURE = 2, TENDENCY = 3, WIND = 4, FROM = 5, RAIN = 6, SKY = 7, FIELDS = 8;
    /** A field left empty. */
    public static final int EMPTY = Integer.MIN_VALUE;
    /** The tendency's choices, and the sky's. */
    public static final String[] TENDENCIES = {"falling", "steady", "rising"};
    public static final String[] SKIES = {"clear", "partly", "overcast", "rain", "thunder"};
    public static final String[] NAMES = {"temperature", "humidity", "pressure", "tendency", "wind", "from", "rain", "sky"};

    /** How many readings may be off and the observation still taken. */
    static final int TAKEN_WITH = 2;

    /** An observation sent from a terminal; what the District makes of it, to the keeper's terminal. */
    public static void send(ServerPlayer p, BlockPos terminal, int[] v) {
        ServerLevel level = p.serverLevel();
        Post post = Desk.posted(p);
        if (post == null || v.length != FIELDS) return;
        Desk desk = Desk.of(level);
        if (Season.phase(level) == Season.Phase.CLOSED) {
            p.displayClientMessage(Component.translatable("desk.fts_lostsignal.closed"), true);
            return;
        }
        if (post.observedRight) {
            desk.mail(level, post, Desk.DISTRICT, Mail.Line.of("desk.fts_lostsignal.obs.already.subject"),
                    List.of(Mail.Line.of("desk.fts_lostsignal.obs.already.body")));
            return;
        }
        CompoundTag now = com.jeladastudios.lostsignal.tower.WeatherReport.reading(level, terminal);
        List<Integer> off = wrong(level, now, v);
        long time = level.getDayTime() % 24000;
        boolean scheduled = Duty.WEATHER.open(time);
        List<Mail.Line> lines = new ArrayList<>();
        lines.add(Mail.Line.of("desk.fts_lostsignal.obs.echo", value(v, TEMPERATURE), value(v, HUMIDITY), value(v, PRESSURE),
                "#desk.fts_lostsignal.obs.tendency." + choice(TENDENCIES, v[TENDENCY]), value(v, WIND),
                "#radio.fts_lostsignal.point." + point(v[FROM]), value(v, RAIN), "#desk.fts_lostsignal.obs.sky." + choice(SKIES, v[SKY])));
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) LostSignal.LOGGER.info("DESK observation by {}: {} off {} (terminal {})",
                p.getName().getString(), java.util.Arrays.toString(v), off, now);
        if (off.size() > TAKEN_WITH) {
            for (int f : off) lines.add(Mail.Line.of("desk.fts_lostsignal.obs.check", "#desk.fts_lostsignal.obs.field." + NAMES[f]));
            lines.add(Mail.Line.of("desk.fts_lostsignal.obs.again"));
            desk.mail(level, post, Desk.DISTRICT, Mail.Line.of("desk.fts_lostsignal.obs.back.subject"), lines);
            return;
        }
        post.count("observations");
        if (scheduled) Desk.done(p, Duty.WEATHER);
        // The figures as sent go into tomorrow's fire danger, the instruments' own where a figure was left out.
        CompoundTag sent = new CompoundTag();
        sent.putFloat("Humidity", v[HUMIDITY] != EMPTY ? v[HUMIDITY] : now != null ? now.getFloat("Humidity") : 50);
        sent.putFloat("Temperature", v[TEMPERATURE] != EMPTY ? v[TEMPERATURE] : now != null ? now.getFloat("Temperature") : 15);
        sent.putFloat("Wind", v[WIND] != EMPTY ? v[WIND] : now != null ? now.getFloat("Wind") : 0);
        desk.lastObs = sent;
        if (v[RAIN] != EMPTY && v[RAIN] > 0) desk.lastRain = level.getDayTime() / 24000;
        post.observed = true;
        for (int f : off) lines.add(Mail.Line.of("desk.fts_lostsignal.obs.check", "#desk.fts_lostsignal.obs.field." + NAMES[f]));
        if (off.isEmpty()) {
            post.observedRight = true;
            post.count("good_observations");
            Desk.earn(p, "desk.fts_lostsignal.bonus.observation", Desk.RIGHT_PAY);
            Marks.award(p, "first_obs");
            lines.add(Mail.Line.of("desk.fts_lostsignal.obs.right"));
        } else if (scheduled) {
            lines.add(Mail.Line.of("desk.fts_lostsignal.obs.resend"));
        }
        if (!scheduled) lines.add(Mail.Line.of("desk.fts_lostsignal.obs.unscheduled", Duty.WEATHER.clock));
        desk.mail(level, post, Desk.DISTRICT, Mail.Line.of("desk.fts_lostsignal.obs.taken.subject", clock(time)), lines);
        desk.setDirty();
    }

    /**
     * The fields that do not hold against the instruments: left empty, or further off than a careful reading gives or
     * takes. Without a terminal in the cab to hold them against, only the sky, which anyone can see.
     */
    static List<Integer> wrong(ServerLevel level, CompoundTag now, int[] v) {
        List<Integer> off = new ArrayList<>();
        for (int i = 0; i < FIELDS; i++) if (v[i] == EMPTY) off.add(i);
        boolean thunder = level.isThundering();
        boolean rain = level.isRaining() || now != null && now.getFloat("RainRate") > 0.05f;
        if (v[SKY] != EMPTY) {
            String sky = choice(SKIES, v[SKY]);
            boolean ok = thunder ? sky.equals("thunder") || sky.equals("rain") : rain ? sky.equals("rain") || sky.equals("overcast")
                    : !sky.equals("rain") && !sky.equals("thunder");
            if (!ok) off.add(SKY);
        }
        if (now == null) return off;
        if (v[TEMPERATURE] != EMPTY && now.contains("Temperature") && Math.abs(v[TEMPERATURE] - now.getFloat("Temperature")) > 2) off.add(TEMPERATURE);
        if (v[HUMIDITY] != EMPTY && now.contains("Humidity") && Math.abs(v[HUMIDITY] - now.getFloat("Humidity")) > 7) off.add(HUMIDITY);
        if (v[PRESSURE] != EMPTY && now.contains("Pressure") && Math.abs(v[PRESSURE] - now.getFloat("Pressure")) > 3) off.add(PRESSURE);
        if (v[TENDENCY] != EMPTY && now.contains("Pressure")) {
            float t = now.getFloat("Tendency");
            int truth = t <= -0.5f ? 0 : t >= 0.5f ? 2 : 1;
            if (v[TENDENCY] != truth) off.add(TENDENCY);
        }
        if (now.contains("Wind")) {
            float knots = now.getFloat("Wind");
            if (v[WIND] != EMPTY && Math.abs(v[WIND] - knots) > Math.max(3, knots * 0.25f)) off.add(WIND);
            // Calm, it blows from nowhere: any direction will do.
            if (v[FROM] != EMPTY && knots >= 1) {
                int truth = Math.floorMod(Math.round(now.getFloat("WindFrom") / 45f), 8);
                int d = Math.floorMod(v[FROM] - truth, 8);
                if (Math.min(d, 8 - d) > 1) off.add(FROM);
            }
        }
        if (v[RAIN] != EMPTY && now.contains("RainToday")) {
            float mm = now.getFloat("RainToday");
            if (Math.abs(v[RAIN] - mm) > Math.max(1, mm * 0.2f)) off.add(RAIN);
        }
        off.sort(Integer::compare);
        return off.stream().distinct().toList();
    }

    static String value(int[] v, int field) {
        return v[field] == EMPTY ? "-" : String.valueOf(v[field]);
    }

    static String choice(String[] choices, int i) {
        return i >= 0 && i < choices.length ? choices[i] : "none";
    }

    static String point(int i) {
        String[] points = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
        return i >= 0 && i < 8 ? points[i] : "n";
    }

    /** A time of day as a clock reads it: 13:05. */
    public static String clock(long time) {
        long minutes = Math.floorMod(time * 60 / 1000 + 6 * 60, 24 * 60);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }
}
