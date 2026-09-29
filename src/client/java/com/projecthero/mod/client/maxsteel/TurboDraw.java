package com.projecthero.mod.client.maxsteel;

import com.projecthero.mod.ProjectHeroMod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.14.2: glowing T.U.R.B.O. energy geometry shared by the Turbo Blast bolt, the charge orb in the hand and the
 * Turbo Cannon beam: full-bright, see-through boxes drawn with {@link RenderType#entityTranslucentEmissive} over a
 * plain white texture, tinted per vertex. Layering a white core inside progressively larger, fainter tinted shells is
 * what makes a box read as a ball / bolt of light.
 */
public final class TurboDraw {
	public static final ResourceLocation WHITE = ProjectHeroMod.id("textures/entity/mutation/white.png");
	public static final int CYAN = 0x35E0F0;
	/** The Turbo Cannon's deeper T.U.R.B.O. blue. */
	public static final int BLUE = 0x3FA8FF;

	private TurboDraw() {
	}

	public static VertexConsumer buffer(MultiBufferSource buffers) {
		return buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
	}

	/** An axis-aligned box centred on the origin, half-extents {@code hx, hy, hz}, colour {@code rgb} at {@code alpha}. */
	public static void box(VertexConsumer vc, PoseStack.Pose pose, float hx, float hy, float hz, int rgb, float alpha) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
		// +Y / -Y
		quad(vc, pose, r, g, b, a, 0, 1, 0, -hx, hy, -hz, -hx, hy, hz, hx, hy, hz, hx, hy, -hz);
		quad(vc, pose, r, g, b, a, 0, -1, 0, -hx, -hy, -hz, hx, -hy, -hz, hx, -hy, hz, -hx, -hy, hz);
		// +Z / -Z
		quad(vc, pose, r, g, b, a, 0, 0, 1, -hx, -hy, hz, hx, -hy, hz, hx, hy, hz, -hx, hy, hz);
		quad(vc, pose, r, g, b, a, 0, 0, -1, -hx, -hy, -hz, -hx, hy, -hz, hx, hy, -hz, hx, -hy, -hz);
		// +X / -X
		quad(vc, pose, r, g, b, a, 1, 0, 0, hx, -hy, -hz, hx, hy, -hz, hx, hy, hz, hx, -hy, hz);
		quad(vc, pose, r, g, b, a, -1, 0, 0, -hx, -hy, -hz, -hx, -hy, hz, -hx, hy, hz, -hx, hy, -hz);
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose pose, int r, int g, int b, int a, float nx, float ny, float nz,
			float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3) {
		vertex(vc, pose, x0, y0, z0, 0, 0, r, g, b, a, nx, ny, nz);
		vertex(vc, pose, x1, y1, z1, 0, 1, r, g, b, a, nx, ny, nz);
		vertex(vc, pose, x2, y2, z2, 1, 1, r, g, b, a, nx, ny, nz);
		vertex(vc, pose, x3, y3, z3, 1, 0, r, g, b, a, nx, ny, nz);
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v,
			int r, int g, int b, int a, float nx, float ny, float nz) {
		vc.addVertex(pose, x, y, z)
				.setColor(r, g, b, a)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(LightTexture.FULL_BRIGHT)
				.setNormal(pose, nx, ny, nz);
	}

	/**
	 * A ball of energy of radius {@code radius} at the current origin: a white core, two tinted shells turned against
	 * each other (so the silhouette reads round, not cubic) and a faint outer halo. {@code spin} in degrees.
	 */
	public static void orb(VertexConsumer vc, PoseStack pose, float radius, int rgb, float spin, float alpha) {
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(spin));
		pose.mulPose(Axis.XP.rotationDegrees(spin * 0.7f));
		box(vc, pose.last(), radius * 0.45f, radius * 0.45f, radius * 0.45f, 0xFFFFFF, alpha);
		box(vc, pose.last(), radius * 0.72f, radius * 0.72f, radius * 0.72f, rgb, 0.55f * alpha);
		pose.mulPose(Axis.ZP.rotationDegrees(45.0f));
		pose.mulPose(Axis.XP.rotationDegrees(35.0f));
		box(vc, pose.last(), radius * 0.72f, radius * 0.72f, radius * 0.72f, rgb, 0.45f * alpha);
		box(vc, pose.last(), radius, radius, radius, rgb, 0.18f * alpha);
		pose.popPose();
	}

	/** Mixes {@code rgb} toward white by {@code t} (0..1). */
	public static int toWhite(int rgb, float t) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		r = Math.round(r + (255 - r) * t);
		g = Math.round(g + (255 - g) * t);
		b = Math.round(b + (255 - b) * t);
		return (r << 16) | (g << 8) | b;
	}
}
