package com.projecthero.mod.client.omnitrix.alien.wildmutt;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.17.1: Wildmutt's first-person arms -- his own orange forearm and paw from the body model (the {@code forearm_*} /
 * {@code paw_*} bones and texture), drawn where vanilla draws the player's arm. The wrist is bent so the three dark claws
 * point into the view; the claws flex with the swipe. Both paws are always in view ({@code bothHandsInFirstPerson}; paws hold
 * nothing, so a held item never replaces them).
 *
 * <p>Rework 2 (user: "the arms dont follow the player in first person"): they move like real first-person hands -- laid
 * out from vanilla's hand pose (view bob, hurt shake, the sway and lag as you turn and look, plus a little extra trail), a
 * striding walk bob (the paws reach and lift in turn), a rear-up-and-rake swipe on Attack, and each move's
 * {@code FirstPersonClip} on top. Smaller and narrower, like the slim new model.
 *
 * <p>The baked model is GeckoLib's shared one: the bones' visibility and rotations are saved before the draw and put back
 * after, so the third-person body never sees this pass.
 */
public final class WildmuttFirstPerson {
	/** The forearm + hand drawn this big (blocks per model pixel x 16). */
	public static float SCALE = 0.72f; // v0.17.1 rework 3: the thicker paws drawn a touch smaller, so they fill the corners as before
	/** Where the elbow sits in camera space (x toward the arm's side, y up, z negative = ahead). */
	public static float EX = 0.48f, EY = -0.35f, EZ = -0.78f;
	/** How far the forearm swings forward / in toward the centre, degrees. */
	public static float TILT = 45f, TOE_IN = 12f, ROLL = 6f;
	/** The wrist bend (degrees, GeckoLib's sign) and the elbow's. */
	public static float WRIST = 90f, ELBOW = 0f;
	/** v0.17.1 rework 3: the elbow pivot of the thick forearm (json px; gen_wildmutt.js prints it). */
	static final float ELBOW_X = 7.0f, ELBOW_Y = 10.51f, ELBOW_Z = -2.94f;
	/** Blocks of extra trail per degree the view runs ahead of the hands; stride cycles per block walked. */
	public static float LAG = 0.0045f, GAIT = 1.1f;
	private static final Arms ARMS = new Arms();
	private static Renderer renderer;

	private WildmuttFirstPerson() {
	}

