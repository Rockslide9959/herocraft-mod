package com.projecthero.mod.client.omnitrix.sequence;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienParts;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.3: swelling flesh -- springy lumps erupting on a body's bones and throbbing, with an additive glow round them (moved out of
 * the v0.17.2 Four Arms code, where it is the peach flesh with red veins). A lump list is rows of
 * <pre>
 *   { bone index, centre x, y, z (px, in the bone's space), half-size (px), the tick it erupts }
 * </pre>
 * with the bone index into the {@code bones} array the caller passes (Four Arms: 0 left arm, 1 body, 2 head, 3 right arm).
 * Another alien can pass its own textures ({@code skin} drawn cutout, {@code glow} additive).
 */
public final class SwellLumps {
	public static final ResourceLocation SWELL = ProjectHeroMod.id("textures/entity/omnitrix/swell.png");
	public static final ResourceLocation SWELL_GLOW = ProjectHeroMod.id("textures/entity/omnitrix/swell_glow.png");

	private SwellLumps() {
	}

	/** Draws every lump that has erupted by {@code t} (each pops over {@code popLen} ticks), then their glow. */
	public static void render(PoseStack pose, MultiBufferSource buffers, ModelPart[] bones, float[][] lumps, float t, float popLen, ResourceLocation skin,
			ResourceLocation glowTex) {
		VertexConsumer flesh = buffers.getBuffer(RenderType.entityCutoutNoCull(skin));
		int light = LightTexture.FULL_BRIGHT;
		for (int i = 0; i < lumps.length; i++) {
			float[] b = lumps[i];
			float g = ShowMath.pop(t - b[5], popLen);
			if (g <= 0.02f) {
				continue;
			}
			float r = b[4] * g * (1f + 0.09f * Mth.sin(t * (0.8f + 0.05f * i) + i * 1.7f));
			float cx = b[1], cy = b[2], cz = b[3];
			AlienParts.on(pose, bones[(int) b[0]], () -> {
				lump(pose, flesh, cx, cy, cz, r, 0xFFFFFFFF, light);
			});
		}
		if (glowTex == null) {
			return;
		}
		VertexConsumer glow = buffers.getBuffer(RenderType.eyes(glowTex));
		for (int i = 0; i < lumps.length; i++) {
			float[] b = lumps[i];
			float g = ShowMath.pop(t - b[5], popLen);
			if (g <= 0.02f) {
				continue;
			}
			float r = b[4] * g * 1.2f * (1f + 0.09f * Mth.sin(t * (0.8f + 0.05f * i) + i * 1.7f));
			float k = 0.55f + 0.25f * Mth.sin(t * 1.3f + i);
			int c = ShowMath.grey(k);
			float cx = b[1], cy = b[2], cz = b[3];
			AlienParts.on(pose, bones[(int) b[0]], () -> lump(pose, glow, cx, cy, cz, r, c, LightTexture.FULL_BRIGHT));
		}
	}

	/** A rounded lump of radius {@code r} px: three crossed boxes (a little longer along the limb), so it reads as a blob, not a cube. */
	public static void lump(PoseStack pose, VertexConsumer vc, float cx, float cy, float cz, float r, int argb, int light) {
		float s = r * 0.68f, ly = r * 1.15f;
		AlienParts.box(pose, vc, cx - r, cy - s, cz - s, cx + r, cy + s, cz + s, 0f, 0f, 1f, 1f, argb, light);
		AlienParts.box(pose, vc, cx - s, cy - ly, cz - s, cx + s, cy + ly, cz + s, 0f, 0f, 1f, 1f, argb, light);
		AlienParts.box(pose, vc, cx - s, cy - s, cz - r, cx + s, cy + s, cz + r, 0f, 0f, 1f, 1f, argb, light);
	}
}
