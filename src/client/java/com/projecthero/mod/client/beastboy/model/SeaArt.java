package com.projecthero.mod.client.beastboy.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.beastboy.BeastForm;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy HQ round: the blocky sea animals (shark, dolphin, whale, octopus), drawn from {@link SeaShapes} ModelPart trees with
 * their own pixel-art textures and animated by {@link SharkModel}, {@link DolphinModel}, {@link WhaleModel} and {@link OctopusModel}
 * from the pose plus the per-player {@link SwimRig} state. {@code BeastFormArt.renderPose} hands these forms here.
 */
public final class SeaArt {
	private SeaArt() {
	}

	public static boolean handles(BeastForm f) {
		return f == BeastForm.SHARK || f == BeastForm.DOLPHIN || f == BeastForm.WHALE || f == BeastForm.OCTOPUS;
	}

	/** Draws {@code f} at the feet (pose stack +Z forward, blocks), like {@code BeastFormArt.renderPose}. */
	public static boolean render(BeastForm f, BeastPose s, PoseStack pose, MultiBufferSource buffers, int light, int tint) {
		if (!handles(f) || s == null) {
			return false;
		}
		SwimRig.State st = SwimRig.take(s);
		float[] m = s.morph();
		pose.pushPose();
		if (m[0] != 1f) {
			float cy = f.height * 0.35f;
			pose.translate(0f, cy * (1f - m[0]) * 0.3f, 0f);
			pose.scale(m[0], m[0], m[0]);
		}
		switch (f) {
			case SHARK -> SharkModel.render(pose, buffers, light, tint, m[1], s, st);
			case DOLPHIN -> DolphinModel.render(pose, buffers, light, tint, m[1], s, st);
			case WHALE -> WhaleModel.render(pose, buffers, light, tint, m[1], s, st);
			case OCTOPUS -> OctopusModel.render(pose, buffers, light, tint, m[1], s, st);
			default -> {
			}
		}
		pose.popPose();
		return true;
	}

	// -------------------------------------------------------------------------------------------------------------- helpers

	/** Back to the generated pose: positions, rotations, scale 1, visible. */
	static void reset(ModelPart root) {
		root.getAllParts().forEach(p -> {
			p.resetPose();
			p.xScale = p.yScale = p.zScale = 1f;
			p.visible = true;
		});
	}

	/** From the feet (+Y up, +Z forward, blocks) into vanilla model space (Y down, front -Z, y = 24 px at the ground). */
	static void toModel(PoseStack pose) {
		// ModelPart works in blocks itself (its offsets and cube vertices are divided by 16)
		pose.scale(1f, -1f, -1f);
		pose.translate(0f, -1.5f, 0f);
	}

	/** {colour ARGB, overlay} for a tint: green / white keeps the paint, red-dominant = hurt, alpha = see-through. */
	static int[] colour(int tint) {
		int tr = (tint >> 16) & 255, tg = (tint >> 8) & 255, tb = tint & 255, ta = (tint >>> 24) & 255;
		if (ta == 0) {
			ta = 255;
		}
		if (tr > tg + 24 && tr > tb) {
			int mg = Math.max(70, tg * 255 / Math.max(1, tr)), mb = Math.max(70, tb * 255 / Math.max(1, tr));
			return new int[] { ta << 24 | 255 << 16 | mg << 8 | mb, OverlayTexture.pack(0, OverlayTexture.v(true)) };
		}
		int m = Math.max(60, Math.max(tr, Math.max(tg, tb)));
		return new int[] { ta << 24 | m << 16 | m << 8 | m, OverlayTexture.NO_OVERLAY };
	}

	/** Draws a model tree: the body, then the additive green morph flash over it. */
	static void draw(ModelPart root, ResourceLocation tex, PoseStack pose, MultiBufferSource buffers, int light, int tint, float flash) {
		int[] c = colour(tint);
		boolean see = (c[0] >>> 24) < 250;
		root.render(pose, buffers.getBuffer(see ? RenderType.entityTranslucent(tex) : RenderType.entityCutoutNoCull(tex)), light, c[1], c[0]);
		if (flash > 0.003f) {
			float k = Mth.clamp(flash, 0f, 1f) * (c[0] >>> 24) / 255f;
			int fc = 0xFF000000 | (int) (150 * k) << 16 | (int) (255 * k) << 8 | (int) (120 * k);
			root.render(pose, buffers.getBuffer(RenderType.eyes(tex)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, fc);
		}
	}

	/**
	 * One see-through puff (ink, spray, splash): {@code puff} placed at model-space (x, y, z) pixels, scaled {@code s}, opacity
	 * {@code alpha}. The pose stack is in model space ({@link #toModel}).
	 */
	static void puff(ModelPart puff, ResourceLocation tex, PoseStack pose, MultiBufferSource buffers, int light, float x, float y, float z, float s,
			float spin, float alpha, int tint) {
		if (alpha <= 0.01f || s <= 0.01f) {
			return;
		}
		int[] c = colour(tint);
		int a = (int) (Mth.clamp(alpha, 0f, 1f) * (c[0] >>> 24));
		puff.visible = true;
		puff.x = x;
		puff.y = y;
		puff.z = z;
		puff.xScale = puff.yScale = puff.zScale = s;
		puff.xRot = spin * 0.7f;
		puff.yRot = spin;
		puff.zRot = spin * 0.4f;
		puff.render(pose, buffers.getBuffer(RenderType.entityTranslucent(tex)), light, OverlayTexture.NO_OVERLAY, a << 24 | (c[0] & 0xFFFFFF));
		puff.visible = false;
	}

	static float smooth(float t) {
		return BeastPose.smooth(t);
	}

	static float clamp01(float t) {
		return Mth.clamp(t, 0f, 1f);
	}

	/** Fraction of the way from a to b (clamped 0..1). */
	static float span(float p, float a, float b) {
		return Mth.clamp((p - a) / (b - a), 0f, 1f);
	}
}