	/**
	 * Draws one forearm and hand. Called inside vanilla's {@code renderRightHand} / {@code renderLeftHand}, but laid out in
	 * camera space (the pose is reset): a beast on all fours sees his front paws below and ahead of him, claws forward, so
	 * they come up from the bottom corners of the screen. The swing raises the main paw and rakes it down; the kit's
	 * first-person clip is applied on top.
	 */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, boolean right) {
		if (renderer == null) {
			renderer = new Renderer();
		}
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
		boolean main = right == (p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT);
		float swing = main ? p.getAttackAnim(partial) : 0f;
		float side = right ? 1f : -1f;
		float age = p.tickCount + partial;
		float breathe = Mth.sin(age * 0.11f + (right ? 0f : 1.4f)) * 0.012f;
		// the claw swipe (v0.17.1 rework 2): the paw rears up and out, then rakes down and across toward the middle, claws first
		float up = smooth(swing / 0.3f) * (1f - smooth((swing - 0.3f) / 0.3f));
		float rake = smooth((swing - 0.22f) / 0.3f) * (1f - smooth((swing - 0.6f) / 0.4f));
		renderer.right = right;
		renderer.player = p;
		renderer.flex = up - 0.8f * rake;
		pose.pushPose();
		// v0.17.1 rework 2 (user: "the arms dont follow the player in first person ... they just stay in this position even if
		// you turn around"): laid out from vanilla's own hand pose (the view bob, the hurt shake and the sway / lag of the hands
		// as you turn and look), no longer from a bare camera matrix
		pose.last().pose().set(com.projecthero.mod.client.omnitrix.OmnitrixFirstPerson.HAND_BASE);
		pose.last().normal().set(com.projecthero.mod.client.omnitrix.OmnitrixFirstPerson.HAND_BASE_NORMAL);
		// heavy paws trail the turn a little more than a hand does
		if (p instanceof net.minecraft.client.player.LocalPlayer lp) {
			float yawLag = Mth.wrapDegrees(lp.getViewYRot(partial) - Mth.lerp(partial, lp.yBobO, lp.yBob));
			float pitchLag = lp.getViewXRot(partial) - Mth.lerp(partial, lp.xBobO, lp.xBob);
			pose.translate(Mth.clamp(-yawLag * LAG, -0.22f, 0.22f), Mth.clamp(pitchLag * LAG, -0.16f, 0.16f), 0f);
		}
		// the walk: the paws stride in turn (one reaches and lifts while the other pushes back), with the steps' rhythm
		if (mc.options.bobView().get()) {
			float amp = Mth.clamp(Mth.lerp(partial, p.oBob, p.bob) * 10f, 0f, 1f);
			float dist = p.walkDist + (p.walkDist - p.walkDistO) * partial;
			float ph = dist * Mth.PI * GAIT + (right ? 0f : Mth.PI);
			float lift = Math.max(0f, Mth.sin(ph)), reach = Mth.cos(ph);
			pose.translate(0f, amp * 0.085f * lift, -amp * 0.09f * reach);
			pose.mulPose(Axis.XP.rotationDegrees(-amp * 14f * lift));
		}
		com.projecthero.mod.client.omnitrix.FirstPersonClip clip = com.projecthero.mod.client.omnitrix.OmnitrixFirstPerson.clip(p);
		if (clip != null && (main || clip.twoHanded())) {
			clip.apply(pose, com.projecthero.mod.client.omnitrix.OmnitrixFirstPerson.clipTicks(p, partial), side);
		}
		pose.translate(side * (EX + 0.06f * up - 0.34f * rake), EY + breathe + 0.32f * up - 0.1f * rake, EZ - 0.06f * up - 0.24f * rake);
		pose.mulPose(Axis.YP.rotationDegrees(-side * (TOE_IN + 8f * up + 22f * rake)));
		pose.mulPose(Axis.XP.rotationDegrees(TILT + 30f * up - 18f * rake));
		pose.mulPose(Axis.ZP.rotationDegrees(side * (ROLL - 14f * up + 26f * rake)));
		pose.scale(SCALE, SCALE, SCALE);
		// the forearm's elbow pivot to the origin (render space: GeckoLib mirrors json X, so the right arm is at +X)
		pose.translate(-side * ELBOW_X / 16f, -ELBOW_Y / 16f, -ELBOW_Z / 16f);
		renderer.render(pose, ARMS, buffers, null, null, light, partial);
		pose.popPose();
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
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
		boolean right;
		float flex;
		AbstractClientPlayer player;
		private final List<Object[]> saved = new ArrayList<>();

		Renderer() {
			super(new WildmuttModel<>());
			addRenderLayer(new WildmuttRenderer.Badge(this, () -> player));
		}

		@Override
		public void preRender(PoseStack pose, Arms animatable, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer, boolean isReRender,
				float partial, int light, int overlay, int colour) {
			super.preRender(pose, animatable, model, buffers, buffer, isReRender, partial, light, overlay, colour);
			if (isReRender) {
				return;
			}
			pose.translate(-0.5f, -0.51f, -0.5f); // undo the object renderer's block-centring
			saved.clear();
			String n = right ? "r" : "l";
			for (GeoBone b : model.topLevelBones()) {
				pick(b, "forearm_" + n);
			}
		}

		/** Shows only {@code keep} and everything under it; zeroes the arm chain's pose and bends the wrist. */
		private void pick(GeoBone bone, String keep) {
			saved.add(new Object[] { bone, bone.isHidden(), bone.isHidingChildren(), bone.getRotX(), bone.getRotY(), bone.getRotZ(), bone.getPosX(),
					bone.getPosY(), bone.getPosZ(), bone.getScaleX(), bone.getScaleY(), bone.getScaleZ() });
			boolean mine = keep.equals(bone.getName());
			if (mine) {
				bone.setHidden(false);
				bone.setChildrenHidden(false);
				pose(bone);
				return;
			}
			bone.setHidden(true);
			bone.setChildrenHidden(false);
			// the body above the arm at rest, so the arm never rides the third-person clip
			bone.updatePosition(0f, 0f, 0f);
			bone.updateScale(1f, 1f, 1f);
			bone.updateRotation(bone.getInitialSnapshot().getRotX(), bone.getInitialSnapshot().getRotY(), bone.getInitialSnapshot().getRotZ());
			for (GeoBone c : bone.getChildBones()) {
				pick(c, keep);
			}
		}

		private void pose(GeoBone bone) {
			for (GeoBone c : collect(bone, new ArrayList<>())) {
				if (c != bone) {
					saved.add(new Object[] { c, c.isHidden(), c.isHidingChildren(), c.getRotX(), c.getRotY(), c.getRotZ(), c.getPosX(), c.getPosY(),
							c.getPosZ(), c.getScaleX(), c.getScaleY(), c.getScaleZ() });
				}
				c.setHidden(false);
				c.updatePosition(0f, 0f, 0f);
				c.updateScale(1f, 1f, 1f);
				c.updateRotation(0f, 0f, 0f);
				String name = c.getName();
				if (name.startsWith("forearm")) {
					c.updateRotation(ELBOW * Mth.DEG_TO_RAD, 0f, 0f);
				} else if (name.startsWith("paw")) {
					// the claws rake down as the swing lands
					c.updateRotation((WRIST + 30f * flex) * Mth.DEG_TO_RAD, 0f, 0f);
				}
				c.updateRotation(c.getRotX() + c.getInitialSnapshot().getRotX(), c.getRotY() + c.getInitialSnapshot().getRotY(),
						c.getRotZ() + c.getInitialSnapshot().getRotZ());
			}
		}

		private static List<GeoBone> collect(GeoBone b, List<GeoBone> out) {
			out.add(b);
			for (GeoBone c : b.getChildBones()) {
				collect(c, out);
			}
			return out;
		}

		@Override
		public void doPostRenderCleanup() {
			super.doPostRenderCleanup();
			for (int i = saved.size() - 1; i >= 0; i--) {
				Object[] s = saved.get(i);
				GeoBone b = (GeoBone) s[0];
				b.setHidden((Boolean) s[1]);
				b.setChildrenHidden((Boolean) s[2]);
				b.updateRotation((Float) s[3], (Float) s[4], (Float) s[5]);
				b.updatePosition((Float) s[6], (Float) s[7], (Float) s[8]);
				b.updateScale((Float) s[9], (Float) s[10], (Float) s[11]);
			}
			saved.clear();
			player = null;
		}
	}
}
