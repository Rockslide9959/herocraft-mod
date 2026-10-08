package com.projecthero.mod.client.greenlantern;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * v0.15.15: free-form quads of hard light -- what the box-built {@link HardLightDraw} parts can't do: ribbons that
 * curve (the Blast Wave's arc, the sprint-flight trail) with a colour and alpha per corner, so they can fade along their
 * length. Every quad is emitted with both windings, so it shows from either side whatever the render type's culling.
 * {@link #additive} is vanilla's lightning buffer (adds light instead of tinting, used for glows).
 */
public final class HardLightRibbon {
	private HardLightRibbon() {
	}

	/** Translucent full-bright (the {@link HardLightDraw} buffer). */
	public static VertexConsumer translucent(MultiBufferSource buffers) {
		return HardLightDraw.buffer(buffers);
	}

	/** Additive glow (vanilla lightning: position + colour only, blended additively). */
	public static VertexConsumer additive(MultiBufferSource buffers) {
		return buffers.getBuffer(RenderType.lightning());
	}

	/**
	 * One quad, corners {@code (x0..z3)} in order round the edge, colour {@code rgb}, an alpha per corner. Drawn
	 * double-sided.
	 */
	public static void quad(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1,
			float x2, float y2, float z2, float x3, float y3, float z3, int rgb, float a0, float a1, float a2, float a3) {
		quad(vc, pose, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, rgb, rgb, a0, a1, a2, a3);
	}

	/** As {@link #quad(VertexConsumer, PoseStack.Pose, float, float, float, float, float, float, float, float, float, float, float, float, int, float, float, float, float)}
	 *  with corners 0/1 in {@code rgbA} and corners 2/3 in {@code rgbB} (a colour ramp across the quad). */
	public static void quad(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1,
			float x2, float y2, float z2, float x3, float y3, float z3, int rgbA, int rgbB, float a0, float a1, float a2, float a3) {
		if (a0 <= 0.004f && a1 <= 0.004f && a2 <= 0.004f && a3 <= 0.004f) {
			return;
		}
		// face normal from the two diagonals
		float ax = x2 - x0, ay = y2 - y0, az = z2 - z0;
		float bx = x3 - x1, by = y3 - y1, bz = z3 - z1;
		float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len < 1.0e-6f) {
			nx = 0f;
			ny = 1f;
			nz = 0f;
		} else {
			nx /= len;
			ny /= len;
			nz /= len;
		}
		v(vc, pose, x0, y0, z0, 0, 0, rgbA, a0, nx, ny, nz);
		v(vc, pose, x1, y1, z1, 0, 1, rgbA, a1, nx, ny, nz);
		v(vc, pose, x2, y2, z2, 1, 1, rgbB, a2, nx, ny, nz);
		v(vc, pose, x3, y3, z3, 1, 0, rgbB, a3, nx, ny, nz);
		v(vc, pose, x3, y3, z3, 1, 0, rgbB, a3, -nx, -ny, -nz);
		v(vc, pose, x2, y2, z2, 1, 1, rgbB, a2, -nx, -ny, -nz);
		v(vc, pose, x1, y1, z1, 0, 1, rgbA, a1, -nx, -ny, -nz);
		v(vc, pose, x0, y0, z0, 0, 0, rgbA, a0, -nx, -ny, -nz);
	}

	private static void v(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float vv, int rgb, float alpha,
			float nx, float ny, float nz) {
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
		vc.addVertex(pose, x, y, z)
				.setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, a)
				.setUv(u, vv)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightTexture.FULL_BRIGHT)
				.setNormal(pose, nx, ny, nz);
	}
}
