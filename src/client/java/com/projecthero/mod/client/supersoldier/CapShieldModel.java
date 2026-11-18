package com.projecthero.mod.client.supersoldier;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.supersoldier.item.SuperSoldierItems;
import com.projecthero.mod.ultron.contagion.item.ContagionItems;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.5: the Captain America shields' one model -- a round dish built from stepped rings, drawn the same way for the
 * Soldier's, Vibranium and Adamantium Shields (each its own 64x64 art, {@code scratchpad/gen_cap_shields_v0175.js}).
 *
 * <p><b>Shield space</b> (model pixels, drawn at 1/16 block each): the disc lies in the XY plane centred on the origin, +Y
 * is up (the star's point), the decorated <b>front faces +Z</b>. The front is convex: five rings step up toward the centre
 * (rim 0.8, outer ring 1.1, white 1.45, inner ring 1.8, centre 2.1 px), each ring's outward-facing step wall drawn. The
 * back is concave the same way (rim lip -0.1 down to the centre's 1.4), and two vertical leather straps with steel rivets
 * bridge the hollow at {@link #CONTACT_Z}: the forearm (or the back) rests against them. Front and back faces take their
 * UVs straight from X / Y (2 texels a pixel), so the ring art always lines up with the ring geometry; the step walls read a
 * strip of plain colours.
 */
public final class CapShieldModel {
	public static final ResourceLocation SOLDIER = ProjectHeroMod.id("textures/entity/soldier_shield.png");
	public static final ResourceLocation VIBRANIUM = ProjectHeroMod.id("textures/entity/vibranium_shield.png");
	public static final ResourceLocation ADAMANTIUM = ProjectHeroMod.id("textures/entity/adamantium_shield.png");

	/** Outer radius in model pixels. */
	public static final float RADIUS = 8.0f;
	/** The straps' outer face (z, model px): what touches the arm or the back. */
	public static final float CONTACT_Z = -0.45f;
	/** Front-most point (the centre's face), model px. */
	public static final float FRONT_Z = 2.1f;
	/** The middle of the shield's depth (spin centre for the thrown shield). */
	public static final float MID_Z = (FRONT_Z + CONTACT_Z) * 0.5f;

	/** Ring outer radii (px), inside out: centre, inner ring, white ring, outer ring, rim. */
	private static final float[] R = { 3.0f, 4.5f, 6.0f, 7.0f, 8.0f };
	private static final float[] FRONT = { 2.1f, 1.8f, 1.45f, 1.1f, 0.8f };
	private static final float[] BACK = { 1.4f, 1.15f, 0.85f, 0.5f, -0.1f };
	/** Wall strip row (texture v / 2 - 16) for each ring's outer wall: centre 4, inner 3, white 2, outer 1, rim 0. */
	private static final int[] WALL_ROW = { 4, 3, 2, 1, 0 };
	private static final int SIDES = 32;
	private static final float TEX = 64.0f;

	private static final float[] COS = new float[SIDES + 1];
	private static final float[] SIN = new float[SIDES + 1];
	static {
		for (int i = 0; i <= SIDES; i++) {
			double a = i * Math.PI * 2.0 / SIDES;
			COS[i] = (float) Math.cos(a);
			SIN[i] = (float) Math.sin(a);
		}
	}

	private CapShieldModel() {
	}

	/** The art for a shield stack: the Soldier's, the Vibranium or the Adamantium Shield (anything else: Adamantium). */
	public static ResourceLocation texture(ItemStack stack) {
		if (ContagionItems.VIBRANIUM_SHIELD != null && stack.is(ContagionItems.VIBRANIUM_SHIELD)) {
			return VIBRANIUM;
		}
		if (stack.is(SuperSoldierItems.SOLDIER_SHIELD)) {
			return SOLDIER;
		}
		return ADAMANTIUM;
	}

	/** True for the three shields this model draws. */
	public static boolean isCapShield(ItemStack stack) {
		return !stack.isEmpty() && (stack.getItem() instanceof com.projecthero.mod.supersoldier.item.AdamantiumShieldItem
				|| stack.is(SuperSoldierItems.SOLDIER_SHIELD));
	}

	/** Draws the shield in shield space (see the class doc), solid. */
	public static void render(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture, boolean foil, int light, int overlay) {
		VertexConsumer vc = ItemRenderer.getFoilBufferDirect(buffers, RenderType.entityCutoutNoCull(texture), true, foil);
		draw(vc, pose.last(), light, overlay, 0xFFFFFFFF);
	}

	/** A see-through copy (the thrown shield's motion trail): {@code alpha} 0..1. */
	public static void renderGhost(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture, int light, int overlay, float alpha) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(texture));
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
		draw(vc, pose.last(), light, overlay, (a << 24) | 0xFFFFFF);
	}

	private static void draw(VertexConsumer vc, PoseStack.Pose p, int light, int overlay, int color) {
		float s = 1.0f / 16.0f;
		for (int ring = 0; ring < R.length; ring++) {
			float r0 = ring == 0 ? 0.0f : R[ring - 1];
			float r1 = R[ring];
			float zf = FRONT[ring];
			float zb = BACK[ring];
			for (int i = 0; i < SIDES; i++) {
				int j = i + 1;
				// front (+Z): wound counter-clockwise seen from the front
				float ax = COS[i] * r0, ay = SIN[i] * r0, bx = COS[j] * r0, by = SIN[j] * r0;
				float cx = COS[j] * r1, cy = SIN[j] * r1, dx = COS[i] * r1, dy = SIN[i] * r1;
				vertex(vc, p, ax * s, ay * s, zf * s, fu(ax), fv(ay), 0, 0, 1, light, overlay, color);
				vertex(vc, p, dx * s, dy * s, zf * s, fu(dx), fv(dy), 0, 0, 1, light, overlay, color);
				vertex(vc, p, cx * s, cy * s, zf * s, fu(cx), fv(cy), 0, 0, 1, light, overlay, color);
				vertex(vc, p, bx * s, by * s, zf * s, fu(bx), fv(by), 0, 0, 1, light, overlay, color);
				// back (-Z)
				vertex(vc, p, ax * s, ay * s, zb * s, bu(ax), fv(ay), 0, 0, -1, light, overlay, color);
				vertex(vc, p, bx * s, by * s, zb * s, bu(bx), fv(by), 0, 0, -1, light, overlay, color);
				vertex(vc, p, cx * s, cy * s, zb * s, bu(cx), fv(cy), 0, 0, -1, light, overlay, color);
				vertex(vc, p, dx * s, dy * s, zb * s, bu(dx), fv(dy), 0, 0, -1, light, overlay, color);
				// the step wall on this ring's outer edge, front side (faces outward): from the next ring's front up to this one's
				float nx = (COS[i] + COS[j]) * 0.5f, ny = (SIN[i] + SIN[j]) * 0.5f;
				float lowF = ring == R.length - 1 ? BACK[ring] : FRONT[ring + 1];
				float u0 = i * 2.0f, u1 = j * 2.0f;
				float v0 = 32 + WALL_ROW[ring] * 2, v1 = v0 + 2;
				vertex(vc, p, dx * s, dy * s, zf * s, u0 / TEX, v0 / TEX, nx, ny, 0, light, overlay, color);
				vertex(vc, p, dx * s, dy * s, lowF * s, u0 / TEX, v1 / TEX, nx, ny, 0, light, overlay, color);
				vertex(vc, p, cx * s, cy * s, lowF * s, u1 / TEX, v1 / TEX, nx, ny, 0, light, overlay, color);
				vertex(vc, p, cx * s, cy * s, zf * s, u1 / TEX, v0 / TEX, nx, ny, 0, light, overlay, color);
				// back side: the step from this ring's back down to the next (outer) ring's, facing the centre
				if (ring < R.length - 1) {
					float outB = BACK[ring + 1];
					float bv0 = 32 + 5 * 2, bv1 = bv0 + 2;
					vertex(vc, p, dx * s, dy * s, outB * s, u0 / TEX, bv0 / TEX, -nx, -ny, 0, light, overlay, color);
					vertex(vc, p, dx * s, dy * s, zb * s, u0 / TEX, bv1 / TEX, -nx, -ny, 0, light, overlay, color);
					vertex(vc, p, cx * s, cy * s, zb * s, u1 / TEX, bv1 / TEX, -nx, -ny, 0, light, overlay, color);
					vertex(vc, p, cx * s, cy * s, outB * s, u1 / TEX, bv0 / TEX, -nx, -ny, 0, light, overlay, color);
				}
			}
		}
		// the straps: two vertical leather bands across the hollow, riveted into the back at each end
		for (int side = -1; side <= 1; side += 2) {
			float x0 = side * 2.0f - 0.7f, x1 = side * 2.0f + 0.7f;
			strap(vc, p, x0, x1, -5.0f, 5.0f, CONTACT_Z, CONTACT_Z + 0.35f, light, overlay, color);
			// anchors: from the band down into the back at both ends, rivet heads on the band
			box(vc, p, x0, -5.6f, CONTACT_Z, x1, -4.6f, 1.0f, 16, 48, 24, 56, light, overlay, color);
			box(vc, p, x0, 4.6f, CONTACT_Z, x1, 5.6f, 1.0f, 16, 48, 24, 56, light, overlay, color);
		}
		// the forearm pad between the straps
		box(vc, p, -1.3f, -2.4f, CONTACT_Z + 0.15f, 1.3f, 2.4f, 1.4f, 24, 48, 32, 56, light, overlay, color);
	}

	private static void strap(VertexConsumer vc, PoseStack.Pose p, float x0, float x1, float y0, float y1, float z0, float z1, int light, int overlay,
			int color) {
		// the band's texture runs along its length (u 0..16, v 48..56)
		float s = 1.0f / 16.0f;
		float a = x0 * s, b = x1 * s, c = y0 * s, d = y1 * s, e = z0 * s, f = z1 * s;
		float U0 = 0 / TEX, U1 = 16 / TEX, V0 = 48 / TEX, V1 = 56 / TEX;
		// outer face (-Z, toward the arm)
		vertex(vc, p, a, c, e, U0, V1, 0, 0, -1, light, overlay, color);
		vertex(vc, p, a, d, e, U1, V1, 0, 0, -1, light, overlay, color);
		vertex(vc, p, b, d, e, U1, V0, 0, 0, -1, light, overlay, color);
		vertex(vc, p, b, c, e, U0, V0, 0, 0, -1, light, overlay, color);
		// inner face (+Z, toward the shield)
		vertex(vc, p, a, c, f, U0, V1, 0, 0, 1, light, overlay, color);
		vertex(vc, p, b, c, f, U0, V0, 0, 0, 1, light, overlay, color);
		vertex(vc, p, b, d, f, U1, V0, 0, 0, 1, light, overlay, color);
		vertex(vc, p, a, d, f, U1, V1, 0, 0, 1, light, overlay, color);
		// edges
		vertex(vc, p, a, c, e, U0, V0, -1, 0, 0, light, overlay, color);
		vertex(vc, p, a, c, f, U0, V1, -1, 0, 0, light, overlay, color);
		vertex(vc, p, a, d, f, U1, V1, -1, 0, 0, light, overlay, color);
		vertex(vc, p, a, d, e, U1, V0, -1, 0, 0, light, overlay, color);
		vertex(vc, p, b, c, e, U0, V0, 1, 0, 0, light, overlay, color);
		vertex(vc, p, b, d, e, U1, V0, 1, 0, 0, light, overlay, color);
		vertex(vc, p, b, d, f, U1, V1, 1, 0, 0, light, overlay, color);
		vertex(vc, p, b, c, f, U0, V1, 1, 0, 0, light, overlay, color);
	}

	private static void box(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, float tu0, float tv0, float tu1,
			float tv1, int light, int overlay, int color) {
		float s = 1.0f / 16.0f;
		float a = x0 * s, b = y0 * s, c = z0 * s, d = x1 * s, e = y1 * s, f = z1 * s;
		float u0 = tu0 / TEX, v0 = tv0 / TEX, u1 = tu1 / TEX, v1 = tv1 / TEX;
		quad(vc, p, a, b, c, a, e, c, d, e, c, d, b, c, u0, v0, u1, v1, 0, 0, -1, light, overlay, color);
		quad(vc, p, a, b, f, d, b, f, d, e, f, a, e, f, u0, v0, u1, v1, 0, 0, 1, light, overlay, color);
		quad(vc, p, a, b, c, a, b, f, a, e, f, a, e, c, u0, v0, u1, v1, -1, 0, 0, light, overlay, color);
		quad(vc, p, d, b, c, d, e, c, d, e, f, d, b, f, u0, v0, u1, v1, 1, 0, 0, light, overlay, color);
		quad(vc, p, a, b, c, d, b, c, d, b, f, a, b, f, u0, v0, u1, v1, 0, -1, 0, light, overlay, color);
		quad(vc, p, a, e, c, a, e, f, d, e, f, d, e, c, u0, v0, u1, v1, 0, 1, 0, light, overlay, color);
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose p, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
			float x4, float y4, float z4, float u0, float v0, float u1, float v1, float nx, float ny, float nz, int light, int overlay, int color) {
		vertex(vc, p, x1, y1, z1, u0, v0, nx, ny, nz, light, overlay, color);
		vertex(vc, p, x2, y2, z2, u1, v0, nx, ny, nz, light, overlay, color);
		vertex(vc, p, x3, y3, z3, u1, v1, nx, ny, nz, light, overlay, color);
		vertex(vc, p, x4, y4, z4, u0, v1, nx, ny, nz, light, overlay, color);
	}

	/** Front art u for an x (px): centre at texel 16, 2 texels a pixel. */
	private static float fu(float x) {
		return (16.0f + x * 2.0f) / TEX;
	}

	/** Back art u: mirrored so it reads the right way round from behind, at texel 48. */
	private static float bu(float x) {
		return (48.0f - x * 2.0f) / TEX;
	}

	/** Art v for a y (px; +Y up, texture v down). */
	private static float fv(float y) {
		return (16.0f - y * 2.0f) / TEX;
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, float nx, float ny, float nz, int light,
			int overlay, int color) {
		vc.addVertex(p, x, y, z).setColor(color).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(p, nx, ny, nz);
	}
}
