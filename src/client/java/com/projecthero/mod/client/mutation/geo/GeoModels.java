package com.projecthero.mod.client.mutation.geo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.16.5 Geokinesis: the building blocks of every earth model the power draws -- scaled, turned copies of the real block
 * model of the ground they came from (its own textures, tint and shading), so a rock torn out of a grass field is a clod
 * of grass and dirt and a pillar in a desert is sandstone.
 */
public final class GeoModels {
	private GeoModels() {
	}

	/** A box of {@code state} with its minimum corner at (x, y, z) and size (sx, sy, sz), in the current pose's units. */
	public static void box(PoseStack pose, MultiBufferSource buffers, int light, BlockState state, float x, float y, float z, float sx,
			float sy, float sz) {
		if (sx <= 0.001f || sy <= 0.001f || sz <= 0.001f) {
			return;
		}
		pose.pushPose();
		pose.translate(x, y, z);
		pose.scale(sx, sy, sz);
		Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}

	/** A box of {@code state} centred on the origin. */
	public static void centred(PoseStack pose, MultiBufferSource buffers, int light, BlockState state, float sx, float sy, float sz) {
		box(pose, buffers, light, state, -sx / 2, -sy / 2, -sz / 2, sx, sy, sz);
	}

	/** A cheap stable hash -> 0..1 for the {@code i}-th random number of {@code seed}. */
	public static float rnd(int seed, int i) {
		int h = seed * 0x9E3779B1 + i * 0x85EBCA77;
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		h ^= h >>> 12;
		h *= 0x297A2D39;
		h ^= h >>> 15;
		return (h & 0xFFFFFF) / (float) 0x1000000;
	}

	/**
	 * A rough, jagged rock about {@code size} across, centred on the origin: a squat core block with three smaller lumps
	 * knocked into it at odd angles (stable per {@code seed}).
	 */
	public static void rock(PoseStack pose, MultiBufferSource buffers, int light, BlockState state, int seed, float size) {
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(rnd(seed, 0) * 360));
		pose.mulPose(Axis.XP.rotationDegrees((rnd(seed, 1) - 0.5f) * 30));
		centred(pose, buffers, light, state, size * 0.92f, size * 0.78f, size * 0.85f);
		for (int i = 0; i < 3; i++) {
			pose.pushPose();
			float a = (i / 3.0f + rnd(seed, 10 + i) * 0.2f) * (float) Math.PI * 2;
			float r = size * 0.36f;
			pose.translate(Math.cos(a) * r, (rnd(seed, 20 + i) - 0.4f) * size * 0.45f, Math.sin(a) * r);
			pose.mulPose(Axis.YP.rotationDegrees(rnd(seed, 30 + i) * 90));
			pose.mulPose(Axis.ZP.rotationDegrees((rnd(seed, 40 + i) - 0.5f) * 70));
			float s = size * (0.42f + rnd(seed, 50 + i) * 0.2f);
			centred(pose, buffers, light, state, s, s * 0.85f, s);
			pose.popPose();
		}
		pose.popPose();
	}
}
