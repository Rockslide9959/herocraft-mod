package com.projecthero.mod.client.raven;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Raven: the little geometry kit her effects are drawn with. Two kinds of quads:
 * <ul>
 *   <li><b>shadow</b> -- see-through black / violet, drawn with {@code entityTranslucent} on a white texture (vertex colour), lit
 *   full-bright so the black stays black;</li>
 *   <li><b>glow</b> -- additive light ({@code RenderType.lightning()}), for the violet edges, the red Wrath glow and the healing thread.</li>
 * </ul>
 * Points are world positions minus the camera, in the pose's frame.
 */
public final class RavenDraw {
	public static final ResourceLocation WHITE = ProjectHeroMod.id("textures/entity/raven/white.png");

	private RavenDraw() {
	}

	public static VertexConsumer shadow(MultiBufferSource buffers) {
		return buffers.getBuffer(RenderType.entityTranslucent(WHITE));
	}

	public static VertexConsumer glow(MultiBufferSource buffers) {
		return buffers.getBuffer(RenderType.lightning());
	}

	/** One shadow quad (both sides show: the render type doesn't cull). */
	public static void quad(VertexConsumer vc, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb) {
		int al = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, bl = argb & 0xFF;
		Vec3 n = b.subtract(a).cross(d.subtract(a));
		if (n.lengthSqr() < 1.0e-9) {
			n = new Vec3(0, 1, 0);
		}
		n = n.normalize();
		v(vc, pose, a, r, g, bl, al, 0, 0, n);
		v(vc, pose, b, r, g, bl, al, 1, 0, n);
		v(vc, pose, c, r, g, bl, al, 1, 1, n);
		v(vc, pose, d, r, g, bl, al, 0, 1, n);
	}

