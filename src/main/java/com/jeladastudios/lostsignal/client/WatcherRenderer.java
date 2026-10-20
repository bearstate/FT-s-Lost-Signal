package com.jeladastudios.lostsignal.client;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.entity.Watcher;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * The watcher drawn as a person in a long hooded coat, a little too tall and a little too narrow: the proportions are
 * the first thing wrong about it, and the only thing seen from far off.
 */
public class WatcherRenderer extends HumanoidMobRenderer<Watcher, HumanoidModel<Watcher>> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(LostSignal.MODID, "textures/entity/watcher.png");

    public WatcherRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.4f);
    }

    @Override
    protected void scale(Watcher watcher, PoseStack pose, float partialTick) {
        pose.scale(0.92f, 1.1f, 0.92f);
    }

    @Override
    public ResourceLocation getTextureLocation(Watcher watcher) {
        return TEXTURE;
    }
}
