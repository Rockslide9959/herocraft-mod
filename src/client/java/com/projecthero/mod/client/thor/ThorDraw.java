package com.projecthero.mod.client.thor;

import com.projecthero.mod.client.maxsteel.TurboDraw;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.4: Thor's lightning look for effects drawn in code. A bolt is a jagged node path drawn as three camera-facing
 * ribbons -- a wide, faint storm-blue halo, a brighter electric-blue glow and a thin white-hot core -- so it reads as a
 * thick glowing bolt from any angle (the old arcs were bundles of 1-pixel GL lines). Rings (shockwaves) are flat
 * annuli plus a short vertical band of light. Everything is full-bright and translucent over the plain white texture
 * {@link TurboDraw} uses; all positions are relative to the camera.
 */
public final class ThorDraw {
	public static final int HALO = 0x2F5BFF;
	public static final int GLOW = 0x7FC4FF;
	public static final int CORE = 0xF4FAFF;

	private ThorDraw() {
	}

	public static VertexConsumer buffer(MultiBufferSource buffers) {
		return TurboDraw.buffer(buffers);
	}

	/** A full lightning bolt along {@code path}: halo + glow + core, {@code width} scales all three. */
	public static void bolt(VertexConsumer vc, PoseStack.Pose pose, Vec3[] path, float width, float alpha) {
		ribbon(vc, pose, path, 0.26f * width, HALO, 0.2f * alpha);
		ribbon(vc, pose, path, 0.11f * width, GLOW, 0.5f * alpha);
		ribbon(vc, pose, path, 0.04f * width, CORE, 0.95f * alpha);
	}

	/**
	 * A camera-facing strip of half-width {@code halfWidth} through every node of {@code path}. The side vector at each
	 * node is taken from the averaged tangent there, so consecutive segments share their edge and the strip never
	 * cracks open at a bend.
	 */
	public static void ribbon(VertexConsumer vc, PoseStack.Pose pose, Vec3[] path, float halfWidth, int rgb, float alpha) {
		if (path.length < 2 || alpha <= 0.003f) {
			return;
		}
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
		Vec3[] side = new Vec3[path.length];
		for (int i = 0; i < path.length; i++) {
			Vec3 prev = path[Math.max(0, i - 1)];
			Vec3 next = path[Math.min(path.length - 1, i + 1)];
			Vec3 tangent = next.subtract(prev);
			Vec3 toCam = path[i].scale(-1.0);
			Vec3 s = tangent.cross(toCam);
			double len = s.length();
			side[i] = len < 1.0E-6 ? new Vec3(halfWidth, 0, 0) : s.scale(halfWidth / len);
		}
		for (int i = 1; i < path.length; i++) {
			Vec3 p0 = path[i - 1];
			Vec3 p1 = path[i];
			Vec3 s0 = side[i - 1];
			Vec3 s1 = side[i];
			vertex(vc, pose, p0.subtract(s0), r, g, b, a);
			vertex(vc, pose, p0.add(s0), r, g, b, a);
			vertex(vc, pose, p1.add(s1), r, g, b, a);
			vertex(vc, pose, p1.subtract(s1), r, g, b, a);
		}
	}

