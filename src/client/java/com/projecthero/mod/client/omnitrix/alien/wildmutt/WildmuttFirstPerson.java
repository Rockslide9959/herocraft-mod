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
	/** The arm is drawn this much smaller than the body's (22 px from shoulder to claws -- a player's arm is 12). */
	public static final float SCALE = 0.72f;
	/** The wrist / elbow bend that lays the hand flat with its claws ahead. */
	public static final float WRIST = -72f, ELBOW = -18f;
	private static final Arms ARMS = new Arms();
	private static Renderer renderer;

	private WildmuttFirstPerson() {
	}

	/** Draws one arm in vanilla's {@code renderRightHand} / {@code renderLeftHand} space. */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, boolean right) {
		if (renderer == null) {
			renderer = new Renderer();
		}
		float partial = net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float swing = p.getAttackAnim(partial);
		renderer.right = right;
		renderer.player = p;
		renderer.flex = Mth.sin(Mth.sqrt(swing) * Mth.PI);
		pose.pushPose();
		// the player model's arm pivot (y down), then the geo arm (y up, x mirrored by GeckoLib) turned to hang the same way
		pose.translate((right ? -5f : 5f) / 16f, 2f / 16f, 0f);
		pose.mulPose(Axis.ZP.rotationDegrees(180f));
		pose.scale(SCALE, SCALE, SCALE);
		pose.translate((right ? 9.5f : -9.5f) / 16f, -18.73f / 16f, 5f / 16f);
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
				pick(b, "arm_" + n);
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
					c.updateRotation((WRIST + 26f * flex) * Mth.DEG_TO_RAD, 0f, 0f);
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
