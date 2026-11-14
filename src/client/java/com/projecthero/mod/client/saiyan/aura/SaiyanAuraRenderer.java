package com.projecthero.mod.client.saiyan.aura;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.saiyan.SaiyanFx;
import com.projecthero.mod.saiyan.Saiyan;
import com.projecthero.mod.saiyan.SaiyanForm;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.4: the Saiyan aura as a real 3D flame round the body (the user: "the aura still looks 2d, can you recreate this style of
 * aura as a 3d aura that actually shows up around the player"; the look picked: the classic DBZ flame). It replaces v0.17.3's
 * viewer-facing sheets. Drawn in the player model's space from the render layer (third person, every viewer) and, for the local
 * player in first person, from the world render ({@link #renderFirstPerson}) so he sees it rise round him too.
 * <ul>
 * <li><b>The flame shell</b>: a closed surface of revolution round the body ({@value #COLS} columns x {@value #ROWS} rows),
 * slightly oval (narrower front to back), narrow at the feet, widest round the chest, closing in over the head into a CROWN
 * of flame points -- alternate columns rise into tall tips that flicker to a new height every couple of ticks, like a
 * hand-drawn aura. Thin ridges run UP every column (each column out of step), so the outline boils with thorns travelling up.</li>
 * <li><b>Bright edges, hollow middle</b>: every vertex is lit by how edge-on the surface is to the viewer (1 - |N.V|): where
 * the shell turns away at the silhouette it is bright and near-white, where you look straight through it (the body's middle)
 * it is almost clear. Being a real closed surface, this holds from any angle: from above it reads as a bright ring of flame
 * with the player in its see-through centre; in first person it is a glow rising round the edges of the view.</li>
 * <li><b>Flame tongues</b>: crossed ribbons (one along the surface, one sticking out of it) so they have volume from every
 * side; born low on the shell, they rise up it, stretch and fade, flaring out at the feet like thorns and curling in to the
 * crown over the head. Faded where you look through the middle, like the shell.</li>
 * <li><b>Layers</b>: an alpha-blended wash of the aura colour on the outer shell (keeps the colour against a bright sky), the
 * additive outer shell, a tighter white-hot core shell, the tongues, embers, and (charging) fast rising streaks, a glow on the
 * ground; the transformation burst (flash, pillar, ground shockwave).</li>
 * </ul>
 * Colours: Super Saiyan gold, Kaioken red (eased in over any form), Ultra Instinct silver, a Base charge white. Everything is
 * procedural from (time, a per-player seed): no per-frame allocation. Model space: block units, +y DOWN, the feet at y =
 * {@link #FEET}, the top of the head at y = -0.5, scaled by the player model's 15/16.
 */
public final class SaiyanAuraRenderer {
	static final float FEET = 1.501f;
	private static final float HEAD_TOP = -0.5f;
	private static final float MODEL_SCALE = 0.9375f;
	/** Feet to the top of the head, in model units. */
	private static final float HEAD_H = FEET - HEAD_TOP;
	/** The shell is this deep front to back for its width (the body is wider than it is deep). */
	private static final float DEPTH = 0.8f;

	/** Entities are being drawn for the world (not a GUI preview such as the inventory's player). Set by SaiyanVisualsClient. */
	public static boolean inWorld;
	/** Drawing the local player's own aura round the first-person camera (render thread only). */
	private static boolean firstPerson;

	// per-frame scratch (render thread only)
	private static final Matrix4f INV = new Matrix4f();
	/** The camera in model space. */
	private static float camX, camY, camZ;
	/** The viewer-facing plane (the streaks, embers and burst only): F = unit horizontal toward the camera, R = its right. */
	private static float fx, fz, rx, rz;

	private SaiyanAuraRenderer() {
	}

	// ---------------------------------------------------------------- smoothed charge (client tick)

	/** entity id -> {previous, current, ground distance (blocks, -1 none), stamp, daylight 0..1 (eased), kaioken prev, kaioken}. */
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
	private static final int[] SSJ_PAL = { 0xFFB800, rgb(SaiyanForm.SSJ.auraColor), 0xFFF6D0 };
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

	/**
	 * The local player's own aura in first person (the player render layer never runs for him then): from the world render
	 * (after entities), in his model's space, so the same 3D flame rises round the camera -- softened, and faded out close to
	 * the eyes so it never fogs the view.
	 */
	public static void renderFirstPerson(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || ctx.consumers() == null || ctx.matrixStack() == null || !mc.options.getCameraType().isFirstPerson()
				|| ctx.camera().getEntity() != p || p.isInvisible() || p.isSpectator() || !Saiyan.hasPower(p)) {
			return;
		}
		if (!SaiyanFx.active(p) && !tracked(p.getId())) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		var cam = ctx.camera().getPosition();
		PoseStack pose = ctx.matrixStack();
		pose.pushPose();
		pose.translate(Mth.lerp(partial, p.xo, p.getX()) - cam.x, Mth.lerp(partial, p.yo, p.getY()) - cam.y,
				Mth.lerp(partial, p.zo, p.getZ()) - cam.z);
		// LivingEntityRenderer's own model transform
		pose.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot)));
		pose.scale(-1f, -1f, 1f);
		pose.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
		pose.translate(0f, -FEET, 0f);
		firstPerson = true;
		try {
			render(pose, ctx.consumers(), p, partial, p.tickCount + partial);
		} finally {
			firstPerson = false;
			pose.popPose();
		}
	}

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
		// first person: the same flame, softer (the near-eye fade is in v())
		float view = firstPerson ? 0.55f : 1f;

		// the flame's size, in model units: radius at the feet and at the widest (the chest), the shell's height (feet to where
		// the crown closes; its flame points rise above it) and how far the points and tongues reach
		float foot = 0.38f + 0.1f * power;
		float half = 0.8f + 0.3f * power;
		float height = HEAD_H + 0.45f + 0.8f * power;
		float jut = 0.5f + 0.25f * power;
		float top = FEET - height;

		// the edge stays the form's colour, only a little whiter (white-hot is the core shell's job)
		int rimCol = mix(pal[1], pal[2], 0.2f);
		int fillCol = pal[1];
		int coreCol = mix(pal[1], pal[2], 0.55f);
		// first person you are inside the flame: a fuller glow, so it reads round the edges of the view
		float fpFill = firstPerson ? 0.1f : 0f;

		// ---- pass 1: the alpha-blended wash (the colour that survives daylight), on the outer shell's edges
		VertexConsumer tint = buffers.getBuffer(SaiyanAuraRenderType.TINT);
		alphaMul = (1f + 0.6f * daylight) * view;
		shell(tint, time, seed, speed, foot * 1.04f, half * 1.04f, height * 1.03f, jut, pal[0], pal[0], 0.03f + fpFill * 0.4f, 0.42f + 0.15f * power,
				0.4f, 1f);

		// ---- pass 2: the additive glow
		VertexConsumer glow = buffers.getBuffer(SaiyanAuraRenderType.GLOW);
		// by day additive light washes out to white against the sky: less of it, and more of the coloured wash under it
		alphaMul = (1f - 0.55f * daylight) * view;
		// the outer flame shell
		shell(glow, time, seed, speed, foot, half, height, jut, fillCol, rimCol, 0.025f + 0.02f * power + fpFill, 0.55f + 0.3f * power, 0.35f, 1f);
		// the white-hot core: tighter and lower, its points short and quick
		shell(glow, time * 1.35f, seed + 5, speed, foot * 0.78f, half * 0.72f, height * 0.8f, jut * 0.6f, coreCol, pal[2], 0.02f + 0.02f * power,
				0.22f + 0.22f * power, 0.5f, 1.6f);
		// flame tongues rising up the outer shell (flaring out at the feet), and a few curling into the crown
		tongues(glow, time, seed + 17, speed, foot, half, height, Math.round(14 + 20 * power), 0.0f, 0.62f, 0.45f, jut * 1.1f, fillCol, rimCol,
				0.4f + 0.3f * power, 1.0f);
		tongues(glow, time, seed + 23, speed * 1.2f, foot, half, height, Math.round(5 + 7 * power), 0.62f, 0.9f, 0.3f, jut * 1.25f, rimCol, coreCol,
				0.4f + 0.3f * power, 0.9f);
		embers(glow, m, time, seed + 31, Math.round(3 + 12 * charge + 6 * flare), half * 1.2f, top, pal[2], rimCol, 0.8f);
		alphaMul = (1f - 0.25f * daylight) * view; // the streaks, ground glow and burst stay strong by day
		if (charge > 0.02f && !firstPerson) {
			streaks(glow, m, time, seed + 41, Math.round(12 * charge), half * 1.2f, top, pal[2], 0.55f * charge);
		}
		if (groundDist >= 0f && groundDist < 3f) {
			float gy = FEET + groundDist / MODEL_SCALE - 0.03f;
			float fade = 1f - groundDist / 3f;
			float ga = (0.08f + 0.32f * charge + 0.35f * flare) * fade;
			if (ga > 0.01f) {
				groundGlow(glow, m, time, gy, pal[1], pal[2], ga, (0.9f + 0.7f * charge) * (1f + 0.04f * Mth.sin(time * 0.9f)));
			}
		}
		if (burst && !firstPerson) {
			burst(glow, m, bt, pal, groundDist >= 0f && groundDist < 4f ? FEET + groundDist / MODEL_SCALE - 0.04f : Float.NaN, seed);
		}
	}

	/** Where the camera is in model space, and the viewer-facing plane for the few camera-facing bits. */
	private static void setupCamera(Matrix4f m) {
		curM = m;
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
	}

	// ---------------------------------------------------------------- the 3D flame shell

	/** Columns round the body and rows from the feet to the crown. */
	private static final int COLS = 28, ROWS = 14;
	/** The current shell's grid (render thread only): positions (model space), base alpha. Column COLS repeats column 0. */
	private static final float[][] GX = new float[COLS + 1][ROWS + 1], GY = new float[COLS + 1][ROWS + 1], GZ = new float[COLS + 1][ROWS + 1],
			GK = new float[COLS + 1][ROWS + 1], GR = new float[COLS + 1][ROWS + 1];
	/** The current shell's (or tongue set's) body: radius at the feet, at the widest, its height. */
	private static float fFoot, fHalf, fHeight;
	/** The matrix being drawn with (render thread only). */
	private static Matrix4f curM;

	/**
	 * The shell's radius (x; z is {@link #DEPTH} of it) at {@code v} (0 feet .. 1 where the crown closes, beyond for the tongues
	 * curling over the top): {@link #fFoot} at the feet, swelling to {@link #fHalf} round the chest (45% up), closing in over the
	 * head to a fifth of it, then to nothing.
	 */
	private static float radius(float v) {
		if (v <= 0f) {
			return fFoot;
		}
		if (v < 0.45f) {
			float s = v / 0.45f;
			return fFoot + (fHalf - fFoot) * s * s * (3f - 2f * s);
		}
		if (v <= 1f) {
			float t = (v - 0.45f) / 0.55f;
			return fHalf * (1f - 0.8f * (float) Math.pow(t, 1.8f));
		}
		return Math.max(0f, fHalf * 0.2f * (1f - (v - 1f) * 2.2f));
	}

	/** Height above the feet (model units) of {@code v} up the shell. */
	private static float heightAt(float v) {
		return -0.06f + v * fHeight;
	}

	private static float smooth(float a, float b, float x) {
		float t = Mth.clamp((x - a) / (b - a), 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	/**
	 * One flame shell round the body. {@code aFill} where you look straight through it, up to {@code aFill + aEdge} at the
	 * silhouette (colour {@code colFill} -> {@code colRim} with it). {@code tipFade}: how much the crown's points dim toward their
	 * tips; {@code quick}: how fast its points flicker.
	 */
	private static void shell(VertexConsumer vc, float time, long seed, float speed, float foot, float half, float height, float jut,
			int colFill, int colRim, float aFill, float aEdge, float tipFade, float quick) {
		fFoot = foot;
		fHalf = half;
		fHeight = height;
		float ts = time * speed;
		float breathe = 1f + 0.03f * Mth.sin(ts * 0.21f + hash(seed) * 6.283f);
		// the whole flame sways a little
		float swayX = 0.05f * Mth.sin(ts * 0.11f + hash(seed + 1) * 6.283f), swayZ = 0.04f * Mth.sin(ts * 0.087f + hash(seed + 2) * 6.283f);
		for (int i = 0; i < COLS; i++) {
			float ph = hash(seed + i * 7L) * 6.283f;
			// the crown: alternate columns rise into tall points, each jumping to a new height every couple of ticks
			long boil = (long) Math.floor(ts * quick / 2.5f + hash(seed + i * 13L) * 3f);
			float spike = ((i & 1) == 0 ? 0.62f : 0.12f) + 0.38f * hash(seed + i * 31L + boil * 7919L);
			float ang0 = i * (Mth.TWO_PI / COLS);
			for (int j = 0; j <= ROWS; j++) {
				float v = j / (float) ROWS;
				float wv = (float) Math.pow(smooth(0.5f, 1f, v), 1.5f);
				// a slow swirl up the flame
				float ang = ang0 + 0.07f * Mth.sin(ts * 0.13f + v * 4f + ph) + 0.08f * v * Mth.sin(ts * 0.05f);
				// thorns climbing every column, out of step: each swells slowly going up and breaks off sharply at its tip (a
				// sawtooth), so the outline is a stack of upward-pointing flame thorns that travel up the sides
				float saw = v * 5f - ts * 0.028f + ph * 0.159f;
				float ridge = (float) Math.pow(saw - Mth.floor(saw), 3f);
				float r = radius(v) * breathe * (1f + 0.28f * ridge * (1f - 0.6f * wv)) * (1f + 0.35f * spike * wv);
				float u = heightAt(v) + jut * 2.0f * spike * wv;
				float lean = (u / fHeight) * (u / fHeight);
				GX[i][j] = r * Mth.cos(ang) + swayX * lean;
				GZ[i][j] = r * DEPTH * Mth.sin(ang) + swayZ * lean;
				GY[i][j] = FEET - u;
				// the feet sit in it (soft bottom), bands of light flow up, the points dim toward their tips
				float bottom = smooth(-0.04f, 0.24f, v);
				float flow = 0.72f + 0.28f * Mth.sin(v * 9f - ts * 0.75f + ph * 2f);
				float tip = 1f - tipFade * smooth(0.82f, 1f, v);
				GK[i][j] = bottom * flow * tip * (1f + 0.4f * ridge);
			}
		}
		for (int j = 0; j <= ROWS; j++) {
			GX[COLS][j] = GX[0][j];
			GY[COLS][j] = GY[0][j];
			GZ[COLS][j] = GZ[0][j];
			GK[COLS][j] = GK[0][j];
		}
		// how edge-on each vertex is to the viewer: 1 at the silhouette, 0 looking straight through
		for (int i = 0; i < COLS; i++) {
			int ip = (i + 1) % COLS, im = (i + COLS - 1) % COLS;
			for (int j = 0; j <= ROWS; j++) {
				int jp = Math.min(ROWS, j + 1), jm = Math.max(0, j - 1);
				float ax = GX[ip][j] - GX[im][j], ay = GY[ip][j] - GY[im][j], az = GZ[ip][j] - GZ[im][j];
				float bx = GX[i][jp] - GX[i][jm], by = GY[i][jp] - GY[i][jm], bz = GZ[i][jp] - GZ[i][jm];
				float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
				float vx = camX - GX[i][j], vy = camY - GY[i][j], vz = camZ - GZ[i][j];
				float nl = Mth.sqrt(nx * nx + ny * ny + nz * nz) * Mth.sqrt(vx * vx + vy * vy + vz * vz);
				float dot = nl < 1e-6f ? 0f : Math.abs(nx * vx + ny * vy + nz * vz) / nl;
				GR[i][j] = 1f - dot;
			}
		}
		for (int j = 0; j <= ROWS; j++) {
			GR[COLS][j] = GR[0][j];
		}
		for (int i = 0; i < COLS; i++) {
			for (int j = 0; j < ROWS; j++) {
				sv(vc, i, j, colFill, colRim, aFill, aEdge);
				sv(vc, i + 1, j, colFill, colRim, aFill, aEdge);
				sv(vc, i + 1, j + 1, colFill, colRim, aFill, aEdge);
				sv(vc, i, j + 1, colFill, colRim, aFill, aEdge);
			}
		}
	}

	/** A shell vertex: the edge light curve (sharp, so the middle stays clear) and the colour turning white-hot toward the rim. */
	private static void sv(VertexConsumer vc, int i, int j, int colFill, int colRim, float aFill, float aEdge) {
		float rim = GR[i][j];
		float e = rim * rim * rim;
		float a = (aFill + aEdge * (0.25f * rim * rim + 0.75f * e * e)) * GK[i][j];
		v(vc, curM, GX[i][j], GY[i][j], GZ[i][j], mix(colFill, colRim, e), a);
	}

	// ---------------------------------------------------------------- flame tongues (3D crossed ribbons)

	private static final int TONGUE_SEGS = 6;

	/**
	 * {@code count} flame tongues on a shell of the given size: each born between {@code vMin} and {@code vMax} up it at a random
	 * angle, rising {@code rise} further while it stretches and fades. Two ribbons each, crossed (along the shell and out of it),
	 * so each tongue has volume from any side. Low ones flare out like thorns, high ones curl in over the head. {@code colBase}
	 * at the root to {@code colTip}; dimmed where the viewer looks through the middle of the aura.
	 */
	private static void tongues(VertexConsumer vc, float time, long seed, float speed, float foot, float half, float height, int count,
			float vMin, float vMax, float rise, float len0, int colBase, int colTip, float alpha, float width) {
		fFoot = foot;
		fHalf = half;
		fHeight = height;
		long boil = (long) Math.floor(time * speed / 2f);
		for (int i = 0; i < count; i++) {
			float h0 = hash(seed + i * 977L), h1 = hash(seed + i * 131L + 5), h2 = hash(seed + i * 71L + 9);
			float period = (16f + 12f * h0) / speed;
			float ph = time / period + h1;
			int cycle = (int) Math.floor(ph);
			ph -= cycle;
			float life = (float) Math.pow(Mth.sin(Mth.PI * ph), 0.8f);
			if (life < 0.02f) {
				continue;
			}
			// every cycle re-rolls where it is born, so the pattern never repeats visibly
			float ang = hash(seed + i * 977L + cycle * 7919L) * Mth.TWO_PI;
			float v0 = Mth.lerp(hash(seed + i * 131L + cycle * 104729L), vMin, vMax) + ph * rise;
			// the hand-drawn flicker: a new length every couple of ticks
			float flick = 0.7f + 0.6f * hash(seed + i * 37L + (boil + i) * 15485863L);
			float len = len0 * (0.7f + 0.6f * h2) * (0.6f + 0.6f * Mth.sin(Mth.PI * ph)) * flick;
			float w = width * (0.13f + 0.08f * h0) * (fHalf / 0.8f);
			// low tongues flare out (thorns at the feet), high ones lean in over the head
			float out = Mth.clamp(0.55f - v0, -0.4f, 0.55f) * 1.4f;
			float swirl = (h2 - 0.5f) * 0.5f;
			float ca = Mth.cos(ang), sa = Mth.sin(ang);
			// how edge-on this side of the aura is to the viewer (outward normal vs the view), and each ribbon's facing
			float bx = radius(Math.min(v0, 1f)) * ca, bz = radius(Math.min(v0, 1f)) * DEPTH * sa, by = FEET - heightAt(v0);
			float vx = camX - bx, vy = camY - by, vz = camZ - bz;
			float vl = Mth.sqrt(vx * vx + vy * vy + vz * vz) + 1e-5f;
			vx /= vl;
			vy /= vl;
			vz /= vl;
			// the oval's outward normal at this angle: (DEPTH cos, sin)
			float nX = DEPTH * ca, nZ = sa;
			float nl2 = Mth.sqrt(nX * nX + nZ * nZ) + 1e-5f;
			nX /= nl2;
			nZ /= nl2;
			float rim = 1f - Math.abs(nX * vx + nZ * vz);
			float vis = firstPerson ? 1f : 0.25f + 0.75f * rim * rim;
			// the ribbon along the shell (normal = outward) and the one sticking out of it (normal = along the shell)
			float tX = -nZ, tZ = nX;
			float faceAlong = Math.abs(nX * vx + nZ * vz), faceOut = Math.abs(tX * vx + tZ * vz);
			float a = alpha * life * vis;
			ribbon(vc, i, ang, swirl, v0, len, w, out, tX, 0f, tZ, colBase, colTip, a * (0.35f + 0.65f * faceAlong) * 0.6f);
			ribbon(vc, i, ang, swirl, v0, len, w, out, nX, 0.25f, nZ, colBase, colTip, a * (0.35f + 0.65f * faceOut));
		}
	}

	/**
	 * One tongue ribbon along its spine (rising from {@code v0} by {@code len} model units, flaring by {@code out}), spread
	 * across ({@code sx}, {@code sy}, {@code sz}); bright along the spine, soft at the sides, round at the root, pointed at the tip.
	 */
	private static void ribbon(VertexConsumer vc, int idx, float ang0, float swirl, float v0, float len, float w, float out, float sx, float sy,
			float sz, int colBase, int colTip, float a) {
		if (a < 0.004f) {
			return;
		}
		float pxL = 0, pyL = 0, pzL = 0, pxC = 0, pyC = 0, pzC = 0, pxR = 0, pyR = 0, pzR = 0, pa = 0;
		int pc = colBase;
		float u0 = heightAt(v0);
		for (int k = 0; k <= TONGUE_SEGS; k++) {
			float s = k / (float) TONGUE_SEGS;
			float u = u0 + s * len;
			float v = (u + 0.06f) / fHeight;
			float ang = ang0 + swirl * s;
			float r = radius(Math.max(0f, v)) * (1.03f + out * (float) Math.pow(s, 1.4f));
			// a slight S-curve sideways as it rises
			float bend = Mth.sin(s * Mth.PI) * 0.06f * (idx % 2 == 0 ? 1f : -1f);
			float cx = r * Mth.cos(ang) - Mth.sin(ang) * bend, cz = r * DEPTH * Mth.sin(ang) + Mth.cos(ang) * bend, cy = FEET - u;
			float cw = w * (float) Math.pow(Mth.sin(Mth.PI * Math.min(1f, s * 0.85f + 0.15f)), 0.6f) * (1f - 0.7f * s);
			float ca = a * (float) Math.pow(1f - s, 0.6f) * Math.min(1f, s * 4f + 0.35f);
			int cc = mix(colBase, colTip, s);
			// the ribbon's own up-tilt: a radial ribbon also leans a touch up so it never lies flat from above
			float lx = cx - sx * cw, ly = cy - sy * cw, lz = cz - sz * cw;
			float rx2 = cx + sx * cw, ry2 = cy + sy * cw, rz2 = cz + sz * cw;
			if (k > 0) {
				v(vc, curM, pxL, pyL, pzL, pc, 0f);
				v(vc, curM, pxC, pyC, pzC, pc, pa);
				v(vc, curM, cx, cy, cz, cc, ca);
				v(vc, curM, lx, ly, lz, cc, 0f);
				v(vc, curM, pxC, pyC, pzC, pc, pa);
				v(vc, curM, pxR, pyR, pzR, pc, 0f);
				v(vc, curM, rx2, ry2, rz2, cc, 0f);
				v(vc, curM, cx, cy, cz, cc, ca);
			}
			pxL = lx;
			pyL = ly;
			pzL = lz;
			pxC = cx;
			pyC = cy;
			pzC = cz;
			pxR = rx2;
			pyR = ry2;
			pzR = rz2;
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

	/** Small sparks drifting up and out of the aura, all round it. */
	private static void embers(VertexConsumer vc, Matrix4f m, float time, long seed, int count, float halfW, float topY, int col, int colOut,
			float alpha) {
		for (int i = 0; i < count; i++) {
			float h0 = hash(seed + i * 41L), h1 = hash(seed + i * 43L + 3);
			float period = 18f + 14f * h0;
			float ph = time / period + h1;
			int cycle = (int) Math.floor(ph);
			ph -= cycle;
			float r0 = hash(seed + i * 41L + cycle * 7919L), r1 = hash(seed + i * 43L + cycle * 104729L);
			float ang = r0 * Mth.TWO_PI;
			float rr = halfW * (0.55f + 0.35f * r1 + 0.3f * ph);
			float ex = rr * Mth.cos(ang) + 0.05f * Mth.sin(time * 0.4f + h0 * 6f), ez = rr * DEPTH * Mth.sin(ang);
			float y = FEET - 0.2f - ph * (FEET - topY + 0.6f) * (0.6f + 0.4f * r1);
			float a = alpha * Mth.sin(Mth.PI * ph) * (0.5f + 0.5f * Mth.sin(time * 1.3f + h1 * 20f));
			float s = 0.025f + 0.025f * r1;
			int c = mix(col, colOut, ph);
			// a tiny soft diamond facing the viewer with a short tail below (it is rising)
			float px = ex, pz = ez;
			v(vc, m, px, y, pz, c, a);
			v(vc, m, px - rx * s, y, pz - rz * s, c, 0f);
			v(vc, m, px, y + s * 2.6f, pz, c, 0f);
			v(vc, m, px + rx * s, y, pz + rz * s, c, 0f);
			v(vc, m, px, y, pz, c, a);
			v(vc, m, px + rx * s, y, pz + rz * s, c, 0f);
			v(vc, m, px, y - s * 1.6f, pz, c, 0f);
			v(vc, m, px - rx * s, y, pz - rz * s, c, 0f);
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

	/** Scales every vertex alpha: the daylight balance between the wash and the glow, and first person's softening. */
	private static float alphaMul = 1f;

	private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, int rgb, float a) {
		float k = a * alphaMul;
		if (firstPerson && k > 0f) {
			// nothing right at the eyes: fade out inside ~0.75 blocks of the camera
			float dx = x - camX, dy = y - camY, dz = z - camZ;
			k *= smooth(0.3f, 0.8f, Mth.sqrt(dx * dx + dy * dy + dz * dz));
		}
		vc.addVertex(m, x, y, z).setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, Math.max(0, Math.min(255, (int) (k * 255f))));
	}

	static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
		int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
		int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
		return (r << 16) | (g << 8) | bl;
	}
}
