package com.projecthero.mod.client.render.raid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.entity.AcidGlobEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * v0.16.1: the Acid Zombie's glob -- a glowing, wobbling blob (geo/raid/acid_glob) that tumbles in flight, full-bright, with
 * the additive halo. Replaces the vanilla slime-ball item sprite.
 */
public class AcidGlobRenderer extends GeoEntityRenderer<AcidGlobEntity> {
	public AcidGlobRenderer(EntityRendererProvider.Context context) {
		super(context, new GeoModel<AcidGlobEntity>() {
			@Override
			public ResourceLocation getModelResource(AcidGlobEntity animatable) {
				return RaidModelFit.geo("acid_glob");
			}

			@Override
			public ResourceLocation getTextureResource(AcidGlobEntity animatable) {
				return RaidModelFit.texture("acid_glob");
			}

			@Override
			public ResourceLocation getAnimationResource(AcidGlobEntity animatable) {
				return ProjectHeroMod.id("animations/raid/acid_glob.animation.json");
			}
		});
		this.shadowRadius = 0.15f;
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
		addRenderLayer(new RaidHaloLayer<>(this, g -> 0x99D07A));
	}

	@Override
	protected int getBlockLightLevel(AcidGlobEntity entity, BlockPos pos) {
		return 15;
	}

	@Override
	public void preRender(PoseStack poseStack, AcidGlobEntity glob, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
			boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
		super.preRender(poseStack, glob, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
		if (!isReRender) {
			float t = glob.tickCount + partialTick;
			poseStack.translate(0, 0.08, 0);
			poseStack.mulPose(Axis.YP.rotationDegrees(t * 23f));
			poseStack.mulPose(Axis.XP.rotationDegrees(t * 31f));
			float w = 1.0f + 0.12f * Mth.sin(t * 0.9f);
			poseStack.scale(0.9f * w, 0.9f / w, 0.9f * w);
			poseStack.translate(0, -0.16, 0);
		}
	}
}
