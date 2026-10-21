package com.jeladastudios.lostsignal.client;

import com.jeladastudios.lostsignal.LostSignal;
import com.jeladastudios.lostsignal.entity.Stranger;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * A man, as the player is drawn, and what he wears at night over it: two horns rising from the mask, swept back and
 * in at their tips, and a robe falling from his waist to his ankles. The night's parts are drawn only when he is
 * masked.
 */
public class StrangerModel extends HumanoidModel<Stranger> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(LostSignal.MODID, "stranger"), "main");

    private final ModelPart hornLeft, hornRight, robe;

    public StrangerModel(ModelPart root) {
        super(root);
        hornLeft = head.getChild("horn_left");
        hornRight = head.getChild("horn_right");
        robe = body.getChild("robe");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0f);
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.getChild("head");
        // The horns: from the top of the head either side, out and up, then back and in at the tips.
        PartDefinition right = head.addOrReplaceChild("horn_right", CubeListBuilder.create().texOffs(64, 0)
                .addBox(-1f, -3f, -1f, 2, 3, 2), PartPose.offsetAndRotation(-2.5f, -8f, -1f, -0.25f, 0f, -0.45f));
        right.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(72, 0)
                .addBox(-0.5f, -3f, -0.5f, 1, 3, 1), PartPose.offsetAndRotation(0f, -2.6f, 0f, -0.55f, 0f, 0.5f));
        PartDefinition left = head.addOrReplaceChild("horn_left", CubeListBuilder.create().texOffs(64, 0).mirror()
                .addBox(-1f, -3f, -1f, 2, 3, 2), PartPose.offsetAndRotation(2.5f, -8f, -1f, -0.25f, 0f, 0.45f));
        left.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(72, 0).mirror()
                .addBox(-0.5f, -3f, -0.5f, 1, 3, 1), PartPose.offsetAndRotation(0f, -2.6f, 0f, -0.55f, 0f, -0.5f));
        // The robe's skirt, from the waist down over the legs.
        root.getChild("body").addOrReplaceChild("robe", CubeListBuilder.create().texOffs(64, 16)
                .addBox(-4.5f, 12f, -2.5f, 9, 11, 5), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public void setupAnim(Stranger s, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(s, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        boolean night = s.masked();
        hornLeft.visible = night;
        hornRight.visible = night;
        robe.visible = night;
        // His arms hang still: he does not swing them as he walks.
        rightArm.xRot *= 0.25f;
        leftArm.xRot *= 0.25f;
    }
}
