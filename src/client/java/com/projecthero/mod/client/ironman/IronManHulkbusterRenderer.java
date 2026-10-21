package com.projecthero.mod.client.ironman;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * v0.16.1: draws the Hulkbuster body ({@link IronManHulkbusterAnimatable}) in place of a player wearing the full Mark 8
 * -- so the vanilla body, the armour pieces, the cape and held items are all replaced, like the Hulk. Called from
 * {@code PlayerRendererHulkbusterMixin}. GeckoLib scales a replaced entity by its scale attribute (the Mark 8's 1.45x);
 * the body is modelled at its true size (v0.16.7: a Steve player model x1.5, 3 blocks at model scale 1), so that scale is divided back out here.
 * The glowmask (eyes, reactor, palms, thrusters) is drawn full-bright by the auto-glow layer.
 */
public class IronManHulkbusterRenderer extends GeoReplacedEntityRenderer<AbstractClientPlayer, IronManHulkbusterAnimatable> {
	private static IronManHulkbusterRenderer instance;

	public IronManHulkbusterRenderer(EntityRendererProvider.Context context) {
		super(context, new IronManHulkbusterModel<>(), IronManHulkbusterAnimatable.INSTANCE);
		this.shadowRadius = 1.0f;
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
	}

	public static void rebuild(EntityRendererProvider.Context context) {
		instance = new IronManHulkbusterRenderer(context);
	}

	public static IronManHulkbusterRenderer get() {
		return instance;
	}

	/** Render {@code player} as the Hulkbuster at model scale 1 (undoing the scale attribute GeckoLib applies). */
	public void renderBody(AbstractClientPlayer player, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
			int packedLight) {
		float s = Math.max(0.01f, player.getScale());
		poseStack.pushPose();
		poseStack.scale(1f / s, 1f / s, 1f / s);
		try {
			render(player, entityYaw, partialTick, poseStack, buffer, packedLight);
		} finally {
			poseStack.popPose();
		}
	}

	/** The baked model is shared with the Rocket Fist / first-person arms, which hide bones: show everything again. */
	@Override
	public void preRender(PoseStack poseStack, IronManHulkbusterAnimatable animatable, software.bernie.geckolib.cache.object.BakedGeoModel model,
			MultiBufferSource bufferSource, com.mojang.blaze3d.vertex.VertexConsumer buffer, boolean isReRender, float partialTick,
			int packedLight, int packedOverlay, int colour) {
		if (!isReRender) {
			for (software.bernie.geckolib.cache.object.GeoBone bone : model.topLevelBones()) {
				showAll(bone);
			}
		}
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
	}

	static void showAll(software.bernie.geckolib.cache.object.GeoBone bone) {
		bone.setHidden(false);
		bone.setChildrenHidden(false);
		for (software.bernie.geckolib.cache.object.GeoBone child : bone.getChildBones()) {
			showAll(child);
		}
	}

	@Override
	public boolean shouldShowName(AbstractClientPlayer player) {
		Minecraft mc = Minecraft.getInstance();
		return Minecraft.renderNames() && player != mc.getCameraEntity() && mc.player != null && !player.isInvisibleTo(mc.player)
				&& !player.isVehicle();
	}
}
