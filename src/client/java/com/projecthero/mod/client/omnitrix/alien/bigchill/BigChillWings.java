package com.projecthero.mod.client.omnitrix.alien.bigchill;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.alien.BigChillClient;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.bigchill.BigChillKit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.2 (user: "uncloaked i think the badge position is perfect but give him his wings please, make the cloak unfurl from around
 * his body to make his wings since his wings are his cloak"): Big Chill's moth wings, unfurling out of the cloak.
 * <p>
 * v0.17.2 (user: "some of big chills wings are in his legs also the antenna should come from the back of his wings not his head
 * please fix this, also big chills wings dont flap really so let him just stay in the air please, also the top and bottom wings are
 * supposed to be connected since its 1 wing similar to stinkfly, please make it look better according to my notes and the reference
 * pics i sent earlier please"): ONE wing per side -- a single membrane mesh ({@link #NU} x {@link #NW} cells over the texture's
 * (u, w) rectangle) with a big upper lobe sweeping up and out to a point above his head and a smaller lower lobe down and out to a
 * point by his knees, joined at the root on his upper back (the notch where they meet is cut by the texture's alpha). Every vertex
 * comes from one function ({@link Shape#at}), so the lobes can tilt differently and the membrane still never tears:
 * <ul>
 *   <li><b>wrapped</b> -- the membrane curled round his back, side and front like a cloak, at the cloak's size;</li>
 *   <li><b>spread</b> -- the curl opened flat behind him, the lobes at full size, the upper lobe tipped back a little, the lower
 *       lobe tipped back further -- always further than his legs swing back ({@link #render} reads the legs' angles), and its
 *       inner edge passes his hips well outside them, so no pose puts a wing in his legs.</li>
 * </ul>
 * Each wing carries a thin curved spine ({@link #spine}) rising from its root behind his shoulders, along the upper lobe's inner
 * leading edge and on up past his head -- the "antennae" of the references (no more antennae on his head). The wings do not
 * flap: in flight he just hangs in the air, wings spread and still (a very slow breath); flying fast he leans forward a little
 * ({@link #bodyTilt}) with the wings swept back. {@code big_chill_wings.png}, scratchpad/recal_v0172/big_chill/gen_big_chill_wings.js.
 */
public final class BigChillWings {
	private BigChillWings() {
	}

	private static final ResourceLocation WINGS = ProjectHeroMod.id("textures/entity/omnitrix/big_chill_wings.png");
	/** Ticks to unfurl / fold: 0.6 s. */
	private static final float UNFURL_TICKS = 12f;
	/** The texture cell's rectangle in wing coordinates (px): u out from the root, w up from it. Keep in step with the generator. */
	private static final float U_MAX = 22f, W_TOP = 24f, W_BOT = -20f;
	/** The mesh: 2 px cells, a row boundary exactly on w = 0 (the line the two lobes tilt about). */
	private static final int NU = 11, NW = 22;
	/** The root on his upper back (px, body space: y down, +z behind him), a little off his spine. */
	private static final float ROOT_X = 0.8f, ROOT_Y = 2.0f, ROOT_Z_WRAP = 2.45f, ROOT_Z = 3.4f;
	/** The wrapped cloak: the curl's radius round his body (px), and the membrane's size and drop. */
	private static final float WRAP_RADIUS = 4.2f, WRAP_SU = 0.55f, WRAP_SW = 0.42f, WRAP_DROP = 5f;
	/** About his flying speed at a cruise (blocks per tick), for the lean and the sweep. */
	private static final float CRUISE = 0.75f;
	/** The spines take their colour from the membrane's dark root (texel 2,68 of the back cell: just inside the root, past the outline). */
	private static final float SW_U0 = 2.2f / 128f, SW_V0 = 68.2f / 128f, SW_U1 = 2.8f / 128f, SW_V1 = 68.8f / 128f;

	/**
	 * Per player {previous, now} of: the unfurl (0 wrapped .. 1 spread), the flight's strength (0 .. 1), the forward lean
	 * (degrees), the speed (0 hovering .. 1 fast) and the bank (degrees).
	 */
	private static final Map<AbstractClientPlayer, float[]> STATE = new WeakHashMap<>();
	private static final int UNF = 0, FLY = 2, LEAN = 4, SPEED = 6, BANK = 8;

	public static void prune(Minecraft mc) {
		if (mc.level == null) {
			STATE.clear();
			return;
		}
		STATE.keySet().removeIf(p -> p.isRemoved() || p.level() != mc.level || !(AlienClients.shownKit(p) instanceof BigChillClient));
	}

	private static boolean open(AbstractClientPlayer p) {
		return (Omnitrix.state(p).kitFlags & BigChillKit.F_OPEN) != 0;
	}

	private static boolean flying(AbstractClientPlayer p) {
		return (Omnitrix.state(p).kitFlags & BigChillKit.F_FLYING) != 0 && !p.onGround();
	}

	public static void tick(AbstractClientPlayer p) {
		float[] s = STATE.computeIfAbsent(p, k -> new float[10]);
		for (int i = 0; i < 10; i += 2) {
			s[i] = s[i + 1];
		}
		s[UNF + 1] = open(p) ? Math.min(1f, s[UNF + 1] + 1f / UNFURL_TICKS) : Math.max(0f, s[UNF + 1] - 1f / UNFURL_TICKS);
		boolean fly = flying(p);
		s[FLY + 1] += ((fly ? 1f : 0f) - s[FLY + 1]) * 0.15f;
		// the attitude from how far he actually moved (other players' velocity is not synced) -- forward leans him in a little,
		// backward tips him back a touch, a climb stands him up, a turn or a sideways drift banks him
		double dx = p.getX() - p.xo, dy = p.getY() - p.yo, dz = p.getZ() - p.zo;
		float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
		float fwd = (float) (-Mth.sin(yaw) * dx + Mth.cos(yaw) * dz);
		float side = (float) (-Mth.cos(yaw) * dx - Mth.sin(yaw) * dz);
		float horiz = (float) Math.sqrt(dx * dx + dz * dz);
		float lean = fly ? Mth.clamp(fwd / CRUISE * (fwd >= 0f ? 20f : 8f) - (float) dy * 14f, -8f, 24f) : 0f;
		float turn = Mth.wrapDegrees(p.yBodyRot - p.yBodyRotO);
		float bank = fly ? Mth.clamp(-turn * 1.2f - side / CRUISE * 10f, -12f, 12f) : 0f;
		s[LEAN + 1] += (lean - s[LEAN + 1]) * 0.12f;
		s[SPEED + 1] += ((fly ? Mth.clamp(horiz / CRUISE, 0f, 1f) : 0f) - s[SPEED + 1]) * 0.12f;
		s[BANK + 1] += (bank - s[BANK + 1]) * 0.12f;
	}

	private static float get(AbstractClientPlayer p, int what, float partial) {
		float[] s = STATE.get(p);
		return s == null ? 0f : Mth.lerp(partial, s[what], s[what + 1]);
	}

	/** The unfurl now, 0 wrapped .. 1 spread (eased). */
	public static float unfurl(AbstractClientPlayer p, float partial) {
		float[] s = STATE.get(p);
		return s == null ? (open(p) ? 1f : 0f) : smooth(Mth.lerp(partial, s[UNF], s[UNF + 1]));
	}

	/** How much he is flying (0 .. 1, eased). */
	public static float flight(AbstractClientPlayer p, float partial) {
		return get(p, FLY, partial);
	}

	/** How fast he is flying (0 hovering .. 1 cruising or faster, eased). */
	public static float speed(AbstractClientPlayer p, float partial) {
		return get(p, SPEED, partial);
	}

	/** The forward lean now (degrees, eased), scaled by how open the wings are. */
	public static float lean(AbstractClientPlayer p, float partial) {
		return get(p, LEAN, partial) * unfurl(p, partial);
	}

	/** The hover's slow breath (radians of phase): everything that sways while he hangs in the air follows sin(breath). */
	public static float breath(float age) {
		return age * 0.055f;
	}

	/** Whether the uncloaked skin (and the chest badge) shows: once the cloak has peeled a third of the way off him. */
	public static boolean uncloaked(AbstractClientPlayer p) {
		float[] s = STATE.get(p);
		return s == null ? open(p) : s[UNF + 1] > 0.34f;
	}

	/**
	 * The whole-body attitude for {@code StinkflyFlightPoseMixin} -- {pitch forward, roll} in degrees and the hover drift (blocks)
	 * -- or null when he is not a flying Big Chill. Upright while he hovers, just hanging in the air (a faint slow drift, no
	 * wing-beat bob); a small forward lean into his speed.
	 */
	public static float[] bodyTilt(AbstractClientPlayer p, float partial) {
		if (!(AlienClients.shownKit(p) instanceof BigChillClient) || (Omnitrix.state(p).kitFlags & BigChillKit.F_DIVING) != 0) {
			return null;
		}
		float fly = flight(p, partial) * unfurl(p, partial);
		if (fly < 0.02f) {
			return null;
		}
		float age = p.tickCount + partial;
		float drift = Mth.sin(breath(age)) * 0.025f * (1f - speed(p, partial)) * fly;
		return new float[] { get(p, LEAN, partial) * fly, get(p, BANK, partial) * fly, drift };
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	/**
	 * One wing's shape this frame: (u, w) on the membrane -> a point in body space (px). The membrane leaves the root heading out
	 * along {@code theta0} (negative = swept back) and curls forward round his body with curvature {@code kappa} (0 = flat); w runs
	 * up, scaled by {@code sw}; the upper lobe (w > 0) leans back by {@code tanUp} per px, the lower lobe (w < 0) by {@code tanLow}.
	 */
	private static final class Shape {
		int side;
		float x0, y0, z0, kappa, theta0, su, sw, tanUp, tanLow;

		void at(float u, float w, float[] out) {
			float us = u * su;
			float ax, az; // the integrals of cos / sin of the heading along the curl
			if (Math.abs(kappa) < 1.0e-4f) {
				ax = us * Mth.cos(theta0);
				az = us * Mth.sin(theta0);
			} else {
				float t1 = theta0 + kappa * us;
				ax = (Mth.sin(t1) - Mth.sin(theta0)) / kappa;
				az = (Mth.cos(theta0) - Mth.cos(t1)) / kappa;
			}
			float ws = w * sw;
			out[0] = x0 + side * ax;
			out[1] = y0 - ws;
			out[2] = z0 - az + (ws > 0f ? ws * tanUp : -ws * tanLow);
		}
	}

	public static void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model,
			float partial, float age, int alpha) {
		float[] s = STATE.get(p);
		if (s == null || (s[UNF] <= 0f && s[UNF + 1] <= 0f && !open(p))) {
			return; // wrapped and closed: the cloaked skin is the cloak
		}
		float e = smooth(Mth.lerp(partial, s[UNF], s[UNF + 1]));
		float sp = Mth.lerp(partial, s[SPEED], s[SPEED + 1]);
		// first the cloak uncurls off his body, then the wings rise and grow into the spread
		float uncurl = smooth(e * 1.5f);
		float rise = smooth((e - 0.25f) / 0.75f);
		float br = Mth.sin(breath(age));
		// the legs: however far back they swing (relative to his body), the lower lobe leans back further, with room to spare
		float legBack = Mth.clamp(Math.max(model.rightLeg.xRot, model.leftLeg.xRot) - model.body.xRot, 0f, 1.3f);
		float tanLow = Math.max(0.5f + sp * 0.2f, (float) Math.tan(legBack) + 0.35f);
		Shape sh = new Shape();
		sh.x0 = ROOT_X;
		sh.y0 = ROOT_Y + WRAP_DROP * (1f - rise);
		sh.z0 = Mth.lerp(uncurl, ROOT_Z_WRAP, ROOT_Z);
		sh.kappa = (1f - uncurl) / WRAP_RADIUS;
		// spread: swept back a little behind him (more at speed), a very slow breath while he hangs there
		sh.theta0 = -rise * (0.26f + sp * 0.5f + br * 0.025f);
		sh.su = Mth.lerp(rise, WRAP_SU, 1f);
		sh.sw = Mth.lerp(rise, WRAP_SW, 1f);
		sh.tanUp = rise * (0.27f + sp * 0.22f + br * 0.02f);
		sh.tanLow = rise * tanLow;
		int argb = (Mth.clamp(alpha, 0, 255) << 24) | 0xFFFFFF;
		VertexConsumer vc = buffers.getBuffer(alpha < 255 ? RenderType.entityTranslucent(WINGS) : RenderType.entityCutoutNoCull(WINGS));
		AlienParts.on(pose, model.body, () -> {
			PoseStack.Pose last = pose.last();
			for (int side = -1; side <= 1; side += 2) {
				sh.side = side;
				membrane(last, vc, light, argb, sh);
				spine(last, vc, light, argb, sh, rise);
			}
		});
	}

	/** The membrane: a grid over the texture cell, both faces (the back's cell behind him, the front's cell toward the viewer). */
	private static void membrane(PoseStack.Pose last, VertexConsumer vc, int light, int argb, Shape sh) {
		float[][] g = new float[(NU + 1) * (NW + 1)][3];
		for (int j = 0; j <= NW; j++) {
			float w = W_TOP - j * (W_TOP - W_BOT) / NW;
			for (int i = 0; i <= NU; i++) {
				sh.at(i * U_MAX / NU, w, g[j * (NU + 1) + i]);
			}
		}
		float[] n = new float[3];
		// cross(b - a, d - a) of the (i, j) cell points along the back for the +x wing and along the front for the -x wing
		boolean abcdIsBack = sh.side > 0;
		for (int j = 0; j < NW; j++) {
			for (int i = 0; i < NU; i++) {
				float[] a = g[j * (NU + 1) + i], b = g[j * (NU + 1) + i + 1], c = g[(j + 1) * (NU + 1) + i + 1], d = g[(j + 1) * (NU + 1) + i];
				// the cell's normal from its diagonals
				float px = c[0] - a[0], py = c[1] - a[1], pz = c[2] - a[2];
				float qx = d[0] - b[0], qy = d[1] - b[1], qz = d[2] - b[2];
				n[0] = py * qz - pz * qy;
				n[1] = pz * qx - px * qz;
				n[2] = px * qy - py * qx;
				float len = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
				if (len < 1.0e-6f) {
					continue;
				}
				n[0] /= len;
				n[1] /= len;
				n[2] /= len;
				float u0 = i / (float) NU * 0.5f, u1 = (i + 1) / (float) NU * 0.5f, v0 = j / (float) NW, v1 = (j + 1) / (float) NW;
				float fwd = abcdIsBack ? 0f : 0.5f, rev = abcdIsBack ? 0.5f : 0f;
				float off = 0.04f;
				// (a, b, c, d) faces +n; (a, d, c, b) faces -n
				vertex(vc, last, a, n, off, u0 + fwd, v0, argb, light);
				vertex(vc, last, b, n, off, u1 + fwd, v0, argb, light);
				vertex(vc, last, c, n, off, u1 + fwd, v1, argb, light);
				vertex(vc, last, d, n, off, u0 + fwd, v1, argb, light);
				n[0] = -n[0];
				n[1] = -n[1];
				n[2] = -n[2];
				vertex(vc, last, a, n, off, u0 + rev, v0, argb, light);
				vertex(vc, last, d, n, off, u0 + rev, v1, argb, light);
				vertex(vc, last, c, n, off, u1 + rev, v1, argb, light);
				vertex(vc, last, b, n, off, u1 + rev, v0, argb, light);
			}
		}
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose last, float[] at, float[] n, float off, float u, float v, int argb, int light) {
		int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
		vc.addVertex(last, (at[0] + n[0] * off) / 16f, (at[1] + n[1] * off) / 16f, (at[2] + n[2] * off) / 16f).setColor(r, g, b, a).setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, n[0], n[1], n[2]);
	}

	/** The spine's path on the membrane: along the upper lobe's inner leading edge from the root, then free up past his head. */
	private static final int SPINE_SEGS = 12;

	/**
	 * The wing's spine (the references' "antennae"): a thin tapering rod on the same shape as the membrane, rising from the root
	 * behind his shoulders along the upper lobe's inner edge and on up past his head, curling out a little at the tip.
	 */
	private static void spine(PoseStack.Pose last, VertexConsumer vc, int light, int argb, Shape sh, float rise) {
		float[] a = new float[3], b = new float[3];
		float top = Mth.lerp(rise, 15f, 25.5f);
		sh.at(0.6f, 0f, a);
		for (int k = 1; k <= SPINE_SEGS; k++) {
			float t = k / (float) SPINE_SEGS;
			sh.at(0.6f + 2.2f * t + 3.0f * t * t * t, top * t, b);
			float r0 = Mth.lerp((k - 1) / (float) SPINE_SEGS, 0.5f, 0.2f), r1 = Mth.lerp(t, 0.5f, 0.2f);
			rod(last, vc, light, argb, a, b, r0, r1);
			a[0] = b[0];
			a[1] = b[1];
			a[2] = b[2];
		}
	}

	/** A square rod from a (half-width r0) to b (half-width r1), px, in the spine swatch's colour. */
	private static void rod(PoseStack.Pose last, VertexConsumer vc, int light, int argb, float[] a, float[] b, float r0, float r1) {
		float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
		float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1.0e-5f) {
			return;
		}
		dx /= len;
		dy /= len;
		dz /= len;
		// e1 = d x z (sideways), e2 = d x e1
		float e1x = dy, e1y = -dx, e1z = 0f;
		float l1 = (float) Math.sqrt(e1x * e1x + e1y * e1y);
		if (l1 < 1.0e-4f) {
			e1x = 1f;
			e1y = 0f;
		} else {
			e1x /= l1;
			e1y /= l1;
		}
		float e2x = dy * e1z - dz * e1y, e2y = dz * e1x - dx * e1z, e2z = dx * e1y - dy * e1x;
		float[][] dirs = { { e1x, e1y, e1z }, { e2x, e2y, e2z }, { -e1x, -e1y, -e1z }, { -e2x, -e2y, -e2z } };
		float[] p = new float[3];
		for (int f = 0; f < 4; f++) {
			float[] m = dirs[f], q = dirs[(f + 1) % 4];
			// the face between corner directions m and q, its normal half way
			float[] n = { m[0] + q[0], m[1] + q[1], m[2] + q[2] };
			float nl = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
			n[0] /= nl;
			n[1] /= nl;
			n[2] /= nl;
			corner(p, a, m, r0);
			vertex(vc, last, p, n, 0f, SW_U0, SW_V0, argb, light);
			corner(p, a, q, r0);
			vertex(vc, last, p, n, 0f, SW_U1, SW_V0, argb, light);
			corner(p, b, q, r1);
			vertex(vc, last, p, n, 0f, SW_U1, SW_V1, argb, light);
			corner(p, b, m, r1);
			vertex(vc, last, p, n, 0f, SW_U0, SW_V1, argb, light);
		}
	}

	private static void corner(float[] out, float[] at, float[] dir, float r) {
		out[0] = at[0] + dir[0] * r;
		out[1] = at[1] + dir[1] * r;
		out[2] = at[2] + dir[2] * r;
	}
}
