package com.projecthero.mod.client.beastboy.model;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy HQ round: Minecraft-style box models (like ModelPart cubes) for the T-Rex, pterodactyl, snake, fly, hawk and bat,
 * drawn through {@link BeastMesh}'s raw-UV hooks from per-animal pixel-art atlases.
 *
 * <p>A part is an int array {u, v, w, h, d, texW, texH, seeThrough} written by {@code scratchpad/beastboy/gen_reptiles_flyers_tex.js}
 * (the generated {@code *Uv} classes), the same unwrap as a vanilla cube with this body's +Z front: top row [top w x d][bottom w x d]
 * after a d-wide gap; below it [right (-X) d][front (+Z) w][left (+X) d][back (-Z) w], each h tall. Planes (h = 0) use only the top
 * region. Units are the body's pixels; bones are plain pose-stack pushes ({@link #bone}).
 */
public final class Blocky {
	private Blocky() {
	}

	/** A box of the part's own size with its min corner at (x, y, z). */
	public static void box(PoseStack pose, int[] p, float x, float y, float z) {
		box(pose, p, x, y, z, p[2], p[3], p[4], false);
	}

	/** Same, mirrored left to right (the texture flips, so one painted side serves both). */
	public static void boxM(PoseStack pose, int[] p, float x, float y, float z, boolean mirror) {
		box(pose, p, x, y, z, p[2], p[3], p[4], mirror);
	}

	/** A box centred on x and z with its bottom at y (handy for hanging / standing parts). */
	public static void boxC(PoseStack pose, int[] p, float cx, float y, float cz) {
		box(pose, p, cx - p[2] * 0.5f, y, cz - p[4] * 0.5f, p[2], p[3], p[4], false);
	}

	/**
	 * A box from (x0, y0, z0) sized (sx, sy, sz) model pixels, textured with part {@code p} (stretched when the size differs).
	 */
	public static void box(PoseStack pose, int[] p, float x0, float y0, float z0, float sx, float sy, float sz, boolean mirror) {
		boolean see = p.length > 7 && p[7] != 0;
		if (!BeastMesh.drawsRaw(see) || sx <= 0f || sy <= 0f || sz <= 0f) {
			return;
		}
		PoseStack.Pose m = pose.last();
		float U = p[0], V = p[1], w = p[2], h = p[3], d = p[4], iu = 1f / p[5], iv = 1f / p[6];
		float y1 = y0 + sy, z1 = z0 + sz;
		float mx = mirror ? -1f : 1f;
		// X(t): texture-space -X side at t = 0; mirrored boxes swap the sides
		float xa = mirror ? x0 + sx : x0, xb = mirror ? x0 : x0 + sx;
		// top: u from -X to +X, v from the back to the front
		q(m, xa, y1, z0, U + d, V, xb, y1, z0, U + d + w, V, xb, y1, z1, U + d + w, V + d, xa, y1, z1, U + d, V + d, 0f, 1f, 0f, iu, iv);
		// bottom: u from -X to +X, v from the front to the back
		q(m, xa, y0, z1, U + d + w, V, xb, y0, z1, U + d + w + w, V, xb, y0, z0, U + d + w + w, V + d, xa, y0, z0, U + d + w, V + d, 0f, -1f, 0f, iu,
				iv);
		// right (-X): u from the back to the front
		q(m, xa, y1, z0, U, V + d, xa, y1, z1, U + d, V + d, xa, y0, z1, U + d, V + d + h, xa, y0, z0, U, V + d + h, -mx, 0f, 0f, iu, iv);
		// front (+Z): u from -X to +X
		q(m, xa, y1, z1, U + d, V + d, xb, y1, z1, U + d + w, V + d, xb, y0, z1, U + d + w, V + d + h, xa, y0, z1, U + d, V + d + h, 0f, 0f, 1f, iu,
				iv);
		// left (+X): u from the front to the back
		q(m, xb, y1, z1, U + d + w, V + d, xb, y1, z0, U + d + w + d, V + d, xb, y0, z0, U + d + w + d, V + d + h, xb, y0, z1, U + d + w, V + d + h, mx,
				0f, 0f, iu, iv);
		// back (-Z): u from +X to -X
		float ub = U + d + w + d;
		q(m, xb, y1, z0, ub, V + d, xa, y1, z0, ub + w, V + d, xa, y0, z0, ub + w, V + d + h, xb, y0, z0, ub, V + d + h, 0f, 0f, -1f, iu, iv);
	}

	/** A flat horizontal quad (feathers, wing membranes) at height y from (x0, z0) sized (sx, sz), the part's top region on it. */
	public static void plane(PoseStack pose, int[] p, float x0, float y, float z0, float sx, float sz, boolean mirror) {
		boolean see = p.length > 7 && p[7] != 0;
		if (!BeastMesh.drawsRaw(see)) {
			return;
		}
		PoseStack.Pose m = pose.last();
		float U = p[0], V = p[1], w = p[2], d = p[4], iu = 1f / p[5], iv = 1f / p[6];
		float xa = mirror ? x0 + sx : x0, xb = mirror ? x0 : x0 + sx;
		float z1 = z0 + sz;
		q(m, xa, y, z0, U + d, V, xb, y, z0, U + d + w, V, xb, y, z1, U + d + w, V + d, xa, y, z1, U + d, V + d, 0f, 1f, 0f, iu, iv);
	}

