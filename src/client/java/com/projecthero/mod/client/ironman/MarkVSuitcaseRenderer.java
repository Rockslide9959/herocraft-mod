package com.projecthero.mod.client.ironman;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.item.MarkVSuitcaseItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * v0.14.21: the Mark V Suitcase as a 3D GeckoLib model -- a red briefcase with silver trim and gold latches, a handle,
 * a front shell hinged along its bottom edge, two side panels and an arc-reactor core inside. Held and dropped it is
 * the closed case; in the inventory it keeps its flat sprite ({@code item/mark_v_suitcase_icon}, the existing
 * {@code textures/item/mark_v_suitcase.png}).
 *
 * <p>{@link #openness} (0 closed .. 1 fully unfolded) is set by {@link MarkVSuitcaseLayer} around its own render of the
 * case during a Mark V suit-up / fold, and poses the bones procedurally: the front shell drops open, the side panels
 * swing out, the handle folds flat and the core swells and glows. Bone posing is applied around each bone's own render
 * and restored afterwards, so the shared item animatable is never left in an open pose.
 */
public class MarkVSuitcaseRenderer extends GeoItemRenderer<MarkVSuitcaseItem> {
	public static final ResourceLocation ICON_MODEL = ProjectHeroMod.id("item/mark_v_suitcase_icon");
	/** 0 = closed (every ordinary render) .. 1 = fully unfolded. Render thread only. */
	public static float openness = 0f;

	public MarkVSuitcaseRenderer() {
		super(new Model());
		addRenderLayer(new software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer<>(this)); // core + latch lights
	}

	@Override
	protected void renderInGui(ItemDisplayContext transformType, PoseStack poseStack, MultiBufferSource bufferSource,
			int packedLight, int packedOverlay, float partialTick) {
		BakedModel icon = Minecraft.getInstance().getModelManager().getModel(ICON_MODEL);
		if (icon == null || icon == Minecraft.getInstance().getModelManager().getMissingModel()) {
			super.renderInGui(transformType, poseStack, bufferSource, packedLight, packedOverlay, partialTick);
			return;
		}
		// the outer item render already applied this (identity) GUI transform and the -0.5 centring: undo the centring,
		// then draw the flat sprite model exactly as a plain item would be drawn
		poseStack.pushPose();
		poseStack.translate(0.5f, 0.5f, 0.5f);
		Minecraft.getInstance().getItemRenderer().render(getCurrentItemStack(), transformType, false, poseStack,
				bufferSource, packedLight, packedOverlay, icon);
		poseStack.popPose();
	}

	@Override
	public void renderRecursively(PoseStack poseStack, MarkVSuitcaseItem animatable, GeoBone bone, RenderType renderType,
			MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight,
			int packedOverlay, int colour) {
		float o = Mth.clamp(openness, 0f, 1f);
		if (o <= 0.001f) {
			super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
					packedLight, packedOverlay, colour);
			return;
		}
		float rx = bone.getRotX();
		float ry = bone.getRotY();
		float sx = bone.getScaleX();
		float sy = bone.getScaleY();
		float sz = bone.getScaleZ();
		switch (bone.getName()) {
			case "front_shell" -> bone.setRotX(rx + o * (float) Math.toRadians(105));
			case "panel_left" -> bone.setRotY(ry - o * (float) Math.toRadians(80));
			case "panel_right" -> bone.setRotY(ry + o * (float) Math.toRadians(80));
			case "handle" -> bone.setRotX(rx - o * (float) Math.toRadians(90));
			case "core" -> {
				float s = 0.2f + 1.0f * o;
				bone.setScaleX(s);
				bone.setScaleY(s);
				bone.setScaleZ(s);
			}
			default -> {
			}
		}
		super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
				packedLight, packedOverlay, colour);
		bone.setRotX(rx);
		bone.setRotY(ry);
		bone.setScaleX(sx);
		bone.setScaleY(sy);
		bone.setScaleZ(sz);
	}

	static final class Model extends GeoModel<MarkVSuitcaseItem> {
		private static final ResourceLocation GEO = ProjectHeroMod.id("geo/mark_v_suitcase.geo.json");
		private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/mark_v_suitcase.png");
		private static final ResourceLocation ANIMATION = ProjectHeroMod.id("animations/mark_v_suitcase.animation.json");

		@Override
		public ResourceLocation getModelResource(MarkVSuitcaseItem item) {
			return GEO;
		}

		@Override
		public ResourceLocation getTextureResource(MarkVSuitcaseItem item) {
			return TEXTURE;
		}

		@Override
		public ResourceLocation getAnimationResource(MarkVSuitcaseItem item) {
			return ANIMATION;
		}
	}
}
