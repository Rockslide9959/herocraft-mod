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
 * v0.17.1: Wildmutt's first-person arms -- his own huge orange forearm and clawed hand from the body model (the
 * {@code arm_*} / {@code forearm_*} / {@code paw_*} bones and texture), drawn where vanilla draws the player's arm (so the
 * swing, the equip and the kit's {@code FirstPersonClip}s all move it). The wrist is bent so the four dark claws point into
 * the view; the claws flex with the swing. The left arm carries the Omnitrix strap. Both arms show while the hands are empty
 * ({@code bothHandsInFirstPerson}).
 *
 * <p>The baked model is GeckoLib's shared one: the bones' visibility and rotations are saved before the draw and put back
 * after, so the third-person body never sees this pass.
 */
public final class WildmuttFirstPerson {
	/** The forearm + hand drawn this big (blocks per model pixel x 16). */
	public static float SCALE = 0.85f;
	/** Where the elbow sits in camera space (x toward the arm's side, y up, z negative = ahead). */
	public static float EX = 0.5f, EY = -0.5f, EZ = -0.85f;
	/** How far the forearm swings forward / in toward the centre, degrees. */
	public static float TILT = 50f, TOE_IN = 12f, ROLL = 6f;
	/** The wrist bend (degrees, GeckoLib's sign) and the elbow's. */
	public static float WRIST = 40f, ELBOW = 0f;
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
		float partial = net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		boolean main = right == (p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT);
		float swing = main ? p.getAttackAnim(partial) : 0f;
		float k = Mth.sin(Mth.sqrt(swing) * Mth.PI); // 0 -> 1 -> 0
		float rake = Mth.sin(swing * Mth.PI); // the claws: up then down
		float side = right ? 1f : -1f;
		float age = p.tickCount + partial;
		float breathe = Mth.sin(age * 0.11f + (right ? 0f : 1.4f)) * 0.012f;
		renderer.right = right;
		renderer.player = p;
		renderer.flex = rake;
		pose.pushPose();
		pose.last().pose().identity();
		pose.last().normal().identity();
		com.projecthero.mod.client.omnitrix.FirstPersonClip clip = com.projecthero.mod.client.omnitrix.OmnitrixFirstPerson.clip(p);
		if (clip != null && (main || clip.twoHanded())) {
			clip.apply(pose, com.projecthero.mod.client.omnitrix.OmnitrixFirstPerson.clipTicks(p, partial), side);
		}
		pose.translate(side * EX, EY + breathe + k * 0.22f, EZ - k * 0.18f);
		pose.mulPose(Axis.YP.rotationDegrees(-side * (TOE_IN + k * 10f)));
		pose.mulPose(Axis.XP.rotationDegrees(TILT + k * 34f));
		pose.mulPose(Axis.ZP.rotationDegrees(side * ROLL));
		pose.scale(SCALE, SCALE, SCALE);
		// the forearm's elbow pivot to the origin (render space: GeckoLib mirrors json X, so the right arm is at +X)
		pose.translate(-side * 9.6f / 16f, -10.43f / 16f, 5f / 16f);
		renderer.render(pose, ARMS, buffers, null, null, light, partial);
		pose.popPose();
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
