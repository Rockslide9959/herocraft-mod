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
 * v0.17.5 Symbiote Monster form, first person: instead of the hands, the monster's own forearms and clawed hands (the body model's
 * {@code forearm_*} / {@code hand_*} / {@code finger_*} / {@code thumb_*} / {@code spur_*} bones, in the host's colours). Called from
 * {@code ItemInHandRendererSymbioteMonsterMixin}.
 *
 * <p>Polish pass: the forearms come in from the bottom corners angled toward the middle like a big creature's, palms turned in and the
 * claws fanned toward each other low on the screen (well clear of the crosshair), breathing and flexing at rest. Every move has its
 * own motion ({@link #armPose}): left click rakes the right claws forward, R winds out and slashes across the screen, G raises both
 * fists and slams them down, Z flings both arms wide with the claws splayed and shaking, C lunges both claws in, seizes and drags the
 * prey down, V thrusts out and yanks back, X crouches and throws the claws up for the pounce, the landing smashes down. The pose
 * maths is twinned in {@code scratchpad/symbiote/fp_pose_monster_v0175.js} (software preview {@code fp_preview_monster_v0175.js}).
 */
public final class SymbioteMonsterFirstPerson {
	private static final Arms ARMS = new Arms();
	public static float SCALE = 0.4f;
	/** The claws drawn bigger in first person (at body scale they read as small prongs past the huge forearm). */
	static final float CLAW_SCALE = 1.45f;
	// pose fields (camera-space blocks / degrees; the bone extras in bedrock degrees, like the generator)
	static final int X = 0, Y = 1, Z = 2, YAW = 3, PITCH = 4, ROLL = 5, WRIST = 6, CURL = 7, CLAW_CURL = 8, SPREAD = 9, FIELDS = 10;
	private static final float[] REST = { 1.0f, -0.8f, -1.25f, 24f, 30f, -75f, -10f, 6f, -12f, 15f };
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
		float emerge = s.phase() == SymbioteMonsterState.MONSTER
				? Mth.clamp((player.level().getGameTime() - s.phaseStart() + partialTick) / 12f, 0f, 1f) : 1f;
		float time = player.tickCount + partialTick;
		renderer.owner = player;
		for (boolean right : new boolean[] { true, false }) {
			drawArm(poseStack, buffer, packedLight, right, armPose(s.move(), age, time, emerge, punch, right));
		}
	}

	/** Smoothstepped keys {@code t0, v0, t1, v1, ...}. */
	static float k(float t, float... kv) {
		if (t <= kv[0]) {
			return kv[1];
		}
		for (int i = 0; i + 3 < kv.length; i += 2) {
			if (t <= kv[i + 2]) {
				float u = Mth.clamp((t - kv[i]) / (kv[i + 2] - kv[i]), 0f, 1f);
				return kv[i + 1] + (kv[i + 3] - kv[i + 1]) * (u * u * (3f - 2f * u));
			}
		}
		return kv[kv.length - 1];
	}

	/**
	 * One arm's pose: rest + the idle breath + the move at {@code t} ticks + the left-click rake ({@code punch} 0..1). Canonical values
	 * are for the right arm; {@link #drawArm} mirrors x, yaw and roll for the left.
	 */
	static float[] armPose(int move, float t, float time, float emerge, float punch, boolean right) {
		float[] a = REST.clone();
		float ph = right ? 0f : 1.7f;
		a[Y] += 0.025f * Mth.sin(time * 0.1f + ph) - (1f - emerge) * 1.2f;
		a[PITCH] += 2f * Mth.sin(time * 0.1f + ph);
		a[ROLL] += 3f * Mth.sin(time * 0.07f + ph);
		a[CURL] += 5f * Mth.sin(time * 0.13f + ph);
		float[] d = new float[FIELDS];
		switch (move) {
			case SymbioteMonsterState.MOVE_SWIPE -> { // wind out to the right, slash across the screen to the left (hit on tick 6)
				if (right) {
					d[X] = k(t, 0, 0, 4, 0.45f, 7, -0.75f, 11, -1.25f, 18, 0);
					d[Y] = k(t, 0, 0, 4, 0.3f, 7, 0.3f, 11, 0.15f, 18, 0);
					d[Z] = k(t, 0, 0, 4, 0.05f, 7, -0.45f, 11, -0.4f, 18, 0);
					d[YAW] = k(t, 0, 0, 4, -35, 7, 46, 11, 56, 18, 0);
					d[PITCH] = k(t, 0, 0, 4, 10, 7, -10, 11, -25, 18, 0);
					d[ROLL] = k(t, 0, 0, 4, -20, 7, -45, 11, -55, 18, 0);
					d[CURL] = k(t, 0, 0, 4, 24, 7, -12, 18, 0);
					d[SPREAD] = k(t, 0, 0, 4, 12, 9, 12, 18, 0);
				} else {
					d[X] = k(t, 0, 0, 5, 0.12f, 9, 0.18f, 18, 0);
					d[Y] = k(t, 0, 0, 5, -0.08f, 9, -0.12f, 18, 0);
					d[YAW] = k(t, 0, 0, 7, -10, 18, 0);
				}
			}
			case SymbioteMonsterState.MOVE_POUND -> { // both fists up overhead, then slammed down (impact on tick 10)
				d[X] = k(t, 0, 0, 8, -0.32f, 10, -0.25f, 22, 0);
				d[Y] = k(t, 0, 0, 8, 0.8f, 10, 0.12f, 16, 0.1f, 22, 0);
				d[Z] = k(t, 0, 0, 8, -0.15f, 10, -0.3f, 22, 0);
				d[PITCH] = k(t, 0, 0, 8, 58, 10, 0, 16, 2, 22, 0);
				d[ROLL] = k(t, 0, 0, 8, 35, 16, 35, 22, 0);
				d[CURL] = k(t, 0, 0, 6, -60, 16, -60, 22, 0);
				d[CLAW_CURL] = k(t, 0, 0, 6, -35, 16, -35, 22, 0);
				d[SPREAD] = k(t, 0, 0, 6, -15, 16, -15, 22, 0);
				float quake = t > 10 && t < 15 ? (15 - t) / 5f : 0f;
				d[Y] += 0.05f * quake * Mth.sin(t * 3.1f + (right ? 0 : 1.3f));
			}
			case SymbioteMonsterState.MOVE_ROAR -> { // both arms flung out, claws splayed, shaking with the roar (blast on tick 6)
				float tr = t > 5 && t < 24 ? 1f : 0f;
				d[X] = k(t, 0, 0, 5, 0.55f, 22, 0.55f, 30, 0) + 0.018f * tr * Mth.sin(t * 2.7f + (right ? 0 : 2));
				d[Y] = k(t, 0, 0, 5, 0.3f, 22, 0.3f, 30, 0) + 0.018f * tr * Mth.sin(t * 3.3f + (right ? 1 : 0));
				d[Z] = k(t, 0, 0, 5, -0.25f, 22, -0.25f, 30, 0);
				d[YAW] = k(t, 0, 0, 5, -9, 22, -9, 30, 0);
				d[PITCH] = k(t, 0, 0, 5, 10, 22, 10, 30, 0);
				d[ROLL] = k(t, 0, 0, 5, -65, 22, -65, 30, 0);
				d[CURL] = k(t, 0, 0, 5, 30, 22, 30, 30, 0);
				d[CLAW_CURL] = k(t, 0, 0, 5, 18, 22, 18, 30, 0);
				d[SPREAD] = k(t, 0, 0, 5, 25, 22, 25, 30, 0);
			}
			case SymbioteMonsterState.MOVE_DEVOUR -> { // both claws lunge in, seize (tick 6), drag the prey down to the jaws (tick 18)
				d[X] = k(t, 0, 0, 5, -0.48f, 8, -0.52f, 16, -0.55f, 22, -0.5f, 30, 0);
				d[Y] = k(t, 0, 0, 5, 0.14f, 8, 0.1f, 16, -0.2f, 22, -0.22f, 30, 0);
				d[Z] = k(t, 0, 0, 5, -0.55f, 8, -0.5f, 16, 0.05f, 22, 0.05f, 30, 0);
				d[YAW] = k(t, 0, 0, 5, 15, 16, 25, 30, 0);
				d[PITCH] = k(t, 0, 0, 5, 10, 16, 0, 30, 0);
				d[CURL] = k(t, 0, 0, 4, 32, 7, -55, 22, -55, 30, 0);
				d[CLAW_CURL] = k(t, 0, 0, 4, 15, 7, -30, 22, -30, 30, 0);
				d[SPREAD] = k(t, 0, 0, 4, 25, 7, 0, 30, 0);
			}
			case SymbioteMonsterState.MOVE_PULL -> { // claws thrust out, then yanked back to the chest (the yank on tick 6)
				float u = right ? t : t - 1f;
				d[X] = k(u, 0, 0, 4, -0.2f, 10, 0.05f, 20, 0);
				d[Y] = k(u, 0, 0, 4, 0.12f, 10, -0.1f, 20, 0);
				d[Z] = k(u, 0, 0, 4, -0.6f, 6, -0.6f, 10, 0.12f, 14, 0.12f, 20, 0);
				d[PITCH] = k(u, 0, 0, 4, 15, 10, 0, 20, 0);
				d[CURL] = k(u, 0, 0, 3, 30, 6, -55, 14, -55, 20, 0);
				d[CLAW_CURL] = k(u, 0, 0, 3, 15, 6, -30, 14, -30, 20, 0);
				d[SPREAD] = k(u, 0, 0, 3, 25, 6, 0, 20, 0);
			}
			case SymbioteMonsterState.MOVE_LEAP -> { // crouch (launched on tick 3), then claws up and forward, held in the air
				d[Y] = k(t, 0, 0, 3, -0.28f, 7, 0.32f);
				d[PITCH] = k(t, 0, 0, 3, -15, 7, 42);
				d[X] = k(t, 0, 0, 7, -0.12f);
				d[Z] = k(t, 0, 0, 7, -0.2f);
				d[CURL] = k(t, 0, 0, 7, 28);
				d[SPREAD] = k(t, 0, 0, 7, 22);
			}
			case SymbioteMonsterState.MOVE_LAND -> { // the landing smash
				d[Y] = k(t, 0, -0.2f, 4, -0.2f, 14, 0);
				d[PITCH] = k(t, 0, -12, 4, -12, 14, 0);
				d[X] = k(t, 0, 0.12f, 14, 0);
				d[CURL] = k(t, 0, -50, 4, -50, 14, 0);
				d[CLAW_CURL] = k(t, 0, -25, 14, 0);
			}
			default -> {
			}
		}
		if (right && punch > 0f) { // left click: a quick forward rake with the right claws
			d[X] += -0.45f * punch;
			d[Y] += 0.15f * punch;
			d[Z] += -0.5f * punch;
			d[YAW] += 40f * punch;
			d[PITCH] += 4f * punch;
			d[ROLL] += -40f * punch;
			d[CURL] += -30f * punch;
			d[CLAW_CURL] += -15f * punch;
		}
		for (int i = 0; i < FIELDS; i++) {
			a[i] += d[i];
		}
		return a;
	}

	private static void drawArm(PoseStack poseStack, MultiBufferSource buffer, int light, boolean right, float[] a) {
		float m = right ? 1f : -1f;
		poseStack.pushPose();
		poseStack.translate(a[X] * m, a[Y], a[Z]);
		poseStack.scale(SCALE, SCALE, SCALE);
		poseStack.mulPose(Axis.YP.rotationDegrees(a[YAW] * m));
		poseStack.mulPose(Axis.XP.rotationDegrees(a[PITCH]));
		poseStack.mulPose(Axis.ZP.rotationDegrees(a[ROLL] * m));
		// lay the hanging forearm along the view: the claws (model -Y) forward, the palm (model front) turned down, the elbow back
		poseStack.mulPose(Axis.XP.rotationDegrees(90f));
		poseStack.mulPose(Axis.YP.rotationDegrees(180f));
		renderer.side = right;
		renderer.pose = a;
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
		float[] pose = REST;
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
				showOnly(bone, s, side, pose);
			}
			// centre the forearm (bedrock x -+27.5 px, mirrored by GeckoLib, y 22..51) and hand on the origin
			poseStack.translate((side ? -27.5f : 27.5f) / 16f, -30f / 16f, 0f);
		}

		/**
		 * Shows only this side's forearm and hand, straight in the bind pose with the pose's bone extras on top -- the wrist, the finger
		 * curl and spread, the claw curl (bedrock degrees; GeckoLib's mirrored bake negates x / y rotations).
		 */
		private static void showOnly(GeoBone bone, String side, boolean right, float[] a) {
			String n = bone.getName();
			boolean keep = n.equals("forearm" + side) || n.equals("hand" + side) || n.startsWith("finger" + side) || n.equals("thumb" + side)
					|| n.startsWith("spur" + side);
			bone.setHidden(!keep);
			bone.setChildrenHidden(false);
			var init = bone.getInitialSnapshot();
			float d2r = Mth.DEG_TO_RAD;
			boolean claw = n.startsWith("finger" + side) && n.endsWith("c");
			if (claw) {
				bone.updateRotation(init.getRotX() - a[CLAW_CURL] * d2r, init.getRotY(), init.getRotZ());
			} else if (n.startsWith("finger" + side)) {
				int idx = n.charAt(n.length() - 1) - '0';
				float spread = (idx - 1.5f) * a[SPREAD] * (right ? 1f : -1f);
				bone.updateRotation(init.getRotX() - a[CURL] * d2r, init.getRotY(), init.getRotZ() + spread * d2r);
			} else if (n.equals("thumb" + side)) {
				bone.updateRotation(init.getRotX() - a[CURL] * 0.6f * d2r, init.getRotY(), init.getRotZ());
			} else if (n.startsWith("finger") || n.startsWith("thumb")) {
				bone.updateRotation(init.getRotX(), init.getRotY(), init.getRotZ());
			} else if (n.equals("hand" + side)) {
				bone.updateRotation(-a[WRIST] * d2r, 0f, 0f);
			} else {
				bone.updateRotation(0f, 0f, 0f);
			}
			bone.updatePosition(0f, 0f, 0f);
			float k = claw ? CLAW_SCALE : 1f;
			bone.updateScale(k, k, k);
			for (GeoBone child : bone.getChildBones()) {
				showOnly(child, side, right, a);
			}
		}
	}
}
