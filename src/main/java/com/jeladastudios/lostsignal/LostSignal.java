package com.jeladastudios.lostsignal;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * FT's Geology: Lost Signal. Nights at one of Feel the Nature's fire lookouts: the rescue frequency, the hikers lost on
 * the mountain who call on it and are guided down by the lookout's keeper, and something in the trees that watches,
 * whistles, and has learnt to use the radio too.
 *
 * <p>It stands on three mods and needs all of them: FT's Geology for the country and its weather, Feel the Nature for
 * the lookouts, their trails and rescue boards, and the Simple Radio ham fork for the radio.</p>
 */
@Mod(LostSignal.MODID)
public final class LostSignal {

    public static final String MODID = "fts_lostsignal";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LostSignal() {
        var bus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        com.jeladastudios.lostsignal.entity.ModEntities.ENTITIES.register(bus);
        com.jeladastudios.lostsignal.signal.ModParticles.PARTICLES.register(bus);
    }
}
