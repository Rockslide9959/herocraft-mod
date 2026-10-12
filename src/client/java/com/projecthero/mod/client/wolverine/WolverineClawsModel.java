package com.projecthero.mod.client.wolverine;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.wolverine.Wolverine;
import com.projecthero.mod.wolverine.data.WolverineState;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import org.joml.Vector3f;

/**
 * The adamantium claws (v0.16.1: rebuilt as a smooth procedural mesh -- the old model was four stacked boxes per blade).
 * Three blades per hand, each a thin, curved, tapering blade: {@link #SEGMENTS} rings along a centreline that bends
 * progressively toward the body, every ring a four-point diamond cross-section (a sharp edge on the inside of the
 * curve, a thicker spine on the outside, two flat bevels), narrowing to a needle point. Lit by real per-face normals
 * and textured with a shaded brushed-metal strip ({@code scratchpad/gen_wolverine_claws_v0161.js}).
 *
 * <p>All geometry is in the player arm bone's space (pixels, /16 on emit): the fist ends about 10 px below the shoulder
 * pivot and the blades start inside it at {@link #ROOT_Y}, spaced along Z on the knuckle line, offset toward the back
 * of the hand. Deploying slides the blades out of the fist (the hidden part is clipped at the root) over
 * {@link #EXTEND_TICKS} ticks, driven purely by the synced {@code WolverineState} so every viewer agrees.
 */
public final class WolverineClawsModel {
	public static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/wolverine_claws.png");

	/** Ticks the blades take to slide out / back in. */
	private static final float EXTEND_TICKS = 4.0f;
	/** Arm-bone Y (px) where the blades start, inside the fist. */
	private static final float ROOT_Y = 9.0f;
	private static final float BLADE_SPACING = 1.3f;
	/** Blade length in first person (px) and on the third-person model (half: a hanging arm must not dig them into the ground). */
	public static final float FIRST_PERSON_LENGTH = 16.0f;
	public static final float WORLD_LENGTH = 8.0f;
	private static final int SEGMENTS = 16;
	/** Blade width (edge to spine) and thickness at the root, px. */
	private static final float WIDTH = 1.15f;
	private static final float THICKNESS = 0.34f;
	/** Total bend toward the body from root to tip, radians (eased: the tip bends most). */
	private static final float CURVE = 0.42f;

	private WolverineClawsModel() {
	}

	/** How far out the claws are for {@code player} right now: 0 (hidden) to 1 (fully deployed). */
	public static float extension(AbstractClientPlayer player, float partialTick) {
		WolverineState s = player.getAttachedOrElse(ModAttachments.WOLVERINE_STATE, null);
		if (s == null || !s.hasPower) {
			return 0.0f;
		}
		float age = player.level().getGameTime() + partialTick - s.clawsChangedAt;
		// v0.16.1: deploying, the blades wait for the arms to be flung out (WolverineClawPose's deploy throw) and pop out
		// of the fists as they reach full extension; retracting, they slide straight back in
		float t = s.clawsOut ? (age - (manualDeploy(s) ? DEPLOY_DELAY_TICKS : 0.0f)) / EXTEND_TICKS : age / EXTEND_TICKS;
		t = Math.max(0.0f, Math.min(1.0f, t));
		return s.clawsOut ? t : 1.0f - t;
	}

	/** v0.16.1: ticks into the H deploy (fists to the chest, then the throw) before the blades start to come out -- fully out at delay + extend = 9.5, as the arms reach full extension. */
	public static final float DEPLOY_DELAY_TICKS = 5.5f;

	/**
	 * v0.16.1: whether the current deploy is a manual one (the H key) -- the only kind that plays the deploy animation. The
	 * claws an ability brings out are followed at once by that ability (its action stamp or the Z charge), which plays
	 * its own animation instead, never combined with the deploy.
	 */
	public static boolean manualDeploy(WolverineState s) {
		return s.clawsOut && s.lastActionTick < s.clawsChangedAt && s.chargeStartedAt < s.clawsChangedAt;
	}

	/** Whether a player currently needs the claws drawn at all (cheap early-out for the layer / mixin). */
	public static boolean visible(AbstractClientPlayer player, float partialTick) {
		return Wolverine.hasPower(player) && extension(player, partialTick) > 0.02f;
	}

