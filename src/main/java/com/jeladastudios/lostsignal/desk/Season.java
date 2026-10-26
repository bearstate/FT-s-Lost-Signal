package com.jeladastudios.lostsignal.desk;

import com.jeladastudios.lostsignal.LostSignal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * The fire season: opened in late spring, at its height through the summer, closing in the autumn, the lookouts shut
 * through the winter. With Serene Seasons, by its seasons; without, by a calendar of our own: a week of opening, two of
 * the height of it, a week of closing, a week shut, over and over from the day the world first had lookouts kept.
 */
public final class Season {

    private Season() {}

    public enum Phase { OPENING, PEAK, CLOSING, CLOSED }

    /** Our own calendar: the days of each phase, in order. */
    static final int OPENING_DAYS = 7, PEAK_DAYS = 14, CLOSING_DAYS = 7, CLOSED_DAYS = 7;
    static final int CYCLE = OPENING_DAYS + PEAK_DAYS + CLOSING_DAYS + CLOSED_DAYS;

    private static boolean tried, works;
    private static Method getState, getSubSeason;

    /** The phase of the season on a day. */
    public static Phase phase(ServerLevel level) {
        int sub = subSeason(level);
        if (sub >= 0) {
            // Early, mid and late spring, summer, autumn, winter: 0 to 11.
            if (sub == 2) return Phase.OPENING;
            if (sub >= 3 && sub <= 5) return Phase.PEAK;
            if (sub == 6 || sub == 7) return Phase.CLOSING;
            return Phase.CLOSED;
        }
        int d = dayOfCycle(level);
        if (d < OPENING_DAYS) return Phase.OPENING;
        if (d < OPENING_DAYS + PEAK_DAYS) return Phase.PEAK;
        if (d < OPENING_DAYS + PEAK_DAYS + CLOSING_DAYS) return Phase.CLOSING;
        return Phase.CLOSED;
    }

    /** Whether the seasons are Serene Seasons', not our own. */
    public static boolean serene(ServerLevel level) {
        return subSeason(level) >= 0;
    }

    /** The day of our own calendar's cycle, from its first day: 0 on. */
    static int dayOfCycle(ServerLevel level) {
        Desk desk = Desk.of(level);
        long start = desk.seasonStart < 0 ? level.getDayTime() / 24000 : desk.seasonStart;
        return (int) Math.floorMod(level.getDayTime() / 24000 - start, CYCLE);
    }

    /** How many days are left of this phase by our own calendar, today counted; -1 with Serene Seasons. */
    public static int daysLeft(ServerLevel level) {
        if (serene(level)) return -1;
        int d = dayOfCycle(level);
        int end = d < OPENING_DAYS ? OPENING_DAYS : d < OPENING_DAYS + PEAK_DAYS ? OPENING_DAYS + PEAK_DAYS
                : d < OPENING_DAYS + PEAK_DAYS + CLOSING_DAYS ? OPENING_DAYS + PEAK_DAYS + CLOSING_DAYS : CYCLE;
        return end - d;
    }

    /** For trying: today taken for the season's last, whatever the calendar says. */
    static boolean lastForced;

    /**
     * Whether today is the season's last day, the lookouts closing tomorrow: our own calendar's last closing day, or
     * Serene Seasons' last day of mid autumn (where its days cannot be read, its first).
     */
    public static boolean lastDay(ServerLevel level) {
        if (lastForced) return true;
        int sub = subSeason(level);
        if (sub < 0) return phase(level) == Phase.CLOSING && daysLeft(level) == 1;
        if (sub != 7) return false;
        try {
            Object state = getState.invoke(null, level.getServer().getLevel(Level.OVERWORLD));
            Class<?> type = Class.forName("sereneseasons.api.season.ISeasonState");
            int length = (Integer) type.getMethod("getSubSeasonDuration").invoke(state);
            int dayLength = (Integer) type.getMethod("getDayDuration").invoke(state);
            int into = (Integer) type.getMethod("getSeasonCycleTicks").invoke(state) % length;
            return length - into <= dayLength;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            // Every day of it, then: the last night comes once a season, so on the first.
            return true;
        }
    }

    /** Serene Seasons' sub-season (0 early spring to 11 late winter), or -1 where it is not there. */
    static int subSeason(ServerLevel level) {
        if (!tried) link();
        if (!works) return -1;
        try {
            Object state = getState.invoke(null, level.getServer().getLevel(Level.OVERWORLD));
            return ((Enum<?>) getSubSeason.invoke(state)).ordinal();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            works = false;
            LostSignal.LOGGER.warn("Serene Seasons' season could not be read ({}); the lookouts go by their own calendar", e.toString());
            return -1;
        }
    }

    private static void link() {
        tried = true;
        if (!ModList.get().isLoaded("sereneseasons")) return;
        try {
            getState = Class.forName("sereneseasons.api.season.SeasonHelper").getMethod("getSeasonState", Level.class);
            getSubSeason = Class.forName("sereneseasons.api.season.ISeasonState").getMethod("getSubSeason");
            works = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            LostSignal.LOGGER.warn("Serene Seasons is installed but its season API was not found ({}); the lookouts go by their own calendar", e.toString());
        }
    }
}
