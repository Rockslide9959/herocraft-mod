package com.projecthero.mod.client.firearm;

import org.joml.Matrix4f;

import com.projecthero.mod.client.punisher.GunAnim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

/**
 * v0.15.16: the muzzle flash -- drawn in the gun's own model space at its muzzle (barrel along -Z), additive: a hot white
 * core, a starburst of flame petals round the bore, and a forward cone of fire, each shot a new random twist and
 * length, gone in about two ticks. Bigger and longer for the shotgun and the sniper. Used by the first-person rig and
 * the third-person held gun.
 */
public final class MuzzleFlash {
	/** Muzzle positions in each gun's item model (px). */
	private static final float[][] MUZZLE = {
			{ 8f, 9.9f, 3.0f },   // pistol
			{ 8f, 8.44f, -4.2f }, // rifle (the user's mesh, muzzle brake)
			{ 8f, 8.85f, -1.0f }, // shotgun
			{ 8f, 7.55f, -5.7f }, // sniper
	};

	private MuzzleFlash() {
	}

	/**
	 * Draws the flash if one is live. {@code pose} must be the gun's model frame after the renderer's {@code -0.5}
	 * centring (so 1 unit = 16 model px).
	 */
	public static void draw(PoseStack pose, MultiBufferSource buffers, GunAnim.Kind kind, float flash, int seed) {
		if (flash <= 0.001f || kind == null) {
			return;
		}
		float[] m = MUZZLE[kind.ordinal()];
		float size = switch (kind) {
			case SHOTGUN -> 1.5f;
			case SNIPER -> 1.6f;
			case RIFLE -> 1.1f;
			default -> 0.85f;
		};
		float len = size * (0.55f + 0.25f * hash(seed, 1)) * (0.6f + 0.4f * flash);
		float a = flash;
		VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
		pose.pushPose();
		pose.translate(m[0] / 16f, m[1] / 16f, m[2] / 16f);
		pose.mulPose(Axis.ZP.rotationDegrees(hash(seed, 2) * 360f));
		Matrix4f mat = pose.last().pose();
		// forward cone: 4 petals in planes through the bore
		for (int i = 0; i < 4; i++) {
			float ang = i * Mth.PI / 4f;
			float cx = Mth.cos(ang), cy = Mth.sin(ang);
			float w = size * 0.11f;
			quad(vc, mat, -cx * w, -cy * w, 0f, cx * w, cy * w, 0f, cx * w * 0.2f, cy * w * 0.2f, -len, -cx * w * 0.2f, -cy * w * 0.2f, -len,
					0xFFB040, a * 0.55f, a * 0.0f);
		}
		// starburst petals round the bore, facing forward
		int petals = 5;
		for (int i = 0; i < petals; i++) {
			float ang = i * Mth.TWO_PI / petals;
			float r = size * (0.18f + 0.12f * hash(seed, 10 + i));
			float cx = Mth.cos(ang), cy = Mth.sin(ang);
			float px = -cy * size * 0.04f, py = cx * size * 0.04f;
			quad(vc, mat, px, py, -0.01f, -px, -py, -0.01f, cx * r - px * 0.2f, cy * r - py * 0.2f, -0.03f, cx * r + px * 0.2f, cy * r + py * 0.2f,
					-0.03f, 0xFFD070, a * 0.8f, 0f);
		}
		// hot core
		float c = size * 0.07f;
		quad(vc, mat, -c, -c, -0.02f, c, -c, -0.02f, c, c, -0.02f, -c, c, -0.02f, 0xFFF6DC, a, a);
		pose.popPose();
	}

	/** A quad with alpha {@code a0} on its first two corners and {@code a1} on the last two, drawn both ways round. */
	private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2,
			float z2, float x3, float y3, float z3, int rgb, float a0, float a1) {
		int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
		int A0 = Mth.clamp((int) (a0 * 255), 0, 255), A1 = Mth.clamp((int) (a1 * 255), 0, 255);
		vc.addVertex(m, x0, y0, z0).setColor(r, g, b, A0);
		vc.addVertex(m, x1, y1, z1).setColor(r, g, b, A0);
		vc.addVertex(m, x2, y2, z2).setColor(r, g, b, A1);
		vc.addVertex(m, x3, y3, z3).setColor(r, g, b, A1);
		vc.addVertex(m, x3, y3, z3).setColor(r, g, b, A1);
		vc.addVertex(m, x2, y2, z2).setColor(r, g, b, A1);
		vc.addVertex(m, x1, y1, z1).setColor(r, g, b, A0);
		vc.addVertex(m, x0, y0, z0).setColor(r, g, b, A0);
	}

	private static float hash(int seed, int k) {
		int h = seed * 374761393 + k * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
	}
}