	/**
	 * A jagged node path from {@code start} to {@code end}: each interior node pushed sideways by a hash of its index
	 * and {@code seed}, tapering to nothing at both ends so the bolt always meets its endpoints exactly. Re-seeding
	 * over time (a floored clock) makes it hold one shape for a moment, then jump to the next -- the crackle.
	 */
	public static Vec3[] jagged(Vec3 start, Vec3 end, int nodes, double amplitude, double seed) {
		Vec3 delta = end.subtract(start);
		double length = delta.length();
		Vec3[] path = new Vec3[nodes + 1];
		path[0] = start;
		path[nodes] = end;
		if (length < 1.0E-4) {
			for (int i = 1; i < nodes; i++) {
				path[i] = start;
			}
			return path;
		}
		Vec3 dir = delta.scale(1.0 / length);
		Vec3 ref = Math.abs(dir.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
		Vec3 perpA = dir.cross(ref).normalize();
		Vec3 perpB = dir.cross(perpA).normalize();
		for (int i = 1; i < nodes; i++) {
			double t = (double) i / nodes;
			double h1 = hash(seed + i * 13.7);
			double h2 = hash(seed * 1.37 + i * 7.1 + 3.3);
			double taper = Math.sin(Math.PI * t);
			path[i] = start.lerp(end, t)
					.add(perpA.scale((h1 * 2.0 - 1.0) * amplitude * taper))
					.add(perpB.scale((h2 * 2.0 - 1.0) * amplitude * taper));
		}
		return path;
	}

	/** A deterministic 0..1 hash of {@code v}. */
	public static double hash(double v) {
		double s = Math.sin(v * 12.9898 + 78.233) * 43758.5453;
		return s - Math.floor(s);
	}

	/**
	 * A flat ring (annulus) round {@code center} from {@code inner} to {@code outer} blocks, fading out toward both
	 * edges from a bright middle, in {@code segments} slices.
	 */
	public static void ring(VertexConsumer vc, PoseStack.Pose pose, Vec3 center, float inner, float outer, int rgb,
			float alpha, int segments) {
		if (outer <= inner || alpha <= 0.003f) {
			return;
		}
		float mid = (inner + outer) * 0.5f;
		band(vc, pose, center, inner, mid, rgb, 0f, alpha, segments);
		band(vc, pose, center, mid, outer, rgb, alpha, 0f, segments);
	}

	private static void band(VertexConsumer vc, PoseStack.Pose pose, Vec3 c, float r0, float r1, int rgb, float a0, float a1,
			int segments) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		int ia0 = Math.round(Math.max(0f, Math.min(1f, a0)) * 255);
		int ia1 = Math.round(Math.max(0f, Math.min(1f, a1)) * 255);
		for (int i = 0; i < segments; i++) {
			double t0 = Math.PI * 2.0 * i / segments;
			double t1 = Math.PI * 2.0 * (i + 1) / segments;
			float c0 = (float) Math.cos(t0);
			float s0 = (float) Math.sin(t0);
			float c1 = (float) Math.cos(t1);
			float s1 = (float) Math.sin(t1);
			vertex(vc, pose, c.add(c0 * r0, 0, s0 * r0), r, g, b, ia0);
			vertex(vc, pose, c.add(c1 * r0, 0, s1 * r0), r, g, b, ia0);
			vertex(vc, pose, c.add(c1 * r1, 0, s1 * r1), r, g, b, ia1);
			vertex(vc, pose, c.add(c0 * r1, 0, s0 * r1), r, g, b, ia1);
		}
	}

	/** A vertical band of light standing on a circle of radius {@code radius}, bright at the foot, fading upward. */
	public static void wall(VertexConsumer vc, PoseStack.Pose pose, Vec3 center, float radius, float height, int rgb,
			float alpha, int segments) {
		if (alpha <= 0.003f) {
			return;
		}
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255);
		for (int i = 0; i < segments; i++) {
			double t0 = Math.PI * 2.0 * i / segments;
			double t1 = Math.PI * 2.0 * (i + 1) / segments;
			Vec3 p0 = center.add(Math.cos(t0) * radius, 0, Math.sin(t0) * radius);
			Vec3 p1 = center.add(Math.cos(t1) * radius, 0, Math.sin(t1) * radius);
			vertex(vc, pose, p0, r, g, b, a);
			vertex(vc, pose, p1, r, g, b, a);
			vertex(vc, pose, p1.add(0, height, 0), r, g, b, 0);
			vertex(vc, pose, p0.add(0, height, 0), r, g, b, 0);
		}
	}

	/**
	 * A crackling ball of lightning at {@code center}: a small white-hot core in blue shells (see {@link TurboDraw#orb})
	 * with short jagged sparks spitting out of it in directions that jump every couple of ticks ({@code spin} doubles as
	 * the clock), so it reads as live electricity rather than a glowing cube.
	 */
	public static void flare(VertexConsumer vc, PoseStack poseStack, Vec3 center, float radius, float spin, float alpha) {
		if (alpha <= 0.003f || radius <= 0.001f) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(center.x, center.y, center.z);
		TurboDraw.orb(vc, poseStack, radius * 0.6f, GLOW, spin, alpha);
		poseStack.popPose();
		double frame = Math.floor(spin / 25.0);
		PoseStack.Pose pose = poseStack.last();
		for (int i = 0; i < 6; i++) {
			double s = frame * 3.7 + i * 11.3;
			Vec3 dir = new Vec3(hash(s) - 0.5, hash(s + 1.9) - 0.5, hash(s + 4.1) - 0.5);
			if (dir.lengthSqr() < 1.0E-4) {
				continue;
			}
			Vec3 tip = center.add(dir.normalize().scale(radius * (1.4 + 1.4 * hash(s + 7.7))));
			Vec3[] spark = jagged(center, tip, 3, radius * 0.35, s);
			ribbon(vc, pose, spark, 0.05f + radius * 0.08f, GLOW, 0.55f * alpha);
			ribbon(vc, pose, spark, 0.018f + radius * 0.03f, CORE, 0.9f * alpha);
		}
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, int r, int g, int b, int a) {
		vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
				.setColor(r, g, b, a)
				.setUv(0.5f, 0.5f)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightTexture.FULL_BRIGHT)
				.setNormal(pose, 0f, 1f, 0f);
	}
}