	/**
	 * v0.16.1: whether the first-person hands are Wolverine's to draw -- claws out (including the first ticks of the
	 * deploy, before the blades show) or the blades still sliding back in.
	 */
	public static boolean handsShown(AbstractClientPlayer player, float partialTick) {
		WolverineState s = player.getAttachedOrElse(ModAttachments.WOLVERINE_STATE, null);
		if (s == null || !s.hasPower) {
			return false;
		}
		return s.clawsOut || player.level().getGameTime() + partialTick - s.clawsChangedAt < EXTEND_TICKS;
	}

	// ------------------------------------------------------------------ geometry

	/** One blade's rings: per ring the edge, front bevel, spine and back bevel points (px, arm-bone space). */
	private static final class Blade {
		final Vector3f[] edge = new Vector3f[SEGMENTS + 1];
		final Vector3f[] front = new Vector3f[SEGMENTS + 1];
		final Vector3f[] spine = new Vector3f[SEGMENTS + 1];
		final Vector3f[] back = new Vector3f[SEGMENTS + 1];
		final Vector3f[] centre = new Vector3f[SEGMENTS + 1];
		final float[] v = new float[SEGMENTS + 1];
		int first; // first ring outside the fist (rings before it are clipped)
	}

	/**
	 * Build blade {@code index} (0..2) of one hand. {@code extension} slides it out of the fist; {@code length} is the
	 * full blade length in px.
	 */
	private static Blade blade(boolean rightArm, boolean slim, float extension, float length, int index) {
		Blade b = new Blade();
		float bend = rightArm ? 1.0f : -1.0f; // toward the body: +X for the right arm, -X for the left
		float hx = rightArm ? -(slim ? 1.25f : 1.75f) : (slim ? 1.25f : 1.75f);
		float z = (index - 1) * BLADE_SPACING;
		// the outer blades are a touch shorter, like knuckles
		float len = length * (index == 1 ? 1.0f : 0.9f);
		float slide = (1.0f - extension) * len;
		float step = len / SEGMENTS;
		float x = hx;
		float y = ROOT_Y - slide;
		for (int k = 0; k <= SEGMENTS; k++) {
			float u = (float) k / SEGMENTS;
			if (k > 0) {
				float um = (k - 0.5f) / SEGMENTS;
				float am = CURVE * um * um;
				x += bend * (float) Math.sin(am) * step;
				y += (float) Math.cos(am) * step;
			}
			float a = CURVE * u * u;
			// unit vector across the blade, pointing to the inside (concave) side of the curve
			float nx = bend * (float) Math.cos(a);
			float ny = -(float) Math.sin(a);
			// the cutting edge sweeps in toward the spine near the tip; the spine stays full until the very end
			float e = 0.55f * WIDTH * (float) Math.pow(1.0f - u, 0.85f);
			float sp = 0.45f * WIDTH * (1.0f - u * u * u * u);
			float th = 0.5f * THICKNESS * (1.0f - 0.55f * u) * (u >= 1.0f ? 0.0f : 1.0f);
			float mid = -0.1f * WIDTH * (1.0f - u); // the bevel ridge sits a little toward the spine
			b.centre[k] = new Vector3f(x, y, z);
			b.edge[k] = new Vector3f(x + nx * e, y + ny * e, z);
			b.spine[k] = new Vector3f(x - nx * sp, y - ny * sp, z);
			b.front[k] = new Vector3f(x + nx * mid, y + ny * mid, z + th);
			b.back[k] = new Vector3f(x + nx * mid, y + ny * mid, z - th);
			b.v[k] = u;
		}
		// clip everything still inside the fist: the first visible ring is interpolated onto the root plane
		int first = 0;
		while (first < SEGMENTS && b.centre[first + 1].y <= ROOT_Y) {
			first++;
		}
		if (b.centre[first].y < ROOT_Y && first < SEGMENTS) {
			float y0 = b.centre[first].y;
			float y1 = b.centre[first + 1].y;
			float f = (ROOT_Y - y0) / Math.max(1.0e-4f, y1 - y0);
			lerpInto(b.centre, first, f);
			lerpInto(b.edge, first, f);
			lerpInto(b.spine, first, f);
			lerpInto(b.front, first, f);
			lerpInto(b.back, first, f);
			b.v[first] = b.v[first] + (b.v[first + 1] - b.v[first]) * f;
		}
		b.first = first;
		return b;
	}

	private static void lerpInto(Vector3f[] ring, int k, float f) {
		ring[k] = new Vector3f(ring[k]).lerp(ring[k + 1], f);
	}

