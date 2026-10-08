package com.projecthero.mod.client.shield;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.shield.ForceBubble;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

/**
 * v0.15.15: the client half of {@link ForceBubble} -- draws one bubble shield. Shared on purpose: Nova's Force Field
 * draws it in gold ({@link ForceBubble.Style#NOVA}); Green Lantern's shield is meant to switch to it in green
 * ({@link ForceBubble.Style#GREEN_LANTERN}). Call {@link #draw} from a world-render callback (e.g.
 * {@code WorldRenderEvents.AFTER_ENTITIES}) once per raised bubble.
 *
 * <p>The look: a faint shell whose edge glows brighter than its face (a fresnel rim, so it reads as a sphere and never
 * fogs the player inside), a slowly turning lattice of latitude and longitude lines, and three bright tilted rings
 * spinning round it with one accent-coloured. The shell goes in {@code debugQuads} (ordinary alpha), the lines in
 * {@code lightning} (additive) -- each buffer is filled completely before the next is asked for, as {@link BeamDraw}
 * explains.
 */
public final class ForceBubbleRenderer {
	private static final int LAT = 14;
	private static final int LON = 22;

	private ForceBubbleRenderer() {
	}

	/**
	 * Draws a bubble.
	 *
	 * @param poseStack   the world-render pose stack (camera-relative, as handed to world-render callbacks)
	 * @param cam         the camera position
	 * @param center      the bubble's middle in world space ({@link ForceBubble#center})
	 * @param time        game time + partial tick, for the spin and the shimmer
	 * @param alpha       0..1 overall strength (fade it in / out, or flicker it when it is about to fail)
	 * @param firstPerson the viewer is inside this bubble: everything is drawn fainter so it never blinds him
	 */
	public static void draw(PoseStack poseStack, MultiBufferSource consumers, Vec3 cam, Vec3 center, ForceBubble.Style style, float time,
			float alpha, boolean firstPerson) {
		if (alpha <= 0.01f) {
			return;
		}
		double r = style.radius();
		float k = firstPerson ? 0.6f : 1f;
		poseStack.pushPose();
		poseStack.translate(-cam.x, -cam.y, -cam.z);
		PoseStack.Pose pose = poseStack.last();

		// ---- the shell (ordinary alpha): faint face, brighter rim
		VertexConsumer soft = consumers.getBuffer(RenderType.debugQuads());
		float shimmer = 0.85f + 0.15f * Mth.sin(time * 0.3f);
		for (int i = 0; i < LAT; i++) {
			double t0 = Math.PI * i / LAT;
			double t1 = Math.PI * (i + 1) / LAT;
			for (int j = 0; j < LON; j++) {
				double p0 = 2 * Math.PI * j / LON;
				double p1 = 2 * Math.PI * (j + 1) / LON;
				Vec3[] v = { pt(center, r, t0, p0), pt(center, r, t1, p0), pt(center, r, t1, p1), pt(center, r, t0, p1) };
				float[] a = new float[4];
				for (int n = 0; n < 4; n++) {
					a[n] = shellAlpha(v[n], center, cam, firstPerson) * alpha * shimmer;
				}
				quad(soft, pose.pose(), v, a, style.rgb());
			}
		}

		// ---- the lattice and rings (additive)
		VertexConsumer add = consumers.getBuffer(RenderType.lightning());
		float lineA = (firstPerson ? 0.2f : 0.22f) * alpha;
		float spin = time * 0.012f;
		// latitude lines
		for (int i = 1; i < 6; i++) {
			double theta = Math.PI * i / 6;
			double rr = r * Math.sin(theta);
			double y = r * Math.cos(theta);
			circle(add, pose, cam, center.add(0, y, 0), rr, 0.0, spin, 32, 0.012f, style.hotRgb(), lineA);
		}
		// longitude lines, turning slowly
		for (int j = 0; j < 6; j++) {
			double phi = spin + Math.PI * j / 6;
			Vec3 prev = null;
			for (int s = 0; s <= 24; s++) {
				double theta = Math.PI * s / 24;
				Vec3 p = pt(center, r * 1.002, theta, phi);
				if (prev != null) {
					BeamDraw.segment(add, pose, prev, p, cam, 0.012f, 0.012f, style.hotRgb(), lineA, lineA);
				}
				prev = p;
			}
		}
		// three bright tilted rings, one in the accent colour
		for (int i = 0; i < 3; i++) {
			double tilt = time * 0.05 + i * Math.PI / 3;
			circle(add, pose, cam, center, r * 1.01, tilt, i * 1.1 + time * 0.02, 36, 0.026f, i == 2 ? style.accentRgb() : style.hotRgb(),
					(0.55f * k) * alpha);
		}
		poseStack.popPose();
	}

	/** Fresnel: faint where the shell faces the camera, bright at the silhouette edge. */
	private static float shellAlpha(Vec3 p, Vec3 c, Vec3 cam, boolean inside) {
		if (inside) {
			return 0.08f;
		}
		Vec3 n = p.subtract(c).normalize();
		Vec3 v = cam.subtract(p).normalize();
		float facing = (float) Math.abs(n.dot(v));
		float rim = 1f - facing;
		return 0.06f + 0.30f * rim * rim;
	}

	private static Vec3 pt(Vec3 c, double r, double theta, double phi) {
		return c.add(r * Math.sin(theta) * Math.cos(phi), r * Math.cos(theta), r * Math.sin(theta) * Math.sin(phi));
	}

	/** A ring of radius {@code r} round {@code c}, tilted {@code tilt} about X then spun {@code spin} about Y. */
	private static void circle(VertexConsumer vc, PoseStack.Pose pose, Vec3 cam, Vec3 c, double r, double tilt, double spin, int seg,
			float width, int rgb, float alpha) {
		Vec3 prev = null;
		for (int i = 0; i <= seg; i++) {
			double a = 2 * Math.PI * i / seg;
			double x = Math.cos(a) * r;
			double z = Math.sin(a) * r;
			double y = z * Math.sin(tilt);
			z = z * Math.cos(tilt);
			double xs = x * Math.cos(spin) - z * Math.sin(spin);
			double zs = x * Math.sin(spin) + z * Math.cos(spin);
			Vec3 p = c.add(xs, y, zs);
			if (prev != null) {
				BeamDraw.segment(vc, pose, prev, p, cam, width, width, rgb, alpha, alpha);
			}
			prev = p;
		}
	}

	/** Both windings (no culling either way). */
	private static void quad(VertexConsumer vc, Matrix4f m, Vec3[] v, float[] a, int rgb) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		for (int i = 0; i < 4; i++) {
			vc.addVertex(m, (float) v[i].x, (float) v[i].y, (float) v[i].z).setColor(r, g, b, alphaByte(a[i]));
		}
		for (int i = 3; i >= 0; i--) {
			vc.addVertex(m, (float) v[i].x, (float) v[i].y, (float) v[i].z).setColor(r, g, b, alphaByte(a[i]));
		}
	}

	private static int alphaByte(float a) {
		return Math.max(0, Math.min(255, (int) (a * 255)));
	}
}
