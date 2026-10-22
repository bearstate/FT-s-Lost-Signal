package com.jeladastudios.lostsignal.signal;

import com.jeladastudios.lostsignal.LostSignal;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** The signals' particles: always drawn, however far and whatever the player's particle setting. */
public final class ModParticles {

    private ModParticles() {}

    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, LostSignal.MODID);

    /** A burning flare's point of red light. */
    public static final RegistryObject<SimpleParticleType> FLARE = PARTICLES.register("flare", () -> new SimpleParticleType(true));
    /** A lamp's warm point, held up and waved. */
    public static final RegistryObject<SimpleParticleType> LAMP = PARTICLES.register("lamp", () -> new SimpleParticleType(true));
    /** A puff of a smoke column, seen across a valley: big, grey, through the haze. */
    public static final RegistryObject<SimpleParticleType> SMOKE = PARTICLES.register("far_smoke", () -> new SimpleParticleType(true));
    /** A puff of a forest fire's smoke: dark brown off the burning, greying as it rises and drifts on the wind. */
    public static final RegistryObject<SimpleParticleType> FIRE_SMOKE = PARTICLES.register("fire_smoke", () -> new SimpleParticleType(true));
    /** The glow of a fire under its smoke at night: a broad dull orange. */
    public static final RegistryObject<SimpleParticleType> FIRE_GLOW = PARTICLES.register("fire_glow", () -> new SimpleParticleType(true));
}
