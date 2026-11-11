package com.projecthero.mod.client.saiyan.kit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * v0.17.3 Saiyan revamp: the move kit's shapes -- spheres, flat rings, tubes, spirals and boxes -- drawn additively
 * ({@code RenderType.lightning()}, {@link #G}) with a solid white core ({@code RenderType.debugQuads()}, {@link #S}). The
 * buffer is fetched fresh for every shape: fetching another type ends the batch before.
 */
final class SaiyanKitDraw {
	static MultiBufferSource buffers;

	private SaiyanKitDraw() {
	}

	static VertexConsumer G() {
		return buffers.getBuffer(RenderType.lightning());
	}

	static VertexConsumer S() {
		return buffers.getBuffer(RenderType.debugQuads());
	}

	static float r(int rgb) {
		return ((rgb >> 16) & 255) / 255f;
	}

	static float g(int rgb) {
		return ((rgb >> 8) & 255) / 255f;
	}

	static float b(int rgb) {
		return (rgb & 255) / 255f;
	}

	static void sphere(PoseStack pose, Vec3 cam, VertexConsumer vc, Vec3 at, float rad, float r, float g, float b, float a, int seg) {
		if (rad <= 0.001f || a <= 0.001f) {
			return;
		}
		pose.pushPose();
		pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		Matrix4f m = pose.last().pose();
		int rings = Math.max(4, seg / 2);
		for (int i = 0; i < rings; i++) {
			float t0 = Mth.PI * i / rings - Mth.HALF_PI, t1 = Mth.PI * (i + 1) / rings - Mth.HALF_PI;
			for (int j = 0; j < seg; j++) {
				float p0 = Mth.TWO_PI * j / seg, p1 = Mth.TWO_PI * (j + 1) / seg;
				quad(vc, m, sp(t0, p0, rad), sp(t0, p1, rad), sp(t1, p1, rad), sp(t1, p0, rad), r, g, b, a);
			}
		}
		pose.popPose();
	}

	/** A glowing ball: white core, coloured body, soft halo. */
	static void orb(PoseStack pose, Vec3 cam, Vec3 at, float rad, int rgb, float alpha) {
		sphere(pose, cam, S(), at, rad * 0.45f, 1f, 1f, 1f, 1f, 8);
		sphere(pose, cam, G(), at, rad, r(rgb), g(rgb), b(rgb), 0.65f * alpha, 10);
		sphere(pose, cam, G(), at, rad * 1.8f, r(rgb), g(rgb), b(rgb), 0.18f * alpha, 10);
	}

	private static Vector3f sp(float lat, float lon, float rad) {
		return new Vector3f(Mth.cos(lat) * Mth.cos(lon) * rad, Mth.sin(lat) * rad, Mth.cos(lat) * Mth.sin(lon) * rad);
	}

	static void ring(PoseStack pose, Vec3 cam, VertexConsumer vc, Vec3 at, float rad, float width, float r, float g, float b, float a) {
		ringFacing(pose, cam, vc, at, new Vec3(0, 1, 0), rad, width, r, g, b, a);
	}

	/** A flat ring round {@code at}, its plane facing {@code normal}. */
	static void ringFacing(PoseStack pose, Vec3 cam, VertexConsumer vc, Vec3 at, Vec3 normal, float rad, float width, float r, float g, float b, float a) {
		if (rad <= 0.01f || a <= 0.001f) {
			return;
		}
		pose.pushPose();
		pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		Matrix4f m = pose.last().pose();
		Vector3f n = new Vector3f((float) normal.x, (float) normal.y, (float) normal.z).normalize();
		Vector3f u = new Vector3f(n).cross(Math.abs(n.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
		Vector3f v = new Vector3f(n).cross(u).normalize();
		int seg = 48;
		float in = Math.max(0f, rad - width);
		for (int i = 0; i < seg; i++) {
			float t0 = Mth.TWO_PI * i / seg, t1 = Mth.TWO_PI * (i + 1) / seg;
			Vector3f o0 = new Vector3f(u).mul(Mth.cos(t0)).add(new Vector3f(v).mul(Mth.sin(t0)));
			Vector3f o1 = new Vector3f(u).mul(Mth.cos(t1)).add(new Vector3f(v).mul(Mth.sin(t1)));
			quad(vc, m, new Vector3f(o0).mul(in), new Vector3f(o1).mul(in), new Vector3f(o1).mul(rad), new Vector3f(o0).mul(rad), r, g, b, a);
		}
		pose.popPose();
	}

	/** A tube from {@code a0} to {@code a1}. */
	static void line(PoseStack pose, Vec3 cam, VertexConsumer vc, Vec3 a0, Vec3 a1, float rad, int sides, float r, float g, float b, float a) {
		Vec3 d = a1.subtract(a0);
		float len = (float) d.length();
		if (len < 0.01f || a <= 0.001f) {
			return;
		}
		pose.pushPose();
		pose.translate(a0.x - cam.x, a0.y - cam.y, a0.z - cam.z);
		Vector3f dir = new Vector3f((float) d.x, (float) d.y, (float) d.z).normalize();
		Vector3f aa = perp(dir);
		Vector3f bb = new Vector3f(dir).cross(aa).normalize();
		tube(vc, pose.last().pose(), dir, aa, bb, len, rad, sides, r, g, b, a);
		pose.popPose();
	}

	static Vector3f perp(Vector3f dir) {
		return new Vector3f(dir).cross(Math.abs(dir.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
	}

	static void tube(VertexConsumer vc, Matrix4f m, Vector3f dir, Vector3f a, Vector3f b, float len, float r, int sides, float cr, float cg, float cb,
			float ca) {
		for (int i = 0; i < sides; i++) {
			float t0 = i * Mth.TWO_PI / sides, t1 = (i + 1) * Mth.TWO_PI / sides;
			Vector3f p0 = new Vector3f(a).mul(Mth.cos(t0) * r).add(new Vector3f(b).mul(Mth.sin(t0) * r));
			Vector3f p1 = new Vector3f(a).mul(Mth.cos(t1) * r).add(new Vector3f(b).mul(Mth.sin(t1) * r));
			Vector3f e = new Vector3f(dir).mul(len);
			quad(vc, m, p0, p1, new Vector3f(p1).add(e), new Vector3f(p0).add(e), cr, cg, cb, ca);
		}
	}

	static void helix(VertexConsumer vc, Matrix4f m, Vector3f dir, Vector3f a, Vector3f b, float len, float r, float phase, float w, float cr, float cg,
			float cb, float ca) {
		int steps = Math.max(4, (int) (len * 5));
		for (int i = 0; i < steps; i++) {
			float s0 = i / (float) steps * len, s1 = (i + 1) / (float) steps * len;
			float t0 = phase + s0 * 1.3f, t1 = phase + s1 * 1.3f;
			Vector3f p0 = new Vector3f(a).mul(Mth.cos(t0) * r).add(new Vector3f(b).mul(Mth.sin(t0) * r)).add(new Vector3f(dir).mul(s0));
			Vector3f p1 = new Vector3f(a).mul(Mth.cos(t1) * r).add(new Vector3f(b).mul(Mth.sin(t1) * r)).add(new Vector3f(dir).mul(s1));
			Vector3f o0 = new Vector3f(a).mul(Mth.cos(t0)).add(new Vector3f(b).mul(Mth.sin(t0))).mul(w);
			Vector3f o1 = new Vector3f(a).mul(Mth.cos(t1)).add(new Vector3f(b).mul(Mth.sin(t1))).mul(w);
			quad(vc, m, new Vector3f(p0).sub(o0), new Vector3f(p1).sub(o1), new Vector3f(p1).add(o1), new Vector3f(p0).add(o0), cr, cg, cb, ca);
		}
	}

	static void box(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float r, float g, float b, float a) {
		Vector3f[] c = new Vector3f[8];
		for (int i = 0; i < 8; i++) {
			c[i] = new Vector3f((i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1);
		}
		int[][] faces = { { 0, 4, 6, 2 }, { 1, 3, 7, 5 }, { 0, 1, 5, 4 }, { 2, 6, 7, 3 }, { 0, 2, 3, 1 }, { 4, 5, 7, 6 } };
		for (int[] f : faces) {
			quad(vc, m, c[f[0]], c[f[1]], c[f[2]], c[f[3]], r, g, b, a);
		}
	}

	/** Both windings, so it shows from either side. */
	static void quad(VertexConsumer vc, Matrix4f m, Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float r, float g, float b, float a) {
		vc.addVertex(m, p0.x, p0.y, p0.z).setColor(r, g, b, a);
		vc.addVertex(m, p1.x, p1.y, p1.z).setColor(r, g, b, a);
		vc.addVertex(m, p2.x, p2.y, p2.z).setColor(r, g, b, a);
		vc.addVertex(m, p3.x, p3.y, p3.z).setColor(r, g, b, a);
		vc.addVertex(m, p3.x, p3.y, p3.z).setColor(r, g, b, a);
		vc.addVertex(m, p2.x, p2.y, p2.z).setColor(r, g, b, a);
		vc.addVertex(m, p1.x, p1.y, p1.z).setColor(r, g, b, a);
		vc.addVertex(m, p0.x, p0.y, p0.z).setColor(r, g, b, a);
	}
}
