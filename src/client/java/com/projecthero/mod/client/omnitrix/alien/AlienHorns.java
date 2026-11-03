package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.omnitrix.AlienParts;

/**
 * v0.17.2 (user: "Give him a pink spike on his head, 2 on his chest ... and also 2 coming out of his back, make it the same colour
 * of the pink on his skin model" / "add the head spikes for alien x ... make it the same black as his skin"): crystal spikes and
 * curved horns built from tapering boxes, coloured by pointing every face at one pixel of the alien's own skin (so they match it
 * exactly). All sizes in pixels, in the bone's space (y down, -z forward); a spike / horn grows along -y from the pose origin.
 */
final class AlienHorns {
	private AlienHorns() {
	}

	/** The UV of skin pixel (px, py) on a {@code size}-px sheet: {u0, v0, u1, v1} squeezed onto that one pixel. */
	static float[] pixel(int px, int py, int size) {
		float u = (px + 0.5f) / size, v = (py + 0.5f) / size;
		return new float[] { u, v, u, v };
	}

	/**
	 * A straight crystal spike {@code len} long and {@code base} thick, tilted {@code pitch} degrees forward (toward -z) and
	 * {@code roll} degrees out to +x, in three tapering steps -- {@code shades} from base to tip.
	 */
	static void spike(PoseStack pose, VertexConsumer vc, int light, float len, float base, float pitch, float roll, float[]... shades) {
		pose.pushPose();
		pose.mulPose(Axis.ZP.rotationDegrees(-roll));
		pose.mulPose(Axis.XP.rotationDegrees(pitch));
		float step = len / 3f;
		for (int i = 0; i < 3; i++) {
			float w = base * (1f - i * 0.3f) / 2f;
			float[] uv = shades[Math.min(i, shades.length - 1)];
			AlienParts.box(pose, vc, -w, -step * (i + 1), -w, w, -step * i + 0.01f, w, uv[0], uv[1], uv[2], uv[3], 0xFFFFFFFF, light);
		}
		pose.popPose();
	}

	/**
	 * v0.17.2 (user: "chromastones front crystals should be like 2 pixels long and not curve upwards and inwards ... make them thin
	 * crystals but just come out straight similar to diamondheads crystals"): a straight crystal pointing exactly along (dx, dy, dz)
	 * in the bone's space (y down, -z forward) -- no angle guesswork -- {@code len} long, {@code base} thick, tapering in three steps.
	 */
	static void spikeAlong(PoseStack pose, VertexConsumer vc, int light, float len, float base, float dx, float dy, float dz, float[]... shades) {
		pose.pushPose();
		org.joml.Vector3f to = new org.joml.Vector3f(dx, dy, dz).normalize();
		pose.mulPose(new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(0f, -1f, 0f), to));
		float step = len / 3f;
		for (int i = 0; i < 3; i++) {
			float w = base * (1f - i * 0.3f) / 2f;
			float[] uv = shades[Math.min(i, shades.length - 1)];
			AlienParts.box(pose, vc, -w, -step * (i + 1), -w, w, -step * i + 0.01f, w, uv[0], uv[1], uv[2], uv[3], 0xFFFFFFFF, light);
		}
		pose.popPose();
	}

	/**
	 * v0.17.2 (user: "make the spikes look smooth and not so blocky"): a smooth horn -- a round (10-sided) tube following the
	 * quadratic curve p0 -> p1 -> p2 (pixels, the bone's space), tapering from radius {@code r0} to a point, in {@code steps}
	 * pieces; every face on one skin pixel ({@code uv}) so it is that colour.
	 */
	static void tube(PoseStack pose, VertexConsumer vc, int light, float[] p0, float[] p1, float[] p2, float r0, int steps, float[] uv) {
		PoseStack.Pose last = pose.last();
		int sides = 10;
		float s = 1f / 16f;
		org.joml.Vector3f[] prevRing = null;
		for (int i = 0; i <= steps; i++) {
			float t = i / (float) steps, a = (1 - t) * (1 - t), b = 2 * (1 - t) * t, c = t * t;
			org.joml.Vector3f at = new org.joml.Vector3f(a * p0[0] + b * p1[0] + c * p2[0], a * p0[1] + b * p1[1] + c * p2[1], a * p0[2] + b * p1[2] + c * p2[2]);
			// the curve's direction here, and two axes across it
			org.joml.Vector3f dir = new org.joml.Vector3f(2 * (1 - t) * (p1[0] - p0[0]) + 2 * t * (p2[0] - p1[0]), 2 * (1 - t) * (p1[1] - p0[1]) + 2 * t * (p2[1] - p1[1]),
					2 * (1 - t) * (p1[2] - p0[2]) + 2 * t * (p2[2] - p1[2])).normalize();
			org.joml.Vector3f side = new org.joml.Vector3f(dir).cross(Math.abs(dir.x) < 0.9f ? new org.joml.Vector3f(1, 0, 0) : new org.joml.Vector3f(0, 0, 1)).normalize();
			org.joml.Vector3f up = new org.joml.Vector3f(dir).cross(side).normalize();
			float r = r0 * (1f - t * 0.92f);
			org.joml.Vector3f[] ring = new org.joml.Vector3f[sides];
			for (int k = 0; k < sides; k++) {
				double ang = k * Math.PI * 2 / sides;
				ring[k] = new org.joml.Vector3f(at).add(new org.joml.Vector3f(side).mul((float) Math.cos(ang) * r)).add(new org.joml.Vector3f(up).mul((float) Math.sin(ang) * r));
			}
			if (prevRing != null) {
				for (int k = 0; k < sides; k++) {
					org.joml.Vector3f q0 = prevRing[k], q1 = prevRing[(k + 1) % sides], q2 = ring[(k + 1) % sides], q3 = ring[k];
					org.joml.Vector3f n = new org.joml.Vector3f(q1).sub(q0).cross(new org.joml.Vector3f(q3).sub(q0)).normalize();
					AlienParts.quad(vc, last, 0xFFFFFFFF, light, q0.x * s, q0.y * s, q0.z * s, q1.x * s, q1.y * s, q1.z * s, q2.x * s, q2.y * s, q2.z * s,
							q3.x * s, q3.y * s, q3.z * s, n.x, n.y, n.z, uv[0], uv[1], uv[2], uv[3]);
				}
			}
			prevRing = ring;
		}
	}

	/**
	 * A curved horn of {@code segs} segments, each {@code segLen} long, tapering from {@code base} thick, starting tilted
	 * {@code pitch} degrees (toward -z = forward) and {@code roll} degrees out, bending {@code bend} degrees more forward each segment.
	 */
	static void horn(PoseStack pose, VertexConsumer vc, int light, int segs, float segLen, float base, float pitch, float roll, float bend, float[] uv) {
		pose.pushPose();
		pose.mulPose(Axis.ZP.rotationDegrees(-roll));
		pose.mulPose(Axis.XP.rotationDegrees(pitch));
		for (int i = 0; i < segs; i++) {
			float w = Math.max(0.25f, base * (1f - i / (float) (segs + 1))) / 2f;
			AlienParts.box(pose, vc, -w, -segLen - 0.05f, -w, w, 0.05f, w, uv[0], uv[1], uv[2], uv[3], 0xFFFFFFFF, light);
			pose.translate(0f, -segLen / 16f, 0f);
			pose.mulPose(Axis.XP.rotationDegrees(bend));
		}
		pose.popPose();
	}
}
