package com.jeladastudios.lostsignal.desk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * The day's fire danger, as the District rates it each morning from the lookouts' one o'clock observations: low,
 * moderate, high, very high, extreme. Worked out the way the old indices are, from how dry the air is and how warm,
 * raised by wind and by days without rain, lowered by rain; and the fires go by it, a dry afternoon in extreme danger
 * ten times as likely to burn as one in low.
 */
public final class Danger {

    private Danger() {}

    public enum Rating {
        LOW(0.25f), MODERATE(0.6f), HIGH(1f), VERY_HIGH(1.6f), EXTREME(2.5f);

        /** How much likelier a fire is on a dry afternoon than as designed. */
        public final float fires;

        Rating(float fires) {
            this.fires = fires;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * The rating from a set of readings: humidity, temperature, wind, and whole days since it last rained. An index of
     * dryness and warmth (as Angstrom's: the humidity in twentieths and how far the temperature is under 27 degrees, in
     * tenths), lower the drier and warmer; a strong wind a step worse, five dry days a step worse, rain today two better.
     */
    public static Rating of(float humidity, float temperature, float wind, int dryDays) {
        double index = humidity / 20.0 + (27 - temperature) / 10.0;
        int step = index > 4.0 ? 0 : index > 3.0 ? 1 : index > 2.5 ? 2 : index > 2.0 ? 3 : 4;
        if (wind > 15) step++;
        if (dryDays >= 5) step++;
        if (dryDays == 0) step -= 2;
        return Rating.values()[Math.max(0, Math.min(4, step))];
    }

    /** The rating from a terminal's readings, or moderate where there are none to go by. */
    static Rating of(CompoundTag now, int dryDays) {
        if (now == null || !now.contains("Humidity") || !now.contains("Temperature")) return dryDays == 0 ? Rating.LOW : Rating.MODERATE;
        return of(now.getFloat("Humidity"), now.getFloat("Temperature"), now.contains("Wind") ? now.getFloat("Wind") : 0, dryDays);
    }

    /** Today's rating in a world: as rated this morning, or moderate before the first morning. */
    public static Rating today(ServerLevel level) {
        Desk desk = Desk.of(level);
        return desk.rating < 0 ? Rating.MODERATE : Rating.values()[desk.rating];
    }

    /** The morning's rating made: from the last observation sent in, the days since rain; and sent to every keeper. */
    static void morning(ServerLevel level, Desk desk, boolean told) {
        long day = level.getDayTime() / 24000;
        int dry = desk.lastRain < 0 ? 3 : (int) Math.max(0, day - desk.lastRain);
        Rating r = desk.lastObs == null ? (dry == 0 ? Rating.LOW : Rating.MODERATE) : of(desk.lastObs, dry);
        desk.rating = r.ordinal();
        desk.ratingDay = day;
        desk.setDirty();
        if (com.jeladastudios.lostsignal.dev.TestCommands.ON) com.jeladastudios.lostsignal.LostSignal.LOGGER.info("DESK fire danger {} (dry {} days)", r, dry);
        if (!told) {
            desk.lastObs = null;
            return;
        }
        List<Mail.Line> lines = new java.util.ArrayList<>();
        lines.add(Mail.Line.of("desk.fts_lostsignal.danger.body", "#desk.fts_lostsignal.danger." + r.key()));
        if (desk.lastObs != null) {
            lines.add(Mail.Line.of("desk.fts_lostsignal.danger.from", Math.round(desk.lastObs.getFloat("Humidity")),
                    Math.round(desk.lastObs.getFloat("Temperature")), Math.round(desk.lastObs.getFloat("Wind"))));
        } else lines.add(Mail.Line.of("desk.fts_lostsignal.danger.guess"));
        lines.add(Mail.Line.of(dry == 0 ? "desk.fts_lostsignal.danger.rained" : "desk.fts_lostsignal.danger.dry", dry));
        if (r == Rating.EXTREME) lines.add(Mail.Line.of("desk.fts_lostsignal.danger.red_flag"));
        lines.add(Mail.Line.of("desk.fts_lostsignal.danger.sign"));
        for (Post post : desk.posts.values()) {
            if (post.tower == 0) continue;
            desk.mail(level, post, Desk.DISTRICT, Mail.Line.of("desk.fts_lostsignal.danger.subject", "#desk.fts_lostsignal.danger." + r.key()), lines);
        }
        // Yesterday's figures are spent: a day with none sent in is rated on the District's guess.
        desk.lastObs = null;
    }
}