	private static void v(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, int r, int g, int b, int a, float u, float vv, Vec3 n) {
		vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightTexture.FULL_BRIGHT).setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
	}

	/** One glow quad (additive). */
	public static void glowQuad(VertexConsumer vc, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb) {
		int al = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, bl = argb & 0xFF;
		for (Vec3 p : new Vec3[] { a, b, c, d }) {
			vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, bl, al);
		}
	}

	/**
	 * A camera-facing ribbon through {@code pts}, {@code w0} wide at the start tapering to {@code w1}. {@code glow}: additive, else
	 * shadow. Colours are ARGB, start to end.
	 */
	public static void ribbon(VertexConsumer vc, PoseStack.Pose pose, List<Vec3> pts, double w0, double w1, int c0, int c1, boolean glow) {
		int n = pts.size();
		if (n < 2) {
			return;
		}
		Vec3[] left = new Vec3[n], right = new Vec3[n];
		for (int i = 0; i < n; i++) {
			Vec3 p = pts.get(i);
			Vec3 dir = (i < n - 1 ? pts.get(i + 1).subtract(p) : p.subtract(pts.get(i - 1)));
			Vec3 side = dir.cross(p).normalize(); // p is relative to the camera, so this faces it
			if (!Double.isFinite(side.x) || side.lengthSqr() < 1.0e-6) {
				side = new Vec3(0, 1, 0);
			}
			double w = (w0 + (w1 - w0) * i / (double) (n - 1)) * 0.5;
			left[i] = p.add(side.scale(w));
			right[i] = p.subtract(side.scale(w));
		}
		for (int i = 0; i < n - 1; i++) {
			int col = lerpColour(c0, c1, (i + 0.5f) / (n - 1));
			if (glow) {
				glowQuad(vc, pose, left[i], left[i + 1], right[i + 1], right[i], col);
			} else {
				quad(vc, pose, left[i], left[i + 1], right[i + 1], right[i], col);
			}
		}
	}

	/** A tapered four-sided spike from {@code base} (radius {@code r}) to {@code tip}. */
	public static void spike(VertexConsumer vc, PoseStack.Pose pose, Vec3 base, Vec3 tip, double r, int argb) {
		Vec3 axis = tip.subtract(base).normalize();
		Vec3 ref = Math.abs(axis.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
		Vec3 s1 = axis.cross(ref).normalize().scale(r);
		Vec3 s2 = axis.cross(s1).normalize().scale(r);
		Vec3[] ring = { base.add(s1), base.add(s2), base.subtract(s1), base.subtract(s2) };
		for (int i = 0; i < 4; i++) {
			quad(vc, pose, ring[i], ring[(i + 1) % 4], tip, tip, argb);
		}
	}

	/** A curved, tapering claw: {@code segs} spikes along a curve (the points), radius {@code r} at the root. */
	public static void claw(VertexConsumer vc, PoseStack.Pose pose, List<Vec3> pts, double r, int argb) {
		int n = pts.size();
		for (int i = 0; i < n - 1; i++) {
			double r0 = r * (1.0 - i / (double) (n - 1));
			double r1 = r * (1.0 - (i + 1) / (double) (n - 1));
			prism(vc, pose, pts.get(i), pts.get(i + 1), r0, Math.max(0.005, r1), argb);
		}
	}

	/** A four-sided tapered prism between two points. */
	public static void prism(VertexConsumer vc, PoseStack.Pose pose, Vec3 a, Vec3 b, double ra, double rb, int argb) {
		Vec3 axis = b.subtract(a);
		if (axis.lengthSqr() < 1.0e-8) {
			return;
		}
		axis = axis.normalize();
		Vec3 ref = Math.abs(axis.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
		Vec3 s1 = axis.cross(ref).normalize();
		Vec3 s2 = axis.cross(s1).normalize();
		Vec3[] dirs = { s1, s2, s1.scale(-1), s2.scale(-1) };
		for (int i = 0; i < 4; i++) {
			Vec3 d0 = dirs[i], d1 = dirs[(i + 1) % 4];
			quad(vc, pose, a.add(d0.scale(ra)), a.add(d1.scale(ra)), b.add(d1.scale(rb)), b.add(d0.scale(rb)), argb);
		}
	}

	/** A latitude / longitude sphere of shadow quads. */
	public static void sphere(VertexConsumer vc, PoseStack.Pose pose, Vec3 c, double r, int lat, int lon, int argb, boolean glow) {
		for (int i = 0; i < lat; i++) {
			double t0 = Math.PI * i / lat - Math.PI / 2, t1 = Math.PI * (i + 1) / lat - Math.PI / 2;
			for (int j = 0; j < lon; j++) {
				double p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
				Vec3 a = c.add(pt(r, t0, p0)), b = c.add(pt(r, t0, p1)), cc = c.add(pt(r, t1, p1)), d = c.add(pt(r, t1, p0));
				if (glow) {
					glowQuad(vc, pose, a, b, cc, d, argb);
				} else {
					quad(vc, pose, a, b, cc, d, argb);
				}
			}
		}
	}

	private static Vec3 pt(double r, double theta, double phi) {
		return new Vec3(r * Math.cos(theta) * Math.cos(phi), r * Math.sin(theta), r * Math.cos(theta) * Math.sin(phi));
	}

	/** A flat ring on the ground from radius {@code r0} to {@code r1}. */
	public static void ring(VertexConsumer vc, PoseStack.Pose pose, Vec3 c, double r0, double r1, int segs, int argb, boolean glow) {
		for (int j = 0; j < segs; j++) {
			double p0 = Math.PI * 2 * j / segs, p1 = Math.PI * 2 * (j + 1) / segs;
			Vec3 a = c.add(Math.cos(p0) * r0, 0, Math.sin(p0) * r0), b = c.add(Math.cos(p1) * r0, 0, Math.sin(p1) * r0);
			Vec3 cc = c.add(Math.cos(p1) * r1, 0, Math.sin(p1) * r1), d = c.add(Math.cos(p0) * r1, 0, Math.sin(p0) * r1);
			if (glow) {
				glowQuad(vc, pose, a, b, cc, d, argb);
			} else {
				quad(vc, pose, a, b, cc, d, argb);
			}
		}
	}

	public static int lerpColour(int a, int b, float t) {
		int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
	}

	/** {@code argb} with its alpha multiplied by {@code k}. */
	public static int fade(int argb, float k) {
		int a = Math.max(0, Math.min(255, Math.round(((argb >>> 24) & 0xFF) * k)));
		return (a << 24) | (argb & 0xFFFFFF);
	}
}
