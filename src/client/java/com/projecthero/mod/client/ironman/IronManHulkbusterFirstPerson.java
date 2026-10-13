package com.projecthero.mod.client.ironman;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.16.1: first person in the full Mark 8 -- instead of the player's hands, the Hulkbuster's own huge forearms and fists
 * (the body model's {@code forearm_*} / {@code hand_*} bones and texture) reach in from the bottom corners of the screen,
 * fists forward. A punch / swing drives the right fist forward; the Rocket Fist hides the right one while it is away
 * (the server's pose clock, like the body). Called from {@code ItemInHandRendererHulkbusterMixin}.
 */
public final class IronManHulkbusterFirstPerson {
	private static final Arms ARMS = new Arms();
	private static Renderer renderer;

	private IronManHulkbusterFirstPerson() {
	}

	public static void render(float partialTick, PoseStack poseStack, MultiBufferSource buffer, LocalPlayer player, int packedLight) {
		if (renderer == null) {
			renderer = new Renderer();
		}
		float swing = player.getAttackAnim(partialTick);
		float punch = Mth.sin(Mth.sqrt(swing) * Mth.PI);
		IronManAbilityPose.Play play = IronManAbilityPose.current(player);
		boolean fistAway = false;
		if (play != null && play.anim() == com.projecthero.mod.ironman.IronManAbilityFx.HB_ROCKET_FIST) {
			float t = IronManAbilityPose.age(play);
			fistAway = t >= 13f && t <= 34f;
			if (t < 13f) {
				punch = Math.max(punch, t / 13f * 0.5f);
			}
		} else if (play != null && com.projecthero.mod.ironman.IronManAbilityFx.hulkbusterClip(play.anim()) != null) {
			float t = IronManAbilityPose.age(play);
			punch = Math.max(punch, 0.35f * Mth.sin(t * 0.35f) * Mth.sin(t * 0.35f));
		}
		float bob = Mth.sin((player.tickCount + partialTick) * 0.12f) * 0.012f;
		if (!fistAway) {
			drawArm(poseStack, buffer, packedLight, partialTick, true, 1.0f, -1.0f + bob, -2.0f - punch * 0.7f, punch);
		}
		drawArm(poseStack, buffer, packedLight, partialTick, false, -1.0f, -1.05f - bob, -2.05f, 0f);
	}

	private static void drawArm(PoseStack poseStack, MultiBufferSource buffer, int light, float partialTick, boolean right, float x, float y,
			float z, float punch) {
		poseStack.pushPose();
		poseStack.translate(x, y, z);
		// the forearm points into the screen, tipped slightly in toward the centre, the fist leading
		poseStack.mulPose(Axis.YP.rotationDegrees(right ? 8f : -8f));
		poseStack.mulPose(Axis.XP.rotationDegrees(-8f - punch * 10f));
		// lay the forearm along the view, raised: the elbow (model +Y) down and back toward the camera, the fist up and
		// leading into the screen
		poseStack.mulPose(Axis.XP.rotationDegrees(118f));
		renderer.side = right;
		renderer.render(poseStack, ARMS, buffer, null, null, light, partialTick);
		poseStack.popPose();
	}

	/** A bare animatable for the arm draw (no animations of its own). */
	static final class Arms implements GeoAnimatable {
		private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

		@Override
		public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		}

		@Override
		public AnimatableInstanceCache getAnimatableInstanceCache() {
			return cache;
		}

		@Override
		public double getTick(Object object) {
			return 0;
		}
	}

	static final class Renderer extends GeoObjectRenderer<Arms> {
		boolean side;

		Renderer() {
			super(new IronManHulkbusterModel<>());
			addRenderLayer(new AutoGlowingGeoLayer<>(this));
		}

		@Override
		public void preRender(PoseStack poseStack, Arms animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
				boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
			super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
			if (isReRender) {
				return;
			}
			poseStack.translate(-0.5f, -0.51f, -0.5f); // undo the object renderer's block-centring
			for (GeoBone bone : model.topLevelBones()) {
				showOnly(bone, side ? "forearm_r" : "forearm_l", side ? "hand_r" : "hand_l");
			}
			// centre the forearm on the origin (drawArm has already laid it along the view)
			float cx = (side ? -15f : 15f) / 16f;
			poseStack.translate(cx, -16f / 16f, 0f); // GeckoLib mirrors bedrock X, so the right forearm sits at +cx
		}

		private static void showOnly(GeoBone bone, String a, String b) {
			boolean keep = a.equals(bone.getName()) || b.equals(bone.getName());
			bone.setHidden(!keep);
			bone.setChildrenHidden(false);
			bone.updateRotation(0f, 0f, 0f);
			bone.updatePosition(0f, 0f, 0f);
			bone.updateScale(1f, 1f, 1f);
			for (GeoBone child : bone.getChildBones()) {
				showOnly(child, a, b);
			}
		}
	}
}
