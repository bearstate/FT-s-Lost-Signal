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
    public static void particles(net.minecraftforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(com.jeladastudios.lostsignal.signal.ModParticles.FLARE.get(), SignalParticle.Flare::new);
        event.registerSpriteSet(com.jeladastudios.lostsignal.signal.ModParticles.LAMP.get(), SignalParticle.Lamp::new);
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(StrangerModel.LAYER, StrangerModel::createLayer);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.STRANGER.get(), StrangerRenderer::new);
        event.registerEntityRenderer(ModEntities.HIKER.get(), HikerRenderer::new);
        event.registerEntityRenderer(ModEntities.REMAINS.get(), RemainsRenderer::new);
        event.registerEntityRenderer(ModEntities.KEEPER.get(), KeeperRenderer::new);
    }
}
