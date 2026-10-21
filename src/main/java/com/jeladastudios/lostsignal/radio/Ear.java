package com.jeladastudios.lostsignal.radio;

import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.central.WorldlyPosition;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.radio.RadioManager;
import com.codinglitch.simpleradio.radio.RadioMessage;
import com.codinglitch.simpleradio.radio.RadioReceiver;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Someone listening on a frequency at a place who is not a player: a lost hiker's handheld, the district's set. A
 * receiver of the radio's own kind, so it hears only what the radio would let it hear from there, but plays nothing:
 * it is told of each frame that arrives (who sent it, how strong, whether it was the end of a transmission) and
 * may keep the sound itself.
 */
public final class Ear {

    /** One frame heard. */
    public record Heard(UUID speaker, UUID radio, float signalDb, boolean end, short[] pcm) {}

    private final UUID id = UUID.randomUUID();
    private final Frequency frequency;
    private final RadioReceiver receiver;
    private boolean valid = true;

    public Ear(ServerLevel level, Vec3 at, String frequency, Consumer<Heard> heard) {
        this.frequency = RadioManager.getInstance().frequencies().getOrCreate(frequency, Frequency.Modulation.FREQUENCY);
        WorldlyPosition where = WorldlyPosition.of(new Vector3f((float) at.x, (float) at.y, (float) at.z), level);
        this.receiver = new RadioReceiver(this.frequency, where, id) {
            @Override
            public boolean validate() {
                return valid;
            }
        };
        receiver.frequencingType(SimpleRadioFrequencing.STATION);
        receiver.setAcceptingCriteria(m -> {
            if (m instanceof RadioMessage r) {
                short[] pcm = null;
                if (!r.endOfTransmission && r.getSource() instanceof com.codinglitch.simpleradio.radio.RadioSource source) {
                    short[] decoded = source.getDecoded(r.getRealOwner());
                    // Shared between every copy of the message: kept only as a copy.
                    if (decoded != null) pcm = decoded.clone();
                }
                heard.accept(new Heard(r.getRealOwner(), r.getOwner(), r.signalDb, r.endOfTransmission, pcm));
            }
            // Heard here only: nothing is played from it.
            return false;
        });
        this.frequency.addReceiver(receiver);
    }

    public void close() {
        if (!valid) return;
        valid = false;
        frequency.removeReceiver(receiver);
        RadioManager.getInstance().removeRouter(receiver);
    }
}
