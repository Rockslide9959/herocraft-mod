package com.projecthero.mod.client.omnitrix;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.1 (user: "make the omnitrix capsule look like the omnitrix capsule from the show"; their reference frames): the pod as a
 * smooth mesh built here, in blocks, +z = its front, y up, resting on y = 0.
 * <ul>
 *   <li>The <b>base</b>: the lower half of a slightly flattened sphere ({@link #R} wide, {@link #FLAT} tall) of dark gunmetal
 *       plating, banded by the darker seams that curve round it like a ball's lines (the equirectangular {@code pod.png} from
 *       {@code scratchpad/omnitrix_acquire_v0171/gen_pod.js}), lit by the entity shader, with the show's pale highlight crescent
 *       along its upper-left edge as seen by the viewer (an additive pass, {@link #GLINT}).</li>
 *   <li>v0.17.2 (user: "its supposed to slide open in 2 ways, look at the reference image"): the <b>upper shell</b> is four
 *       curved plates -- two layers each side of the crown's centre line, each an orange-peel segment running from the front
 *       pivot to the back one. Opening, the left pair slides down round the left side and the right pair round the right,
 *       rotating about the front-to-back axis through the sphere's centre so they stay on its surface, the crown plate
 *       travelling further ({@link #SLIDE_MAX}) and nesting over the lower one, both tucking over the base's sides. Nothing lifts
 *       off a hinge.</li>
 *   <li>The round <b>pivot caps</b> front and back, where the plates' ends converge, each with a knob.</li>
 *   <li>The cut at the base's equator shows the <b>layered metal bands</b> of the inner rim stepping down into the dark ribbed
 *       interior, with a lit green ring round the cradle the watch sits on ({@link #GLOW_IN}).</li>
 *   <li>Fresh from the fall the seams glow orange-hot ({@link #HEAT}, fading).</li>
 * </ul>
 */
public final class OmnitrixPodModel {
	public static final ResourceLocation TEX = ProjectHeroMod.id("textures/entity/omnitrix/pod.png");
	public static final ResourceLocation HEAT = ProjectHeroMod.id("textures/entity/omnitrix/pod_heat.png");
	public static final ResourceLocation GLOW_IN = ProjectHeroMod.id("textures/entity/omnitrix/pod_inner_glow.png");
	public static final ResourceLocation GLINT = ProjectHeroMod.id("textures/entity/omnitrix/pod_glint.png");

	/** The sphere's radius and its vertical squash; its centre's height. */
	public static final float R = 0.48f, FLAT = 0.84f, CY = R * FLAT;
	/** The shell's thickness at the cut (the rim bands span it). */
	private static final float WALL = 0.1f;
	/** The rim's two steps down into the cavity: {radius in from the outside, drop below the cut}. */
	private static final float[][] STEPS = { { 0.035f, 0.018f }, { 0.07f, 0.04f } };
	/** The pivot caps: radius, how far they stand proud of the base, how deep their wall goes in; the knob on each. */
	private static final float HUB_R = 0.125f, HUB_OUT = 0.06f, HUB_IN = 0.03f, KNOB_R = 0.055f, KNOB_OUT = 0.03f;
	/** The crown plates' slide at full open (radians, 100 degrees). */
	public static final float SLIDE_MAX = (float) Math.toRadians(100);
	/**
	 * v0.17.2: the sliding plates, per side, crown first: {from, to (rest angle from the top toward that side, radians), inner and
	 * outer radius (x R), how far it slides at full open (radians)}. Both come to rest over the same stretch of the base's side,
	 * the crown plate outside the lower one.
	 */
	private static final float[][] PLATES = {
			{ 0f, (float) (Math.PI / 4), 1.042f, 1.072f, SLIDE_MAX },
			{ (float) (Math.PI / 4), (float) (Math.PI / 2), 1.006f, 1.036f, SLIDE_MAX - (float) (Math.PI / 4) } };
	/** Where the watch rests in the cradle (its centre's height). */
	public static final float SEAT_Y = 0.27f;
	private static final float CRADLE_R = 0.13f, CRADLE_TOP = 0.19f;
	private static final int NLON = 48, NLAT = 12, PLATE_ALONG = 28;

	private OmnitrixPodModel() {
	}

	/**
	 * Draws the pod. {@code open} 0..1 the plates' slide (already eased; a little past 1 overshoots), {@code heat} 0..1 the orange
	 * glow, {@code inner} 0..1 the cavity's green light.
	 */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, float open, float heat, float inner) {
		// ---- the solid parts
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(TEX));
		base(pose, vc, light);
		for (int side = -1; side <= 1; side += 2) {
			for (float[] pl : PLATES) {
				pose.pushPose();
				slideTransform(pose, side, open * pl[4]);
				plate(pose, vc, light, side, pl, 0xFFFFFFFF, 0);
				pose.popPose();
			}
		}
		// ---- the pale crescent (view dependent)
		VertexConsumer glint = buffers.getBuffer(RenderType.eyes(GLINT));
		crescent(pose, glint);
		for (int side = -1; side <= 1; side += 2) {
			for (float[] pl : PLATES) {
				pose.pushPose();
				slideTransform(pose, side, open * pl[4]);
				plateCrescent(pose, glint, side, pl);
				pose.popPose();
			}
		}
		// ---- hot seams
		if (heat > 0.01f) {
			VertexConsumer hv = buffers.getBuffer(RenderType.eyes(HEAT));
			int c = argb(heat, 1.0f, 0.42f, 0.1f);
			shell(pose, hv, LightTexture.FULL_BRIGHT, false, R * 1.002f, c, 1);
			for (int side = -1; side <= 1; side += 2) {
				for (float[] pl : PLATES) {
					pose.pushPose();
					slideTransform(pose, side, open * pl[4]);
					plate(pose, hv, LightTexture.FULL_BRIGHT, side, pl, c, 1);
					pose.popPose();
				}
			}
		}
		// ---- the cavity's light
		if (inner > 0.01f && open > 0.001f) {
			VertexConsumer gv = buffers.getBuffer(RenderType.eyes(GLOW_IN));
			innerShell(pose, gv, LightTexture.FULL_BRIGHT, false, argb(inner, 1f, 1f, 1f), 3);
		}
	}

	/**
	 * v0.17.2: a plate's slide round the sphere -- about the front-to-back axis through the centre, toward {@code side} (+1 = +x),
	 * done in the unsquashed frame so the plate keeps to the flattened sphere's surface the whole way round.
	 */
	public static void slideTransform(PoseStack pose, int side, float angle) {
		if (angle == 0f) {
			return;
		}
		pose.translate(0f, CY, 0f);
		pose.scale(1f, FLAT, 1f);
		pose.mulPose(Axis.ZP.rotation(-side * angle));
		pose.scale(1f, 1f / FLAT, 1f);
		pose.translate(0f, -CY, 0f);
	}

	private static int argb(float k, float r, float g, float b) {
		k = Math.max(0f, Math.min(1f, k));
		return 0xFF000000 | ((int) (r * k * 255) << 16) | ((int) (g * k * 255) << 8) | (int) (b * k * 255);
	}

	// ================================================================ the base

	/** The fixed lower half: shell, the cut's bands, the cavity, the cradle, the two pivot caps. */
	private static void base(PoseStack pose, VertexConsumer vc, int light) {
		shell(pose, vc, light, false, R, 0xFFFFFFFF, 0);
		rim(pose, vc, light);
		innerShell(pose, vc, light, false, 0xFFFFFFFF, 2);
		cradle(pose, vc, light);
		hub(pose, vc, light);
		pose.pushPose();
		pose.mulPose(Axis.YP.rotation((float) Math.PI));
		hub(pose, vc, light);
		pose.popPose();
	}

	/** {x, y, z} on the ellipsoid of radius {@code r} at (lat, lon). */
	private static float[] at(float r, double lat, double lon) {
		double c = Math.cos(lat);
		return new float[] { (float) (r * c * Math.sin(lon)), (float) (CY + r * FLAT * Math.sin(lat)), (float) (r * c * Math.cos(lon)) };
	}

	private static float[] normal(double lat, double lon) {
		double c = Math.cos(lat);
		float nx = (float) (c * Math.sin(lon)), ny = (float) (Math.sin(lat) / FLAT), nz = (float) (c * Math.cos(lon));
		float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		return new float[] { nx / l, ny / l, nz / l };
	}

	/**
	 * The outer shell's half: {@code uvMode} 0 the atlas's equirect (its top half), 1 a full-texture equirect (the heat mask).
	 * {@code argb} tints every vertex.
	 */
	private static void shell(PoseStack pose, VertexConsumer vc, int light, boolean top, float r, int argb, int uvMode) {
		PoseStack.Pose p = pose.last();
		double la0 = top ? 0 : -Math.PI / 2, la1 = top ? Math.PI / 2 : 0;
		for (int j = 0; j < NLAT; j++) {
			double a0 = la0 + (la1 - la0) * j / NLAT, a1 = la0 + (la1 - la0) * (j + 1) / NLAT;
			for (int i = 0; i < NLON; i++) {
				double o0 = -Math.PI + 2 * Math.PI * i / NLON, o1 = -Math.PI + 2 * Math.PI * (i + 1) / NLON;
				quadOnShell(vc, p, light, argb, r, a0, a1, o0, o1, uvMode, false);
			}
		}
	}

	private static void quadOnShell(VertexConsumer vc, PoseStack.Pose p, int light, int argb, float r, double a0, double a1, double o0, double o1,
			int uvMode, boolean inward) {
		double[][] corners = inward ? new double[][] { { a0, o0 }, { a1, o0 }, { a1, o1 }, { a0, o1 } }
				: new double[][] { { a0, o0 }, { a0, o1 }, { a1, o1 }, { a1, o0 } };
		for (double[] c : corners) {
			float[] q = at(r, c[0], c[1]);
			float[] n = normal(c[0], c[1]);
			float u = (float) ((c[1] + Math.PI) / (2 * Math.PI));
			float v = (float) ((Math.PI / 2 - c[0]) / Math.PI);
			if (uvMode == 0) {
				v *= 0.5f;
			} else if (uvMode == 2) {
				// the interior strip: u round, v from the cut (0) to the pole (1)
				u = 0.5f + u * 0.25f;
				v = 0.5f + (float) (Math.abs(c[0]) / (Math.PI / 2)) * 0.25f;
			} else if (uvMode == 3) {
				u = (float) ((c[1] + Math.PI) / (2 * Math.PI));
				v = (float) (Math.abs(c[0]) / (Math.PI / 2));
			}
			float s = inward ? -1f : 1f;
			vtx(vc, p, light, argb, q[0], q[1], q[2], n[0] * s, n[1] * s, n[2] * s, u, v);
		}
	}

	/** The cavity: the inner ellipsoid from below the second step to the pole, facing in. */
	private static void innerShell(PoseStack pose, VertexConsumer vc, int light, boolean top, int argb, int uvMode) {
		PoseStack.Pose p = pose.last();
		float ri = R - WALL;
		double start = Math.asin(Math.min(0.99, STEPS[1][1] / (ri * FLAT)));
		double la0 = top ? start : -Math.PI / 2, la1 = top ? Math.PI / 2 : -start;
		for (int j = 0; j < NLAT; j++) {
			double a0 = la0 + (la1 - la0) * j / NLAT, a1 = la0 + (la1 - la0) * (j + 1) / NLAT;
			for (int i = 0; i < NLON; i++) {
				double o0 = -Math.PI + 2 * Math.PI * i / NLON, o1 = -Math.PI + 2 * Math.PI * (i + 1) / NLON;
				quadOnShell(vc, p, light, argb, ri, a0, a1, o0, o1, uvMode, true);
			}
		}
	}

	/** The base's layered bands round the cut: the bright outer lip, a step down, a darker step, and the walls between. */
	private static void rim(PoseStack pose, VertexConsumer vc, int light) {
		PoseStack.Pose p = pose.last();
		float y = CY;
		float[] radii = { R, R - STEPS[0][0], R - STEPS[1][0], R - WALL };
		float[] drops = { 0f, STEPS[0][1], STEPS[1][1] };
		for (int k = 0; k < 3; k++) {
			float r0 = radii[k], r1 = radii[k + 1];
			float yy = y - drops[k];
			annulus(vc, p, light, r1, r0, yy, 1f, k);
			if (k < 2) {
				wall(vc, p, light, r1, yy, y - drops[k + 1], 3);
			}
		}
	}

	private static void annulus(VertexConsumer vc, PoseStack.Pose p, int light, float rIn, float rOut, float y, float ny, int swatch) {
		float u0 = (384 + swatch * 32 + 4) / 512f, u1 = (384 + swatch * 32 + 28) / 512f;
		for (int i = 0; i < NLON; i++) {
			double o0 = 2 * Math.PI * i / NLON, o1 = 2 * Math.PI * (i + 1) / NLON;
			float v0 = (256 + 64f * i / NLON) / 512f, v1 = (256 + 64f * (i + 1) / NLON) / 512f;
			float c0 = (float) Math.cos(o0), s0 = (float) Math.sin(o0), c1 = (float) Math.cos(o1), s1 = (float) Math.sin(o1);
			if (ny > 0) {
				vtx(vc, p, light, -1, s0 * rIn, y, c0 * rIn, 0, 1, 0, u0, v0);
				vtx(vc, p, light, -1, s0 * rOut, y, c0 * rOut, 0, 1, 0, u1, v0);
				vtx(vc, p, light, -1, s1 * rOut, y, c1 * rOut, 0, 1, 0, u1, v1);
				vtx(vc, p, light, -1, s1 * rIn, y, c1 * rIn, 0, 1, 0, u0, v1);
			} else {
				vtx(vc, p, light, -1, s0 * rIn, y, c0 * rIn, 0, -1, 0, u0, v0);
				vtx(vc, p, light, -1, s1 * rIn, y, c1 * rIn, 0, -1, 0, u0, v1);
				vtx(vc, p, light, -1, s1 * rOut, y, c1 * rOut, 0, -1, 0, u1, v1);
				vtx(vc, p, light, -1, s0 * rOut, y, c0 * rOut, 0, -1, 0, u1, v0);
			}
		}
	}

	/** A cylinder wall at radius r between y0 and y1, facing the axis (into the cavity). */
	private static void wall(VertexConsumer vc, PoseStack.Pose p, int light, float r, float y0, float y1, int swatch) {
		float u0 = (384 + swatch * 32 + 4) / 512f, u1 = (384 + swatch * 32 + 28) / 512f;
		float lo = Math.min(y0, y1), hi = Math.max(y0, y1);
		for (int i = 0; i < NLON; i++) {
			double o0 = 2 * Math.PI * i / NLON, o1 = 2 * Math.PI * (i + 1) / NLON;
			float v0 = (256 + 64f * i / NLON) / 512f, v1 = (256 + 64f * (i + 1) / NLON) / 512f;
			float c0 = (float) Math.cos(o0), s0 = (float) Math.sin(o0), c1 = (float) Math.cos(o1), s1 = (float) Math.sin(o1);
			vtx(vc, p, light, -1, s0 * r, lo, c0 * r, -s0, 0, -c0, u0, v0);
			vtx(vc, p, light, -1, s0 * r, hi, c0 * r, -s0, 0, -c0, u1, v0);
			vtx(vc, p, light, -1, s1 * r, hi, c1 * r, -s1, 0, -c1, u1, v1);
			vtx(vc, p, light, -1, s1 * r, lo, c1 * r, -s1, 0, -c1, u0, v1);
		}
	}

	/**
	 * v0.17.2: a pivot cap on the front (the back one is this turned round) -- the ringed drum standing out of the shell where the
	 * plates' ends meet, with a rounded knob on its face.
	 */
	private static void hub(PoseStack pose, VertexConsumer vc, int light) {
		PoseStack.Pose p = pose.last();
		drum(vc, p, light, HUB_R, R - HUB_IN, R + HUB_OUT, 64 / 512f, 320 / 512f, 64 / 512f);
		drum(vc, p, light, KNOB_R, R + HUB_OUT - 0.01f, R + HUB_OUT + KNOB_OUT, 64 / 512f, 320 / 512f, 22 / 512f);
	}

	/** A drum facing +z: its face (a fan, as quads with the centre doubled) mapping onto a disc of the atlas, and its side. */
	private static void drum(VertexConsumer vc, PoseStack.Pose p, int light, float rad, float z0, float z1, float uc, float vc0, float ur) {
		int seg = 32;
		for (int i = 0; i < seg; i++) {
			double t0 = 2 * Math.PI * i / seg, t1 = 2 * Math.PI * (i + 1) / seg;
			float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
			float x0 = -c0 * rad, y0 = CY + s0 * rad, x1 = -c1 * rad, y1 = CY + s1 * rad;
			vtx(vc, p, light, -1, 0, CY, z1, 0, 0, 1, uc, vc0);
			vtx(vc, p, light, -1, x1, y1, z1, 0, 0, 1, uc + (-c1) * ur * 0.98f, vc0 - s1 * ur * 0.98f);
			vtx(vc, p, light, -1, x0, y0, z1, 0, 0, 1, uc + (-c0) * ur * 0.98f, vc0 - s0 * ur * 0.98f);
			vtx(vc, p, light, -1, 0, CY, z1, 0, 0, 1, uc, vc0);
			float su0 = (128 + 128f * i / seg) / 512f, su1 = (128 + 128f * (i + 1) / seg) / 512f;
			float sv0 = 257 / 512f, sv1 = 287 / 512f;
			vtx(vc, p, light, -1, x0, y0, z1, -c0, s0, 0, su0, sv0);
			vtx(vc, p, light, -1, x1, y1, z1, -c1, s1, 0, su1, sv0);
			vtx(vc, p, light, -1, x1, y1, z0, -c1, s1, 0, su1, sv1);
			vtx(vc, p, light, -1, x0, y0, z0, -c0, s0, 0, su0, sv1);
		}
	}

	/** The cradle the watch rests on: a short dark drum on the cavity floor with its lit green band. */
	private static void cradle(PoseStack pose, VertexConsumer vc, int light) {
		PoseStack.Pose p = pose.last();
		int seg = 24;
		float y0 = CY - (R - WALL) * FLAT + 0.01f, y1 = CRADLE_TOP;
		for (int i = 0; i < seg; i++) {
			double o0 = 2 * Math.PI * i / seg, o1 = 2 * Math.PI * (i + 1) / seg;
			float c0 = (float) Math.cos(o0), s0 = (float) Math.sin(o0), c1 = (float) Math.cos(o1), s1 = (float) Math.sin(o1);
			float u0 = (384 + 128f * i / seg) / 512f, u1 = (384 + 128f * (i + 1) / seg) / 512f;
			vtx(vc, p, light, -1, s0 * CRADLE_R, y1, c0 * CRADLE_R, s0, 0, c0, u0, 321 / 512f);
			vtx(vc, p, light, -1, s0 * CRADLE_R, y0, c0 * CRADLE_R, s0, 0, c0, u0, 383 / 512f);
			vtx(vc, p, light, -1, s1 * CRADLE_R, y0, c1 * CRADLE_R, s1, 0, c1, u1, 383 / 512f);
			vtx(vc, p, light, -1, s1 * CRADLE_R, y1, c1 * CRADLE_R, s1, 0, c1, u1, 321 / 512f);
			// the top
			vtx(vc, p, light, -1, 0, y1, 0, 0, 1, 0, 448 / 512f, 340 / 512f);
			vtx(vc, p, light, -1, s0 * CRADLE_R, y1, c0 * CRADLE_R, 0, 1, 0, 440 / 512f, 336 / 512f);
			vtx(vc, p, light, -1, s1 * CRADLE_R, y1, c1 * CRADLE_R, 0, 1, 0, 456 / 512f, 336 / 512f);
			vtx(vc, p, light, -1, 0, y1, 0, 0, 1, 0, 448 / 512f, 340 / 512f);
		}
	}

	// ================================================================ v0.17.2 the sliding plates

	/** The unit direction at (phi round the front-back axis from the top toward {@code side}, beta toward the front pivot). */
	private static float[] dir(int side, double phi, double beta) {
		double c = Math.cos(beta);
		return new float[] { (float) (side * c * Math.sin(phi)), (float) (c * Math.cos(phi)), (float) Math.sin(beta) };
	}

	private static float[] onSquashed(float r, float[] d) {
		return new float[] { r * d[0], CY + r * FLAT * d[1], r * d[2] };
	}

	private static float[] squashedNormal(float[] d) {
		float nx = d[0], ny = d[1] / FLAT, nz = d[2];
		float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		return l < 1e-6f ? new float[] { 0, 1, 0 } : new float[] { nx / l, ny / l, nz / l };
	}

	/** The shell texture's (u, v) for a rest-pose direction: the same equirect the closed sphere always wore, riding with the plate. */
	private static float[] shellUv(int side, float[] d, int uvMode) {
		double lat = Math.asin(Math.max(-1, Math.min(1, d[1])));
		double lon = side * Math.atan2(Math.abs(d[0]), d[2]);
		float u = (float) ((lon + Math.PI) / (2 * Math.PI));
		float v = (float) ((Math.PI / 2 - lat) / Math.PI);
		if (uvMode == 0) {
			v *= 0.5f;
		} else if (uvMode == 2) {
			u = 0.5f + u * 0.25f;
			v = 0.5f + (float) (Math.abs(lat) / (Math.PI / 2)) * 0.25f;
		}
		return new float[] { u, v };
	}

	/**
	 * One plate in its rest pose: {@code uvMode} 0 the solid plate (outer face, inner face, the two long edges), 1 the heat mask on
	 * its outer face only.
	 */
	private static void plate(PoseStack pose, VertexConsumer vc, int light, int side, float[] pl, int argb, int uvMode) {
		PoseStack.Pose p = pose.last();
		float rIn = R * pl[2], rOut = R * pl[3];
		int nPhi = Math.max(2, Math.round((pl[1] - pl[0]) / (float) (Math.PI / 32)));
		for (int i = 0; i < nPhi; i++) {
			double f0 = pl[0] + (pl[1] - pl[0]) * i / nPhi, f1 = pl[0] + (pl[1] - pl[0]) * (i + 1) / nPhi;
			for (int j = 0; j < PLATE_ALONG; j++) {
				double b0 = -Math.PI / 2 + Math.PI * j / PLATE_ALONG, b1 = -Math.PI / 2 + Math.PI * (j + 1) / PLATE_ALONG;
				float[][] d = { dir(side, f0, b0), dir(side, f1, b0), dir(side, f1, b1), dir(side, f0, b1) };
				if (uvMode == 1) {
					surfQuad(vc, p, light, argb, side, d, rOut * 1.003f, 1, false);
				} else {
					surfQuad(vc, p, light, argb, side, d, rOut, 0, false);
					surfQuad(vc, p, light, argb, side, d, rIn, 2, true);
				}
			}
		}
		if (uvMode == 1) {
			return;
		}
		// the long edges: the leading one (the crown's centre line / the step between the layers) a bright lip, the trailing one darker
		for (int e = 0; e < 2; e++) {
			double f = pl[e];
			int swatch = e == 0 ? 0 : 2;
			float u0 = (384 + swatch * 32 + 4) / 512f, u1 = (384 + swatch * 32 + 28) / 512f;
			// the face points off the plate: up toward the crown for the leading edge, down toward the base for the trailing one
			float sgn = e == 0 ? -1f : 1f;
			float[] tn = { (float) (side * Math.cos(f)) * sgn, (float) (-Math.sin(f)) * sgn, 0f };
			for (int j = 0; j < PLATE_ALONG; j++) {
				double b0 = -Math.PI / 2 + Math.PI * j / PLATE_ALONG, b1 = -Math.PI / 2 + Math.PI * (j + 1) / PLATE_ALONG;
				float[] d0 = dir(side, f, b0), d1 = dir(side, f, b1);
				float v0 = (256 + 64f * j / PLATE_ALONG) / 512f, v1 = (256 + 64f * (j + 1) / PLATE_ALONG) / 512f;
				float[][] q = { onSquashed(rIn, d0), onSquashed(rOut, d0), onSquashed(rOut, d1), onSquashed(rIn, d1) };
				float[][] uv = { { u0, v0 }, { u1, v0 }, { u1, v1 }, { u0, v1 } };
				emit(vc, p, light, -1, q, new float[][] { tn, tn, tn, tn }, uv, tn);
			}
		}
	}

	private static void surfQuad(VertexConsumer vc, PoseStack.Pose p, int light, int argb, int side, float[][] d, float r, int uvMode,
			boolean inward) {
		float[][] q = new float[4][], n = new float[4][], uv = new float[4][];
		float[] avg = new float[3];
		for (int k = 0; k < 4; k++) {
			q[k] = onSquashed(r, d[k]);
			float[] nn = squashedNormal(d[k]);
			n[k] = inward ? new float[] { -nn[0], -nn[1], -nn[2] } : nn;
			uv[k] = shellUv(side, d[k], uvMode);
			avg[0] += n[k][0];
			avg[1] += n[k][1];
			avg[2] += n[k][2];
		}
		emit(vc, p, light, argb, q, n, uv, avg);
	}

	/** A quad, wound so its front faces {@code facing} (the entity shader culls back faces). */
	private static void emit(VertexConsumer vc, PoseStack.Pose p, int light, int argb, float[][] q, float[][] n, float[][] uv, float[] facing) {
		float[] c = cross(q[0], q[1], q[2]);
		if (c[0] * c[0] + c[1] * c[1] + c[2] * c[2] < 1e-14f) {
			c = cross(q[0], q[2], q[3]); // the first triangle is a pole's sliver
		}
		boolean flip = c[0] * facing[0] + c[1] * facing[1] + c[2] * facing[2] < 0f;
		for (int k = 0; k < 4; k++) {
			int i = flip ? 3 - k : k;
			vtx(vc, p, light, argb, q[i][0], q[i][1], q[i][2], n[i][0], n[i][1], n[i][2], uv[i][0], uv[i][1]);
		}
	}

	private static float[] cross(float[] a, float[] b, float[] c) {
		float ax = b[0] - a[0], ay = b[1] - a[1], az = b[2] - a[2];
		float bx = c[0] - a[0], by = c[1] - a[1], bz = c[2] - a[2];
		return new float[] { ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx };
	}

	// ================================================================ the glint

	/** The viewer's frame in the current pose's space: {camera position, up, left}. */
	private static Vector3f[] viewFrame(PoseStack.Pose p) {
		Matrix4f inv = new Matrix4f(p.pose()).invert();
		Vector3f cam = inv.transformPosition(new Vector3f(0, 0, 0));
		var rot = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
		Matrix3f invN = new Matrix3f(p.normal()).invert();
		Vector3f up = invN.transform(rot.transform(new Vector3f(0, 1, 0))).normalize();
		Vector3f left = invN.transform(rot.transform(new Vector3f(-1, 0, 0))).normalize();
		return new Vector3f[] { cam, up, left };
	}

	/** The crescent's strength at a surface point: bright where the shell turns away from the viewer on its upper-left side. */
	private static float sheen(Vector3f[] f, float[] q, float[] n) {
		Vector3f v = new Vector3f(f[0].x - q[0], f[0].y - q[1], f[0].z - q[2]).normalize();
		float nv = n[0] * v.x + n[1] * v.y + n[2] * v.z;
		Vector3f l = new Vector3f(f[1]).mul(0.72f).add(new Vector3f(f[2]).mul(0.62f)).add(new Vector3f(v).mul(0.12f)).normalize();
		float nl = n[0] * l.x + n[1] * l.y + n[2] * l.z;
		float rim = 1f - Math.max(0f, nv);
		float k1 = smooth(0.5f, 0.74f, rim) * (1f - smooth(0.9f, 1.0f, rim)) * smooth(0.45f, 0.85f, nl);
		float k2 = smooth(0.7f, 0.98f, nl) * 0.05f; // and a soft broad sheen up there too
		return Math.min(1f, k1 * 0.55f + k2);
	}

	/** The show's pale crescent on the base (in the viewer's frame, so it stays put as the pod or the camera moves). Additive. */
	private static void crescent(PoseStack pose, VertexConsumer vc) {
		PoseStack.Pose p = pose.last();
		Vector3f[] f = viewFrame(p);
		float rr = R * 1.004f;
		for (int j = 0; j < NLAT; j++) {
			double a0 = -Math.PI / 2 + (Math.PI / 2) * j / NLAT, a1 = -Math.PI / 2 + (Math.PI / 2) * (j + 1) / NLAT;
			for (int i = 0; i < NLON; i++) {
				double o0 = -Math.PI + 2 * Math.PI * i / NLON, o1 = -Math.PI + 2 * Math.PI * (i + 1) / NLON;
				double[][] corners = { { a0, o0 }, { a0, o1 }, { a1, o1 }, { a1, o0 } };
				float[][] q = new float[4][], n = new float[4][];
				for (int k = 0; k < 4; k++) {
					q[k] = at(rr, corners[k][0], corners[k][1]);
					n[k] = normal(corners[k][0], corners[k][1]);
				}
				glintQuad(vc, p, f, q, n, null);
			}
		}
	}

	/** The crescent over a sliding plate's outer face (it moves with the plate, the light stays with the viewer). */
	private static void plateCrescent(PoseStack pose, VertexConsumer vc, int side, float[] pl) {
		PoseStack.Pose p = pose.last();
		Vector3f[] f = viewFrame(p);
		float rr = R * pl[3] * 1.004f;
		int nPhi = Math.max(2, Math.round((pl[1] - pl[0]) / (float) (Math.PI / 32)));
		for (int i = 0; i < nPhi; i++) {
			double f0 = pl[0] + (pl[1] - pl[0]) * i / nPhi, f1 = pl[0] + (pl[1] - pl[0]) * (i + 1) / nPhi;
			for (int j = 0; j < PLATE_ALONG; j++) {
				double b0 = -Math.PI / 2 + Math.PI * j / PLATE_ALONG, b1 = -Math.PI / 2 + Math.PI * (j + 1) / PLATE_ALONG;
				float[][] d = { dir(side, f0, b0), dir(side, f1, b0), dir(side, f1, b1), dir(side, f0, b1) };
				float[][] q = new float[4][], n = new float[4][];
				float[] avg = new float[3];
				for (int k = 0; k < 4; k++) {
					q[k] = onSquashed(rr, d[k]);
					n[k] = squashedNormal(d[k]);
					avg[0] += n[k][0];
					avg[1] += n[k][1];
					avg[2] += n[k][2];
				}
				glintQuad(vc, p, f, q, n, avg);
			}
		}
	}

	/** One additive glint quad (skipped when dark); {@code facing} null = already wound outward. */
	private static void glintQuad(VertexConsumer vc, PoseStack.Pose p, Vector3f[] f, float[][] q, float[][] n, float[] facing) {
		int[] col = new int[4];
		boolean any = false;
		for (int k = 0; k < 4; k++) {
			float kk = sheen(f, q[k], n[k]);
			col[k] = argb(kk, 0.86f, 0.9f, 0.96f);
			any |= kk > 0.004f;
		}
		if (!any) {
			return;
		}
		boolean flip = false;
		if (facing != null) {
			float[] c = cross(q[0], q[1], q[2]);
			if (c[0] * c[0] + c[1] * c[1] + c[2] * c[2] < 1e-14f) {
				c = cross(q[0], q[2], q[3]);
			}
			flip = c[0] * facing[0] + c[1] * facing[1] + c[2] * facing[2] < 0f;
		}
		for (int k = 0; k < 4; k++) {
			int i = flip ? 3 - k : k;
			vtx(vc, p, LightTexture.FULL_BRIGHT, col[i], q[i][0], q[i][1], q[i][2], n[i][0], n[i][1], n[i][2], 0.5f, 0.5f);
		}
	}

	private static float smooth(float e0, float e1, float x) {
		float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
		return t * t * (3f - 2f * t);
	}

	private static void vtx(VertexConsumer vc, PoseStack.Pose p, int light, int argb, float x, float y, float z, float nx, float ny, float nz,
			float u, float v) {
		vc.addVertex(p, x, y, z).setColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
	}
}
