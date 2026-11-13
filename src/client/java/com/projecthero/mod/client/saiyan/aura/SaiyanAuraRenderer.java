package com.projecthero.mod.client.saiyan.aura;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.saiyan.SaiyanFx;
import com.projecthero.mod.saiyan.SaiyanForm;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3: the Saiyan aura, drawn from the player render layer in the model's space (so never round the camera in first
 * person). Dragon Ball style: a volumetric teardrop of light round the body whose edge bristles with sharp flickering thorns.
 * <ul>
 * <li><b>Shells</b> (v3: the first version's layered, 3D-feeling teardrop with the second version's thorns): four sheets
 * that turn to face the viewer, nested at different depths and sizes -- a big one bowed away BEHIND the body, one through the
 * body's centre, a tighter white-hot core, and a faint one bowed out IN FRONT that washes light over the body. Their thorns run
 * out of step, so the edge reads as thorns behind thorns rather than one cut-out. The sheets bow round the body (deepest at
 * the middle) and lean a little toward a camera above or below, so the flame keeps its volume from every angle.</li>
 * <li><b>Body line</b> (the user's sketch and the reference clip in docs/reference/saiyan_aura/): narrow at the feet, widest
 * round the chest, curving in to a crown above the head under a tall centre spike.</li>
 * <li><b>Thorns</b>: splayed almost flat at the feet, up-and-out round the body, straight up near the top. They travel UP the
 * sides (new ones grow out of the feet, the top ones fold into the crown) and flicker -- each jumps to a new length and angle
 * every couple of ticks, like a hand-drawn aura.</li>
 * <li><b>Light</b>: each sheet is soft just outside its edge, brightest along a rim band just inside, then a thin glowing fill
 * broken into flowing vertical streaks that stream upward. An alpha-blended wash of the aura colour (keeps the colour against
 * a bright sky) under additive glow, balanced by how much daylight he stands in. Thin flame strands rise inside.</li>
 * <li>While charging (V): taller, wider, faster and brighter with a slow pulse, fast rising streaks, embers, a glow on the
 * ground (the floating debris is {@link SaiyanAuraParticles}). A Base charge is the same flame in white.</li>
 * <li>The transformation ({@link SaiyanFx#transformingTicks}): a flash with star rays, a pillar of light, a shockwave ring.</li>
 * </ul>
 * <li>Kaioken (v0.17.3 revamp): the flame turns crimson -- in Base a steady red aura, in any form the palette eased to red.</li>
 * Only Base and Super Saiyan are in play.
 * Everything is procedural from (time, a per-player seed): no per-frame allocation. Model space: block units, +y DOWN, the feet
 * at y = {@link #FEET}, the top of the head at y = -0.5, scaled by the player model's 15/16.
 */
public final class SaiyanAuraRenderer {
	static final float FEET = 1.501f;
	private static final float HEAD_TOP = -0.5f;
	private static final float MODEL_SCALE = 0.9375f;
	/** Feet to the top of the head, in model units. */
	private static final float HEAD_H = FEET - HEAD_TOP;
	/** Where the shells pivot when they lean toward a camera above or below (about the chest). */
	private static final float PIVOT_Y = 0.3f;

	/** Entities are being drawn for the world (not a GUI preview such as the inventory's player). Set by SaiyanVisualsClient. */
	public static boolean inWorld;

	// per-frame scratch (render thread only)
	private static final Matrix4f INV = new Matrix4f();
	/** The camera in model space. */
	private static float camX, camY, camZ;
	/** The viewer-facing plane: F = unit horizontal toward the camera, R = its right. */
	private static float fx, fz, rx, rz;
	/** The shells' lean toward the camera's height (cos, sin). */
	private static float tiltC = 1f, tiltS = 0f;

	private SaiyanAuraRenderer() {
	}

	// ---------------------------------------------------------------- smoothed charge (client tick)

	/** entity id -> {previous, current, ground distance (blocks, -1 none), stamp, daylight 0..1 (eased)}. */
	private static final Map<Integer, float[]> LEVEL = new HashMap<>();
	private static int stamp;

	/**
	 * Client tick: eases each Saiyan's charge level in and out so the aura swells and settles instead of popping, and eases
	 * how much open daylight he stands in ({@code daylight}: sky darkening x sky light at his head).
	 */
	static void tickLevel(Player p, float groundDist, float daylight) {
		float target = SaiyanFx.charging(p) ? 1f : 0f;
		float kk = SaiyanFx.kaioken(p) ? 1f : 0f;
		float[] s = LEVEL.get(p.getId());
		if (s == null) {
			s = new float[] { target, target, groundDist, stamp, daylight, kk, kk };
			LEVEL.put(p.getId(), s);
		}
		s[0] = s[1];
		s[1] += (target - s[1]) * (target > s[1] ? 0.28f : 0.14f);
		s[2] = groundDist;
		s[3] = stamp;
		s[4] += (daylight - s[4]) * 0.1f;
		// Kaioken's red eases in fast and fades out slower
		s[5] = s[6];
		s[6] += (kk - s[6]) * (kk > s[6] ? 0.3f : 0.12f);
	}

	/** End of a client tick: forgets players that stopped being Saiyans or left. */
	static void endTick() {
		for (Iterator<float[]> it = LEVEL.values().iterator(); it.hasNext();) {
			if (it.next()[3] != stamp) {
				it.remove();
			}
		}
		stamp = (stamp + 1) & 0xFFFF;
	}

	public static void clear() {
		LEVEL.clear();
	}

	/** Still easing out (a Base charge just let go). */
	static boolean tracked(int entityId) {
		float[] s = LEVEL.get(entityId);
		return s != null && (s[1] > 0.02f || s[6] > 0.02f);
	}


	// ---------------------------------------------------------------- colours

	/** {wash (alpha-blended, saturated), glow (additive, = the form's aura colour), core (white-hot)}. */
	private static final int[] BASE_PAL = { 0xB8CCF0, 0xE6EEFF, 0xFFFFFF };
	private static final int[] SSJ_PAL = { 0xFFC400, rgb(SaiyanForm.SSJ.auraColor), 0xFFF6D0 };
	/** v0.17.3 Ultra Instinct: a cool blue-grey wash, a silver glow, a white core. */
	private static final int[] UI_PAL = { 0x8C9CB8, rgb(SaiyanForm.ULTRA.auraColor), 0xFFFFFF };
	/** Kaioken: a crimson wash, a red glow, a hot pink-white core. */
	private static final int[] KAIOKEN_PAL = { 0xE0101A, 0xFF2A2A, 0xFFD0C8 };
	/** Scratch palette for the eased mix (render thread only). */
	private static final int[] MIX_PAL = new int[3];

	private static int rgb(int argb) {
		return argb & 0xFFFFFF;
	}

	// ---------------------------------------------------------------- entry

	public static void render(PoseStack pose, MultiBufferSource buffers, Player player, float partial, float ageInTicks) {
		if (!inWorld) {
			return;
		}
		SaiyanForm form = SaiyanFx.form(player).playableOrSsj();
		boolean base = form == SaiyanForm.BASE;
		int tt = SaiyanFx.transformingTicks(player);
		boolean burst = tt < 30 && !base; // powering down to Base has no flash
		float[] lv = LEVEL.get(player.getId());
		float charge = lv != null ? Mth.lerp(partial, lv[0], lv[1]) : (SaiyanFx.charging(player) ? 1f : 0f);
		float groundDist = lv != null ? lv[2] : (player.onGround() ? 0f : -1f);
		float daylight = lv != null ? lv[4] : 1f;
		float kaioken = lv != null ? Mth.lerp(partial, lv[5], lv[6]) : (SaiyanFx.kaioken(player) ? 1f : 0f);
		if (base && charge < 0.02f && kaioken < 0.02f) {
			return;
		}
		Matrix4f m = pose.last().pose();
		setupCamera(m);
		float time = ageInTicks; // already includes the partial tick
		long seed = player.getId() * 0x9E3779B97F4A7C15L;
		float bt = burst ? tt + partial : 999f;
		float flare = burst ? Mth.clamp(1f - bt / 26f, 0f, 1f) : 0f;
		int[] pal = base ? BASE_PAL : form == SaiyanForm.ULTRA ? UI_PAL : SSJ_PAL;
		if (kaioken > 0.01f) {
			for (int i = 0; i < 3; i++) {
				MIX_PAL[i] = mix(pal[i], KAIOKEN_PAL[i], Math.min(1f, kaioken));
			}
			pal = MIX_PAL;
		}

		// power: 0.42 idle in a form (Kaioken's flame in Base: 0.5), 1 charging; a fresh transformation flares it up
		float idle = base ? 0.5f * kaioken : 0.42f;
		float power = Mth.lerp(charge, idle, 1f);
		power *= 1f + 0.05f * kaioken * Mth.sin(ageInTicks * 0.9f); // Kaioken's restless flicker
		power = Math.max(power, flare * 1.15f);
		power *= 1f + 0.07f * charge * Mth.sin(time * 0.45f); // the charge's gentle pulse
		float speed = 1f + 0.8f * charge;

		// the flame's size, in model units: half width at the feet and at the widest (the chest), the body line's height (feet
		// to where the sides meet; the centre spike rises above it) and the thorns' length
		float footW = 0.45f + 0.12f * power;
		float halfW = 0.78f + 0.3f * power;
		float height = HEAD_H + 0.5f + 0.85f * power;
		float jut = 0.4f + 0.16f * power;
		float top = FEET - height;

		int rimCol = mix(pal[1], pal[2], 0.25f);
		int innerCol = mix(pal[1], pal[2], 0.15f);

		// ---- pass 1: the alpha-blended wash (colour that survives daylight): the back shell and the centre shell
		VertexConsumer tint = buffers.getBuffer(SaiyanAuraRenderType.TINT);
		alphaMul = 1f + 0.5f * daylight;
		shell(tint, m, time, seed + 101, speed, footW * 1.06f, halfW * 1.07f, height * 1.04f, jut * 1.1f, 0.37f, -0.3f, -0.35f, pal[0], pal[0],
				0.28f + 0.12f * power, 0.24f + 0.08f * power, 0.06f + 0.03f * power, 0.5f, 0f);
		shell(tint, m, time, seed, speed, footW, halfW, height, jut, 0f, 0f, 0f, pal[0], pal[0], 0.42f + 0.15f * power, 0.36f + 0.1f * power,
				0.1f + 0.05f * power, 0.55f, 0f);

		// ---- pass 2: the additive glow
		VertexConsumer glow = buffers.getBuffer(SaiyanAuraRenderType.GLOW);
		// by day additive light washes out to white against the sky: less of it, and more of the coloured wash under it
		alphaMul = 1f - 0.5f * daylight;
		// the big shell behind the body, bowed away from the viewer
		shell(glow, m, time, seed + 101, speed, footW * 1.06f, halfW * 1.07f, height * 1.04f, jut * 1.1f, 0.37f, -0.3f, -0.35f, pal[1], rimCol,
				0.36f + 0.2f * power, 0.2f + 0.12f * power, 0.03f + 0.03f * power, 0.6f, 0.5f);
		// the white-hot core: tighter and lower, its thorns short and quick
		shell(glow, m, time * 1.4f, seed + 5, speed, footW * 0.8f, halfW * 0.74f, height * 0.72f, jut * 0.6f, 0.61f, 0.04f, 0f, pal[2], pal[2],
				0.2f + 0.2f * power, 0.13f + 0.15f * power, 0.04f + 0.05f * power, 0.5f, 0.3f);
		// a faint shell bowed out in front, so the body stands inside the light
		shell(glow, m, time, seed + 3, speed, footW * 0.93f, halfW * 0.93f, height * 0.96f, jut * 0.93f, 0.71f, 0.3f, 0.3f, innerCol, rimCol,
				0.1f + 0.1f * power, 0.06f + 0.06f * power, 0.02f + 0.025f * power, 0.8f, 0.5f);
		// the main shell through the body's centre: the brightest thorns
		shell(glow, m, time, seed, speed, footW, halfW, height, jut, 0f, 0f, 0f, innerCol, rimCol, 0.6f + 0.3f * power, 0.3f + 0.15f * power,
				0.045f + 0.05f * power, 0.55f, 0.3f);
		// thin flame strands rising inside (behind and in front of the body), and a few loose tongues off the crown
		licks(glow, m, time, seed + 17, Math.round(5 + 8 * power), 0.0f, 0.45f, 0.15f, 0.75f, 0.95f, pal[1], innerCol,
				0.16f + 0.16f * power, speed * 1.3f, 0.03f, 0.7f);
		licks(glow, m, time, seed + 23, Math.round(2 + 5 * power), 0.0f, 0.4f, 0.1f, 0.6f, 0.85f, pal[1], innerCol, 0.08f + 0.1f * power,
				speed * 1.3f, 0.4f, 0.55f);
		licks(glow, m, time, seed + 11, Math.round(1 + 3 * power), 0.7f, 0.92f, 0.5f, 0.9f, 1.25f, rimCol, pal[1], 0.18f + 0.18f * power,
				speed, 0.05f, 0.7f);
		embers(glow, m, time, seed + 31, Math.round(3 + 12 * charge + 6 * flare), halfW, top, pal[2], rimCol, 0.8f);
		alphaMul = 1f - 0.25f * daylight; // the streaks, ground glow and burst stay strong by day
		if (charge > 0.02f) {
			streaks(glow, m, time, seed + 41, Math.round(12 * charge), halfW, top, pal[2], 0.55f * charge);
		}
		if (groundDist >= 0f && groundDist < 3f) {
			float gy = FEET + groundDist / MODEL_SCALE - 0.03f;
			float fade = 1f - groundDist / 3f;
			float ga = (0.08f + 0.32f * charge + 0.35f * flare) * fade;
			if (ga > 0.01f) {
				groundGlow(glow, m, time, gy, pal[1], pal[2], ga, (0.9f + 0.7f * charge) * (1f + 0.04f * Mth.sin(time * 0.9f)));
			}
		}
		if (burst) {
			burst(glow, m, bt, pal, groundDist >= 0f && groundDist < 4f ? FEET + groundDist / MODEL_SCALE - 0.04f : Float.NaN, seed);
		}
	}

	/** Where the camera is in model space, the viewer-facing plane, and how far the shells lean toward the camera's height. */
	private static void setupCamera(Matrix4f m) {
		INV.set(m).invert();
		float w = INV.m33();
		if (Math.abs(w) < 1e-6f) {
			w = 1f;
		}
		camX = INV.m30() / w;
		camY = INV.m31() / w;
		camZ = INV.m32() / w;
		float len = Mth.sqrt(camX * camX + camZ * camZ);
		if (len < 1e-4f) {
			fx = 0f;
			fz = 1f;
		} else {
			fx = camX / len;
			fz = camZ / len;
		}
		rx = fz;
		rz = -fx;
		// a camera above (model y is down) tips the shells' tops away from it, one below tips them toward it: part of the way,
		// so the flame keeps its body seen from above or below instead of thinning to an edge
		float pitch = (float) Math.atan2(PIVOT_Y - camY, Math.max(len, 1e-3f));
		float tilt = Mth.clamp(pitch * 0.4f, -0.5f, 0.5f);
		tiltC = Mth.cos(tilt);
		tiltS = Mth.sin(tilt);
	}

	// ---------------------------------------------------------------- the shells

	/** Thorns up each side (from the feet to the tip). */
	private static final float LOBES = 7f;
	private static final int MAX_PTS = 192;
	/** The current shell's body (render thread only): half width at the feet, at the widest, and its height. */
	private static float fFoot, fHalf, fHeight;
	/** The outline, in the viewer-facing plane: x (right), h (height above the feet, model units). Scratch, render thread only. */
	private static final float[] OX = new float[MAX_PTS], OH = new float[MAX_PTS];
	private static final float[] SX = new float[MAX_PTS / 2], SH = new float[MAX_PTS / 2];
	private static int outN;
	/**
	 * The rows drawn from the outline inward to the shell's spine: just outside (soft), the line, the bright rim band, the rim's
	 * inner edge, then the fill in steps (fine enough for the flowing streaks).
	 */
	private static final int ROWS = 9;
	private static final float[] FILL = { 0.2f, 0.42f, 0.64f, 0.84f, 1f };
	private static final float[][] RX = new float[ROWS][MAX_PTS], RH = new float[ROWS][MAX_PTS], RA = new float[ROWS][MAX_PTS],
			RD = new float[ROWS][MAX_PTS];
	private static final int[][] RC = new int[ROWS][MAX_PTS];

	/**
	 * The smooth body under the thorns: its half width at height h. An egg / bonfire: {@link #fFoot} at the feet, swelling to
	 * {@link #fHalf} round the chest (45% up), then curving in to a point at the top.
	 */
	private static float body(float h) {
		float u = h / fHeight;
		if (u <= 0f) {
			return fFoot;
		}
		if (u >= 1f) {
			return 0f;
		}
		if (u < 0.45f) {
			float s = u / 0.45f;
			return fFoot + (fHalf - fFoot) * s * s * (3f - 2f * s);
		}
		float t = (u - 0.45f) / 0.55f;
		return fHalf * (float) Math.pow(1f - t, 1.1f) * (1f + 1.1f * t);
	}

	private static float smooth(float a, float b, float x) {
		float t = Mth.clamp((x - a) / (b - a), 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	/**
	 * One thorned shell facing the viewer, {@code depth} toward the camera from the body's centre and bowed by {@code bow}
	 * (toward the camera if positive) most at its middle, so it wraps round the body. Its outline (see {@link #outline}) at
	 * alpha {@code aEdge} in {@code colRim}, soft just outside, the brightest band at {@code aRim} just inside, then a fill of
	 * {@code aIn} in {@code colIn} broken into flowing streaks ({@code streak} 0 smooth .. 1 strong). {@code phase} shifts its
	 * thorns along the sides (in thorns) so the shells never line up. {@code tipFade}: how much it dims toward the tip
	 * (additive layers pile up there and wash out to white).
	 */
	private static void shell(VertexConsumer vc, Matrix4f m, float time, long seed, float speed, float foot, float half, float height,
			float jut, float phase, float depth, float bow, int colIn, int colRim, float aEdge, float aRim, float aIn, float streak,
			float tipFade) {
		fFoot = foot;
		fHalf = half;
		fHeight = height;
		float h0 = hash(seed) * 6.283f, h1 = hash(seed + 1) * 6.283f;
		float ts = time * speed;
		// the whole flame leans a little side to side
		float lean = 0.06f * Mth.sin(ts * 0.11f + h1);
		outline(time, seed, speed, jut, lean, phase);
		int n = outN;
		float fan = fHeight * 0.55f;
		float top = fHeight + jut * 1.6f;
		int rimIn = mix(colRim, colIn, 0.5f);
		for (int i = 0; i < n; i++) {
			float px = OX[i], ph = OH[i];
			float sh = Mth.clamp(ph, 0f, fan);
			float sx = lean * (sh / fHeight) * (sh / fHeight);
			float dx = px - sx, dh = ph - sh;
			float dist = Mth.sqrt(dx * dx + dh * dh);
			if (dist < 1e-4f) {
				dx = 0f;
				dh = 1f;
				dist = 1e-4f;
			}
			float ux = dx / dist, uh = dh / dist;
			// the bottom fades a little (the feet sit in it); light runs up the outline
			float bottom = 0.35f + 0.65f * smooth(-0.1f, 0.4f, ph);
			float tip = 1f - tipFade * smooth(0.6f, 1f, ph / top);
			float k = bottom * tip;
			float run = 0.8f + 0.2f * Mth.sin(ph * 7f - ts * 0.9f + h0);
			float f2 = Math.min(0.3f, 0.07f / dist), f3 = Math.min(0.42f, 0.18f / dist);
			put(0, i, px + ux * 0.1f, ph + uh * 0.1f, colRim, 0f, depth, bow, lean, 0f, ts, h0, h1);
			put(1, i, px, ph, colRim, aEdge * k * run, depth, bow, lean, 0f, ts, h0, h1);
			put(2, i, Mth.lerp(f2, px, sx), Mth.lerp(f2, ph, sh), colRim, aRim * k, depth, bow, lean, streak * 0.45f, ts, h0, h1);
			put(3, i, Mth.lerp(f3, px, sx), Mth.lerp(f3, ph, sh), rimIn, Mth.lerp(0.6f, aRim, aIn) * k, depth, bow, lean, streak * 0.7f, ts, h0,
					h1);
			for (int r = 0; r < FILL.length; r++) {
				float f = Mth.lerp(FILL[r], f3, 1f);
				put(4 + r, i, Mth.lerp(f, px, sx), Mth.lerp(f, ph, sh), colIn, aIn * k, depth, bow, lean, streak, ts, h0, h1);
			}
		}
		for (int i = 0; i < n - 1; i++) {
			for (int r = 0; r < ROWS - 1; r++) {
				pt(vc, m, RX[r][i], RH[r][i], RD[r][i], RC[r][i], RA[r][i]);
				pt(vc, m, RX[r][i + 1], RH[r][i + 1], RD[r][i + 1], RC[r][i + 1], RA[r][i + 1]);
				pt(vc, m, RX[r + 1][i + 1], RH[r + 1][i + 1], RD[r + 1][i + 1], RC[r + 1][i + 1], RA[r + 1][i + 1]);
				pt(vc, m, RX[r + 1][i], RH[r + 1][i], RD[r + 1][i], RC[r + 1][i], RA[r + 1][i]);
			}
		}
	}

	/**
	 * Stores one shell vertex: its depth (the bow is deepest at the middle of the body line, nothing at the outline), and its
	 * alpha broken by the flowing streaks -- vertical bands of light that wander and stream upward, so it reads as flame, not glass.
	 */
	private static void put(int r, int i, float x, float h, int c, float a, float depth, float bow, float lean, float streak, float ts, float h0,
			float h1) {
		float lineX = lean * (h / fHeight) * (h / fHeight);
		float w = body(Math.min(h, fHeight * 0.98f));
		float u = (x - lineX) / Math.max(w, 0.12f);
		float bowK = Math.max(0f, 1f - u * u) * Mth.clamp(w / fHalf, 0f, 1f);
		if (streak > 0f && a > 0f) {
			float v = Mth.clamp(h / fHeight, 0f, 1.2f);
			float band = 0.5f + 0.5f * Mth.sin(u * 11f + h0 + 1.8f * Mth.sin(v * 3.1f - ts * 0.21f + u * 2f));
			float flow = 0.5f + 0.5f * Mth.sin(v * 11f - ts * 0.85f + u * 5f + h1);
			a *= 1f - streak * (1f - band * (0.45f + 0.55f * flow));
		}
		RX[r][i] = x;
		RH[r][i] = h;
		RC[r][i] = c;
		RA[r][i] = a;
		RD[r][i] = depth + bow * bowK;
	}

	/**
	 * Builds the outline into {@link #OX}/{@link #OH}: up the left side from the feet, the tall centre spike, then down the
	 * right side. Each side is {@link #LOBES} sharp thorns standing on the body line: thorn k rises from a notch at
	 * {@code (k - q0) / LOBES} of the height to its tip and back down to the next notch, both edges bowed in a little so the
	 * tip is sharp. The thorns splay out almost flat at the feet, point up-and-out round the body and straight up near the top.
	 * q0 falls with time, so the thorns travel UP: a new one grows out of the feet, the top one folds into the crown. Each
	 * thorn also flickers -- its length and angle jump to new values every couple of ticks, like a hand-drawn aura.
	 */
	private static void outline(float time, long seed, float speed, float jut, float lean, float phase) {
		outN = 0;
		float rate = 0.085f * speed;
		long boil = (long) Math.floor(time * speed / 2f);
		for (int sd = 0; sd < 2; sd++) {
			float side = sd == 0 ? -1f : 1f;
			// the right side runs half a thorn out of step with the left, so the two never mirror
			float q0 = -time * rate + phase + (sd == 0 ? 0f : 0.5f + 0.2f * hash(seed + 3));
			float q1 = q0 + LOBES;
			int sn = 0;
			for (int k = Mth.floor(q0); k < q1; k++) {
				float pS = Math.max(0f, q0 - k), pE = Math.min(1f, q1 - k);
				if (pE <= pS) {
					continue;
				}
				long ls = seed * 31L + k * 7919L + sd * 104729L;
				float la = hash(ls), lb = hash(ls + 1), bo = hash(ls + boil * 15485863L), bt = hash(ls + boil * 32452843L + 7);
				float uA = Math.max(0f, (k - q0) / LOBES), uB = Math.min(1f, (k + 1 - q0) / LOBES), uT = Mth.clamp((k + 0.5f - q0) / LOBES, 0f, 1f);
				float ax = basex(side, uA, lean), ah = uA * fHeight;
				float bx = basex(side, uB, lean), bh = uB * fHeight;
				// the tip: from the body line at mid-thorn, along a direction that turns from flat-out (feet) to straight up (top)
				float ang = Mth.lerp(smooth(0f, 1f, uT), 0.12f, 1.45f) + (bt - 0.5f) * 0.26f + 0.07f * Mth.sin(time * 0.3f + lb * 6.283f);
				float len = jut * (0.75f + 0.5f * la) * (0.78f + 0.44f * bo) * (0.6f + 0.4f * smooth(0f, 0.35f, uT));
				float tx = basex(side, uT, lean) + side * Mth.cos(ang) * len, th = uT * fHeight + Mth.sin(ang) * len;
				// the points: where it starts (cut off at the feet), the notch, half way up the near edge, the tip, half way down the
				// far edge, where it ends (cut off at the crown); the next notch belongs to the next thorn
				if (pS > 1e-4f) {
					sn = thornPoint(sn, pS, ax, ah, tx, th, bx, bh, side, k, q0, lean);
				}
				for (int j = 0; j <= 4; j++) {
					float p = j * 0.25f;
					boolean afterStart = p > pS + 1e-4f || (j == 0 && pS <= 1e-4f);
					boolean beforeEnd = p < pE - 1e-4f || (j == 4 && pE >= 1f && k + 1 >= q1);
					if (afterStart && beforeEnd) {
						sn = thornPoint(sn, p, ax, ah, tx, th, bx, bh, side, k, q0, lean);
					}
				}
				if (pE < 1f - 1e-4f) {
					sn = thornPoint(sn, pE, ax, ah, tx, th, bx, bh, side, k, q0, lean);
				}
			}
			if (sd == 0) {
				System.arraycopy(SX, 0, OX, outN, sn);
				System.arraycopy(SH, 0, OH, outN, sn);
				outN += sn;
				// the tall centre spike
				float bo = hash(seed + boil * 49979687L);
				OX[outN] = lean + 0.06f * Mth.sin(time * 0.37f + bo);
				OH[outN] = fHeight + jut * (1.25f + 0.35f * bo);
				outN++;
			} else {
				for (int i = sn - 1; i >= 0; i--) {
					OX[outN] = SX[i];
					OH[outN] = SH[i];
					outN++;
				}
			}
		}
	}

	private static int thornPoint(int sn, float p, float ax, float ah, float tx, float th, float bx, float bh, float side, int k, float q0,
			float lean) {
		if (sn >= SX.length) {
			return sn;
		}
		SX[sn] = thornX(p, ax, ah, tx, th, bx, bh, side, k, q0, lean, true);
		SH[sn] = thornX(p, ax, ah, tx, th, bx, bh, side, k, q0, lean, false);
		return sn + 1;
	}

	/** A point on a thorn's outline at {@code p} (0 notch, 0.5 tip, 1 next notch), edges bowed in toward the body line. */
	private static float thornX(float p, float ax, float ah, float tx, float th, float bx, float bh, float side, int k, float q0, float lean,
			boolean wantX) {
		float t, ex, eh;
		if (p <= 0.5f) {
			t = p / 0.5f;
			ex = Mth.lerp(t, ax, tx);
			eh = Mth.lerp(t, ah, th);
		} else {
			t = (1f - p) / 0.5f;
			ex = Mth.lerp(t, bx, tx);
			eh = Mth.lerp(t, bh, th);
		}
		float u = Mth.clamp((k + p - q0) / LOBES, 0f, 1f);
		float bow = 0.28f * Mth.sin(Mth.PI * t) * (t < 1f ? 1f : 0f);
		return wantX ? Mth.lerp(bow, ex, basex(side, u, lean)) : Mth.lerp(bow, eh, u * fHeight);
	}

	/** The body line at {@code u} (0..1) up a side. */
	private static float basex(float side, float u, float lean) {
		return side * body(u * fHeight) + lean * u * u;
	}

	/**
	 * A shell vertex: {@code s} along the viewer-facing plane's right, {@code h} height above the feet, {@code d} toward the
	 * camera; leaned round the chest toward the camera's height.
	 */
	private static void pt(VertexConsumer vc, Matrix4f m, float s, float h, float d, int rgb, float a) {
		float up = (FEET - h) - PIVOT_Y;
		up = -up; // height above the pivot (model y is down)
		float d2 = d * tiltC - up * tiltS;
		float up2 = d * tiltS + up * tiltC;
		v(vc, m, rx * s + fx * d2, PIVOT_Y - up2, rz * s + fz * d2, rgb, a);
	}

	// ---------------------------------------------------------------- flame licks

	/**
	 * {@code count} flame tongues facing the viewer: each born between {@code vMin} and {@code vMax} of the flame's height, at
	 * {@code uMin}..{@code uMax} of its half width there, rising to {@code vEnd} while it stretches and fades; low ones lean out,
	 * high ones lean in over the top. Colour from {@code colBase} (root) to {@code colTip}. Uses the last {@link #shell}'s size.
	 */
	private static void licks(VertexConsumer vc, Matrix4f m, float time, long seed, int count, float vMin, float vMax, float uMin, float uMax,
			float vEnd, int colTip, int colBase, float alpha, float speed, float depth, float size) {
		for (int i = 0; i < count; i++) {
			float h0 = hash(seed + i * 977L), h1 = hash(seed + i * 131L + 5), h2 = hash(seed + i * 71L + 9), h3 = hash(seed + i * 313L + 2);
			float period = (15f + 10f * h0) / speed;
			float ph = time / period + h1;
			int cycle = (int) Math.floor(ph);
			ph -= cycle;
			// every cycle re-rolls where it is born, so the pattern never repeats visibly
			float r0 = hash(seed + i * 977L + cycle * 7919L), r1 = hash(seed + i * 131L + cycle * 104729L);
			float side = ((i + cycle) & 1) == 0 ? -1f : 1f;
			float v0 = Mth.lerp(r0, vMin, vMax);
			float v = v0 + ph * (vEnd - v0);
			float h = v * fHeight;
			float x = side * Mth.lerp(r1, uMin, uMax) * Math.max(body(Math.min(h, fHeight * 0.97f)), 0.1f);
			float y = FEET - h;
			float len = size * (0.35f + 0.4f * h2) * (0.55f + 0.9f * Mth.sin(Mth.PI * ph)) * (fHeight / 2.8f);
			float w = size * (0.07f + 0.06f * h3) * (1f - 0.45f * ph);
			float a = alpha * (float) Math.pow(Mth.sin(Mth.PI * ph), 0.8f);
			if (a < 0.005f) {
				continue;
			}
			float lean = side * len * (0.25f - 0.55f * Math.min(v, 1f)) + 0.04f * Mth.sin(time * 0.3f + h0 * 6.283f);
			tongue(vc, m, x, y, x + lean, y - len, w, depth, colBase, colTip, a);
		}
	}

	/** A soft teardrop tongue from (x0, y0) up to the tip (x1, y1) in the viewer-facing plane: round root, pointed tip. */
	private static void tongue(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float w, float depth, int colBase,
			int colTip, float a) {
		final int segs = 6;
		float px = x0, py = y0, pw = 0f, pa = 0f;
		int pc = colBase;
		for (int k = 1; k <= segs; k++) {
			float s = k / (float) segs;
			// a slight S-curve: the body bows out, the tip comes back
			float bow = Mth.sin(s * Mth.PI) * (x1 - x0) * 0.25f;
			float cx = Mth.lerp(s * s, x0, x1) + bow * 0.3f;
			float cy = Mth.lerp(s, y0, y1);
			float cw = w * (float) Math.pow(Mth.sin(Mth.PI * Math.min(1f, s * 0.85f + 0.15f)), 0.6f) * (1f - 0.6f * s);
			float ca = a * (float) Math.pow(1f - s, 0.7f) * Math.min(1f, s * 4f + 0.4f);
			int cc = mix(colBase, colTip, s);
			pvT(vc, m, px - pw, py, depth, pc, 0f);
			pvT(vc, m, px, py, depth, pc, pa);
			pvT(vc, m, cx, cy, depth, cc, ca);
			pvT(vc, m, cx - cw, cy, depth, cc, 0f);
			pvT(vc, m, px, py, depth, pc, pa);
			pvT(vc, m, px + pw, py, depth, pc, 0f);
			pvT(vc, m, cx + cw, cy, depth, cc, 0f);
			pvT(vc, m, cx, cy, depth, cc, ca);
			px = cx;
			py = cy;
			pw = cw;
			pa = ca;
			pc = cc;
		}
	}

	// ---------------------------------------------------------------- streaks, embers, ground

	/** Fast thin lines of light rising through the aura (charging), some in front of the body and some behind. */
	private static void streaks(VertexConsumer vc, Matrix4f m, float time, long seed, int count, float halfW, float topY, int col, float alpha) {
		for (int i = 0; i < count; i++) {
			float h0 = hash(seed + i * 37L), h1 = hash(seed + i * 59L + 1);
			float period = 7f + 6f * h0;
			float ph = time / period + h1;
			int cycle = (int) Math.floor(ph);
			ph -= cycle;
			float r0 = hash(seed + i * 37L + cycle * 7919L), r1 = hash(seed + i * 59L + cycle * 15485863L);
			float u = (r0 * 2f - 1f) * 0.85f;
			float yBot = FEET + 0.1f - ph * (FEET - topY + 0.8f);
			float len = 0.45f + 0.5f * r1;
			float x = u * halfW * 0.9f;
			float d = (r1 - 0.5f) * 0.9f;
			float a = alpha * Mth.sin(Mth.PI * ph) * (0.6f + 0.4f * r0);
			float w = 0.012f + 0.012f * r1;
			// thin bright core, soft at the sides, fading toward both ends
			pv(vc, m, x - w, yBot, d, col, 0f);
			pv(vc, m, x, yBot, d, col, a * 0.3f);
			pv(vc, m, x, yBot - len, d, col, 0f);
			pv(vc, m, x - w, yBot - len, d, col, 0f);
			pv(vc, m, x, yBot, d, col, a * 0.3f);
			pv(vc, m, x + w, yBot, d, col, 0f);
			pv(vc, m, x + w, yBot - len, d, col, 0f);
			pv(vc, m, x, yBot - len, d, col, 0f);
			// the bright head of the streak sits a third of the way up
			float yh = yBot - len * 0.35f;
			pv(vc, m, x - w, yBot, d, col, 0f);
			pv(vc, m, x, yBot, d, col, a * 0.3f);
			pv(vc, m, x, yh, d, col, a);
			pv(vc, m, x - w * 1.4f, yh, d, col, 0f);
			pv(vc, m, x, yBot, d, col, a * 0.3f);
			pv(vc, m, x + w, yBot, d, col, 0f);
			pv(vc, m, x + w * 1.4f, yh, d, col, 0f);
			pv(vc, m, x, yh, d, col, a);
		}
	}

	/** Small sparks drifting up and out of the aura. */
	private static void embers(VertexConsumer vc, Matrix4f m, float time, long seed, int count, float halfW, float topY, int col, int colOut,
			float alpha) {
		for (int i = 0; i < count; i++) {
			float h0 = hash(seed + i * 41L), h1 = hash(seed + i * 43L + 3);
			float period = 18f + 14f * h0;
			float ph = time / period + h1;
			int cycle = (int) Math.floor(ph);
			ph -= cycle;
			float r0 = hash(seed + i * 41L + cycle * 7919L), r1 = hash(seed + i * 43L + cycle * 104729L);
			float side = r0 < 0.5f ? -1f : 1f;
			float x = side * halfW * (0.35f + 0.5f * r1 + 0.35f * ph) + 0.05f * Mth.sin(time * 0.4f + h0 * 6f);
			float y = FEET - 0.2f - ph * (FEET - topY + 0.6f) * (0.6f + 0.4f * r1);
			float d = (r0 - 0.5f) * 1.2f;
			float a = alpha * Mth.sin(Mth.PI * ph) * (0.5f + 0.5f * Mth.sin(time * 1.3f + h1 * 20f));
			float s = 0.025f + 0.025f * r1;
			int c = mix(col, colOut, ph);
			// a tiny soft diamond with a short tail below (it is rising)
			pv(vc, m, x, y, d, c, a);
			pv(vc, m, x - s, y, d, c, 0f);
			pv(vc, m, x, y + s * 2.6f, d, c, 0f);
			pv(vc, m, x + s, y, d, c, 0f);
			pv(vc, m, x, y, d, c, a);
			pv(vc, m, x + s, y, d, c, 0f);
			pv(vc, m, x, y - s * 1.6f, d, c, 0f);
			pv(vc, m, x - s, y, d, c, 0f);
		}
	}

	/** A soft disc of light on the ground under the feet, with a brighter ring rippling out of it. */
	private static void groundGlow(VertexConsumer vc, Matrix4f m, float time, float y, int col, int core, float alpha, float radius) {
		final int seg = 28;
		float ripple = (time * 0.06f) % 1f;
		float rr = radius * (0.35f + 0.75f * ripple);
		float ra = alpha * 0.7f * (1f - ripple);
		for (int i = 0; i < seg; i++) {
			float a0 = i / (float) seg * Mth.TWO_PI, a1 = (i + 1) / (float) seg * Mth.TWO_PI;
			float s0 = Mth.sin(a0), c0 = Mth.cos(a0), s1 = Mth.sin(a1), c1 = Mth.cos(a1);
			// the disc: bright centre -> half-way -> nothing at the edge
			float mr = radius * 0.45f;
			v(vc, m, 0, y, 0, core, alpha);
			v(vc, m, 0, y, 0, core, alpha);
			v(vc, m, s1 * mr, y, c1 * mr, col, alpha * 0.55f);
			v(vc, m, s0 * mr, y, c0 * mr, col, alpha * 0.55f);
			v(vc, m, s0 * mr, y, c0 * mr, col, alpha * 0.55f);
			v(vc, m, s1 * mr, y, c1 * mr, col, alpha * 0.55f);
			v(vc, m, s1 * radius, y, c1 * radius, col, 0f);
			v(vc, m, s0 * radius, y, c0 * radius, col, 0f);
			// the ripple
			float w = 0.12f + 0.15f * ripple;
			ring(vc, m, y - 0.002f, s0, c0, s1, c1, rr, w, col, ra);
		}
	}

	/** One segment of a soft ring (inner edge 0 -> crest -> outer edge 0) lying flat at height y. */
	private static void ring(VertexConsumer vc, Matrix4f m, float y, float s0, float c0, float s1, float c1, float r, float w, int col, float a) {
		float ri = Math.max(0f, r - w), ro = r + w;
		v(vc, m, s0 * ri, y, c0 * ri, col, 0f);
		v(vc, m, s1 * ri, y, c1 * ri, col, 0f);
		v(vc, m, s1 * r, y, c1 * r, col, a);
		v(vc, m, s0 * r, y, c0 * r, col, a);
		v(vc, m, s0 * r, y, c0 * r, col, a);
		v(vc, m, s1 * r, y, c1 * r, col, a);
		v(vc, m, s1 * ro, y, c1 * ro, col, 0f);
		v(vc, m, s0 * ro, y, c0 * ro, col, 0f);
	}

	// ---------------------------------------------------------------- the transformation burst

	/**
	 * The transformation, over its first ~30 ticks: a flash with star rays out of the chest (first 14), a pillar of light up
	 * into the sky (first 22) and a shockwave ring rolling out along the ground (first 26; {@code groundY} NaN in the air).
	 */
	private static void burst(VertexConsumer vc, Matrix4f m, float t, int[] pal, float groundY, long seed) {
		int glowCol = pal[1], core = pal[2];
		float chestY = 0.35f;
		// the camera-facing basis at the chest, for the flash and the rays
		float tx = camX, ty = camY - chestY, tz = camZ;
		float tl = Mth.sqrt(tx * tx + ty * ty + tz * tz) + 1e-4f;
		tx /= tl;
		ty /= tl;
		tz /= tl;
		// right = up x toCam (model up is -y), then up' = toCam x right
		float ax = -tz, ay = 0f, az = tx;
		float al = Mth.sqrt(ax * ax + az * az);
		if (al < 1e-4f) {
			ax = 1f;
			az = 0f;
			al = 1f;
		}
		ax /= al;
		az /= al;
		float bx = ty * az - tz * ay, by = tz * ax - tx * az, bz = tx * ay - ty * ax;
		if (t < 14f) {
			float k = t / 14f;
			float fade = (1f - k) * (1f - k);
			float grow = Math.min(1f, t / 2.5f);
			// drawn a little toward the viewer, so the body does not hide the flash's heart
			float ox = tx * 0.7f, oy = chestY + ty * 0.7f, oz = tz * 0.7f;
			// the flash: a white core inside a coloured bloom
			disc(vc, m, ox, oy, oz, ax, ay, az, bx, by, bz, (1.0f + 2.4f * k) * grow, glowCol, 0.6f * fade);
			disc(vc, m, ox, oy, oz, ax, ay, az, bx, by, bz, (0.55f + 1.0f * k) * grow, 0xFFFFFF, 0.85f * fade);
			// star rays: soft spikes in the viewer's plane, turning slowly
			int rays = 14;
			float len = (1.6f + 4.2f * (float) Math.sqrt(k)) * grow;
			int rc = mix(core, 0xFFFFFF, 0.4f);
			for (int i = 0; i < rays; i++) {
				float ang = i * (Mth.TWO_PI / rays) + hash(seed + i) * 0.35f + t * 0.02f;
				float l = len * (0.45f + 0.75f * hash(seed + i * 17L + 3));
				float w = (0.11f + 0.07f * hash(seed + i * 5L)) * (1f - 0.4f * k);
				float ca = Mth.cos(ang), sa = Mth.sin(ang);
				float ex = (ax * ca + bx * sa), ey = (ay * ca + by * sa), ez = (az * ca + bz * sa);
				float px = (-ax * sa + bx * ca), py = (-ay * sa + by * ca), pz = (-az * sa + bz * ca);
				float ra = 0.8f * fade;
				// a long thin spike: bright along its centre line, soft at both sides, fading to a point
				float mx = ox + ex * l * 0.3f, my = oy + ey * l * 0.3f, mz = oz + ez * l * 0.3f;
				float tx2 = ox + ex * l, ty2 = oy + ey * l, tz2 = oz + ez * l;
				for (int sd = -1; sd <= 1; sd += 2) {
					v(vc, m, ox, oy, oz, rc, ra);
					v(vc, m, ox + px * w * sd, oy + py * w * sd, oz + pz * w * sd, rc, 0f);
					v(vc, m, mx + px * w * 0.6f * sd, my + py * w * 0.6f * sd, mz + pz * w * 0.6f * sd, rc, 0f);
					v(vc, m, mx, my, mz, rc, ra * 0.7f);
					v(vc, m, mx, my, mz, rc, ra * 0.7f);
					v(vc, m, mx + px * w * 0.6f * sd, my + py * w * 0.6f * sd, mz + pz * w * 0.6f * sd, rc, 0f);
					v(vc, m, tx2, ty2, tz2, rc, 0f);
					v(vc, m, tx2, ty2, tz2, rc, 0f);
				}
			}
		}
		if (t < 22f) {
			// the pillar: a viewer-facing column, soft at both sides, fading with height; it snaps open, then thins away
			float k = t / 22f;
			float open = Math.min(1f, t / 2f);
			float hw = (0.25f + 0.95f * open) * (1f - k * k);
			float a = 0.6f * (1f - k);
			float top = -40f;
			column(vc, m, hw, FEET, top, glowCol, a);
			column(vc, m, hw * 0.35f, FEET, top, mix(core, 0xFFFFFF, 0.5f), a * 0.6f);
		}
		if (t < 26f && !Float.isNaN(groundY)) {
			float k = t / 26f;
			float r = 0.6f + 6.5f * (float) Math.sqrt(k);
			float w = 0.3f + 0.6f * k;
			float a = 0.75f * (1f - k);
			final int seg = 48;
			for (int i = 0; i < seg; i++) {
				float a0 = i / (float) seg * Mth.TWO_PI, a1 = (i + 1) / (float) seg * Mth.TWO_PI;
				float s0 = Mth.sin(a0), c0 = Mth.cos(a0), s1 = Mth.sin(a1), c1 = Mth.cos(a1);
				ring(vc, m, groundY, s0, c0, s1, c1, r, w, glowCol, a);
				ring(vc, m, groundY - 0.003f, s0, c0, s1, c1, r, w * 0.3f, core, a);
				// a short wall of light standing on the crest, so the ring reads from low angles too
				float wallH = 0.6f * (1f - k);
				v(vc, m, s0 * r, groundY, c0 * r, glowCol, a * 0.6f);
				v(vc, m, s1 * r, groundY, c1 * r, glowCol, a * 0.6f);
				v(vc, m, s1 * r * 1.03f, groundY - wallH, c1 * r * 1.03f, glowCol, 0f);
				v(vc, m, s0 * r * 1.03f, groundY - wallH, c0 * r * 1.03f, glowCol, 0f);
			}
		}
	}

	/** A soft round disc on the basis (a, b): bright centre, transparent rim. */
	private static void disc(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float ax, float ay, float az, float bx, float by,
			float bz, float r, int col, float a) {
		final int seg = 20;
		for (int i = 0; i < seg; i++) {
			float a0 = i / (float) seg * Mth.TWO_PI, a1 = (i + 1) / (float) seg * Mth.TWO_PI;
			float c0 = Mth.cos(a0) * r, s0 = Mth.sin(a0) * r, c1 = Mth.cos(a1) * r, s1 = Mth.sin(a1) * r;
			float hr = 0.4f;
			v(vc, m, cx, cy, cz, col, a);
			v(vc, m, cx, cy, cz, col, a);
			v(vc, m, cx + (ax * c1 + bx * s1) * hr, cy + (ay * c1 + by * s1) * hr, cz + (az * c1 + bz * s1) * hr, col, a * 0.45f);
			v(vc, m, cx + (ax * c0 + bx * s0) * hr, cy + (ay * c0 + by * s0) * hr, cz + (az * c0 + bz * s0) * hr, col, a * 0.45f);
			v(vc, m, cx + (ax * c0 + bx * s0) * hr, cy + (ay * c0 + by * s0) * hr, cz + (az * c0 + bz * s0) * hr, col, a * 0.45f);
			v(vc, m, cx + (ax * c1 + bx * s1) * hr, cy + (ay * c1 + by * s1) * hr, cz + (az * c1 + bz * s1) * hr, col, a * 0.45f);
			v(vc, m, cx + ax * c1 + bx * s1, cy + ay * c1 + by * s1, cz + az * c1 + bz * s1, col, 0f);
			v(vc, m, cx + ax * c0 + bx * s0, cy + ay * c0 + by * s0, cz + az * c0 + bz * s0, col, 0f);
		}
	}

	/** A vertical viewer-facing column from y0 up to y1: bright centre line, soft sides, fading out toward the top. */
	private static void column(VertexConsumer vc, Matrix4f m, float hw, float y0, float y1, int col, float a) {
		final float[] ys = { 0f, 0.04f, 0.15f, 0.4f, 1f };
		final float[] as = { 0.6f, 1f, 0.8f, 0.35f, 0f };
		for (int i = 0; i < ys.length - 1; i++) {
			float ya = Mth.lerp(ys[i], y0, y1), yb = Mth.lerp(ys[i + 1], y0, y1);
			float aa = a * as[i], ab = a * as[i + 1];
			float wa = hw * (1f - 0.3f * ys[i]), wb = hw * (1f - 0.3f * ys[i + 1]);
			pv(vc, m, -wa, ya, 0f, col, 0f);
			pv(vc, m, 0f, ya, 0f, col, aa);
			pv(vc, m, 0f, yb, 0f, col, ab);
			pv(vc, m, -wb, yb, 0f, col, 0f);
			pv(vc, m, 0f, ya, 0f, col, aa);
			pv(vc, m, wa, ya, 0f, col, 0f);
			pv(vc, m, wb, yb, 0f, col, 0f);
			pv(vc, m, 0f, yb, 0f, col, ab);
		}
	}

	// ---------------------------------------------------------------- helpers

	static float hash(long s) {
		s = (s ^ (s >>> 33)) * 0xff51afd7ed558ccdL;
		s = (s ^ (s >>> 33)) * 0xc4ceb9fe1a85ec53L;
		return ((s ^ (s >>> 33)) & 0xFFFFFF) / (float) 0xFFFFFF;
	}

	/** A vertex in the viewer-facing plane: {@code s} along its right, {@code y} model height, {@code d} toward the camera. */
	private static void pv(VertexConsumer vc, Matrix4f m, float s, float y, float d, int rgb, float a) {
		v(vc, m, rx * s + fx * d, y, rz * s + fz * d, rgb, a);
	}

	/** {@link #pv} for things that live in the shells (the flame strands): leaned with them toward the camera's height. */
	private static void pvT(VertexConsumer vc, Matrix4f m, float s, float y, float d, int rgb, float a) {
		pt(vc, m, s, FEET - y, d, rgb, a);
	}

	/** Scales every vertex alpha: the daylight balance between the wash and the glow (render thread only). */
	private static float alphaMul = 1f;

	private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, int rgb, float a) {
		vc.addVertex(m, x, y, z).setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255,
				Math.max(0, Math.min(255, (int) (a * alphaMul * 255f))));
	}

	static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
		int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
		int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
		return (r << 16) | (g << 8) | bl;
	}
}