	/**
	 * Where on blade {@code index} the point {@code along} (0 root .. 1 tip) of the fully-shaped blade is, in arm-bone
	 * space (blocks, ready for the pose matrix). Used by the slash trails.
	 */
	public static Vector3f bladePoint(boolean rightArm, boolean slim, float extension, float length, int index, float along) {
		Blade b = blade(rightArm, slim, extension, length, index);
		float pos = b.v[b.first] + (1.0f - b.v[b.first]) * Math.max(0.0f, Math.min(1.0f, along));
		int k = b.first;
		while (k < SEGMENTS && b.v[k + 1] < pos) {
			k++;
		}
		if (k >= SEGMENTS) {
			return new Vector3f(b.centre[SEGMENTS]).div(16.0f);
		}
		float f = (pos - b.v[k]) / Math.max(1.0e-4f, b.v[k + 1] - b.v[k]);
		return new Vector3f(b.centre[k]).lerp(b.centre[k + 1], f).div(16.0f);
	}

	// ------------------------------------------------------------------ drawing

	/**
	 * Draw one hand's three blades. The pose stack must already be in that arm bone's space (after
	 * {@code arm.translateAndRotate}).
	 */
	public static void render(PoseStack pose, VertexConsumer buffer, int light, int overlay, boolean rightArm, boolean slim,
			float extension, float length, int argb) {
		PoseStack.Pose last = pose.last();
		for (int i = 0; i < 3; i++) {
			Blade b = blade(rightArm, slim, extension, length, i);
			for (int k = b.first; k < SEGMENTS; k++) {
				// the four faces, each with its own column of the strip texture: front bevel at the edge, front flat at the
				// spine, back flat, back bevel
				face(buffer, last, b.edge, b.front, k, b.v, 0, light, overlay, argb);
				face(buffer, last, b.front, b.spine, k, b.v, 1, light, overlay, argb);
				face(buffer, last, b.spine, b.back, k, b.v, 2, light, overlay, argb);
				face(buffer, last, b.back, b.edge, k, b.v, 3, light, overlay, argb);
			}
			// cap the clipped root so a half-deployed blade is not hollow
			cap(buffer, last, b, light, overlay, argb);
		}
	}

	private static void face(VertexConsumer vc, PoseStack.Pose pose, Vector3f[] a, Vector3f[] c, int k, float[] v, int column,
			int light, int overlay, int argb) {
		Vector3f a0 = a[k], a1 = a[k + 1], c0 = c[k], c1 = c[k + 1];
		Vector3f n = new Vector3f(c0).sub(a0).cross(new Vector3f(a1).sub(a0));
		if (n.lengthSquared() < 1.0e-10f) {
			n = new Vector3f(c1).sub(a1).cross(new Vector3f(a1).sub(a0));
		}
		if (n.lengthSquared() < 1.0e-10f) {
			return;
		}
		n.normalize();
		float u0 = column * 0.25f + 0.02f;
		float u1 = column * 0.25f + 0.23f;
		vertex(vc, pose, a0, u0, v[k], light, overlay, argb, n);
		vertex(vc, pose, a1, u0, v[k + 1], light, overlay, argb, n);
		vertex(vc, pose, c1, u1, v[k + 1], light, overlay, argb, n);
		vertex(vc, pose, c0, u1, v[k], light, overlay, argb, n);
	}

	private static void cap(VertexConsumer vc, PoseStack.Pose pose, Blade b, int light, int overlay, int argb) {
		int k = b.first;
		Vector3f n = new Vector3f(b.centre[k]).sub(b.centre[Math.min(SEGMENTS, k + 1)]);
		if (n.lengthSquared() < 1.0e-10f) {
			return;
		}
		n.normalize();
		float v = b.v[k];
		vertex(vc, pose, b.edge[k], 0.5f, v, light, overlay, argb, n);
		vertex(vc, pose, b.front[k], 0.6f, v, light, overlay, argb, n);
		vertex(vc, pose, b.spine[k], 0.7f, v, light, overlay, argb, n);
		vertex(vc, pose, b.back[k], 0.6f, v, light, overlay, argb, n);
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Vector3f p, float u, float v, int light, int overlay,
			int argb, Vector3f n) {
		vc.addVertex(pose, p.x / 16.0f, p.y / 16.0f, p.z / 16.0f)
				.setColor(argb)
				.setUv(u, v)
				.setOverlay(overlay)
				.setLight(light)
				.setNormal(pose, n.x, n.y, n.z);
	}
}
