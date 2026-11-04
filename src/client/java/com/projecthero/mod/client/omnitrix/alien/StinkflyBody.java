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
			// the blade: flat, broad on its outer (lower) edge and drawn to a point, curling up and back, the white stripe on its inner
			// (upper) edge
			for (int k = 0; k < BLADE_N; k++) {
				float f = k / (float) (BLADE_N - 1);
				float belly = Mth.sin(Mth.PI * (float) Math.pow(f, 0.7)) * (1f - 0.25f * f);
				float top = -(0.9f - 0.55f * f), bottom = 0.5f + 5.0f * belly - 0.3f * f;
				float len = Mth.lerp(f, 0.74f, 0.5f);
				segment(pose, vc, light, 0.42f - 0.2f * f, top, bottom, -0.12f, len + 0.12f, BLADE, BLADE, BLADE, BLADE_CAP);
				if (k < BLADE_N - 4) { // the white inner edge, broad where the blade leaves the tail and thinning toward the tip
					segment(pose, vc, light, 0.5f - 0.2f * f, top - 0.04f, top + Mth.lerp(f, 2.2f, 0.7f), -0.12f, len + 0.12f, STRIPE, STRIPE, STRIPE, STRIPE);
				}
				pose.translate(0f, 0f, len / 16f);
				pose.mulPose(Axis.XP.rotationDegrees(BLADE_BEND));
			}
			pose.popPose();
		});
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
