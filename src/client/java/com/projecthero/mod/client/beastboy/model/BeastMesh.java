package com.projecthero.mod.client.beastboy.model;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy: the procedural mesh kit every custom animal body is built from (ellipsoids, frustums, cones, boxes, smooth lofted
 * tubes through control points, and ribbons for wings and fins), drawn straight into a buffer. Every primitive maps one whole 32x32
 * swatch of {@code beast_art.png} (made by {@code scratchpad/beastboy/gen_beast_art_tex.js}; keep the swatch numbers in sync).
 *
 * <p>Model space of the bodies: units = pixels (1/16 block) once {@link #begin} has scaled, +Y up, +Z forward, +X the animal's left,
 * origin on the ground under the body. Render-thread only (static draw state).
 */
public final class BeastMesh {
	public static final ResourceLocation TEX = ProjectHeroMod.id("textures/entity/beastboy/art/beast_art.png");

	// swatches (keep in sync with the generator)
	public static final int SKIN = 0, SKIN_DK = 1, BELLY = 2, COUNTER = 3, COUNTER_V = 4, STRIPED = 5, STRIPED_V = 6, SCALES = 7, WRINKLE = 8,
			WRINKLE_V = 9, FUR = 10, FUR_LT = 11, FACE = 12, DARK = 13, MOUTH = 14, TOOTH = 15, CLAW = 16, HORN = 17, EYE = 18, EYE_SLIT = 19,
			EYE_FIERCE = 20, MEMBRANE = 21, FLYWING = 22, TENTACLE = 23, GROOVE = 24, SPOT = 25, SPOT_V = 26, INNER_EAR = 27, MANE = 28, COMPOUND = 29,
			GLOW = 30, BLACK = 31, SCALES_V = 32, WRINKLE_LT = 33, SHARK = 34, FIN = 35;

	/** Passes: the opaque body, the see-through parts (fly wings), the additive morph flash. */
	static final int PASS_OPAQUE = 0, PASS_TRANSLUCENT = 1, PASS_GLOW = 2;

	private static VertexConsumer vc;
	private static int light;
	private static int overlay = OverlayTexture.NO_OVERLAY;
	private static int r = 255, g = 255, b = 255, a = 255;
	private static int pass;

	private BeastMesh() {
	}

	/** Something that draws one body in model space (pixels). */
	@FunctionalInterface
	public interface Body {
		void draw(PoseStack pose);
	}

	/**
	 * Draws {@code body} in all its passes.
	 *
	 * @param tint  ARGB: a green or white tint keeps the painted greens (scaled by its brightness); a red-dominant one (hurt) multiplies
	 *              the body toward red and adds the vanilla red hurt overlay; alpha below 255 draws it see-through
	 * @param flash 0..1 additive green-white morph flash over the whole body
	 * @param translucentParts true when the body has see-through swatches (FLYWING) to draw in a second pass
	 */
	public static void draw(Body body, PoseStack pose, MultiBufferSource buffers, int packedLight, int tint, float flash, boolean translucentParts) {
		int tr = (tint >> 16) & 255, tg = (tint >> 8) & 255, tb = tint & 255, ta = (tint >>> 24) & 255;
		if (ta == 0) {
			ta = 255;
		}
		int mr, mg, mb;
		if (tr > tg + 24 && tr > tb) {
			mr = 255;
			mg = Math.max(70, tg * 255 / Math.max(1, tr));
			mb = Math.max(70, tb * 255 / Math.max(1, tr));
			overlay = OverlayTexture.pack(0, OverlayTexture.v(true));
		} else {
			int m = Math.max(tr, Math.max(tg, tb));
			mr = mg = mb = Math.max(60, m);
			overlay = OverlayTexture.NO_OVERLAY;
		}
		light = packedLight;
		pose.pushPose();
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		boolean see = ta < 250;
		r = mr;
		g = mg;
		b = mb;
		a = ta;
		pass = PASS_OPAQUE;
		vc = buffers.getBuffer(see ? RenderType.entityTranslucent(TEX) : RenderType.entityCutoutNoCull(TEX));
		body.draw(pose);
		if (translucentParts) {
			pass = PASS_TRANSLUCENT;
			vc = buffers.getBuffer(RenderType.entityTranslucent(TEX));
			body.draw(pose);
		}
		if (flash > 0.003f) {
			pass = PASS_GLOW;
			light = LightTexture.FULL_BRIGHT;
			overlay = OverlayTexture.NO_OVERLAY;
			float k = Mth.clamp(flash, 0f, 1f) * ta / 255f;
			r = (int) (150 * k);
			g = (int) (255 * k);
			b = (int) (120 * k);
			a = 255;
			vc = buffers.getBuffer(RenderType.eyes(TEX));
			body.draw(pose);
		}
		pose.popPose();
		vc = null;
	}

	// ------------------------------------------------------------------------------------------------------------------ bones

	/** Push a bone: pivot, then rotation Z * Y * X (like ModelPart, radians). Pop it yourself. */
	public static void bone(PoseStack pose, float px, float py, float pz, float xr, float yr, float zr) {
		pose.pushPose();
		pose.translate(px, py, pz);
		if (xr != 0f || yr != 0f || zr != 0f) {
			pose.mulPose(new Quaternionf().rotationZYX(zr, yr, xr));
		}
	}

	/** Push a bone with a yaw-then-pitch-then-roll order (Y * X * Z), the natural order for heads and bodies. */
	public static void boneYXZ(PoseStack pose, float px, float py, float pz, float yr, float xr, float zr) {
		pose.pushPose();
		pose.translate(px, py, pz);
		if (xr != 0f || yr != 0f || zr != 0f) {
			pose.mulPose(new Quaternionf().rotationYXZ(yr, xr, zr));
		}
	}

	public static void pop(PoseStack pose) {
		pose.popPose();
	}

	// -------------------------------------------------------------------------------------------------------------- primitives

	/** A full ellipsoid. u round from the back (0) through the front (0.5); v = 0 top pole, 1 bottom pole. */
	public static void ellipsoid(PoseStack pose, float x, float y, float z, float rx, float ry, float rz, int sw) {
		ellipsoid(pose, x, y, z, rx, ry, rz, 14, 9, sw, 0f, 1f);
	}

	public static void ellipsoid(PoseStack pose, float x, float y, float z, float rx, float ry, float rz, int nu, int nv, int sw, float v0, float v1) {
		if (!wants(sw)) {
			return;
		}
		PoseStack.Pose p = pose.last();
		for (int j = 0; j < nv; j++) {
			float t0 = Mth.lerp(j / (float) nv, v0, v1), t1 = Mth.lerp((j + 1) / (float) nv, v0, v1);
			float la0 = t0 * Mth.PI, la1 = t1 * Mth.PI;
			float y0 = Mth.cos(la0), r0 = Mth.sin(la0), y1 = Mth.cos(la1), r1 = Mth.sin(la1);
			for (int i = 0; i < nu; i++) {
				float a0 = i / (float) nu * Mth.TWO_PI, a1 = (i + 1) / (float) nu * Mth.TWO_PI;
				float s0 = Mth.sin(a0), c0 = -Mth.cos(a0), s1 = Mth.sin(a1), c1 = -Mth.cos(a1);
				float u0 = i / (float) nu, u1 = (i + 1) / (float) nu;
				ev(p, x, y, z, rx, ry, rz, s0 * r0, y0, c0 * r0, u0, t0, sw);
				ev(p, x, y, z, rx, ry, rz, s0 * r1, y1, c0 * r1, u0, t1, sw);
				ev(p, x, y, z, rx, ry, rz, s1 * r1, y1, c1 * r1, u1, t1, sw);
				ev(p, x, y, z, rx, ry, rz, s1 * r0, y0, c1 * r0, u1, t0, sw);
			}
		}
	}

	/** An ellipsoid turned by yaw / pitch / roll (radians, Y * X * Z) about its centre. */
	public static void ellipsoidR(PoseStack pose, float x, float y, float z, float rx, float ry, float rz, float yaw, float pitch, float roll, int sw) {
		boneYXZ(pose, x, y, z, yaw, pitch, roll);
		ellipsoid(pose, 0f, 0f, 0f, rx, ry, rz, sw);
		pose.popPose();
	}

	private static void ev(PoseStack.Pose p, float x, float y, float z, float rx, float ry, float rz, float ux, float uy, float uz, float u, float v,
			int sw) {
		vert(p, x + ux * rx, y + uy * ry, z + uz * rz, u, v, sw, ux / Math.max(rx, 0.01f), uy / Math.max(ry, 0.01f), uz / Math.max(rz, 0.01f));
	}

	/** An eye: a small ellipsoid at (x, y, z) whose iris looks along yaw (0 = forward, + = toward +X) and pitch (+ = down). */
	public static void eye(PoseStack pose, float x, float y, float z, float rad, float yaw, float pitch, int sw) {
		boneYXZ(pose, x, y, z, yaw, pitch, 0f);
		ellipsoid(pose, 0f, 0f, 0f, rad, rad, rad, 12, 8, sw, 0f, 1f);
		pose.popPose();
	}

	/**
	 * An elliptic frustum: bottom ring at (x, y, z) radii (rx0, rz0), top ring {@code h} above with radii (rx1, rz1). u round from the
	 * back; v = 0 at the top.
	 */
	public static void taper(PoseStack pose, float x, float y, float z, float rx0, float rz0, float h, float rx1, float rz1, int n, int sw,
			boolean caps) {
		if (!wants(sw)) {
			return;
		}
		PoseStack.Pose p = pose.last();
		for (int i = 0; i < n; i++) {
			float a0 = i / (float) n * Mth.TWO_PI, a1 = (i + 1) / (float) n * Mth.TWO_PI;
			float s0 = Mth.sin(a0), c0 = -Mth.cos(a0), s1 = Mth.sin(a1), c1 = -Mth.cos(a1);
			float ny = (rx0 - rx1) / Math.max(Math.abs(h), 0.01f) * 0.6f;
			float u0 = i / (float) n, u1 = (i + 1) / (float) n;
			vert(p, x + s0 * rx1, y + h, z + c0 * rz1, u0, 0f, sw, s0 / Math.max(rx0, 0.01f), ny, c0 / Math.max(rz0, 0.01f));
			vert(p, x + s0 * rx0, y, z + c0 * rz0, u0, 1f, sw, s0 / Math.max(rx0, 0.01f), ny, c0 / Math.max(rz0, 0.01f));
			vert(p, x + s1 * rx0, y, z + c1 * rz0, u1, 1f, sw, s1 / Math.max(rx0, 0.01f), ny, c1 / Math.max(rz0, 0.01f));
			vert(p, x + s1 * rx1, y + h, z + c1 * rz1, u1, 0f, sw, s1 / Math.max(rx0, 0.01f), ny, c1 / Math.max(rz0, 0.01f));
		}
		if (caps) {
			cap(pose, x, y + h, z, rx1, rz1, n, sw, true);
			cap(pose, x, y, z, rx0, rz0, n, sw, false);
		}
	}

	/** A flat elliptic cap (planar UV), facing up or down. */
	public static void cap(PoseStack pose, float x, float y, float z, float rx, float rz, int n, int sw, boolean up) {
		if (!wants(sw) || (rx < 0.03f && rz < 0.03f)) {
			return;
		}
		PoseStack.Pose p = pose.last();
		float ny = up ? 1f : -1f;
		for (int i = 0; i < n; i++) {
			float a0 = i / (float) n * Mth.TWO_PI, a1 = (i + 1) / (float) n * Mth.TWO_PI;
			float s0 = Mth.sin(a0), c0 = -Mth.cos(a0), s1 = Mth.sin(a1), c1 = -Mth.cos(a1);
			vert(p, x, y, z, 0.5f, 0.5f, sw, 0f, ny, 0f);
			vert(p, x + s0 * rx, y, z + c0 * rz, 0.5f + s0 * 0.5f, 0.5f - c0 * 0.5f, sw, 0f, ny, 0f);
			vert(p, x + s1 * rx, y, z + c1 * rz, 0.5f + s1 * 0.5f, 0.5f - c1 * 0.5f, sw, 0f, ny, 0f);
			vert(p, x, y, z, 0.5f, 0.5f, sw, 0f, ny, 0f);
		}
	}

	/**
	 * A cone from a base centred at (x, y, z) pointing along (dx, dy, dz) (need not be normalised), length {@code len}, base half-widths
	 * {@code w} (sideways) and {@code h}. For teeth, claws, horns, quills.
	 */
	public static void cone(PoseStack pose, float x, float y, float z, float dx, float dy, float dz, float len, float w, float h, int sw) {
		if (!wants(sw)) {
			return;
		}
		float l = Mth.sqrt(dx * dx + dy * dy + dz * dz);
		if (l < 1.0e-5f) {
			return;
		}
		pose.pushPose();
		pose.translate(x, y, z);
		pose.mulPose(new Quaternionf().rotationTo(0f, 1f, 0f, dx / l, dy / l, dz / l));
		taper(pose, 0f, 0f, 0f, w, h, len, 0.02f, 0.02f, 7, sw, false);
		pose.popPose();
	}

	/** A curved horn / tusk / claw: a loft through the points with radius falling from {@code r0} to a point. */
	public static void horn(PoseStack pose, float[][] pts, float r0, int sw) {
		float[] w = new float[pts.length];
		for (int i = 0; i < pts.length; i++) {
			w[i] = r0 * (1f - i / (float) (pts.length - 1)) + 0.02f;
		}
		loft(pose, pts, w, w, 3, 8, sw, 0, null);
	}

	/** An axis-aligned box, every face mapping the whole swatch. */
	public static void box(PoseStack pose, float x0, float y0, float z0, float x1, float y1, float z1, int sw) {
		if (!wants(sw)) {
			return;
		}
		PoseStack.Pose p = pose.last();
		vert(p, x0, y1, z1, 0f, 0f, sw, 0, 0, 1);
		vert(p, x0, y0, z1, 0f, 1f, sw, 0, 0, 1);
		vert(p, x1, y0, z1, 1f, 1f, sw, 0, 0, 1);
		vert(p, x1, y1, z1, 1f, 0f, sw, 0, 0, 1);
		vert(p, x1, y1, z0, 0f, 0f, sw, 0, 0, -1);
		vert(p, x1, y0, z0, 0f, 1f, sw, 0, 0, -1);
		vert(p, x0, y0, z0, 1f, 1f, sw, 0, 0, -1);
		vert(p, x0, y1, z0, 1f, 0f, sw, 0, 0, -1);
		vert(p, x1, y1, z1, 0f, 0f, sw, 1, 0, 0);
		vert(p, x1, y0, z1, 0f, 1f, sw, 1, 0, 0);
		vert(p, x1, y0, z0, 1f, 1f, sw, 1, 0, 0);
		vert(p, x1, y1, z0, 1f, 0f, sw, 1, 0, 0);
		vert(p, x0, y1, z0, 0f, 0f, sw, -1, 0, 0);
		vert(p, x0, y0, z0, 0f, 1f, sw, -1, 0, 0);
		vert(p, x0, y0, z1, 1f, 1f, sw, -1, 0, 0);
		vert(p, x0, y1, z1, 1f, 0f, sw, -1, 0, 0);
		vert(p, x0, y1, z0, 0f, 0f, sw, 0, 1, 0);
		vert(p, x0, y1, z1, 0f, 1f, sw, 0, 1, 0);
		vert(p, x1, y1, z1, 1f, 1f, sw, 0, 1, 0);
		vert(p, x1, y1, z0, 1f, 0f, sw, 0, 1, 0);
		vert(p, x0, y0, z1, 0f, 0f, sw, 0, -1, 0);
		vert(p, x0, y0, z0, 0f, 1f, sw, 0, -1, 0);
		vert(p, x1, y0, z0, 1f, 1f, sw, 0, -1, 0);
		vert(p, x1, y0, z1, 1f, 0f, sw, 0, -1, 0);
	}

	/**
	 * A smooth tube through control points (Catmull-Rom, {@code sub} rings per span) with elliptic cross-sections: {@code w} sideways,
	 * {@code h} toward the "up" of the frame. The frame starts from {@code up} (null = +Y, or +Z when the tube starts vertical) and is
	 * carried along without twisting. u round from the top (0) through the left (0.25) to the underside (0.5); v along, repeating every
	 * {@code rep} rings (0 = stretched once over the whole tube).
	 */
	public static void loft(PoseStack pose, float[][] pts, float[] w, float[] h, int sub, int around, int sw, int rep, float[] up) {
		if (!wants(sw) || pts.length < 2) {
			return;
		}
		int n = pts.length;
		int rings = (n - 1) * sub + 1;
		float[][] c = new float[rings][];
		float[] rw = new float[rings], rh = new float[rings];
		for (int i = 0; i < n - 1; i++) {
			float[] p0 = i > 0 ? pts[i - 1] : extrap(pts[0], pts[1]);
			float[] p3 = i + 2 < n ? pts[i + 2] : extrap(pts[n - 1], pts[n - 2]);
			float w0 = i > 0 ? w[i - 1] : w[0], w3 = i + 2 < n ? w[i + 2] : w[n - 1];
			float h0 = i > 0 ? h[i - 1] : h[0], h3 = i + 2 < n ? h[i + 2] : h[n - 1];
			for (int s = 0; s < sub; s++) {
				float t = s / (float) sub;
				c[i * sub + s] = catmull(p0, pts[i], pts[i + 1], p3, t);
				rw[i * sub + s] = Math.max(0.02f, catmull1(w0, w[i], w[i + 1], w3, t));
				rh[i * sub + s] = Math.max(0.02f, catmull1(h0, h[i], h[i + 1], h3, t));
			}
		}
		c[rings - 1] = pts[n - 1];
		rw[rings - 1] = Math.max(0.02f, w[n - 1]);
		rh[rings - 1] = Math.max(0.02f, h[n - 1]);
		// frames
		float[][] tan = new float[rings][], ups = new float[rings][], sides = new float[rings][];
		for (int i = 0; i < rings; i++) {
			float[] d = sub(c[Math.min(i + 1, rings - 1)], c[Math.max(i - 1, 0)]);
			tan[i] = norm(d, i > 0 ? tan[i - 1] : new float[] { 0f, 0f, 1f });
		}
		float[] u0 = up != null ? up : (Math.abs(tan[0][1]) > 0.9f ? new float[] { 0f, 0f, 1f } : new float[] { 0f, 1f, 0f });
		for (int i = 0; i < rings; i++) {
			float[] prev = i == 0 ? u0 : ups[i - 1];
			float[] t = tan[i];
			float dt = dot(prev, t);
			float[] o = { prev[0] - t[0] * dt, prev[1] - t[1] * dt, prev[2] - t[2] * dt };
			if (len(o) < 1.0e-4f) {
				o = Math.abs(t[1]) > 0.9f ? new float[] { 0f, 0f, 1f } : new float[] { 0f, 1f, 0f };
				float d2 = dot(o, t);
				o = new float[] { o[0] - t[0] * d2, o[1] - t[1] * d2, o[2] - t[2] * d2 };
			}
			ups[i] = norm(o, prev);
			sides[i] = cross(ups[i], t);
		}
		PoseStack.Pose p = pose.last();
		for (int i = 0; i < rings - 1; i++) {
			float va, vb;
			if (rep > 0) {
				va = (i % rep) / (float) rep;
				vb = va + 1f / rep;
			} else {
				va = i / (float) (rings - 1);
				vb = (i + 1) / (float) (rings - 1);
			}
			for (int j = 0; j < around; j++) {
				float a0 = j / (float) around * Mth.TWO_PI, a1 = (j + 1) / (float) around * Mth.TWO_PI;
				float ua = j / (float) around, ub = (j + 1) / (float) around;
				ring(p, c[i], ups[i], sides[i], rw[i], rh[i], a0, ua, va, sw);
				ring(p, c[i + 1], ups[i + 1], sides[i + 1], rw[i + 1], rh[i + 1], a0, ua, vb, sw);
				ring(p, c[i + 1], ups[i + 1], sides[i + 1], rw[i + 1], rh[i + 1], a1, ub, vb, sw);
				ring(p, c[i], ups[i], sides[i], rw[i], rh[i], a1, ub, va, sw);
			}
		}
	}

	private static void ring(PoseStack.Pose p, float[] c, float[] up, float[] side, float w, float h, float ang, float u, float v, int sw) {
		float sa = Mth.sin(ang), ca = Mth.cos(ang);
		float x = c[0] + side[0] * sa * w + up[0] * ca * h;
		float y = c[1] + side[1] * sa * w + up[1] * ca * h;
		float z = c[2] + side[2] * sa * w + up[2] * ca * h;
		float nx = side[0] * sa / w + up[0] * ca / h, ny = side[1] * sa / w + up[1] * ca / h, nz = side[2] * sa / w + up[2] * ca / h;
		vert(p, x, y, z, u, v, sw, nx, ny, nz);
	}

	/**
	 * A thin two-sided sheet between two polylines of equal length (wing membranes, fins). u along the polylines, v from A (0) to B (1).
	 */
	public static void ribbon(PoseStack pose, float[][] edgeA, float[][] edgeB, int sw) {
		if (!wants(sw)) {
			return;
		}
		PoseStack.Pose p = pose.last();
		int n = Math.min(edgeA.length, edgeB.length);
		for (int i = 0; i < n - 1; i++) {
			float[] a0 = edgeA[i], a1 = edgeA[i + 1], b0 = edgeB[i], b1 = edgeB[i + 1];
			float[] nrm = cross(sub(a1, a0), sub(b0, a0));
			if (len(nrm) < 1.0e-5f) {
				nrm = cross(sub(b1, b0), sub(b0, a0));
			}
			nrm = norm(nrm, new float[] { 0f, 1f, 0f });
			float ua = i / (float) (n - 1), ub = (i + 1) / (float) (n - 1);
			vert(p, a0[0], a0[1], a0[2], ua, 0f, sw, nrm[0], nrm[1], nrm[2]);
			vert(p, b0[0], b0[1], b0[2], ua, 1f, sw, nrm[0], nrm[1], nrm[2]);
			vert(p, b1[0], b1[1], b1[2], ub, 1f, sw, nrm[0], nrm[1], nrm[2]);
			vert(p, a1[0], a1[1], a1[2], ub, 0f, sw, nrm[0], nrm[1], nrm[2]);
		}
	}

	// ------------------------------------------------------------------------------------------------------------------ vertices

	private static boolean wants(int sw) {
		if (vc == null) {
			return false;
		}
		return switch (pass) {
			case PASS_OPAQUE -> sw != FLYWING;
			case PASS_TRANSLUCENT -> sw == FLYWING;
			default -> sw != FLYWING && sw != EYE && sw != EYE_SLIT && sw != EYE_FIERCE;
		};
	}

	/** One vertex; (u, v) 0..1 inside swatch {@code sw} of the 256x256 atlas (half a texel inset). */
	private static void vert(PoseStack.Pose p, float x, float y, float z, float u, float v, int sw, float nx, float ny, float nz) {
		int s = pass == PASS_GLOW ? GLOW : sw;
		float su = (s % 8) * 32f, sv = (s / 8) * 32f;
		float tu = (su + 0.5f + Mth.clamp(u, 0f, 1f) * 31f) / 256f;
		float tv = (sv + 0.5f + Mth.clamp(v, 0f, 1f) * 31f) / 256f;
		float l = Mth.sqrt(nx * nx + ny * ny + nz * nz);
		if (l < 1.0e-5f) {
			ny = 1f;
			l = 1f;
		}
		vc.addVertex(p, x, y, z).setColor(r, g, b, a).setUv(tu, tv).setOverlay(overlay).setLight(light).setNormal(p, nx / l, ny / l, nz / l);
	}

	// ---------------------------------------------------------------------------------------------------------------- vec math

	public static float[] v(float x, float y, float z) {
		return new float[] { x, y, z };
	}

	public static float[] add(float[] a, float[] b) {
		return new float[] { a[0] + b[0], a[1] + b[1], a[2] + b[2] };
	}

	public static float[] sub(float[] a, float[] b) {
		return new float[] { a[0] - b[0], a[1] - b[1], a[2] - b[2] };
	}

	public static float[] mul(float[] a, float k) {
		return new float[] { a[0] * k, a[1] * k, a[2] * k };
	}

	public static float[] lerp(float[] a, float[] b, float t) {
		return new float[] { Mth.lerp(t, a[0], b[0]), Mth.lerp(t, a[1], b[1]), Mth.lerp(t, a[2], b[2]) };
	}

	public static float dot(float[] a, float[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	public static float[] cross(float[] a, float[] b) {
		return new float[] { a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0] };
	}

	public static float len(float[] a) {
		return Mth.sqrt(dot(a, a));
	}

	public static float[] norm(float[] a, float[] fallback) {
		float l = len(a);
		return l < 1.0e-6f ? fallback.clone() : new float[] { a[0] / l, a[1] / l, a[2] / l };
	}

	private static float[] extrap(float[] end, float[] next) {
		return new float[] { 2 * end[0] - next[0], 2 * end[1] - next[1], 2 * end[2] - next[2] };
	}

	private static float[] catmull(float[] p0, float[] p1, float[] p2, float[] p3, float t) {
		return new float[] { catmull1(p0[0], p1[0], p2[0], p3[0], t), catmull1(p0[1], p1[1], p2[1], p3[1], t), catmull1(p0[2], p1[2], p2[2], p3[2], t) };
	}

	private static float catmull1(float p0, float p1, float p2, float p3, float t) {
		float t2 = t * t, t3 = t2 * t;
		return 0.5f * (2f * p1 + (-p0 + p2) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 + (-p0 + 3f * p1 - 3f * p2 + p3) * t3);
	}

	/**
	 * Two-bone IK: the middle joint of a chain from {@code root} to {@code target} with bone lengths a and b, bending toward {@code pole}
	 * (a direction). When the target is out of reach the chain straightens toward it.
	 */
	public static float[] ik(float[] root, float[] target, float a, float b, float[] pole) {
		float[] d = sub(target, root);
		float dl = Math.max(0.001f, len(d));
		float reach = Math.min(dl, (a + b) * 0.999f);
		reach = Math.max(reach, Math.abs(a - b) + 0.01f);
		float[] dir = mul(d, 1f / dl);
		float along = (a * a - b * b + reach * reach) / (2f * reach);
		float hgt = Mth.sqrt(Math.max(0f, a * a - along * along));
		float pd = dot(pole, dir);
		float[] po = norm(new float[] { pole[0] - dir[0] * pd, pole[1] - dir[1] * pd, pole[2] - dir[2] * pd }, new float[] { 0f, 0f, 1f });
		return new float[] { root[0] + dir[0] * along + po[0] * hgt, root[1] + dir[1] * along + po[1] * hgt, root[2] + dir[2] * along + po[2] * hgt };
	}

	/** Builds a curve by walking from {@code start} along heading (yaw, pitch; pitch + = down), turning by the per-step bends. */
	public static float[][] walk(float[] start, float yaw, float pitch, float step, float[] yawBends, float[] pitchBends) {
		int n = yawBends.length + 1;
		float[][] out = new float[n][];
		out[0] = start.clone();
		float y = yaw, pt = pitch;
		float[] cur = start.clone();
		for (int i = 1; i < n; i++) {
			y += yawBends[i - 1];
			pt += pitchBends[i - 1];
			float cp = Mth.cos(pt);
			cur = new float[] { cur[0] + Mth.sin(y) * cp * step, cur[1] - Mth.sin(pt) * step, cur[2] + Mth.cos(y) * cp * step };
			out[i] = cur;
		}
		return out;
	}
}
