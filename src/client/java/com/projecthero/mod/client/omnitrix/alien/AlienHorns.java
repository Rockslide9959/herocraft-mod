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
