package com.jeladastudios.lostsignal;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;

/**
 * What a world's owner may turn down or off, in the world's own {@code serverconfig/fts_lostsignal-server.toml}: the
 * stranger and each of the things he does, what is left where someone was taken, the lost hikers, the lookouts' day,
 * and the flowing sleep. Read by the server only; a client joining is sent the server's.
 */
public final class Config {

    private Config() {}

    public static final ForgeConfigSpec SPEC;

    public static final BooleanValue STRANGER, TALKS, FAKE_CALLS, ECHO, TAKES_KEEPERS, KEEPS_LOOKOUTS;
    public static final DoubleValue SIGHTINGS, VISITS, TAKES_HIKERS;
    public static final BooleanValue STAINS, BODIES;
    public static final BooleanValue HIKERS, FALLS, HUNTED;
    public static final DoubleValue HIKER_NIGHTS;
    public static final BooleanValue DISTRICT, SUPPLIES, FIRES, LIGHTNING;
    public static final DoubleValue FIRE_CHANCE;
    public static final BooleanValue FLOWING_SLEEP;
    public static final BooleanValue DESK, HORROR;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("The stranger: a man in the woods by day, masked by night.",
                "Change these with the world closed or the server stopped: a running server may not notice the change,",
                "and writes what it has back as it stops.").push("stranger");
        STRANGER = b.comment("Everything he does. Off, he is never seen, never comes to the door, never uses the radio,",
                        "takes no one, and keeps no lookout from now on.")
                .define("enabled", true);
        SIGHTINGS = b.comment("How often he is seen at the edge of the trees, by night and by day (1 = as designed, 0 = never).")
                .defineInRange("sightings", 1.0, 0.0, 5.0);
        TALKS = b.comment("By day, away from the lookouts, he may come up out of the trees to talk.")
                .define("talks", true);
        VISITS = b.comment("How often he comes to a lookout's door in the small hours (1 = as designed, 0 = never).")
                .defineInRange("visits", 1.0, 0.0, 5.0);
        FAKE_CALLS = b.comment("Calls on the rescue frequency from someone who is not who they say.")
                .define("fakeCalls", true);
        ECHO = b.comment("Among them, a player's own voice, kept from what they said on the rescue frequency and played back",
                        "to them. Off, nothing anyone says is kept at all.")
                .define("echoVoices", true);
        TAKES_HIKERS = b.comment("How likely he is to take a lost hiker in the night (1 = as designed, 0 = never).")
                .defineInRange("takesHikers", 1.0, 0.0, 3.0);
        TAKES_KEEPERS = b.comment("Now and then a lookout's keeper is not heard from again.")
                .define("takesKeepers", true);
        KEEPS_LOOKOUTS = b.comment("Some lookouts are his: a light in them at night, black candles on the floor.",
                        "Off, no more of them from now on.")
                .define("keepsLookouts", true);
        b.pop();

        b.comment("What is left where someone was taken.").push("remains");
        STAINS = b.comment("Dark stains and drag marks. Off, the broken camp and the footprints are left, nothing else.")
                .define("stains", true);
        BODIES = b.comment("A body at some of them. Off, never: the journal is found in the tent instead.")
                .define("bodies", true);
        b.pop();

        b.comment("The hikers lost on the mountain, calling on the rescue frequency.").push("hikers");
        HIKERS = b.comment("Lost hikers at all.").define("enabled", true);
        HIKER_NIGHTS = b.comment("How often an evening at a lookout brings one (1 = as designed, 0 = never).")
                .defineInRange("frequency", 1.0, 0.0, 2.5);
        FALLS = b.comment("Walking over steep ground, they may fall and be hurt.").define("falls", true);
        HUNTED = b.comment("The night's monsters hunt them as they hunt villagers: zombies and skeletons, spiders in the dark. They",
                "run from them; one killed is lost, and what is left of them stays where it happened.").define("monsters", true);
        b.pop();

        b.comment("The lookouts' day.").push("lookouts");
        DISTRICT = b.comment("The District's calls through the day: the radio checks, the weather, the rules at dusk.")
                .define("districtCalls", true);
        SUPPLIES = b.comment("Supply crates set down at the foot of the trail every few days.")
                .define("supplies", true);
        FIRES = b.comment("Forest fires seen from the lookout, to be read off the fire finder and called in. Only smoke",
                        "and the glow at night: nothing in the world burns.")
                .define("fires", true);
        FIRE_CHANCE = b.comment("How often they start (1 = as designed).")
                .defineInRange("fireFrequency", 1.0, 0.0, 5.0);
        LIGHTNING = b.comment("In a thunderstorm lightning strikes a lookout someone is at, once or twice a storm, after a warning",
                        "(a hum and a blue glow at the rod). On the lightning stool, nothing; on the floor, thrown and hurt, but never",
                        "left with less than two hearts.")
                .define("lightning", true);
        b.pop();

        b.comment("The lookout's desk work: the terminal, the day's duties, pay credit and orders, the season.").push("desk");
        DESK = b.comment("All of it. Off, no one is posted, no duties are kept and nothing is paid; the terminal stays dark.")
                .define("enabled", true);
        HORROR = b.comment("What a duty let go costs at night: a call from \"the District\" that is not the District, an observation",
                        "on the terminal no one sent, thanks on the radio for a fire let burn, now and then a message from no one.",
                        "Off, none of it (the stranger's own setting turns it off too).")
                .define("horror", true);
        b.pop();

        b.comment("Sleep.").push("sleep");
        FLOWING_SLEEP = b.comment("The night flows by fast while everyone needed is asleep, instead of the jump to morning:",
                        "what happens in it can wake them. Turn it off if another mod handles sleep.")
                .define("flowing", true);
        b.pop();

        SPEC = b.build();
    }

    /** A switch's setting; its default where the world's settings are not loaded (no server running). */
    public static boolean on(BooleanValue value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }

    /** A scale's setting, so; as a float, for the chances it scales. */
    public static float scale(DoubleValue value) {
        try {
            return value.get().floatValue();
        } catch (IllegalStateException e) {
            return value.getDefault().floatValue();
        }
    }

    /** The stranger at all, and this one of the things he does. */
    public static boolean stranger(BooleanValue what) {
        return on(STRANGER) && on(what);
    }

    /** The stranger at all, and this one's scale. */
    public static float stranger(DoubleValue what) {
        return on(STRANGER) ? scale(what) : 0f;
    }
}
