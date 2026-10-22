package com.jeladastudios.lostsignal.tower;

import com.jeladastudios.lostsignal.LostSignal;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Things said one after another, not all at once: each line a few seconds after the one before, as people talk.
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class Speech {

    private Speech() {}

    private record Due(ServerLevel level, long at, Runnable said) {}

    private static final List<Due> DUE = new CopyOnWriteArrayList<>();

    /** Something to say in some ticks' time. */
    public static void after(ServerLevel level, int ticks, Runnable said) {
        DUE.add(new Due(level, level.getGameTime() + ticks, said));
    }

    /**
     * How long a line is given before the next, in ticks: as long as its longest recorded take and a second after, or,
     * not recorded yet, by its words, about as fast as one reads them.
     */
    public static int length(String line) {
        int frames = 0;
        for (String take : com.jeladastudios.lostsignal.rescue.Lines.takes(line)) {
            frames = Math.max(frames, com.jeladastudios.lostsignal.radio.Clips.frames(take).size());
        }
        // A frame is 20 ms, a tick 50.
        if (frames > 0) return frames * 2 / 5 + 20;
        String text = net.minecraft.locale.Language.getInstance().getOrDefault("radio.fts_lostsignal.line." + line);
        return 40 + Math.min(200, text.length() * 2);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Due d : DUE) {
            if (d.level.getGameTime() >= d.at) {
                DUE.remove(d);
                d.said.run();
            }
        }
    }

    @SubscribeEvent
    public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        DUE.clear();
    }
}
