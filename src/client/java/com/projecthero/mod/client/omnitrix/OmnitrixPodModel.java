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
 *   <li>The <b>shell</b>: a slightly flattened sphere ({@link #R} wide, {@link #FLAT} tall) of dark gunmetal plating, banded by
 *       the darker seams that curve round it like a ball's lines (the equirectangular {@code pod.png} from
 *       {@code scratchpad/omnitrix_acquire_v0171/gen_pod.js}), lit by the entity shader, with the show's pale highlight
 *       crescent along its upper-left edge as seen by the viewer (an additive pass, {@link #GLINT}).</li>
 *   <li>The round <b>hub</b> on the front where the halves meet (split with them), and the keyhole notch under it (painted).</li>
 *   <li>It opens like a clam: the top half hinges back about the rear of the equator by up to {@link #LID_MAX}; the cut
 *       shows the <b>layered metal bands</b> of the inner rim stepping down into the dark ribbed interior, with a lit green
 *       ring round the cradle the watch sits on ({@link #GLOW_IN}).</li>
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
	/** The hub: radius, how far it stands proud of the shell, how deep its wall goes in. */
	private static final float HUB_R = 0.125f, HUB_OUT = 0.035f, HUB_IN = 0.03f;
	/** The lid's widest swing (radians). */
	public static final float LID_MAX = 1.92f;
	/** Where the watch rests in the cradle (its centre's height). */
	public static final float SEAT_Y = 0.27f;
	private static final float CRADLE_R = 0.13f, CRADLE_TOP = 0.19f;
	private static final int NLON = 48, NLAT = 12;

	private OmnitrixPodModel() {
	}

	/**
	 * Draws the pod. {@code open} 0..1 the lid's swing (already eased), {@code heat} 0..1 the orange glow, {@code inner} 0..1 the
	 * cavity's green light.
	 */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, float open, float heat, float inner) {
		float lid = open * LID_MAX;
		// ---- the solid parts
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(TEX));
		half(pose, vc, light, false, open > 0.001f);
		pose.pushPose();
		lidTransform(pose, lid);
		half(pose, vc, light, true, open > 0.001f);
		pose.popPose();
		// ---- the pale crescent (view dependent), on both halves
		VertexConsumer glint = buffers.getBuffer(RenderType.eyes(GLINT));
		crescent(pose, glint, false);
		pose.pushPose();
		lidTransform(pose, lid);
		crescent(pose, glint, true);
		pose.popPose();
		// ---- hot seams
		if (heat > 0.01f) {
			VertexConsumer hv = buffers.getBuffer(RenderType.eyes(HEAT));
			int c = argb(heat, 1.0f, 0.42f, 0.1f);
			shell(pose, hv, LightTexture.FULL_BRIGHT, false, R * 1.002f, c, 1);
			pose.pushPose();
			lidTransform(pose, lid);
			shell(pose, hv, LightTexture.FULL_BRIGHT, true, R * 1.002f, c, 1);
			pose.popPose();
		}
		// ---- the cavity's light
		if (inner > 0.01f && open > 0.001f) {
			VertexConsumer gv = buffers.getBuffer(RenderType.eyes(GLOW_IN));
			innerShell(pose, gv, LightTexture.FULL_BRIGHT, false, argb(inner, 1f, 1f, 1f), 3);
			pose.pushPose();
			lidTransform(pose, lid);
			innerShell(pose, gv, LightTexture.FULL_BRIGHT, true, argb(inner * 0.5f, 1f, 1f, 1f), 3);
			pose.popPose();
		}
	}

	/** The lid's hinge: the rear of the cut (z = -R), swinging up and back. */
	public static void lidTransform(PoseStack pose, float angle) {
		pose.translate(0f, CY, -R);
		pose.mulPose(Axis.XP.rotation(-angle));
		pose.translate(0f, -CY, R);
	}

	private static int argb(float k, float r, float g, float b) {
		k = Math.max(0f, Math.min(1f, k));
		return 0xFF000000 | ((int) (r * k * 255) << 16) | ((int) (g * k * 255) << 8) | (int) (b * k * 255);
	}

	// ================================================================ the parts

	private static void half(PoseStack pose, VertexConsumer vc, int light, boolean top, boolean opened) {
		shell(pose, vc, light, top, R, 0xFFFFFFFF, 0);
		hub(pose, vc, light, top);
		// the cut: the outer lip and the two steps, then the cavity (only ever seen open, but cheap)
		rim(pose, vc, light, top);
		innerShell(pose, vc, light, top, 0xFFFFFFFF, 2);
		if (!top) {
			cradle(pose, vc, light);
		}
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

	/** The layered bands round the cut: the bright outer lip, a step down, a darker step, and the walls between. */
	private static void rim(PoseStack pose, VertexConsumer vc, int light, boolean top) {
		PoseStack.Pose p = pose.last();
		float sgn = top ? 1f : -1f; // the lid's steps go UP into it
		float y = CY;
		float[] radii = { R, R - STEPS[0][0], R - STEPS[1][0], R - WALL };
		float[] drops = { 0f, STEPS[0][1], STEPS[1][1] };
		for (int k = 0; k < 3; k++) {
			float r0 = radii[k], r1 = radii[k + 1];
			float yy = y + sgn * drops[k];
			// the flat band (faces away from the cavity's opening: down for the lid, up for the base)
			annulus(vc, p, light, r1, r0, yy, -sgn, k);
			// the wall down to the next step
			if (k < 2) {
				float y2 = y + sgn * drops[k + 1];
				wall(vc, p, light, r1, yy, y2, 3, top);
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
	private static void wall(VertexConsumer vc, PoseStack.Pose p, int light, float r, float y0, float y1, int swatch, boolean top) {
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

	/** The front hub: a short drum standing out of the shell, its face a ringed plate; each half carries its half. */
	private static void hub(PoseStack pose, VertexConsumer vc, int light, boolean top) {
		PoseStack.Pose p = pose.last();
		int seg = 20;
		float z0 = R - HUB_IN, z1 = R + HUB_OUT;
		double a0 = top ? 0 : Math.PI, a1 = top ? Math.PI : 2 * Math.PI;
		for (int i = 0; i < seg; i++) {
			double t0 = a0 + (a1 - a0) * i / seg, t1 = a0 + (a1 - a0) * (i + 1) / seg;
			float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0), c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
			float x0 = -c0 * HUB_R, y0 = CY + s0 * HUB_R, x1 = -c1 * HUB_R, y1 = CY + s1 * HUB_R;
			// the face (a fan from the centre, as quads with the centre doubled)
			float uc = 64 / 512f, vc0 = 320 / 512f, ur = 64 / 512f;
			vtx(vc, p, light, -1, 0, CY, z1, 0, 0, 1, uc, vc0);
			vtx(vc, p, light, -1, x1, y1, z1, 0, 0, 1, uc + (-c1) * ur * 0.98f, vc0 - s1 * ur * 0.98f);
			vtx(vc, p, light, -1, x0, y0, z1, 0, 0, 1, uc + (-c0) * ur * 0.98f, vc0 - s0 * ur * 0.98f);
			vtx(vc, p, light, -1, 0, CY, z1, 0, 0, 1, uc, vc0);
			// the drum's side
			float su0 = (128 + 128f * i / seg) / 512f, su1 = (128 + 128f * (i + 1) / seg) / 512f;
			float sv0 = 257 / 512f, sv1 = 287 / 512f;
			vtx(vc, p, light, -1, x0, y0, z1, -c0, s0, 0, su0, sv0);
			vtx(vc, p, light, -1, x1, y1, z1, -c1, s1, 0, su1, sv0);
			vtx(vc, p, light, -1, x1, y1, z0, -c1, s1, 0, su1, sv1);
			vtx(vc, p, light, -1, x0, y0, z0, -c0, s0, 0, su0, sv1);
		}
		// the half's flat cut across the drum (seen when the lid lifts)
		float ny = top ? -1 : 1;
		float u0 = 130 / 512f, u1 = 250 / 512f, v0 = 258 / 512f, v1 = 286 / 512f;
		if (top) {
			vtx(vc, p, light, -1, -HUB_R, CY, z0, 0, ny, 0, u0, v0);
			vtx(vc, p, light, -1, HUB_R, CY, z0, 0, ny, 0, u1, v0);
			vtx(vc, p, light, -1, HUB_R, CY, z1, 0, ny, 0, u1, v1);
			vtx(vc, p, light, -1, -HUB_R, CY, z1, 0, ny, 0, u0, v1);
		} else {
			vtx(vc, p, light, -1, -HUB_R, CY, z1, 0, ny, 0, u0, v1);
			vtx(vc, p, light, -1, HUB_R, CY, z1, 0, ny, 0, u1, v1);
			vtx(vc, p, light, -1, HUB_R, CY, z0, 0, ny, 0, u1, v0);
			vtx(vc, p, light, -1, -HUB_R, CY, z0, 0, ny, 0, u0, v0);
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

	/**
	 * The show's pale crescent: bright where the shell turns away from the viewer on its upper-left side (in the viewer's frame,
	 * so it stays put as the pod or the camera moves). Additive.
	 */
	private static void crescent(PoseStack pose, VertexConsumer vc, boolean top) {
		PoseStack.Pose p = pose.last();
		Matrix4f inv = new Matrix4f(p.pose()).invert();
		Vector3f cam = inv.transformPosition(new Vector3f(0, 0, 0));
		var rot = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
		Matrix3f invN = new Matrix3f(p.normal()).invert();
		Vector3f up = invN.transform(rot.transform(new Vector3f(0, 1, 0))).normalize();
		Vector3f left = invN.transform(rot.transform(new Vector3f(-1, 0, 0))).normalize();
		float rr = R * 1.004f;
		double la0 = top ? 0 : -Math.PI / 2, la1 = top ? Math.PI / 2 : 0;
		for (int j = 0; j < NLAT; j++) {
			double a0 = la0 + (la1 - la0) * j / NLAT, a1 = la0 + (la1 - la0) * (j + 1) / NLAT;
			for (int i = 0; i < NLON; i++) {
				double o0 = -Math.PI + 2 * Math.PI * i / NLON, o1 = -Math.PI + 2 * Math.PI * (i + 1) / NLON;
				double[][] corners = { { a0, o0 }, { a0, o1 }, { a1, o1 }, { a1, o0 } };
				int[] col = new int[4];
				boolean any = false;
				for (int k = 0; k < 4; k++) {
					float[] q = at(rr, corners[k][0], corners[k][1]);
					float[] n = normal(corners[k][0], corners[k][1]);
					Vector3f v = new Vector3f(cam.x - q[0], cam.y - q[1], cam.z - q[2]).normalize();
					float nv = n[0] * v.x + n[1] * v.y + n[2] * v.z;
					Vector3f l = new Vector3f(up).mul(0.72f).add(new Vector3f(left).mul(0.62f)).add(new Vector3f(v).mul(0.12f)).normalize();
					float nl = n[0] * l.x + n[1] * l.y + n[2] * l.z;
					float rim = 1f - Math.max(0f, nv);
					float k1 = smooth(0.5f, 0.74f, rim) * (1f - smooth(0.9f, 1.0f, rim)) * smooth(0.45f, 0.85f, nl);
					// and a soft broad sheen up there too
					float k2 = smooth(0.7f, 0.98f, nl) * 0.05f;
					float kk = Math.min(1f, k1 * 0.55f + k2);
					col[k] = argb(kk, 0.86f, 0.9f, 0.96f);
					any |= kk > 0.004f;
				}
				if (!any) {
					continue;
				}
				for (int k = 0; k < 4; k++) {
					float[] q = at(rr, corners[k][0], corners[k][1]);
					float[] n = normal(corners[k][0], corners[k][1]);
					vtx(vc, p, LightTexture.FULL_BRIGHT, col[k], q[0], q[1], q[2], n[0], n[1], n[2], 0.5f, 0.5f);
				}
			}
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
