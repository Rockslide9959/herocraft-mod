package com.projecthero.mod.client.symbiote.monster;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.symbiote.monster.SymbioteMonster;
import com.projecthero.mod.symbiote.monster.SymbioteMonsterState;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.17.5 Symbiote Monster form, first person: instead of the hands, the monster's own huge forearms and clawed hands (the body
 * model's {@code forearm_*} / {@code hand_*} / {@code finger_*} / {@code thumb_*} / {@code spur_*} bones, in the host's colours) reach
 * up from the bottom corners, claws forward. Swinging drives the right claw forward; the monster's moves rake both (R the right, G both
 * down, C both in). Called from {@code ItemInHandRendererSymbioteMonsterMixin}.
 */
public final class SymbioteMonsterFirstPerson {
	private static final Arms ARMS = new Arms();
	public static float SCALE = 0.42f;
	private static Renderer renderer;

	private SymbioteMonsterFirstPerson() {
	}

	public static boolean active(LocalPlayer player) {
		return SymbioteMonster.isMonster(player) && !player.isInvisible();
	}

	public static void render(float partialTick, PoseStack poseStack, MultiBufferSource buffer, LocalPlayer player, int packedLight) {
		if (renderer == null) {
			renderer = new Renderer();
		}
		SymbioteMonsterState s = SymbioteMonster.state(player);
		float swing = player.getAttackAnim(partialTick);
		float punch = Mth.sin(Mth.sqrt(swing) * Mth.PI);
		float age = player.level().getGameTime() - s.moveStart() + partialTick;
		float rightRake = 0f, leftRake = 0f, drop = 0f;
		switch (s.move()) {
			case SymbioteMonsterState.MOVE_SWIPE -> rightRake = rake(age, 4f, 7f, 18f);
			case SymbioteMonsterState.MOVE_POUND -> {
				drop = age < 8f ? -0.6f * Mth.sin(age / 8f * Mth.HALF_PI) : rake(age, 8f, 10f, 22f);
			}
			case SymbioteMonsterState.MOVE_DEVOUR, SymbioteMonsterState.MOVE_PULL -> {
				rightRake = rake(age, 4f, 6f, 18f);
				leftRake = rightRake;
			}
			default -> {
			}
		}
		float emerge = s.phase() == SymbioteMonsterState.MONSTER
				? Mth.clamp((player.level().getGameTime() - s.phaseStart() + partialTick) / 12f, 0f, 1f) : 1f;
		float rise = (1f - emerge) * -1.2f;
		float bob = Mth.sin((player.tickCount + partialTick) * 0.1f) * 0.02f;
		renderer.owner = player;
		drawArm(poseStack, buffer, packedLight, true, 0.9f, -0.92f + bob + rise + drop * 0.6f, -1.6f - Math.max(punch, rightRake) * 0.7f,
				Math.max(punch, rightRake), drop);
		drawArm(poseStack, buffer, packedLight, false, -0.9f, -0.96f - bob + rise + drop * 0.6f, -1.65f - leftRake * 0.7f, leftRake, drop);
	}

	/** 0 -> 1 over [a, b] ticks, back to 0 by c. */
	private static float rake(float t, float a, float b, float c) {
		if (t < a) {
			return t / a * 0.25f;
		}
		if (t < b) {
			return 0.25f + 0.75f * (t - a) / (b - a);
		}
		return t < c ? 1f - (t - b) / (c - b) : 0f;
	}

	private static void drawArm(PoseStack poseStack, MultiBufferSource buffer, int light, boolean right, float x, float y, float z,
			float punch, float drop) {
		poseStack.pushPose();
		poseStack.translate(x, y, z);
		poseStack.scale(SCALE, SCALE, SCALE);
		poseStack.mulPose(Axis.YP.rotationDegrees(right ? 12f : -12f));
		poseStack.mulPose(Axis.XP.rotationDegrees(-6f - punch * 16f + drop * 40f));
		poseStack.mulPose(Axis.ZP.rotationDegrees(right ? -10f : 10f));
		// lay the forearm along the view: the elbow (model +Y) down toward the camera, the claws up and into the screen
		poseStack.mulPose(Axis.XP.rotationDegrees(102f));
		renderer.side = right;
		renderer.render(poseStack, ARMS, buffer, null, null, light, 0f);
		poseStack.popPose();
	}

	static void showAll(GeoBone bone) {
		bone.setHidden(false);
		bone.setChildrenHidden(false);
		for (GeoBone child : bone.getChildBones()) {
			showAll(child);
		}
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
		LocalPlayer owner;

		Renderer() {
			super(new SymbioteMonsterModel<>());
		}

		@Override
		public ResourceLocation getTextureLocation(Arms animatable) {
			return owner != null ? SymbioteMonsterTint.texture(owner) : SymbioteMonsterTint.CLASSIC;
		}

		@Override
		public RenderType getRenderType(Arms animatable, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
			return RenderType.entityCutoutNoCull(texture);
		}

		@Override
		public void preRender(PoseStack poseStack, Arms animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
				boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
			super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
			if (isReRender) {
				return;
			}
			poseStack.translate(-0.5f, -0.51f, -0.5f); // undo the object renderer's block-centring
			String s = side ? "_r" : "_l";
			for (GeoBone bone : model.topLevelBones()) {
				showOnly(bone, s);
			}
			// centre the forearm (bedrock x -+27.5 px, mirrored by GeckoLib, y 22..51) and hand on the origin
			poseStack.translate((side ? -27.5f : 27.5f) / 16f, -30f / 16f, 0f);
		}

		private static void showOnly(GeoBone bone, String side) {
			String n = bone.getName();
			boolean keep = n.equals("forearm" + side) || n.equals("hand" + side) || n.startsWith("finger" + side) || n.equals("thumb" + side)
					|| n.startsWith("spur" + side);
			bone.setHidden(!keep);
			bone.setChildrenHidden(false);
			// the arm hangs straight in the bind pose; the fingers keep a curl
			var init = bone.getInitialSnapshot();
			if (n.startsWith("finger") || n.startsWith("thumb")) {
				bone.updateRotation(init.getRotX(), init.getRotY(), init.getRotZ());
			} else {
				bone.updateRotation(0f, 0f, 0f);
			}
			bone.updatePosition(0f, 0f, 0f);
			// the claws drawn bigger in first person (at body scale they read as small prongs past the huge forearm)
			boolean claw = n.startsWith("finger" + side) && n.endsWith("c");
			float k = claw ? 1.7f : 1f;
			bone.updateScale(k, k, k);
			for (GeoBone child : bone.getChildBones()) {
				showOnly(child, side);
			}
		}
	}
}
