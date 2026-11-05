package com.projecthero.mod.client.omnitrix.alien;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienParts;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.2 (user: "Replace our stinkfly skin with this one please ... it got thin legs and double arms aswell, remove the tail from
 * the skin and make the tail model on our stinkfly skin look better please, it must be a replacement for the tail on the skin"):
 * the parts of Stinkfly that his second skin paints with see-through cut-outs, modelled instead (the skin keeps the head and
 * the thorax; its limb rectangles and painted tail are cleared by {@code scratchpad/ben10_v0171/gen_stinkfly_v2.js}, which also
 * paints {@code stinkfly_body.png} in the skin's own colours):
 * <ul>
 *   <li><b>the tail</b> -- a long insect abdomen out of the base of his back: seven white chitin plates (a lit dorsal ridge, grey
 *       flanks, a grey belly, a dark seam where each tucks under the next, a dark membrane ring between them), widening and then
 *       tapering into a glossy black three-part stinger. It is a small spring chain per player: it hangs curving down behind him
 *       standing, lifts and sways as he walks, hangs low under him hovering and streams out straight behind at speed, lags and
 *       whips back on every turn, and curls under his body for the stinger stab;</li>
 * </ul>
 * v0.17.2 second pass (user: "why did you do this with stinkfly, just use the skin please"): the modelled arms and legs are gone
 * -- the skin's own cut-out double arms and thin legs are drawn as its author made them -- and the tail is slimmer, black-banded,
 * ending in the show's curved black hook.
 */
final class StinkflyBody {
	static final ResourceLocation SHEET = ProjectHeroMod.id("textures/entity/omnitrix/stinkfly_body.png");

	// the sheet's rectangles (px on 64x64): u0, v0, u1, v1
	private static final float[] SMOOTH_TOP = { 0, 24, 8, 32 }, SMOOTH_SIDE = { 8, 24, 16, 32 }, SMOOTH_BELLY = { 16, 24, 24, 32 };
	private static final float[] SMOOTH_CAP = { 24, 24, 32, 32 }, BLADE = { 32, 24, 36, 32 }, BLADE_CAP = { 36, 24, 40, 28 };
	private static final float[] STRIPE = { 40, 24, 44, 28 };
	static final float[] MOUNT = { 0, 16, 8, 24 };

	// ---------------------------------------------------------------- the tail's shape (body-bone pixels)
	static final int N = 7;
	/** v0.17.2 third pass: the drawn tail -- its segments, their length (px), the first one's extra downward bend and every later
	 * one's upward sweep (degrees, on top of the spring chain's share); the blade's segments and bend. */
	private static final int SEGS = 13;
	private static final float SEG_LEN = 1.35f, ROOT_BEND = -32f, ARC_BEND = 7f;
	private static final int BLADE_N = 26;
	private static final float BLADE_BEND = 4.45f;
	/** Where it leaves his back: low on the body bone, just inside the back face. */
	private static final float ROOT_Y = 10.4f, ROOT_Z = 1.2f;
	/** How much of a body turn each segment keeps (the tip lags most). */
	private static final float[] LAG = { 0.25f, 0.38f, 0.5f, 0.6f, 0.7f, 0.78f, 0.85f };

	/** One player's tail: relative joint angles (degrees, pitch - = down) now and last tick, and their velocities. */
	private static final class Tail {
		final float[] pitch = new float[N], yaw = new float[N], vp = new float[N], vy = new float[N];
		final float[] prevP = new float[N], prevY = new float[N];
		boolean started;
		float lastBodyYaw;
	}

	private static final Map<AbstractClientPlayer, Tail> TAILS = new WeakHashMap<>();

	private StinkflyBody() {
	}

	/**
	 * Once a client tick: the tail's joints chase their targets.
	 *
	 * @param open   wings spread 0 (on the ground) .. 1 (flying)
	 * @param speed  0 hovering .. 1 full forward speed
	 * @param lean   the body's forward lean in flight (degrees)
	 * @param bank   the body's roll in flight (degrees)
	 * @param curl   0 .. 1 the stab's curl under the body
	 */
	static void tick(AbstractClientPlayer p, float open, float speed, float lean, float bank, float curl) {
		Tail t = TAILS.computeIfAbsent(p, k -> new Tail());
		float walk = Mth.clamp(p.walkAnimation.speed(), 0f, 1f) * (1f - open);
		float pos = p.walkAnimation.position();
		float age = p.tickCount;
		// the pose to chase: root pitch and the bend at every further joint
		float rootG = -9f + walk * 6f, stepG = -6.2f + walk * 2.5f; // standing: hanging in a curve behind; walking: lifted a little
		float rootH = -40f, stepH = -5f; // hovering: hanging low under him
		float rootS = -lean * 0.95f + 4f, stepS = -1.5f; // at speed: streaming out straight behind
		float rootF = Mth.lerp(speed, rootH, rootS), stepF = Mth.lerp(speed, stepH, stepS);
		float root = Mth.lerp(open, rootG, rootF), step = Mth.lerp(open, stepG, stepF);
		if (p.isCrouching() && open < 0.5f) {
			root -= 24f; // the body bone pitches forward when he crouches: keep the tail down behind him
		}
		root = Mth.lerp(curl, root, -78f);
		step = Mth.lerp(curl, step, -24f);
		// turning: each joint keeps part of the body's swing, so the tail lags and whips back
		float turn = Mth.clamp(Mth.wrapDegrees(p.yBodyRot - t.lastBodyYaw), -40f, 40f);
		t.lastBodyYaw = p.yBodyRot;
		if (!t.started) {
			turn = 0f;
		}
		for (int i = 0; i < N; i++) {
			float tp = i == 0 ? root : step;
			float sway = Mth.sin(age * 0.09f + i * 0.6f) * 2.2f + Mth.sin(pos * 0.6662f - i * 0.55f) * 5f * walk - bank * 0.25f * open
					+ Mth.sin(age * 0.5f - i * 0.7f) * 1.6f * open * (1f - speed); // a little buzz while hovering
			float ty = sway / N * 2.5f;
			if (!t.started) {
				t.pitch[i] = tp;
				t.yaw[i] = ty;
			}
			t.prevP[i] = t.pitch[i];
			t.prevY[i] = t.yaw[i];
			// the body's turn carries the tail with it: kick the joints the other way, the far ones harder
			t.vy[i] += turn * LAG[i] * 0.35f;
			t.vp[i] = (t.vp[i] + (tp - t.pitch[i]) * 0.28f) * 0.66f;
			t.vy[i] = (t.vy[i] + (ty - t.yaw[i]) * 0.22f) * 0.7f;
			t.pitch[i] = Mth.clamp(t.pitch[i] + t.vp[i], -90f, 40f);
			t.yaw[i] = Mth.clamp(t.yaw[i] + t.vy[i], -14f, 14f);
		}
		t.started = true;
	}

	/** The tail, in the body bone's space. */
	static void renderTail(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model,
			float partial) {
		Tail t = TAILS.get(p);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(SHEET));
		AlienParts.on(pose, model.body, () -> {
			pose.pushPose();
			pose.translate(0f, ROOT_Y / 16f, ROOT_Z / 16f);
			// the spring chain's joints spread over the drawn segments (each segment takes its joint's share, so the whole bend is kept)
			float[] sp = new float[N], sy = new float[N];
			for (int i = 0; i < N; i++) {
				sp[i] = t != null && t.started ? Mth.lerp(partial, t.prevP[i], t.pitch[i]) : (i == 0 ? -9f : -6.2f);
				sy[i] = t != null && t.started ? Mth.lerp(partial, t.prevY[i], t.yaw[i]) : 0f;
			}
			// v0.17.2 third pass (user: "keep looking at the reference till his tail looks exactly the same as in the picture"): one
			// smooth white tail of many thin segments -- leaving his abdomen downward, arcing down and then sweeping back and up like a
			// scorpion's held low, narrowing and going flat-sided toward the end -- then the big black crescent blade hooking up
			for (int s = 0; s < SEGS; s++) {
				int j = Math.min(N - 1, s * N / SEGS);
				float share = N / (float) SEGS;
				pose.mulPose(Axis.YP.rotationDegrees(sy[j] * share));
				pose.mulPose(Axis.XP.rotationDegrees(sp[j] * share + (s == 0 ? ROOT_BEND : ARC_BEND)));
				float f = s / (float) (SEGS - 1);
				float hw = Mth.lerp(f, 1.75f, 0.62f), hh = hw * Mth.lerp(f, 0.95f, 1.6f);
				segment(pose, vc, light, hw, -hh, hh, -0.25f, SEG_LEN + 0.25f, SMOOTH_TOP, SMOOTH_SIDE, SMOOTH_BELLY, SMOOTH_CAP);
				pose.translate(0f, 0f, SEG_LEN / 16f);
			}
			// v0.17.2 fourth pass (user: "make his tail look like how it looks in these images please" -- the show still and the model
			// sheet): the stinger is ONE smooth flat sickle, not a stack of boxes (that read as a jagged comb). In the tail tip's frame
			// (+z on along the tail, -y his top) it flares from the tail's end into a broad black crescent -- the convex outer edge
			// bulging down and back, the concave inner edge -- that sweeps on and curls UP to a sharp point, with the white stripe
			// running along the inner edge from its base, as on the model sheet.
			// v0.17.2 fifth pass (the model sheet): a two-tipped crescent like an axe head -- its lower tip below the tail's end, its upper
			// tip above, the convex edge bulging back, the concave edge (facing him) passing through the tail's end
			float[][] outer = { { -1.5f, 6.8f }, { 15.0f, -1.0f }, { 1.5f, -8.8f } }, inner = { { -1.5f, 6.8f }, { 3.2f, -1.0f }, { 1.5f, -8.8f } };
			sickle(pose, vc, light, outer, inner, 0.42f, BLADE);
			// the stripe: a thin white band hugging the inner edge from the base to near the tip (the exact first 88% of that edge,
			// and a copy of it moved ~0.9 px into the blade)
			float[][] band = { inner[0], { inner[1][0] + 2.0f, inner[1][1] }, inner[2] };
			sickle(pose, vc, light, band, inner, 0.5f, STRIPE);
			pose.popPose();
		});
	}

	private static final int SICKLE_SEG = 24;

	/**
	 * A smooth flat blade in the y-z plane (pixels), {@code half} thick each side of x = 0: between two quadratic edges {@code a} and
	 * {@code b} (three {z, y} control points each, sharing the tip), both faces and both edge walls, in one solid colour from the
	 * sheet rectangle {@code uv}. Thins a little toward the tip.
	 */
	private static void sickle(PoseStack pose, VertexConsumer vc, int light, float[][] a, float[][] b, float half, float[] uv) {
		PoseStack.Pose last = pose.last();
		float u = (uv[0] + uv[2]) * 0.5f / 64f, v = (uv[1] + uv[3]) * 0.5f / 64f;
		float s = 1f / 16f;
		for (int i = 0; i < SICKLE_SEG; i++) {
			float t0 = i / (float) SICKLE_SEG, t1 = (i + 1) / (float) SICKLE_SEG;
			float h0 = half * (1f - 0.55f * t0) * s, h1 = half * (1f - 0.55f * t1) * s;
			float[] a0 = quad(a, t0), a1 = quad(a, t1), b0 = quad(b, t0), b1 = quad(b, t1);
			// the two faces (+x and -x)
			for (int side = -1; side <= 1; side += 2) {
				face(vc, last, light, u, v, side * h0, a0, side * h0, b0, side * h1, b1, side * h1, a1, side, 0, 0);
			}
			// the edge walls
			for (float[][] e : new float[][][] { { a0, a1 }, { b0, b1 } }) {
				float dz = e[1][0] - e[0][0], dy = e[1][1] - e[0][1], l = Mth.sqrt(dz * dz + dy * dy);
				float ny = l < 1e-5f ? 0f : dz / l, nz = l < 1e-5f ? 0f : -dy / l;
				face(vc, last, light, u, v, -h0, e[0], h0, e[0], h1, e[1], -h1, e[1], 0, ny, nz);
			}
		}
	}

	/** A quad of four (x, {z, y} px) corners, drawn both ways round so it never culls. */
	private static void face(VertexConsumer vc, PoseStack.Pose last, int light, float u, float v, float x0, float[] p0, float x1, float[] p1, float x2,
			float[] p2, float x3, float[] p3, float nx, float ny, float nz) {
		float s = 1f / 16f;
		float[][] ps = { { x0, p0[1] * s, p0[0] * s }, { x1, p1[1] * s, p1[0] * s }, { x2, p2[1] * s, p2[0] * s }, { x3, p3[1] * s, p3[0] * s } };
		for (int k = 0; k < 8; k++) {
			float[] q = ps[k < 4 ? k : 7 - k];
			float sign = k < 4 ? 1f : -1f;
			vc.addVertex(last, q[0], q[1], q[2]).setColor(255, 255, 255, 255).setUv(u, v)
					.setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light)
					.setNormal(last, nx * sign, ny * sign, nz * sign);
		}
	}

	/** The exact part of a quadratic edge from its start to {@code t}, as its own three control points. */
	private static float[][] sub(float[][] p, float t) {
		return new float[][] { p[0], { Mth.lerp(t, p[0][0], p[1][0]), Mth.lerp(t, p[0][1], p[1][1]) }, quad(p, t) };
	}

	/** A point on a quadratic edge ({z, y} control points) at {@code t}. */
	private static float[] quad(float[][] p, float t) {
		float w = 1f - t;
		return new float[] { w * w * p[0][0] + 2 * w * t * p[1][0] + t * t * p[2][0], w * w * p[0][1] + 2 * w * t * p[1][1] + t * t * p[2][1] };
	}

	/**
	 * One box along +z (pixels): x in -hw..hw, y in -hh..hh (-y = his top), z in z0..z1; the top, flank and belly textures run
	 * root (v0) to tip (v1), the caps on both ends.
	 */
	private static void segment(PoseStack pose, VertexConsumer vc, int light, float hw, float hh, float z0, float z1, float[] top, float[] side,
			float[] belly, float[] cap) {
		segment(pose, vc, light, hw, -hh, hh, z0, z1, top, side, belly, cap);
	}

	/** As above, from y = {@code ya} (its top) to {@code yb}. */
	private static void segment(PoseStack pose, VertexConsumer vc, int light, float hw, float ya, float yb, float z0, float z1, float[] top,
			float[] side, float[] belly, float[] cap) {
		float s = 1f / 16f;
		float x0 = -hw * s, x1 = hw * s, y0 = ya * s, y1 = yb * s, a = z0 * s, b = z1 * s;
		PoseStack.Pose last = pose.last();
		int c = 0xFFFFFFFF;
		AlienParts.quad(vc, last, c, light, x0, y0, a, x1, y0, a, x1, y0, b, x0, y0, b, 0, -1, 0, top[0] / 64f, top[1] / 64f, top[2] / 64f, top[3] / 64f);
		AlienParts.quad(vc, last, c, light, x1, y1, a, x0, y1, a, x0, y1, b, x1, y1, b, 0, 1, 0, belly[0] / 64f, belly[1] / 64f, belly[2] / 64f,
				belly[3] / 64f);
		AlienParts.quad(vc, last, c, light, x0, y1, a, x0, y0, a, x0, y0, b, x0, y1, b, -1, 0, 0, side[0] / 64f, side[1] / 64f, side[2] / 64f,
				side[3] / 64f);
		AlienParts.quad(vc, last, c, light, x1, y0, a, x1, y1, a, x1, y1, b, x1, y0, b, 1, 0, 0, side[0] / 64f, side[1] / 64f, side[2] / 64f,
				side[3] / 64f);
		AlienParts.quad(vc, last, c, light, x0, y0, a, x1, y0, a, x1, y1, a, x0, y1, a, 0, 0, -1, cap[0] / 64f, cap[1] / 64f, cap[2] / 64f, cap[3] / 64f);
		AlienParts.quad(vc, last, c, light, x1, y0, b, x0, y0, b, x0, y1, b, x1, y1, b, 0, 0, 1, cap[0] / 64f, cap[1] / 64f, cap[2] / 64f, cap[3] / 64f);
	}

}
