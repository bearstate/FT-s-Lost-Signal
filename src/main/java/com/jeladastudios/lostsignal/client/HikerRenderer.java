package com.jeladastudios.lostsignal.client;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.entity.Hiker;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** A hiker in one of the kits: a bright rain jacket, a knitted hat, a pack. */
public class HikerRenderer extends HumanoidMobRenderer<Hiker, HumanoidModel<Hiker>> {

    private static final ResourceLocation[] TEXTURES = new ResourceLocation[Hiker.KITS];

    static {
        for (int i = 0; i < Hiker.KITS; i++) {
            TEXTURES[i] = new ResourceLocation(LostSignal.MODID, "textures/entity/hiker_" + i + ".png");
        }
    }

    public HikerRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(Hiker hiker) {
        return TEXTURES[Math.floorMod(hiker.kit(), Hiker.KITS)];
    }
}
