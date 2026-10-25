package com.jeladastudios.lostsignal.desk;

/**
 * The lookout's day, as the duty roster on the wall has it: when each thing is to be done, by the time of day it opens
 * and closes. Done while it is open; missed if the keeper was at their lookout while it was open and did not do it;
 * neither, if they were away (no pay for it, and nothing held against them).
 */
public enum Duty {
    /** The District's morning radio check, answered. */
    MORNING(1000, 7000, "07:00"),
    /** The fire weather observation at one o'clock, read off the instruments and sent from the terminal. */
    WEATHER(6500, 9500, "13:00"),
    /** The District's end of day check, answered. */
    EVENING(11500, 16000, "17:30"),
    /** The lamp on the walkway lit at dusk. */
    LAMP(12500, 14500, "18:30");

    public final int opens, closes;
    /** When it is, as the roster writes it. */
    public final String clock;

    Duty(int opens, int closes, String clock) {
        this.opens = opens;
        this.closes = closes;
        this.clock = clock;
    }

    public int bit() {
        return 1 << ordinal();
    }

    /** Whether it is open at a time of day. */
    public boolean open(long time) {
        return time >= opens && time < closes;
    }
}
