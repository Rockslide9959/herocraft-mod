package com.projecthero.mod.client.greenlantern.recon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.greenlantern.HardLightDraw;
import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.greenlantern.recon.GreenLanternReconConfig;
import com.projecthero.mod.greenlantern.recon.HardLightGatewayEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.1: one door of the Hard-Light Gateway. It reads as a door first: two thick stepped pillars on bases, a squared
 * lintel with an arched crown over it carrying the Lantern emblem and a keystone, a threshold step, rune-like dashes of
 * light running up the pillar faces, and a bright inner edge round the opening. Inside, the surface swirls: three layered
 * planes at increasing depth, each a spiral of light turning its own way (darker and slower the deeper it sits), a bright
 * rim glow where the light meets the frame, and motes of light flowing in from round the frame and spiralling down into the
 * middle. No true see-through.
 *
 * <p>Animated from the synced state ({@link HardLightGatewayEntity}): while the bearer channels, the frame's outline traces
 * itself out of light from the threshold up both sides to the crown; when it opens, a seam of light grows from the centre
 * and the door unfolds outward from it, the surface fading in; closing folds it back. Built facing +Z (the door's front),
 * the opening from x = -0.95 to 0.95 and y = 0 to 2.9 in blocks.
 */
public class HardLightGatewayRenderer extends EntityRenderer<HardLightGatewayEntity> {
	private static final int GREEN = HardLightDraw.GREEN;
	private static final int PALE = HardLightDraw.PALE;
	private static final int DEEP = HardLightDraw.DEEP;
	private static final float HW = (float) GreenLanternReconConfig.DOOR_HALF_WIDTH;
	private static final float H = (float) GreenLanternReconConfig.DOOR_HEIGHT;
	/** Pillar centre-line x and half-thickness. */
	private static final float PX = HW + 0.17f;
	private static final float PT = 0.17f;
	private static final float LINTEL_Y = H + 0.17f;
	private static final float CROWN = 0.45f;

	public HardLightGatewayRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0f;
	}

	@Override
	public boolean shouldRender(HardLightGatewayEntity e, Frustum frustum, double x, double y, double z) {
		return frustum.isVisible(e.getBoundingBoxForCulling());
	}

	@Override
	public ResourceLocation getTextureLocation(HardLightGatewayEntity e) {
		return TurboDraw.WHITE;
	}

	@Override
	public void render(HardLightGatewayEntity e, float entityYaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		VertexConsumer vc = HardLightDraw.buffer(buffers);
		float time = e.level().getGameTime() + partial;
		HardLightDraw.golden = e.golden();
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(-e.faceYaw()));
		if (e.state() == HardLightGatewayEntity.TRACE) {
			float t = Mth.clamp(e.stateAge(partial) / GreenLanternReconConfig.GATEWAY_CHANNEL_TICKS, 0f, 1f);
			trace(vc, pose, t, time);
		} else {
			float u = e.openness(partial);
			if (u > 0.001f) {
				door(vc, pose, u, time);
			}
		}
		pose.popPose();
		HardLightDraw.golden = false;
	}

	// ---------------------------------------------------------------- the channel: the outline traces itself

	/** The frame's outline, from the threshold's middle out and up both sides, meeting at the crown. */
	private static void trace(VertexConsumer vc, PoseStack pose, float t, float time) {
		float[][] path = outline();
		float total = 0f;
		for (int i = 1; i < path.length; i++) {
			total += dist(path[i - 1], path[i]);
		}
		float drawn = total * easeInOut(t);
		float flicker = 0.85f + 0.15f * Mth.sin(time * 1.3f);
		for (int side = -1; side <= 1; side += 2) {
			float left = drawn;
			float[] tip = path[0];
			for (int i = 1; i < path.length && left > 0f; i++) {
				float[] a = path[i - 1];
				float[] b = path[i];
				float len = dist(a, b);
				float k = Math.min(1f, left / len);
				float[] end = { a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k };
				line(vc, pose, side * a[0], a[1], side * end[0], end[1], 0.035f, flicker);
				tip = end;
				left -= len;
			}
			// the drawing point: a white spark with a soft green bloom
			if (t < 0.999f) {
				pose.pushPose();
				pose.translate(side * tip[0], tip[1], 0f);
				TurboDraw.orb(vc, pose, 0.16f, HardLightDraw.tint(GREEN), time * 25f, 1f);
				pose.popPose();
			}
		}
		// the ghost of the whole frame, faint, so the shape is foreseeable
		for (int side = -1; side <= 1; side += 2) {
			for (int i = 1; i < path.length; i++) {
				line(vc, pose, side * path[i - 1][0], path[i - 1][1], side * path[i][0], path[i][1], 0.02f, 0.18f);
			}
		}
		// the emblem lights at the crown as the two lines meet
		float em = Mth.clamp((t - 0.85f) / 0.15f, 0f, 1f);
		if (em > 0f) {
			pose.pushPose();
			pose.translate(0f, LINTEL_Y + CROWN * 0.62f, 0.05f);
			HardLightDraw.emblem(vc, pose, 0.5f * (0.6f + 0.4f * em), em);
			pose.popPose();
		}
		// a faint curtain of light already gathering in the opening
		float c = t * t * 0.35f;
		if (c > 0.01f) {
			surfacePlane(vc, pose, -0.02f, 1f, t, time * 0.6f, c, 3, 1f);
		}
	}

	/** Half the outline (the right side, x >= 0), threshold middle -> pillar foot -> pillar top -> lintel -> crown top. */
	private static float[][] outline() {
		float[][] out = new float[9][];
		out[0] = new float[] { 0f, -0.05f };
		out[1] = new float[] { PX + PT, -0.05f };
		out[2] = new float[] { PX + PT, LINTEL_Y + 0.18f };
		out[3] = new float[] { PX + PT + 0.1f, LINTEL_Y + 0.18f };
		for (int i = 0; i < 5; i++) { // the crown's arc, from its foot at the lintel up to the top
			float a = (i + 1) / 5f * Mth.HALF_PI;
			out[4 + i] = new float[] { (PX + 0.05f) * Mth.cos(a), LINTEL_Y + 0.18f + CROWN * Mth.sin(a) };
		}
		return out;
	}

	private static float dist(float[] a, float[] b) {
		return Mth.sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1]));
	}

	/** A glowing line of light in the door plane (z = 0) from (x0, y0) to (x1, y1). */
	private static void line(VertexConsumer vc, PoseStack pose, float x0, float y0, float x1, float y1, float w, float a) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		float len = Mth.sqrt(dx * dx + dy * dy);
		if (len < 1.0e-3f) {
			return;
		}
		pose.pushPose();
		pose.translate((x0 + x1) / 2f, (y0 + y1) / 2f, 0f);
		pose.mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
		TurboDraw.box(vc, pose.last(), len / 2f + w * 0.5f, w * 0.45f, w * 0.45f, 0xF0FFF4, 0.95f * a);
		TurboDraw.box(vc, pose.last(), len / 2f + w, w * 1.4f, w * 1.4f, HardLightDraw.tint(GREEN), 0.35f * a);
		TurboDraw.box(vc, pose.last(), len / 2f + w * 2f, w * 3f, w * 3f, HardLightDraw.tint(GREEN), 0.08f * a);
		pose.popPose();
	}

	// ---------------------------------------------------------------- the open door

	private static void door(VertexConsumer vc, PoseStack pose, float u, float time) {
		// unfold: a seam of light grows from the centre (first 40%), then the door opens out sideways from it
		float sy = easeOut(Mth.clamp(u / 0.4f, 0f, 1f));
		float sx = easeOut(Mth.clamp((u - 0.3f) / 0.7f, 0f, 1f));
		float cy = LINTEL_Y * 0.5f;
		if (sx < 0.06f) {
			pose.pushPose();
			pose.translate(0f, cy, 0f);
			float half = (LINTEL_Y + CROWN) * 0.5f * sy;
			TurboDraw.box(vc, pose.last(), 0.03f, half, 0.03f, 0xF0FFF4, 0.95f);
			TurboDraw.box(vc, pose.last(), 0.09f, half + 0.05f, 0.09f, HardLightDraw.tint(GREEN), 0.35f);
			pose.popPose();
		}
		pose.pushPose();
		pose.translate(0f, cy, 0f);
		pose.scale(Math.max(0.02f, sx), Math.max(0.02f, sy), 1f);
		pose.translate(0f, -cy, 0f);
		float a = Mth.clamp(u * 1.6f, 0f, 1f);
		frame(vc, pose, a, time);
		float s = Mth.clamp((u - 0.55f) / 0.45f, 0f, 1f);
		if (s > 0.001f) {
			surface(vc, pose, s, time);
		}
		pose.popPose();
	}

	private static void p(VertexConsumer vc, PoseStack pose, float x, float y, float z, float hx, float hy, float hz, int rgb, float a) {
		HardLightDraw.part(vc, pose, x, y, z, hx, hy, hz, rgb, a);
	}

	/** Pillars, bases, capitals, lintel, the arched crown with the emblem and keystone, threshold, runes, inner edge. */
	private static void frame(VertexConsumer vc, PoseStack pose, float a, float time) {
		float breathe = 0.85f + 0.15f * Mth.sin(time * 0.12f);
		for (int s = -1; s <= 1; s += 2) {
			float x = s * PX;
			// the pillar, a stepped base and a capital
			p(vc, pose, x, LINTEL_Y / 2f, 0f, PT, LINTEL_Y / 2f, 0.2f, GREEN, a * 0.9f);
			p(vc, pose, x, 0.12f, 0f, PT + 0.08f, 0.12f, 0.27f, DEEP, a);
			p(vc, pose, x, 0.3f, 0f, PT + 0.04f, 0.06f, 0.24f, GREEN, a);
			p(vc, pose, x, H - 0.06f, 0f, PT + 0.05f, 0.07f, 0.25f, GREEN, a);
			// a fluted face: two grooves of deeper light, and the runes between them
			for (int g = -1; g <= 1; g += 2) {
				p(vc, pose, x + g * 0.08f, LINTEL_Y / 2f + 0.1f, 0.205f, 0.018f, LINTEL_Y / 2f - 0.55f, 0.01f, DEEP, a * 0.9f);
			}
			runes(vc, pose, x, s, a, time);
			// the bright inner edge that rims the opening
			HardLightDraw.glow(vc, pose, s * (HW + 0.005f), H / 2f, 0.04f, 0.012f, H / 2f, 0.015f, a * breathe);
		}
		// lintel: a squared beam over the pillars, a lip under it
		p(vc, pose, 0f, LINTEL_Y, 0f, PX + PT + 0.12f, 0.18f, 0.24f, GREEN, a * 0.92f);
		p(vc, pose, 0f, H + 0.01f, 0.02f, HW + 0.02f, 0.04f, 0.23f, DEEP, a);
		HardLightDraw.glow(vc, pose, 0f, H - 0.005f, 0.04f, HW, 0.012f, 0.015f, a * breathe);
		// the arched crown over the lintel: an arc of blocks rising to a keystone that carries the emblem
		int segs = 9;
		float cr = PX + 0.05f;
		for (int i = 0; i < segs; i++) {
			float a0 = Mth.PI * i / segs;
			float a1 = Mth.PI * (i + 1) / segs;
			float am = (a0 + a1) / 2f;
			float x = cr * Mth.cos(am);
			float y = LINTEL_Y + 0.16f + CROWN * Mth.sin(am);
			float dx = -cr * Mth.sin(am);
			float dy = CROWN * Mth.cos(am);
			float len = (a1 - a0) * Mth.sqrt(dx * dx + dy * dy);
			pose.pushPose();
			pose.translate(x, y, 0f);
			pose.mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
			p(vc, pose, 0f, 0f, 0f, len / 2f + 0.03f, 0.08f, 0.2f, i == segs / 2 ? PALE : GREEN, a * 0.9f);
			pose.popPose();
		}
		// fill under the arc (the tympanum), the keystone, and the emblem on it
		p(vc, pose, 0f, LINTEL_Y + 0.17f + CROWN * 0.4f, -0.02f, cr * 0.72f, CROWN * 0.4f, 0.12f, DEEP, a * 0.75f);
		p(vc, pose, 0f, LINTEL_Y + 0.2f + CROWN * 0.55f, 0.03f, 0.26f, 0.26f, 0.2f, GREEN, a);
		pose.pushPose();
		pose.translate(0f, LINTEL_Y + 0.2f + CROWN * 0.55f, 0.3f);
		HardLightDraw.emblem(vc, pose, 0.48f, a * (0.85f + 0.15f * Mth.sin(time * 0.2f)));
		pose.popPose();
		// the threshold step
		p(vc, pose, 0f, -0.05f, 0.05f, PX + PT + 0.1f, 0.06f, 0.32f, GREEN, a * 0.9f);
		HardLightDraw.glow(vc, pose, 0f, 0.015f, 0.37f, PX + PT, 0.008f, 0.008f, a * 0.8f);
	}

	/** Rune-like dashes of light up a pillar face, lit by a pulse that climbs the pillar. */
	private static void runes(VertexConsumer vc, PoseStack pose, float x, int side, float a, float time) {
		int rows = 11;
		float y0 = 0.55f;
		float y1 = H - 0.25f;
		for (int i = 0; i < rows; i++) {
			float y = y0 + (y1 - y0) * i / (rows - 1);
			int code = (int) ((((i * 7L + (side > 0 ? 3 : 0)) * 2654435761L) >>> 13) & 7);
			float wave = Mth.clamp(1f - Math.abs(((time * 0.04f) % 1.4f) - (float) i / rows) * 4f, 0f, 1f);
			float glow = a * (0.35f + 0.65f * wave);
			// each rune: a short bar plus one or two ticks, picked by the code
			HardLightDraw.glow(vc, pose, x, y, 0.215f, 0.045f, 0.008f, 0.005f, glow);
			if ((code & 1) != 0) {
				HardLightDraw.glow(vc, pose, x - 0.04f, y + 0.04f, 0.215f, 0.007f, 0.035f, 0.005f, glow);
			}
			if ((code & 2) != 0) {
				HardLightDraw.glow(vc, pose, x + 0.04f, y - 0.04f, 0.215f, 0.007f, 0.035f, 0.005f, glow);
			}
			if ((code & 4) != 0) {
				HardLightDraw.glow(vc, pose, x, y + 0.06f, 0.215f, 0.012f, 0.012f, 0.005f, glow);
			}
		}
	}

	// ---------------------------------------------------------------- the swirling surface

	private static void surface(VertexConsumer vc, PoseStack pose, float s, float time) {
		// deep to shallow: darker and slower the deeper it sits, each layer turning its own way
		surfacePlane(vc, pose, -0.55f, 0.55f, s, -time * 0.035f, 0.55f, 2, 0.5f);
		surfacePlane(vc, pose, -0.28f, 0.8f, s, time * 0.06f, 0.5f, 3, 0.8f);
		surfacePlane(vc, pose, -0.04f, 1f, s, -time * 0.09f, 0.42f, 4, 1f);
		rimGlow(vc, pose, s, time);
		motes(vc, pose, s, time);
	}

	/**
	 * One swirling plane filling the opening at depth {@code z}: a grid of quads whose vertex colours follow a spiral
	 * (arms {@code arms}, turned by {@code phase}) -- a dark emerald ground with brighter bands, brightening toward the rim.
	 */
	private static void surfacePlane(VertexConsumer vc, PoseStack pose, float z, float bright, float s, float phase, float alpha, int arms,
			float rimBoost) {
		int nx = 10;
		int ny = 16;
		PoseStack.Pose last = pose.last();
		float cy = H / 2f;
		for (int i = 0; i < nx; i++) {
			for (int j = 0; j < ny; j++) {
				float x0 = -HW + 2f * HW * i / nx;
				float x1 = -HW + 2f * HW * (i + 1) / nx;
				float y0 = H * j / ny;
				float y1 = H * (j + 1) / ny;
				int c00 = swirl(x0, y0, cy, phase, arms, bright, rimBoost, s * alpha);
				int c10 = swirl(x1, y0, cy, phase, arms, bright, rimBoost, s * alpha);
				int c11 = swirl(x1, y1, cy, phase, arms, bright, rimBoost, s * alpha);
				int c01 = swirl(x0, y1, cy, phase, arms, bright, rimBoost, s * alpha);
				quad(vc, last, x0, y0, x1, y1, z, c00, c10, c11, c01);
			}
		}
	}

	/** ARGB of the swirl at (x, y): spiral bands over a deep ground, hotter near the frame and in the very centre. */
	private static int swirl(float x, float y, float cy, float phase, int arms, float bright, float rimBoost, float alpha) {
		float nx = x / HW;
		float ny = (y - cy) / (H / 2f);
		float r = Mth.sqrt(nx * nx + ny * ny);
		float ang = (float) Math.atan2(ny, nx);
		float band = 0.5f + 0.5f * Mth.sin(arms * ang + r * 7.5f - phase * 6f);
		band = band * band * band;
		float edge = Math.max(Math.abs(nx), Math.abs(ny));
		float rim = Mth.clamp((edge - 0.8f) / 0.2f, 0f, 1f) * rimBoost;
		float core = Mth.clamp(1f - r * 2.6f, 0f, 1f);
		float lum = Mth.clamp((0.04f + 0.85f * band * (1f - r * 0.3f) + 0.35f * rim + 0.9f * core) * bright, 0f, 1.25f);
		int base = HardLightDraw.golden ? 0x1A2406 : 0x03241A; // a dark sea-green ground, so the bands and the frame stand out
		int hi = HardLightDraw.tint(GREEN);
		float t = Mth.clamp(lum, 0f, 1f);
		int rr = (int) Mth.lerp(t, (base >> 16) & 0xFF, (hi >> 16) & 0xFF);
		int gg = (int) Mth.lerp(t, (base >> 8) & 0xFF, (hi >> 8) & 0xFF);
		int bb = (int) Mth.lerp(t, base & 0xFF, hi & 0xFF);
		if (lum > 1f) { // the hottest bits run toward white
			float w = (lum - 1f) * 4f;
			rr = (int) Mth.lerp(w, rr, 255);
			gg = (int) Mth.lerp(w, gg, 255);
			bb = (int) Mth.lerp(w, bb, 255);
		}
		int aa = Mth.clamp((int) ((0.62f + 0.38f * t) * alpha * 255f), 0, 255);
		return (aa << 24) | (rr << 16) | (gg << 8) | bb;
	}

	/** A soft band of light just inside the frame, breathing. */
	private static void rimGlow(VertexConsumer vc, PoseStack pose, float s, float time) {
		float b = (0.6f + 0.4f * Mth.sin(time * 0.17f)) * s;
		PoseStack.Pose last = pose.last();
		int hot = (Mth.clamp((int) (0.55f * b * 255f), 0, 255) << 24) | HardLightDraw.tint(PALE);
		int none = HardLightDraw.tint(PALE);
		float w = 0.22f;
		float z = 0.0f;
		quad(vc, last, -HW, 0f, -HW + w, H, z, hot, none, none, hot);
		quad(vc, last, HW - w, 0f, HW, H, z, none, hot, hot, none);
		quad(vc, last, -HW, H - w, HW, H, z, none, none, hot, hot);
		quad(vc, last, -HW, 0f, HW, w * 0.6f, z, hot, hot, none, none);
	}

	/** Motes of light flowing in from round the frame and spiralling down into the middle of the door. */
	private static void motes(VertexConsumer vc, PoseStack pose, float s, float time) {
		int n = 46;
		float cy = H / 2f;
		for (int i = 0; i < n; i++) {
			float seed = i * 0.6180339f;
			float speed = 0.012f + (seed * 7.3f % 1f) * 0.01f;
			float k = (time * speed + seed) % 1f; // 0 = at the frame, 1 = gone into the middle
			float ang = seed * Mth.TWO_PI * 3f + k * 2.2f;
			float r = 1.15f * (1f - k);
			float x = Mth.cos(ang) * r * HW;
			float y = cy + Mth.sin(ang) * r * (H / 2f);
			float z = 0.5f * (1f - k) * (i % 3 == 0 ? 1f : 0.25f) - 0.5f * k;
			float a = Mth.sin(k * Mth.PI) * s;
			float size = 0.022f + 0.02f * (1f - k);
			pose.pushPose();
			pose.translate(x, y, z);
			TurboDraw.box(vc, pose.last(), size, size, size, 0xE8FFEE, 0.85f * a);
			TurboDraw.box(vc, pose.last(), size * 2.4f, size * 2.4f, size * 2.4f, HardLightDraw.tint(GREEN), 0.22f * a);
			pose.popPose();
		}
	}

	/** A quad in the z plane with a colour per corner (x0,y0) (x1,y0) (x1,y1) (x0,y1), drawn from both sides. */
	private static void quad(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float z, int c00, int c10, int c11,
			int c01) {
		v(vc, pose, x0, y0, z, c00, 0f, 0f, 1f);
		v(vc, pose, x1, y0, z, c10, 0f, 0f, 1f);
		v(vc, pose, x1, y1, z, c11, 0f, 0f, 1f);
		v(vc, pose, x0, y1, z, c01, 0f, 0f, 1f);
		v(vc, pose, x0, y1, z, c01, 0f, 0f, -1f);
		v(vc, pose, x1, y1, z, c11, 0f, 0f, -1f);
		v(vc, pose, x1, y0, z, c10, 0f, 0f, -1f);
		v(vc, pose, x0, y0, z, c00, 0f, 0f, -1f);
	}

	private static void v(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, int argb, float nx, float ny, float nz) {
		vc.addVertex(pose, x, y, z).setColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, argb >>> 24).setUv(0.5f, 0.5f)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, nx, ny, nz);
	}

	private static float easeOut(float t) {
		return 1f - (1f - t) * (1f - t) * (1f - t);
	}

	private static float easeInOut(float t) {
		return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
	}
}
