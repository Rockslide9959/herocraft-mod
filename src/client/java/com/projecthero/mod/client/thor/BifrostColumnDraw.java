package com.projecthero.mod.client.thor;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.stormbreaker.BifrostEntities;
import com.projecthero.mod.stormbreaker.BifrostTravel;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * v0.17.7: the Bifrost Travel column ({@link BifrostEntities.Beam#LIFT} / {@link BifrostEntities.Beam#DESCEND}) -- the one
 * place the rainbow stays, so it gets the full treatment:
 * <ul>
 * <li>a blinding white core inside a soft glow;</li>
 * <li>a luminous sheath of smooth rainbow bands spiralling up the wall, and a second, counter-turning layer of brighter
 * streaks inside it (the swirl);</li>
 * <li>rings of Asgardian runes -- gold-white glyph strokes -- turning round the column and climbing it (sinking, on the way
 * down);</li>
 * <li>motes of light streaming up (down) through it;</li>
 * <li>a glowing pool where it meets the ground, a rainbow shockwave when it strikes and when the travellers land, and a
 * white flare at the top at the moment of transport.</li>
 * </ul>
 * Timings follow {@link BifrostTravel}: LIFT spawns at the strike (crash 0..4, the rise, the flare at
 * {@code TRANSPORT - STRIKE}, then pulls up into the sky); DESCEND spawns at the transport (crash 0..4, the riders lowered
 * until {@link BifrostTravel#DESCEND_TICKS}, a landing shockwave, then it fades).
 */
public final class BifrostColumnDraw {
	private static final float HEIGHT = 72f;
	private static final int GOLD = 0xFFF0C4;

	/** Rune glyphs as strokes in a 1 x 1.6 cell (u across, v up): Tiwaz, Algiz, Fehu, Thurisaz, Raido, Kenaz, Gebo, Sowilo. */
	private static final float[][][] GLYPHS = {
			{ { 0, 0, 0, 1.6f }, { 0, 1.6f, -0.45f, 1.1f }, { 0, 1.6f, 0.45f, 1.1f } },
			{ { 0, 0, 0, 1.6f }, { 0, 0.8f, -0.45f, 1.5f }, { 0, 0.8f, 0.45f, 1.5f } },
			{ { -0.3f, 0, -0.3f, 1.6f }, { -0.3f, 0.9f, 0.4f, 1.4f }, { -0.3f, 0.4f, 0.4f, 0.9f } },
			{ { -0.3f, 0, -0.3f, 1.6f }, { -0.3f, 1.2f, 0.35f, 0.8f }, { -0.3f, 0.4f, 0.35f, 0.8f } },
			{ { -0.3f, 0, -0.3f, 1.6f }, { -0.3f, 1.6f, 0.35f, 1.2f }, { 0.35f, 1.2f, -0.3f, 0.8f }, { -0.3f, 0.8f, 0.4f, 0f } },
			{ { 0.35f, 1.4f, -0.35f, 0.8f }, { -0.35f, 0.8f, 0.35f, 0.2f } },
			{ { -0.45f, 0.1f, 0.45f, 1.5f }, { 0.45f, 0.1f, -0.45f, 1.5f } },
			{ { 0.35f, 1.6f, -0.35f, 1.0f }, { -0.35f, 1.0f, 0.35f, 0.6f }, { 0.35f, 0.6f, -0.35f, 0f } },
	};

	private BifrostColumnDraw() {
	}

	/** {@code cam}: the camera relative to the column's foot (a rider inside it gets a thinner veil). */
	public static void render(BifrostEntities.Beam e, float partial, PoseStack pose, VertexConsumer vc, net.minecraft.world.phys.Vec3 cam) {
		float t = e.tickCount + partial;
		boolean lift = e.mode() == BifrostEntities.Beam.LIFT;
		float life = e.life();
		float flareAt = lift ? BifrostTravel.TRANSPORT - BifrostTravel.STRIKE : BifrostTravel.DESCEND_TICKS;
		float R = e.radius();
		float bottom;
		float alpha;
		float width;
		if (lift) {
			bottom = t < 4f ? HEIGHT * (1f - t / 4f) : t < flareAt ? 0f : (t - flareAt) / (life - flareAt) * HEIGHT;
			alpha = t < flareAt ? 1f : Math.max(0f, 1f - (t - flareAt) / (life - flareAt));
			width = t < 6f ? 1.25f - t / 6f * 0.25f : 1f;
		} else {
			bottom = HEIGHT * Math.max(0f, 1f - t / 4f);
			float fadeFrom = flareAt + 6f;
			alpha = t < fadeFrom ? 1f : Math.max(0f, 1f - (t - fadeFrom) / (life - fadeFrom));
			width = t < 6f ? 1.25f - t / 6f * 0.25f : 1f - Math.max(0f, t - fadeFrom) / (life - fadeFrom) * 0.6f;
		}
		if (alpha <= 0.01f || bottom >= HEIGHT - 0.5f) {
			return;
		}
		float r = R * width;
		Matrix4f m = pose.last().pose();
		// 1 when the camera is inside the column (a rider's view): the veil thins so the world shows through
		float inside = Mth.clamp((r + 0.5f - (float) Math.hypot(cam.x, cam.z)) / 1.2f, 0f, 1f);

		// the core and its glow
		pose.pushPose();
		pose.translate(0f, (bottom + HEIGHT) / 2f, 0f);
		float half = (HEIGHT - bottom) / 2f;
		float coreA = alpha * (1f - 0.9f * inside); // a rider stands in the core: keep it from milking out their view
		TurboDraw.box(vc, pose.last(), r * 0.16f, half, r * 0.16f, 0xFFFFFF, 0.9f * coreA);
		TurboDraw.box(vc, pose.last(), r * 0.34f, half, r * 0.34f, 0xF2F8FF, 0.28f * coreA);
		pose.popPose();

		// the sheath: smooth rainbow bands spiralling up the wall (hue by angle + height, scrolling with time)
		int sides = Math.max(20, (int) (r * 9));
		float step = 2f;
		float scroll = lift ? -t * 0.035f : t * 0.035f;
		for (float y = bottom; y < HEIGHT; y += step) {
			float y1 = Math.min(HEIGHT, y + step);
			float topFade = 1f - Mth.clamp((y - 40f) / 32f, 0f, 1f) * 0.85f;
			for (int k = 0; k < sides; k++) {
				float a0 = k * Mth.TWO_PI / sides + t * 0.05f;
				float a1 = (k + 1) * Mth.TWO_PI / sides + t * 0.05f;
				float h00 = hue(k / (float) sides + y * 0.022f + scroll);
				float h10 = hue((k + 1) / (float) sides + y * 0.022f + scroll);
				float h01 = hue(k / (float) sides + y1 * 0.022f + scroll);
				float h11 = hue((k + 1) / (float) sides + y1 * 0.022f + scroll);
				float stripe = 0.5f + 0.5f * Mth.sin(Mth.TWO_PI * ((k + 0.5f) / sides * 3f + (y + step / 2f) * 0.07f + scroll * 3f));
				float wa = (0.1f + 0.3f * stripe * stripe) * alpha * topFade * (1f - 0.55f * inside);
				wall(vc, pose, m, r, a0, a1, y, y1, h00, h10, h01, h11, wa);
			}
		}
		// the swirl: a counter-turning inner layer of brighter streaks
		float r2 = r * 0.72f;
		for (float y = bottom; y < Math.min(HEIGHT, 40f); y += 0.8f) {
			for (int s = 0; s < 3; s++) {
				float ang = s * Mth.TWO_PI / 3f - t * 0.16f + y * 0.35f;
				pose.pushPose();
				pose.translate(Mth.cos(ang) * r2, y + 0.4f, Mth.sin(ang) * r2);
				pose.mulPose(Axis.YP.rotation(-ang));
				pose.mulPose(Axis.XP.rotation(0.55f));
				int c = rgb(hue(y * 0.05f + s / 3f + t * 0.02f));
				float near = (float) cam.distanceTo(new net.minecraft.world.phys.Vec3(Mth.cos(ang) * r2, y + 0.4f, Mth.sin(ang) * r2));
				float sa = 0.6f * alpha * (1f - 0.6f * inside) * Mth.clamp((near - 0.8f) / 1.5f, 0f, 1f);
				if (sa > 0.01f) {
					TurboDraw.box(vc, pose.last(), 0.03f, 0.42f, 0.13f, TurboDraw.toWhite(c, 0.35f), sa);
				}
				pose.popPose();
			}
		}

		// rings of runes turning round the column and climbing it (sinking on the way down)
		float ringR = r * 1.06f;
		for (int ring = 0; ring < 4; ring++) {
			float span = 16f;
			float travel = ((lift ? t * 0.22f : -t * 0.22f) + ring * span / 4f) % span;
			if (travel < 0) {
				travel += span;
			}
			float y = bottom + 0.6f + travel;
			if (y > HEIGHT) {
				continue;
			}
			float edge = Math.min(Mth.clamp(travel / 2.5f, 0f, 1f), Mth.clamp((span - travel) / 3f, 0f, 1f));
			int glyphs = Math.max(8, (int) (ringR * 3.2f));
			float spin = (ring % 2 == 0 ? 1f : -1f) * t * 0.04f;
			for (int g = 0; g < glyphs; g++) {
				float ang = g * Mth.TWO_PI / glyphs + spin + ring * 0.4f;
				pose.pushPose();
				pose.translate(Mth.cos(ang) * ringR, y, Mth.sin(ang) * ringR);
				pose.mulPose(Axis.YP.rotation(-ang + Mth.HALF_PI));
				glyph(vc, pose, GLYPHS[(g + ring * 3) % GLYPHS.length], 0.3f, GOLD, 0.95f * alpha * edge);
				pose.popPose();
			}
			// the ring's thin band of light under the glyphs
			band(vc, pose, ringR, y - 0.12f, 0.025f, GOLD, 0.55f * alpha * edge, 48);
		}

		// motes of light streaming up (down) through the column
		for (int i = 0; i < 46; i++) {
			double h = ThorDraw.hash(i * 7.31 + e.getId());
			double h2 = ThorDraw.hash(i * 3.17 + 11 + e.getId());
			float ang = (float) (h * Mth.TWO_PI) + t * 0.03f;
			float rad = r * (0.2f + 0.8f * (float) h2);
			float speed = 0.25f + (float) h * 0.3f;
			float y = (float) ((h2 * 22 + (lift ? t * speed : -t * speed)) % 22);
			if (y < 0) {
				y += 22;
			}
			if (y < bottom) {
				continue;
			}
			pose.pushPose();
			pose.translate(Mth.cos(ang) * rad, y, Mth.sin(ang) * rad);
			TurboDraw.box(vc, pose.last(), 0.05f, 0.18f, 0.05f, TurboDraw.toWhite(rgb(hue((float) h)), 0.6f), 0.85f * alpha);
			pose.popPose();
		}

		// the pool of light on the ground and the shockwave rings
		if (bottom <= 0.01f) {
			disc(vc, pose, m, r * 1.15f, 0.06f, 0xFFF6E0, 0.5f * alpha, 40);
			shockwave(vc, pose, r, t - 4f, alpha);
			if (!lift) {
				shockwave(vc, pose, r, t - flareAt, alpha);
			}
		}
		// the flare at the top as they are carried across (LIFT) / as they arrive at the top (DESCEND)
		float since = lift ? t - flareAt : t;
		if (since > -3f && since < 10f) {
			float k = Mth.clamp((since + 3f) / 13f, 0f, 1f);
			float a = Mth.sin(k * Mth.PI);
			float fy = (float) BifrostTravel.RISE_HEIGHT + 1.0f;
			ThorDraw.ring(vc, pose.last(), new net.minecraft.world.phys.Vec3(0, fy, 0), r * (0.4f + k * 1.6f), r * (0.9f + k * 2.6f), GOLD, 0.8f * a, 48);
			ThorDraw.ring(vc, pose.last(), new net.minecraft.world.phys.Vec3(0, fy + 0.05f, 0), r * (0.2f + k * 0.8f), r * (0.6f + k * 1.4f), 0xFFFFFF, 0.7f * a, 48);
			pose.pushPose();
			pose.translate(0f, fy, 0f);
			TurboDraw.box(vc, pose.last(), r * 0.3f * (1f + a), 3.5f, r * 0.3f * (1f + a), 0xFFFFFF, 0.5f * a);
			pose.popPose();
		}
	}

	/** One wall quad of the sheath (the render type draws both sides). */
	private static void wall(VertexConsumer vc, PoseStack pose, Matrix4f m, float r, float a0, float a1, float y0, float y1,
			float h00, float h10, float h01, float h11, float alpha) {
		float x0 = Mth.cos(a0) * r, z0 = Mth.sin(a0) * r, x1 = Mth.cos(a1) * r, z1 = Mth.sin(a1) * r;
		int c00 = rgb(h00), c10 = rgb(h10), c01 = rgb(h01), c11 = rgb(h11);
		int a = Mth.clamp((int) (alpha * 255), 0, 255);
		v(vc, pose, m, x0, y0, z0, c00, a);
		v(vc, pose, m, x1, y0, z1, c10, a);
		v(vc, pose, m, x1, y1, z1, c11, a);
		v(vc, pose, m, x0, y1, z0, c01, a);
	}

	private static void v(VertexConsumer vc, PoseStack pose, Matrix4f m, float x, float y, float z, int rgb, int a) {
		vc.addVertex(m, x, y, z).setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, a).setUv(0.5f, 0.5f)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(), 0, 1, 0);
	}

	/** One rune: its strokes as thin bars in the plane facing out from the column. */
	private static void glyph(VertexConsumer vc, PoseStack pose, float[][] strokes, float scale, int rgb, float alpha) {
		if (alpha <= 0.01f) {
			return;
		}
		for (float[] s : strokes) {
			float u0 = s[0] * scale, v0 = s[1] * scale, u1 = s[2] * scale, v1 = s[3] * scale;
			float len = (float) Math.hypot(u1 - u0, v1 - v0);
			pose.pushPose();
			pose.translate((u0 + u1) / 2f, (v0 + v1) / 2f, 0f);
			pose.mulPose(Axis.ZP.rotation((float) Math.atan2(v1 - v0, u1 - u0) - Mth.HALF_PI));
			TurboDraw.box(vc, pose.last(), 0.028f, len / 2f + 0.02f, 0.012f, rgb, alpha);
			TurboDraw.box(vc, pose.last(), 0.06f, len / 2f + 0.05f, 0.006f, rgb, alpha * 0.3f); // glow
			pose.popPose();
		}
	}

	/** A soft round pool of light: bright in the middle, fading to nothing at {@code radius}. */
	private static void disc(VertexConsumer vc, PoseStack pose, Matrix4f m, float radius, float y, int rgb, float alpha, int n) {
		int a = Mth.clamp((int) (alpha * 255), 0, 255);
		for (int i = 0; i < n; i++) {
			float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
			float x0 = Mth.cos(a0) * radius, z0 = Mth.sin(a0) * radius, x1 = Mth.cos(a1) * radius, z1 = Mth.sin(a1) * radius;
			v(vc, pose, m, 0, y, 0, rgb, a);
			v(vc, pose, m, 0, y, 0, rgb, a);
			v(vc, pose, m, x1, y, z1, rgb, 0);
			v(vc, pose, m, x0, y, z0, rgb, 0);
		}
	}

	/** A thin horizontal ring of light. */
	private static void band(VertexConsumer vc, PoseStack pose, float r, float y, float hh, int rgb, float alpha, int n) {
		if (alpha <= 0.01f) {
			return;
		}
		for (int i = 0; i < n; i++) {
			float ang = i * Mth.TWO_PI / n;
			pose.pushPose();
			pose.translate(Mth.cos(ang) * r, y, Mth.sin(ang) * r);
			pose.mulPose(Axis.YP.rotation(-ang));
			TurboDraw.box(vc, pose.last(), 0.012f, hh, Mth.TWO_PI * r / n * 0.52f, rgb, alpha);
			pose.popPose();
		}
	}

	/** A rainbow ring rolling out over the ground {@code since} ticks after an impact. */
	private static void shockwave(VertexConsumer vc, PoseStack pose, float r, float since, float alpha) {
		if (since < 0f || since > 16f) {
			return;
		}
		float k = since / 16f;
		float rr = r + k * 7f;
		int n = 48;
		for (int i = 0; i < n; i++) {
			float ang = i * Mth.TWO_PI / n;
			pose.pushPose();
			pose.translate(Mth.cos(ang) * rr, 0.08f, Mth.sin(ang) * rr);
			pose.mulPose(Axis.YP.rotation(-ang));
			TurboDraw.box(vc, pose.last(), 0.14f, 0.07f, Mth.TWO_PI * rr / n * 0.55f, rgb(hue(i / (float) n)), (1f - k) * 0.85f * alpha);
			pose.popPose();
		}
		ThorDraw.ring(vc, pose.last(), new net.minecraft.world.phys.Vec3(0, 0.12, 0), r * 0.6f + k * 4f, r + k * 6.5f, 0xFFFFFF, (1f - k) * 0.6f * alpha, 48);
	}

	/** 0..1 wrapped. */
	private static float hue(float h) {
		return h - Mth.floor(h);
	}

	/** A vivid rainbow colour for hue 0..1 (red, orange, yellow, green, cyan, blue, violet). */
	static int rgb(float h) {
		return Mth.hsvToRgb(hue(h), 0.78f, 1.0f);
	}
}
