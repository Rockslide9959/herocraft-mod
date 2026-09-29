package com.projecthero.mod.client.greenlantern;

import com.projecthero.mod.client.maxsteel.TurboDraw;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;

/**
 * v0.14.3: the Green Lantern's hard-light look for models built in code -- every part is a translucent, full-bright
 * Lantern-green box with a pale, fainter shell a touch larger round it, which is what reads as solid light with a
 * glowing edge (the same trick {@link TurboDraw} uses for Max Steel's energy, in green). Units are blocks.
 */
public final class HardLightDraw {
	public static final int GREEN = 0x35F075;
	public static final int PALE = 0xA8FFC0;
	public static final int DEEP = 0x13A044;
	public static final int WHITE = 0xFFFFFF;

	private HardLightDraw() {
	}

	public static VertexConsumer buffer(MultiBufferSource buffers) {
		return TurboDraw.buffer(buffers);
	}

	/** A part centred at {@code (x, y, z)} with half-extents {@code (hx, hy, hz)}: solid green core + glowing rim shell. */
	public static void part(VertexConsumer vc, PoseStack pose, float x, float y, float z, float hx, float hy, float hz, float alpha) {
		part(vc, pose, x, y, z, hx, hy, hz, GREEN, alpha);
	}

	public static void part(VertexConsumer vc, PoseStack pose, float x, float y, float z, float hx, float hy, float hz, int rgb,
			float alpha) {
		pose.pushPose();
		pose.translate(x, y, z);
		TurboDraw.box(vc, pose.last(), hx, hy, hz, rgb, 0.62f * alpha);
		float rim = Math.min(0.045f, 0.18f * Math.min(hx, Math.min(hy, hz))) + 0.012f;
		TurboDraw.box(vc, pose.last(), hx + rim, hy + rim, hz + rim, PALE, 0.2f * alpha);
		pose.popPose();
	}

	/** A bright, nearly white accent (gems, emblems, nose cones, exhausts). */
	public static void glow(VertexConsumer vc, PoseStack pose, float x, float y, float z, float hx, float hy, float hz, float alpha) {
		pose.pushPose();
		pose.translate(x, y, z);
		TurboDraw.box(vc, pose.last(), hx, hy, hz, 0xE8FFEE, 0.9f * alpha);
		TurboDraw.box(vc, pose.last(), hx * 1.8f + 0.02f, hy * 1.8f + 0.02f, hz * 1.8f + 0.02f, GREEN, 0.25f * alpha);
		pose.popPose();
	}

	/** A streak of light from the origin along +Z for {@code len} blocks, half-width {@code w}. */
	public static void streak(VertexConsumer vc, PoseStack pose, float len, float w, float age, float alpha) {
		pose.pushPose();
		pose.translate(0f, 0f, len / 2f);
		pose.mulPose(Axis.ZP.rotationDegrees(age * 25f));
		float half = len / 2f;
		TurboDraw.box(vc, pose.last(), w * 0.35f, w * 0.35f, half, 0xF0FFF4, alpha);
		TurboDraw.box(vc, pose.last(), w * 0.7f, w * 0.7f, half, GREEN, 0.6f * alpha);
		pose.mulPose(Axis.ZP.rotationDegrees(45f));
		TurboDraw.box(vc, pose.last(), w, w, half, GREEN, 0.22f * alpha);
		pose.popPose();
	}

	/** The Green Lantern emblem (a ring between two bars) facing +Z, {@code s} blocks across, at the origin. */
	public static void emblem(VertexConsumer vc, PoseStack pose, float s, float alpha) {
		float t = s * 0.09f;
		float r = s * 0.28f;
		for (int i = 0; i < 8; i++) {
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotationDegrees(i * 45f));
			pose.translate(0f, r, 0f);
			TurboDraw.box(vc, pose.last(), r * 0.42f, t, t, 0xE8FFEE, 0.9f * alpha);
			pose.popPose();
		}
		pose.pushPose();
		pose.translate(0f, -r * 1.1f, 0f);
		TurboDraw.box(vc, pose.last(), s * 0.5f, t, t, 0xE8FFEE, 0.85f * alpha);
		pose.popPose();
		pose.pushPose();
		pose.translate(0f, r * 1.1f, 0f);
		TurboDraw.box(vc, pose.last(), s * 0.5f, t, t, 0xE8FFEE, 0.85f * alpha);
		pose.popPose();
	}
}
