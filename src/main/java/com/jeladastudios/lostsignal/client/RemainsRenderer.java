package com.jeladastudios.lostsignal.client;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.entity.Hiker;
import com.jeladastudios.lostsignal.remains.Remains;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * A hiker lying face down on the ground, in their own kit, so they are known: one arm flung out ahead as if reaching,
 * the other at their side, the head turned to the side, the legs a little apart. Nothing moves.
 */
public class RemainsRenderer extends EntityRenderer<Remains> {

    private static final ResourceLocation[] TEXTURES = new ResourceLocation[Hiker.KITS];

    static {
        for (int i = 0; i < Hiker.KITS; i++) {
            TEXTURES[i] = new ResourceLocation(LostSignal.MODID, "textures/entity/hiker_" + i + ".png");
        }
    }

    private final HumanoidModel<Hiker> model;

    public RemainsRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE));
        shadowRadius = 0;
    }

    @Override
    public void render(Remains r, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!r.body()) return;
        pose.pushPose();
        // Turned the way the drag went, then laid down on its front, the head that way; the chest's depth off the
        // ground.
        pose.mulPose(Axis.YP.rotationDegrees(180f - r.getYRot()));
        pose.translate(0, 0.14, 0);
        pose.mulPose(Axis.XP.rotationDegrees(90f));
        pose.translate(0, -0.9, 0);
        // As a living thing's renderer sets it out: the model's own frame upside down, its feet at the origin.
        pose.scale(-1f, -1f, 1f);
        pose.translate(0, -1.501, 0);
        model.young = false;
        model.crouching = false;
        model.riding = false;
        model.head.xRot = 0;
        model.head.yRot = 1.3f;
        model.head.zRot = 0;
        model.hat.copyFrom(model.head);
        model.body.xRot = 0;
        model.rightArm.xRot = -2.9f;
        model.rightArm.yRot = 0;
        model.rightArm.zRot = 0.15f;
        model.leftArm.xRot = 0.1f;
        model.leftArm.yRot = 0;
        model.leftArm.zRot = -0.12f;
        model.rightLeg.xRot = 0;
        model.rightLeg.zRot = 0.12f;
        model.leftLeg.xRot = 0.05f;
        model.leftLeg.zRot = -0.18f;
        var texture = TEXTURES[Math.floorMod(r.kit(), Hiker.KITS)];
        // A little darker than the living: no one has lit a lamp over them.
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY,
                0.78f, 0.76f, 0.74f, 1f);
        pose.popPose();
        super.render(r, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(Remains r) {
        return TEXTURES[Math.floorMod(r.kit(), Hiker.KITS)];
    }
}
