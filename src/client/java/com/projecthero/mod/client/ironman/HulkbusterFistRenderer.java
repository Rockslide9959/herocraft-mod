package com.projecthero.mod.client.ironman;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.ironman.entity.HulkbusterFistEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * v0.16.1: the Rocket Fist in flight -- the Hulkbuster body's own {@code forearm_r} + {@code hand_r} (every other bone
 * hidden), laid along the flight line fist-first (elbow-first on the way back), at the body's true size.
 */
public class HulkbusterFistRenderer extends GeoEntityRenderer<HulkbusterFistEntity> {
	/** The forearm's middle in model pixels (x, y): it is centred on the entity. */
	private static final float CX = -9f / 16f;
	private static final float CY = 22.5f / 16f;

	public HulkbusterFistRenderer(EntityRendererProvider.Context context) {
		super(context, new IronManHulkbusterModel<>());
		this.shadowRadius = 0.3f;
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
	}

	@Override
	public void preRender(PoseStack poseStack, HulkbusterFistEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource,
			com.mojang.blaze3d.vertex.VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
			int colour) {
		for (GeoBone bone : model.topLevelBones()) {
			showOnlyFist(bone);
		}
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
	}

	private static void showOnlyFist(GeoBone bone) {
		boolean fist = "forearm_r".equals(bone.getName()) || "hand_r".equals(bone.getName());
		bone.setHidden(!fist);
		bone.setChildrenHidden(false);
		bone.updateRotation(0f, 0f, 0f);
		bone.updatePosition(0f, 0f, 0f);
		bone.updateScale(1f, 1f, 1f);
		for (GeoBone child : bone.getChildBones()) {
			showOnlyFist(child);
		}
	}

	@Override
	protected void applyRotations(HulkbusterFistEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick,
			float nativeScale) {
		super.applyRotations(entity, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
		poseStack.mulPose(Axis.XP.rotationDegrees(entity.getViewXRot(partialTick)));
		poseStack.mulPose(Axis.XP.rotationDegrees(90f)); // the arm lies along the flight line, fist leading
		poseStack.translate(CX, -CY, 0f); // GeckoLib mirrors bedrock X: the right forearm renders at +9 px, CX is -9 px
	}
}
