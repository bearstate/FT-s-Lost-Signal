package com.jeladastudios.lostsignal.radio;

import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.central.WorldlyPosition;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.radio.CommonRadioPlugin;
import com.codinglitch.simpleradio.radio.RadioManager;
import com.codinglitch.simpleradio.radio.RadioMessage;
import com.codinglitch.simpleradio.radio.RadioTransmitter;
import com.jeladastudios.lostsignal.LostSignal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A voice on the air that is not a player's: the district's ranger, a lost hiker, or what speaks in their voices. A
 * transmitter of the radio's own kind, at a place in the world, on a frequency, through which recorded clips go out in
 * real time -- so they reach every set in range as a player's voice would, with the radio's own propagation, noise and
 * S-meter on them. Each broadcast keys up, sends its clips one after another, and unkeys.
 */
@Mod.EventBusSubscriber(modid = LostSignal.MODID)
public final class Broadcast {

    /** The frequency the lookouts keep: the rescue frequency, as Feel the Nature's boards and logs give it. */
    public static final String RESCUE = "145.500";
    /** One real-time frame, in nanoseconds. */
    private static final long FRAME_NANOS = 20_000_000L;

    private static final List<Broadcast> ON_AIR = new CopyOnWriteArrayList<>();

    private final UUID id = UUID.randomUUID();
    private final RadioTransmitter transmitter;
    private final Frequency frequency;
    private final WorldlyPosition at;
    private final Deque<short[]> pending = new ArrayDeque<>();
    private long start;
    private long sent;
    private boolean valid = true;
    private int idle;

    private Broadcast(ServerLevel level, Vec3 at, String frequency, float power) {
        this.frequency = RadioManager.getInstance().frequencies().getOrCreate(frequency, Frequency.Modulation.FREQUENCY);
        this.at = WorldlyPosition.of(new Vector3f((float) at.x, (float) at.y, (float) at.z), level);
        this.transmitter = new RadioTransmitter(this.frequency, this.at, id) {
            // Not a block or an item of the radio's: kept alive by the broadcast itself, not by what stands at its place.
            @Override
            public boolean validate() {
                return valid;
            }
        };
        this.transmitter.frequencingType(SimpleRadioFrequencing.STATION);
        this.transmitter.powerMultiplier = power;
        this.frequency.addTransmitter(transmitter);
    }

    /**
     * A broadcast from a place, on a frequency, at a power (a multiple of a station's; a handheld is about a tenth),
     * sending these clips in turn. Null where the radio is not running yet or a clip is missing.
     */
    public static Broadcast send(ServerLevel level, Vec3 at, String frequency, float power, String... clips) {
        if (CommonRadioPlugin.serverApi == null) return null;
        List<short[]> frames = new ArrayList<>();
        for (String clip : clips) {
            List<short[]> f = Clips.frames(clip);
            if (f.isEmpty()) return null;
            frames.addAll(f);
        }
        Broadcast b = new Broadcast(level, at, frequency, power);
        b.pending.addAll(frames);
        b.start = System.nanoTime();
        ON_AIR.add(b);
        return b;
    }

    public boolean onAir() {
        return valid;
    }

    /** Cut off where it is, as a voice that stops mid-word. */
    public void stop() {
        pending.clear();
    }

    /** The frames due by now, a few in advance so there is no gap from one tick to the next. */
    private void pump() {
        long due = (System.nanoTime() - start) / FRAME_NANOS + 3;
        while (!pending.isEmpty() && sent < due) {
            short[] frame = pending.poll();
            RadioMessage message = new RadioMessage(id, at, frame, 1f);
            message.activity = CommonRadioPlugin.analyzeActivity(frame);
            transmitter.accept(message);
            sent++;
        }
        if (pending.isEmpty() && ++idle > 2) close();
    }

    private void close() {
        if (!valid) return;
        transmitter.endTransmission(id, null);
        valid = false;
        frequency.removeTransmitter(transmitter);
        RadioManager.getInstance().removeRouter(transmitter);
        ON_AIR.remove(this);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Broadcast b : ON_AIR) b.pump();
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        for (Broadcast b : ON_AIR) b.close();
    }
}
