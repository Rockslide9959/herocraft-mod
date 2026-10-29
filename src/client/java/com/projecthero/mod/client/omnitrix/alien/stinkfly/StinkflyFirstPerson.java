package com.projecthero.mod.client.omnitrix.alien.stinkfly;

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
 * v0.17.1: Stinkfly's first-person arms -- his own thin green forearm and black three-clawed hand from the body model (the
 * {@code forearm_*} / {@code hand_*} bones and texture), drawn where vanilla draws the player's arm, the claws pointing into the
 * view like a mantis's. Both are always in view ({@code bothHandsInFirstPerson}; claws hold nothing). Laid out from vanilla's
 * hand pose (view bob, hurt shake, the sway as you turn), a small flutter of the arms while he flies, a claw jab on Attack and
 * each move's {@code FirstPersonClip} on top. Wildmutt's approach ({@code WildmuttFirstPerson}).
 *
 * <p>The baked model is GeckoLib's shared one: the bones' visibility and rotations are saved before the draw and put back after,
 * so the third-person body never sees this pass.
 */
public final class StinkflyFirstPerson {
	/** The forearm + hand drawn this big. */
	public static float SCALE = 0.75f;
	/** Where the elbow sits in camera space (x toward the arm's side, y up, z negative = ahead). */
	public static float EX = 0.4f, EY = -0.36f, EZ = -0.46f;
	/** How far the forearm tips forward (+: its rest points 72 degrees forward of straight down, so 18 levels it, more raises it) / in toward the centre / rolls, degrees. */
	public static float TILT = 52f, TOE_IN = 20f, ROLL = 16f;
	/** The wrist bend (degrees, GeckoLib's sign). */
	public static float WRIST = 40f;
	/** The forearm's elbow pivot (json px; gen_stinkfly.js prints it). */
	static final float ELBOW_X = 3.7f, ELBOW_Y = 15.99f, ELBOW_Z = -1.97f;
	/** The forearm's own rest angle (gen_stinkfly.js FA): undone so the arm points straight into the view. */
	static final float FOREARM_REST = -72f;
	private static final Arms ARMS = new Arms();
	private static Renderer renderer;

	private StinkflyFirstPerson() {
	}

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
		float breathe = Mth.sin(age * 0.13f + (right ? 0f : 1.4f)) * 0.01f;
		// the claw jab: drawn back a moment, then stabbed forward and in
		float back = smooth(swing / 0.2f) * (1f - smooth((swing - 0.2f) / 0.15f));
		float stab = smooth((swing - 0.18f) / 0.18f) * (1f - smooth((swing - 0.5f) / 0.5f));
		renderer.right = right;
		renderer.player = p;
		renderer.flex = stab - 0.5f * back;
		pose.pushPose();
		pose.last().pose().set(com.projecthero.mod.client.omnitrix.OmnitrixFirstPerson.HAND_BASE);
		pose.last().normal().set(com.projecthero.mod.client.omnitrix.OmnitrixFirstPerson.HAND_BASE_NORMAL);
		// in flight the raised arms tremble with the wings' buzz and bob with the hover
		if (StinkflyAnimatable.flying(p)) {
			pose.translate(0f, 0.025f * Mth.sin(age * 0.25f), 0f);
			pose.mulPose(Axis.ZP.rotationDegrees(side * 1.2f * Mth.sin(age * 2.6f)));
		} else if (mc.options.bobView().get()) {
			// the scuttle: the hands rise and fall in turn
			float amp = Mth.clamp(Mth.lerp(partial, p.oBob, p.bob) * 10f, 0f, 1f);
			float dist = p.walkDist + (p.walkDist - p.walkDistO) * partial;
			float ph = dist * Mth.PI * 1.6f + (right ? 0f : Mth.PI);
			pose.translate(0f, amp * 0.05f * Math.max(0f, Mth.sin(ph)), -amp * 0.04f * Mth.cos(ph));
		}
		com.projecthero.mod.client.omnitrix.FirstPersonClip clip = com.projecthero.mod.client.omnitrix.OmnitrixFirstPerson.clip(p);
		if (clip != null && (main || clip.twoHanded())) {
			clip.apply(pose, com.projecthero.mod.client.omnitrix.OmnitrixFirstPerson.clipTicks(p, partial), side);
		}
		pose.translate(side * (EX - 0.12f * stab), EY + breathe + 0.06f * back + 0.08f * stab, EZ + 0.12f * back - 0.32f * stab);
		pose.mulPose(Axis.YP.rotationDegrees(-side * (TOE_IN + 10f * stab)));
		pose.mulPose(Axis.XP.rotationDegrees(TILT + 12f * back - 6f * stab));
		pose.mulPose(Axis.ZP.rotationDegrees(side * ROLL));
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
			super(new StinkflyModel<>());
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

		private void save(GeoBone b) {
			saved.add(new Object[] { b, b.isHidden(), b.isHidingChildren(), b.getRotX(), b.getRotY(), b.getRotZ(), b.getPosX(), b.getPosY(), b.getPosZ(),
					b.getScaleX(), b.getScaleY(), b.getScaleZ() });
		}

		/** Shows only {@code keep} and everything under it; the body above it at rest. */
		private void pick(GeoBone bone, String keep) {
			save(bone);
			if (keep.equals(bone.getName())) {
				bone.setHidden(false);
				bone.setChildrenHidden(false);
				pose(bone);
				return;
			}
			bone.setHidden(true);
			bone.setChildrenHidden(false);
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
					save(c);
				}
				c.setHidden(false);
				c.updatePosition(0f, 0f, 0f);
				c.updateScale(1f, 1f, 1f);
				float rx = 0f;
				String name = c.getName();
				if (name.startsWith("forearm")) {
					rx = 0f; // the forearm at its rest angle (TILT lays it out ahead)
				} else if (name.startsWith("hand")) {
					rx = WRIST - 14f * flex; // the claws drive in on the jab
				}
				c.updateRotation(rx * Mth.DEG_TO_RAD + c.getInitialSnapshot().getRotX(), c.getInitialSnapshot().getRotY(),
						c.getInitialSnapshot().getRotZ());
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
