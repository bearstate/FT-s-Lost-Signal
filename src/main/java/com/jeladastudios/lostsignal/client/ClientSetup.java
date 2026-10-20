package com.jeladastudios.lostsignal.client;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.entity.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** What only the client sets up: how the creatures are drawn. */
@Mod.EventBusSubscriber(modid = LostSignal.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.WATCHER.get(), WatcherRenderer::new);
        event.registerEntityRenderer(ModEntities.HIKER.get(), HikerRenderer::new);
    }
}
