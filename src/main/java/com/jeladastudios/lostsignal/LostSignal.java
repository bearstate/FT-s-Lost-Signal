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
        // Simple Radio as published, a later version of it say, is not the ham fork: said so plainly, here, rather than
        // left to fail somewhere in the first night.
        try {
            Class.forName("com.codinglitch.simpleradio.ham.HamContent", false, LostSignal.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("FT's Geology: Lost Signal needs the Simple Radio ham fork "
                    + "(simpleradio 1.20.1-4.5.7.9-ham or newer), not Simple Radio as published.");
        }
        var bus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        com.jeladastudios.lostsignal.block.ModBlocks.BLOCKS.register(bus);
        com.jeladastudios.lostsignal.block.ModBlocks.ITEMS.register(bus);
        com.jeladastudios.lostsignal.entity.ModEntities.ENTITIES.register(bus);
        com.jeladastudios.lostsignal.signal.ModParticles.PARTICLES.register(bus);
        com.jeladastudios.lostsignal.net.Net.register();
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER, Config.SPEC);
        // FT's Geology's earthquakes, where it tells of them: the District asks after the lookouts.
        com.jeladastudios.lostsignal.tower.Quakes.listen();
        bus.addListener((net.minecraftforge.event.BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS) {
                e.accept(com.jeladastudios.lostsignal.block.ModBlocks.SIGNAL_LAMP_ITEM.get());
                e.accept(com.jeladastudios.lostsignal.block.ModBlocks.TERMINAL_ITEM.get());
                e.accept(com.jeladastudios.lostsignal.block.ModBlocks.STOOL_ITEM.get());
                e.accept(com.jeladastudios.lostsignal.block.ModBlocks.DANGER_SIGN_ITEM.get());
            }
            if (e.getTabKey() == net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES) {
                e.accept(com.jeladastudios.lostsignal.block.ModBlocks.FLARE.get());
            }
        });
    }
}
