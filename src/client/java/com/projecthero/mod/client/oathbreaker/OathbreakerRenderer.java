package com.projecthero.mod.client.oathbreaker;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import com.projecthero.mod.oathbreaker.OathbreakerTuning;
import com.projecthero.mod.oathbreaker.entity.OathbreakerEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.util.Color;
import software.bernie.geckolib.util.RenderUtil;

/**
 * Renders the Oathbreaker through GeckoLib. The model is hand-built at a {@code MODEL_HEIGHT} reference
 * scale (vanilla-player proportions), then stretched to the entity's real bounding-box height here -- the
 * same trick {@code TitanFormRenderer}/{@code BehemothRenderer} use.
 *
 * <p>The sword is the real vanilla netherite sword item, drawn at the model's empty {@code sword} bone, so
 * every clip's sword track swings it exactly as it swung the old flat-coloured blade geometry. v0.13.9: the
 * v0.14.0 full-bright glow layer (eyes, soul-fire cracks) is gone -- he doesn't glow; the cracks are still
 * painted into the phase textures, lit like the rest of him.
 */
public class OathbreakerRenderer extends GeoEntityRenderer<OathbreakerEntity> {
	private static final ItemStack SWORD = new ItemStack(Items.NETHERITE_SWORD);
	/** Item units == blocks at model scale (before the renderer's height stretch); tuned so the blade is
	 * about as long as the arm and its point just reaches the ground at rest, like the old blade. */
	private static final float SWORD_SCALE = 0.72f;
	/** Distance from the sprite's centre to its grip once the blade points straight down (sprite units):
	 * the vanilla sword's grip sits at about pixel (3.5, 12.5), i.e. (-0.28, -0.28) from centre, which is
	 * 0.28 * sqrt(2) = 0.397 along the blade axis. */
	private static final float GRIP_OFFSET = 0.40f;

	public OathbreakerRenderer(EntityRendererProvider.Context context) {
		super(context, new OathbreakerModel());
		this.shadowRadius = 0.9f;
		addRenderLayer(new BlockAndItemGeoLayer<>(this) {
			@Override
			protected ItemStack getStackForBone(GeoBone bone, OathbreakerEntity animatable) {
				return "sword".equals(bone.getName()) ? SWORD : null;
			}

			@Override
			protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, OathbreakerEntity animatable) {
				return ItemDisplayContext.NONE;
			}

			/**
			 * Replaces GeckoLib's own placement, which calls {@code translateAndRotateMatrixForBone} on top of
			 * a pose stack the entity renderer has ALREADY fully transformed for this bone -- applying the
			 * bone's own rotation a second time. Here: only move to the pivot (the wrist), then orient.
			 */
			@Override
			public void renderForBone(PoseStack poseStack, OathbreakerEntity animatable, GeoBone bone, RenderType renderType,
					MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
				ItemStack stack = getStackForBone(bone, animatable);
				// the item renderer can't fade, so the sword goes with his soul halfway through the fade
				if (stack == null || deathAlpha(animatable, partialTick) < 0.5f) {
					return;
				}
				poseStack.pushPose();
				RenderUtil.translateToPivotPoint(poseStack, bone);
				// grip onto the wrist, blade hanging straight down along the arm, edge (not flat) leading so a
				// swing in the arm's plane cuts with the edge; the sprite's blade runs corner to corner, hence -135
				poseStack.translate(0.0f, -GRIP_OFFSET * SWORD_SCALE, 0.0f);
				poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
				poseStack.mulPose(Axis.ZP.rotationDegrees(-135.0f));
				poseStack.scale(SWORD_SCALE, SWORD_SCALE, SWORD_SCALE);
				Minecraft.getInstance().getItemRenderer().renderStatic(animatable, stack, ItemDisplayContext.NONE, false,
						poseStack, bufferSource, animatable.level(), packedLight, packedOverlay, animatable.getId());
				poseStack.popPose();
			}
		});
	}

	/** 1 while alive; during the 3s death it holds while he sinks to his knees, then fades to 0 as his soul
	 * leaves (vanilla's deathTime runs client-side too). */
	private static float deathAlpha(OathbreakerEntity animatable, float partialTick) {
		if (animatable.deathTime <= 0) {
			return 1.0f;
		}
		float t = animatable.deathTime + partialTick - OathbreakerTuning.DEATH_FADE_START_TICKS;
		float span = OathbreakerTuning.DEATH_TICKS - OathbreakerTuning.DEATH_FADE_START_TICKS;
		return Math.max(0.0f, Math.min(1.0f, 1.0f - t / span));
	}

	/** No vanilla tip-over-sideways on death -- the {@code death} clip kneels him instead. */
	@Override
	protected float getDeathMaxRotation(OathbreakerEntity animatable) {
		return 0.0f;
	}

	@Override
	public Color getRenderColor(OathbreakerEntity animatable, float partialTick, int packedLight) {
		float alpha = deathAlpha(animatable, partialTick);
		return alpha >= 1.0f ? super.getRenderColor(animatable, partialTick, packedLight) : Color.ofRGBA(1.0f, 1.0f, 1.0f, alpha);
	}

	@Override
	public RenderType getRenderType(OathbreakerEntity animatable, ResourceLocation texture, MultiBufferSource bufferSource,
			float partialTick) {
		// translucent only while fading -- cutout is cheaper and sorts correctly the rest of the time
		return deathAlpha(animatable, partialTick) < 1.0f ? RenderType.entityTranslucent(texture)
				: super.getRenderType(animatable, texture, bufferSource, partialTick);
	}

	@Override
	public void preRender(PoseStack poseStack, OathbreakerEntity animatable, BakedGeoModel model,
			MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight,
			int packedOverlay, int colour) {
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
		// Main pass only. Render layers that re-render the model go through reRender, which calls preRender
		// again INSIDE the main pass's already-scaled pose stack -- scaling again drew v0.14.0's glow layer at
		// 4x, as giant floating crack shapes above his head.
		if (isReRender) {
			return;
		}
		float scale = animatable.getBbHeight() / OathbreakerEntity.MODEL_HEIGHT;
		if (Math.abs(scale - 1.0f) > 0.01f) {
			poseStack.scale(scale, scale, scale);
		}
	}
}
