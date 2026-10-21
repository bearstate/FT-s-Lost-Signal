package com.jeladastudios.lostsignal.client;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.entity.Keeper;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** A keeper in the forest service's shirt, in one of the kits. */
public class KeeperRenderer extends HumanoidMobRenderer<Keeper, HumanoidModel<Keeper>> {

    private static final ResourceLocation[] TEXTURES = new ResourceLocation[Keeper.KITS];

    static {
        for (int i = 0; i < Keeper.KITS; i++) TEXTURES[i] = new ResourceLocation(LostSignal.MODID, "textures/entity/keeper_" + i + ".png");
    }

    public KeeperRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(Keeper keeper) {
        return TEXTURES[Math.floorMod(keeper.kit(), Keeper.KITS)];
    }
}