	/** A flat quad between four arbitrary corners (a stretched membrane panel), the part's top region on it (corner order: -X back, +X back, +X front, -X front). */
	public static void quad(PoseStack pose, int[] p, float[] a, float[] b, float[] c, float[] e) {
		boolean see = p.length > 7 && p[7] != 0;
		if (!BeastMesh.drawsRaw(see)) {
			return;
		}
		PoseStack.Pose m = pose.last();
		float U = p[0], V = p[1], w = p[2], d = p[4], iu = 1f / p[5], iv = 1f / p[6];
		float[] n = BeastMesh.norm(BeastMesh.cross(BeastMesh.sub(c, a), BeastMesh.sub(e, b)), new float[] { 0f, 1f, 0f });
		q(m, a[0], a[1], a[2], U + d, V, b[0], b[1], b[2], U + d + w, V, c[0], c[1], c[2], U + d + w, V + d, e[0], e[1], e[2], U + d, V + d, n[0], n[1],
				n[2], iu, iv);
	}

	/**
	 * A membrane panel: a flat quad with the part's top region mapped from u0 to u1 (fractions of its width) along the span; a and b are
	 * the trailing (back) edge corners, c and e the leading (front) edge corners, b and c at u1.
	 */
	public static void panel(PoseStack pose, int[] p, float[] a, float[] b, float[] c, float[] e, float u0, float u1) {
		boolean see = p.length > 7 && p[7] != 0;
		if (!BeastMesh.drawsRaw(see)) {
			return;
		}
		PoseStack.Pose m = pose.last();
		float U = p[0], V = p[1], w = p[2], d = p[4], iu = 1f / p[5], iv = 1f / p[6];
		float ua = U + d + w * u0, ub = U + d + w * u1;
		float[] n = BeastMesh.norm(BeastMesh.cross(BeastMesh.sub(c, a), BeastMesh.sub(e, b)), new float[] { 0f, 1f, 0f });
		q(m, a[0], a[1], a[2], ua, V, b[0], b[1], b[2], ub, V, c[0], c[1], c[2], ub, V + d, e[0], e[1], e[2], ua, V + d, n[0], n[1], n[2], iu, iv);
	}

	/** The point (x, y, z) of the current bone, in the space whose inverse pose matrix is {@code invBase}. */
	public static float[] at(PoseStack pose, org.joml.Matrix4f invBase, float x, float y, float z) {
		org.joml.Vector3f v = pose.last().pose().transformPosition(x, y, z, new org.joml.Vector3f());
		invBase.transformPosition(v);
		return new float[] { v.x, v.y, v.z };
	}

	/** The inverse of the current pose matrix (pair with {@link #at} to read joint positions back in this space). */
	public static org.joml.Matrix4f inv(PoseStack pose) {
		return new org.joml.Matrix4f(pose.last().pose()).invert();
	}

	private static void q(PoseStack.Pose m, float x0, float y0, float z0, float u0, float v0, float x1, float y1, float z1, float u1, float v1, float x2,
			float y2, float z2, float u2, float v2, float x3, float y3, float z3, float u3, float v3, float nx, float ny, float nz, float iu, float iv) {
		BeastMesh.rawVertex(m, x0, y0, z0, u0 * iu, v0 * iv, nx, ny, nz);
		BeastMesh.rawVertex(m, x1, y1, z1, u1 * iu, v1 * iv, nx, ny, nz);
		BeastMesh.rawVertex(m, x2, y2, z2, u2 * iu, v2 * iv, nx, ny, nz);
		BeastMesh.rawVertex(m, x3, y3, z3, u3 * iu, v3 * iv, nx, ny, nz);
	}

	// ------------------------------------------------------------------------------------------------------------------ bones

	/** Push a bone: pivot (x, y, z), then yaw (about +Y), pitch (about +X, + = nose down) and roll (about +Z). Pop it yourself. */
	public static void bone(PoseStack pose, float x, float y, float z, float yaw, float pitch, float roll) {
		pose.pushPose();
		pose.translate(x, y, z);
		if (yaw != 0f || pitch != 0f || roll != 0f) {
			pose.mulPose(new Quaternionf().rotationYXZ(yaw, pitch, roll));
		}
	}

	public static void pop(PoseStack pose) {
		pose.popPose();
	}

	/** Scales the rest of the body (blocky bodies are built in their own pixel size and scaled to the form's hitbox). */
	public static void scale(PoseStack pose, float k) {
		pose.scale(k, k, k);
	}

	// ------------------------------------------------------------------------------------------------------------------ legs

	/**
	 * Two-bone leg IK in the side plane: from a hip at (hy, hz) to a target at (ty, tz) with bone lengths a and b, the knee bending
	 * forward (+Z, birds' and dinosaurs' "knee") when {@code kneeForward}, else backward. Returns {upper pitch, lower pitch relative
	 * to the upper}: pitches of bones that hang straight down at 0 (+ swings the lower end backward, toward -Z).
	 */
	public static float[] ik2(float hy, float hz, float ty, float tz, float a, float b, boolean kneeForward) {
		float dy = ty - hy, dz = tz - hz;
		float dist = Mth.clamp(Mth.sqrt(dy * dy + dz * dz), Math.abs(a - b) + 0.01f, (a + b) * 0.9995f);
		float th = (float) Math.atan2(-dz, -dy);
		float cosA = Mth.clamp((a * a + dist * dist - b * b) / (2f * a * dist), -1f, 1f);
		float alpha = (float) Math.acos(cosA);
		float t1 = kneeForward ? th - alpha : th + alpha;
		float ky = hy - a * Mth.cos(t1), kz = hz - a * Mth.sin(t1);
		float t2 = (float) Math.atan2(-(tz - kz), -(ty - ky));
		return new float[] { t1, t2 - t1 };
	}
}
