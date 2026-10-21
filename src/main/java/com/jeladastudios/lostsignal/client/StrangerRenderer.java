package com.jeladastudios.lostsignal.client;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.entity.Stranger;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** The stranger: by day the man, by night the mask and the robe, and the mask's eyes red in the dark. */
public class StrangerRenderer extends HumanoidMobRenderer<Stranger, StrangerModel> {

    private static final ResourceLocation DAY = new ResourceLocation(LostSignal.MODID, "textures/entity/stranger_day.png");
    private static final ResourceLocation NIGHT = new ResourceLocation(LostSignal.MODID, "textures/entity/stranger_night.png");
    private static final ResourceLocation EYES = new ResourceLocation(LostSignal.MODID, "textures/entity/stranger_eyes.png");

    public StrangerRenderer(EntityRendererProvider.Context context) {
        super(context, new StrangerModel(context.bakeLayer(StrangerModel.LAYER)), 0.5f);
        addLayer(new Eyes(this));
    }

    @Override
    public ResourceLocation getTextureLocation(Stranger s) {
        return s.masked() ? NIGHT : DAY;
    }

    /** The mask's eyes, drawn at full brightness: two red points in the dark. */
    static final class Eyes extends RenderLayer<Stranger, StrangerModel> {

        Eyes(RenderLayerParent<Stranger, StrangerModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, Stranger s, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!s.masked()) return;
            getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(EYES)), 0xF00000, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        }
    }
}
