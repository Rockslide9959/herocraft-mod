package com.projecthero.mod.client.titanshifter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.projecthero.mod.titanshifter.entity.TitanFormEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Renders a Titan form through GeckoLib. In first person the rider's own Titan hides its head (the camera
 * sits inside it) so the view is clear; everyone else, and the rider in third person, see the whole Titan.
 */
public class TitanFormRenderer extends GeoEntityRenderer<TitanFormEntity> {
	public TitanFormRenderer(EntityRendererProvider.Context context) {
		super(context, new TitanFormModel());
		this.shadowRadius = 3.0f;
	}

	@Override
	public void preRender(PoseStack poseStack, TitanFormEntity animatable, BakedGeoModel model,
			MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
			int packedLight, int packedOverlay, int colour) {
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight,
				packedOverlay, colour);
		float scale = animatable.getBbHeight() / animatable.titanType().modelHeight();
		if (Math.abs(scale - 1.0f) > 0.01f) {
			poseStack.scale(scale, scale, scale);
		}
		Minecraft mc = Minecraft.getInstance();
		boolean firstPersonRider = mc.options.getCameraType().isFirstPerson() && mc.player != null
				&& mc.player.getVehicle() == animatable;
		model.getBone("head").ifPresent(bone -> bone.setHidden(firstPersonRider));
	}
}
