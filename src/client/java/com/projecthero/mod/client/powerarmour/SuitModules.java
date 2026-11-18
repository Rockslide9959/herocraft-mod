package com.projecthero.mod.client.powerarmour;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.ironman.IronManBoxes;
import com.projecthero.mod.client.powerarmour.SuitPanel.Group;
import com.projecthero.mod.client.powerarmour.SuitPanel.Part;
import com.projecthero.mod.powerarmour.ModuleType;
import com.projecthero.mod.powerarmour.SuitData;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

/**
 * v0.17.5 Power Armour: the visible modules -- small pixel-boxes on the suit, sampled from the module swatch sheet
 * ({@code modules.png}, 8x4 cells of 4 px). The gatling's barrels spin while it fires, the thrusters and calf jets throw a flame,
 * glow bits are full-bright in the glow paint (or their own colour). Helmet modules ride the helmet shell, back modules lift
 * out of the way when the back doors open, leg pistons slide with the outer leg plates.
 */
public final class SuitModules {
	/** Swatch cells. */
	static final int DARK = 0, MID = 1, LIGHT = 2, BLACK = 3, RED = 4, HAZARD = 5, SOLAR = 6, COPPER = 7, GLOW = 8, VENT = 9, RUBBER = 10,
			GLASS = 11;
	/** Tints: none, primary paint, secondary paint, glow paint, fixed colour. */
	static final int T_NONE = 0, T_PRIMARY = 1, T_SECONDARY = 2, T_GLOW = 3, T_FIXED = 4;
	/** Special behaviour. */
	static final int K_STATIC = 0, K_BARREL = 1, K_THRUST = 2, K_JETS = 3, K_MUZZLE = 4;

	record B(Part part, SuitPanel panel, float x0, float y0, float z0, float x1, float y1, float z1, int sw, int tint, int rgb, int kind) {
		boolean glow() {
			return sw == GLOW;
		}
	}

	/**
	 * The module boxes were laid out on a 1.45 px shell; round 2's shell ends {@code TRIM_IN + TRIM_D} out, so every box is
	 * pulled in by the difference when it is built ({@link #fit}): a box wholly outside a face slides in (keeping its size), the
	 * outer end of a box that wraps or straddles a face is pulled in.
	 */
	private static final float OUTBOARD = 1.45f - (SuitRenderer.TRIM_IN + SuitRenderer.TRIM_D);

	/** {@code [a, b]} (a <= b) on one axis of a part box {@code lo..hi}, moved onto the round-2 shell. */
	static float[] fit(float a, float b, float lo, float hi, boolean straddlers) {
		if (a >= hi) {
			float sh = Math.min(OUTBOARD, a - hi);
			return new float[] { a - sh, b - sh };
		}
		if (b <= lo) {
			float sh = Math.min(OUTBOARD, lo - b);
			return new float[] { a + sh, b + sh };
		}
		return straddlers ? new float[] { pull(a, lo, hi), pull(b, lo, hi) } : new float[] { a, b };
	}

	private static float pull(float v, float lo, float hi) {
		return v > hi ? hi + Math.max(0f, v - hi - OUTBOARD) : v < lo ? lo - Math.max(0f, lo - v - OUTBOARD) : v;
	}

	private static B fitted(Part p, SuitPanel panel, float x0, float y0, float z0, float x1, float y1, float z1, int sw, int tint, int rgb, int kind) {
		float[] x = fit(x0, x1, p.x0, p.x0 + p.w, true), y = fit(y0, y1, p.y0, p.y0 + p.h, false), z = fit(z0, z1, p.z0, p.z0 + p.d, true);
		return new B(p, panel, x[0], y[0], z[0], x[1], y[1], z[1], sw, tint, rgb, kind);
	}

	/** The gatling's barrel axis (x, right arm), moved onto the shell like the boxes. */
	private static final float BARREL_X = pull(-5.2f, Part.RIGHT_ARM.x0, Part.RIGHT_ARM.x0 + Part.RIGHT_ARM.w);

	private static final Map<ModuleType, List<B>> GEO = new EnumMap<>(ModuleType.class);
	/** Calf jets: every suit has them (the Jump Jets). */
	private static final List<B> CALF = new ArrayList<>();

	private static List<B> cur;

	private static void m(ModuleType t) {
		cur = GEO.computeIfAbsent(t, k -> new ArrayList<>());
	}

	private static void b(Part p, SuitPanel panel, float x0, float y0, float z0, float x1, float y1, float z1, int sw, int tint) {
		cur.add(fitted(p, panel, x0, y0, z0, x1, y1, z1, sw, tint, 0, K_STATIC));
	}

	private static void g(Part p, SuitPanel panel, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, int kind) {
		cur.add(fitted(p, panel, x0, y0, z0, x1, y1, z1, GLOW, rgb == 0 ? T_GLOW : T_FIXED, rgb, kind));
	}

	private static void k(Part p, SuitPanel panel, float x0, float y0, float z0, float x1, float y1, float z1, int sw, int tint, int kind) {
		cur.add(kind == K_BARREL ? new B(p, panel, x0, y0, z0, x1, y1, z1, sw, tint, 0, kind) : fitted(p, panel, x0, y0, z0, x1, y1, z1, sw, tint, 0, kind));
	}

	static {
		final Part RA = Part.RIGHT_ARM, LA = Part.LEFT_ARM, BO = Part.BODY, HE = Part.HEAD, RL = Part.RIGHT_LEG, LL = Part.LEFT_LEG;
		final SuitPanel S = SuitPanel.STATIC, H = SuitPanel.VISOR, HB = SuitPanel.HELMET; // round 2: face modules ride the front half
		// ---------------- arms
		m(ModuleType.GATLING);
		b(RA, S, -6.0f, 2.5f, -1.7f, -4.4f, 7.5f, 1.7f, MID, T_SECONDARY);
		b(RA, S, -6.4f, 7.5f, -1.5f, -4.0f, 8.6f, 1.5f, DARK, T_NONE);
		b(RA, S, -6.2f, 3.2f, -2.0f, -4.6f, 4.0f, 2.0f, HAZARD, T_NONE);
		for (int i = 0; i < 3; i++) {
			k(RA, S, -0.28f, 8.6f, -0.28f, 0.28f, 12.6f, 0.28f, BLACK, T_NONE, K_BARREL);
		}
		g(RA, S, -5.7f, 12.6f, -0.6f, -4.7f, 13.1f, 0.4f, 0xFFD060, K_MUZZLE);
		m(ModuleType.FLAMER);
		b(RA, S, -2.8f, 3.0f, 2.5f, 0.4f, 7.2f, 4.4f, RED, T_NONE);
		b(RA, S, -2.6f, 2.4f, 2.8f, 0.2f, 3.0f, 4.1f, LIGHT, T_NONE);
		b(RA, S, -2.0f, 7.2f, 3.0f, -1.2f, 9.6f, 3.6f, BLACK, T_NONE);
		b(RA, S, -2.3f, 9.4f, -3.2f, -0.7f, 11.8f, -1.6f, BLACK, T_NONE);
		b(RA, S, -2.5f, 9.0f, -3.4f, -0.5f, 9.6f, -1.4f, MID, T_SECONDARY);
		g(RA, S, -1.9f, 11.8f, -2.8f, -1.1f, 12.2f, -2.0f, 0x4AA8FF, K_STATIC);
		m(ModuleType.TESLA_COIL);
		for (float y : new float[] { 3.6f, 5.2f, 6.8f }) {
			b(RA, S, -4.9f, y, -3.5f, 2.9f, y + 0.6f, -3.0f, COPPER, T_NONE);
			b(RA, S, -4.9f, y, 3.0f, 2.9f, y + 0.6f, 3.5f, COPPER, T_NONE);
			b(RA, S, -4.9f, y, -3.0f, -4.4f, y + 0.6f, 3.0f, COPPER, T_NONE);
			b(RA, S, 2.4f, y, -3.0f, 2.9f, y + 0.6f, 3.0f, COPPER, T_NONE);
		}
		b(RA, S, -2.0f, 9.6f, -3.6f, 0.0f, 10.4f, -2.8f, DARK, T_NONE);
		g(RA, S, -1.7f, 10.4f, -3.7f, -0.3f, 11.6f, -2.7f, 0, K_STATIC);
		m(ModuleType.RAIL_CANNON);
		b(RA, S, -6.4f, 0.4f, -1.6f, -5.3f, 13.2f, -0.7f, LIGHT, T_SECONDARY);
		b(RA, S, -6.4f, 0.4f, 0.7f, -5.3f, 13.2f, 1.6f, LIGHT, T_SECONDARY);
		b(RA, S, -5.6f, 0.9f, -0.7f, -4.4f, 9.0f, 0.7f, DARK, T_NONE);
		b(RA, S, -6.6f, 2.0f, -1.8f, -4.4f, 3.0f, 1.8f, MID, T_PRIMARY);
		b(RA, S, -6.6f, 9.0f, -1.8f, -4.4f, 9.8f, 1.8f, MID, T_PRIMARY);
		g(RA, S, -6.2f, 3.0f, -0.55f, -5.5f, 12.8f, 0.55f, 0, K_STATIC);
		m(ModuleType.POWER_FIST);
		b(RA, S, -4.9f, 6.6f, -3.4f, 2.9f, 11.7f, 3.4f, MID, T_PRIMARY);
		b(RA, S, -4.9f, 10.0f, -3.9f, 2.9f, 11.7f, -3.4f, LIGHT, T_SECONDARY);
		b(RA, S, -5.3f, 7.4f, -2.0f, -4.9f, 10.2f, 2.0f, DARK, T_NONE);
		b(LA, S, -2.9f, 6.6f, -3.4f, 4.9f, 11.7f, 3.4f, MID, T_PRIMARY);
		b(LA, S, -2.9f, 10.0f, -3.9f, 4.9f, 11.7f, -3.4f, LIGHT, T_SECONDARY);
		b(LA, S, 4.9f, 7.4f, -2.0f, 5.3f, 10.2f, 2.0f, DARK, T_NONE);
		m(ModuleType.MAGNETIC_CLAMP);
		b(LA, S, 4.4f, 3.8f, -1.9f, 5.7f, 7.8f, 1.9f, DARK, T_NONE);
		b(LA, S, 5.7f, 4.2f, -1.5f, 6.1f, 7.4f, -0.7f, RED, T_NONE);
		b(LA, S, 5.7f, 4.2f, 0.7f, 6.1f, 7.4f, 1.5f, LIGHT, T_NONE);
		g(LA, S, 5.7f, 5.4f, -0.4f, 6.15f, 6.2f, 0.4f, 0, K_STATIC);
		// ---------------- back (z >= 3.4); they lift away when the back doors open
		m(ModuleType.MISSILE_POD);
		b(BO, S, -3.4f, 0.4f, 3.4f, 3.4f, 5.6f, 6.2f, MID, T_SECONDARY);
		b(BO, S, -3.5f, 4.9f, 3.35f, 3.5f, 5.6f, 6.25f, HAZARD, T_NONE);
		for (float x : new float[] { -2.7f, -0.8f, 1.1f }) {
			for (float z : new float[] { 3.8f, 5.0f }) {
				b(BO, S, x, -0.5f, z, x + 1.4f, 0.4f, z + 1.0f, RED, T_NONE);
			}
		}
		b(BO, S, -3.6f, 1.6f, 3.2f, 3.6f, 2.2f, 6.4f, DARK, T_NONE);
		m(ModuleType.SHIELD_PROJECTOR);
		b(BO, S, -2.0f, 1.0f, 3.4f, 2.0f, 6.0f, 4.6f, MID, T_SECONDARY);
		b(BO, S, -3.0f, 0.0f, 4.6f, 3.0f, 7.0f, 5.2f, LIGHT, T_NONE);
		b(BO, S, -3.4f, 3.0f, 4.4f, 3.4f, 4.0f, 5.4f, DARK, T_NONE);
		g(BO, S, -0.8f, 2.7f, 5.2f, 0.8f, 4.3f, 5.7f, 0, K_STATIC);
		m(ModuleType.REPAIR_DRONE);
		b(BO, S, -2.2f, 1.5f, 3.4f, 2.2f, 4.5f, 5.8f, MID, T_SECONDARY);
		b(BO, S, -4.2f, 1.6f, 4.2f, 4.2f, 2.1f, 5.0f, DARK, T_NONE);
		b(BO, S, -4.4f, 1.2f, 4.0f, -3.2f, 1.6f, 5.2f, BLACK, T_NONE);
		b(BO, S, 3.2f, 1.2f, 4.0f, 4.4f, 1.6f, 5.2f, BLACK, T_NONE);
		b(BO, S, -2.6f, 4.5f, 3.4f, 2.6f, 5.2f, 4.4f, HAZARD, T_NONE);
		g(BO, S, -0.6f, 3.0f, 5.8f, 0.6f, 3.8f, 6.1f, 0x60FF90, K_STATIC);
		m(ModuleType.SENTRY_TURRET);
		b(BO, S, -2.6f, 0.6f, 3.4f, 2.6f, 6.6f, 5.4f, MID, T_SECONDARY);
		b(BO, S, -0.5f, -2.8f, 4.0f, 0.5f, 0.6f, 5.0f, BLACK, T_NONE);
		b(BO, S, -1.2f, -0.2f, 3.8f, 1.2f, 0.6f, 5.2f, DARK, T_NONE);
		b(BO, S, -2.4f, 6.6f, 3.6f, 2.4f, 7.4f, 5.0f, DARK, T_NONE);
		g(BO, S, 1.0f, 1.4f, 5.4f, 1.8f, 2.2f, 5.7f, 0xFF4040, K_STATIC);
		m(ModuleType.THRUSTER_PACK);
		for (float x : new float[] { -3.4f, 0.8f }) {
			b(BO, S, x, 0.6f, 3.4f, x + 2.6f, 7.6f, 5.8f, MID, T_SECONDARY);
			b(BO, S, x + 0.4f, 7.6f, 3.8f, x + 2.2f, 9.0f, 5.4f, BLACK, T_NONE);
			b(BO, S, x - 0.1f, 2.0f, 3.3f, x + 2.7f, 2.6f, 5.9f, HAZARD, T_NONE);
			g(BO, S, x + 0.7f, 9.0f, 4.1f, x + 1.9f, 11.0f, 5.1f, 0xFFB060, K_THRUST);
		}
		// ---------------- helmet (rides the shell)
		m(ModuleType.NIGHT_VISOR);
		b(HE, H, -3.2f, -5.8f, -5.95f, 3.2f, -4.6f, -5.45f, DARK, T_NONE);
		g(HE, H, -2.9f, -5.6f, -6.05f, -0.7f, -4.8f, -5.9f, 0x70FF70, K_STATIC);
		g(HE, H, 0.7f, -5.6f, -6.05f, 2.9f, -4.8f, -5.9f, 0x70FF70, K_STATIC);
		m(ModuleType.ORE_SCANNER);
		b(HE, HB, -6.0f, -7.6f, 0.9f, -5.4f, -3.8f, 1.6f, LIGHT, T_NONE);
		b(HE, HB, -6.2f, -4.2f, 0.4f, -5.4f, -2.6f, 2.2f, DARK, T_NONE);
		g(HE, HB, -6.1f, -8.3f, 0.85f, -5.3f, -7.6f, 1.65f, 0xFFB040, K_STATIC);
		m(ModuleType.TARGET_LOCK);
		b(HE, H, 2.2f, -5.5f, -6.25f, 4.6f, -3.5f, -5.45f, DARK, T_NONE);
		b(HE, H, 4.6f, -5.0f, -6.0f, 5.6f, -4.0f, -3.0f, MID, T_SECONDARY);
		g(HE, H, 2.6f, -5.1f, -6.35f, 4.2f, -3.9f, -6.25f, 0xFF3030, K_STATIC);
		m(ModuleType.REBREATHER);
		b(HE, H, -4.8f, -2.7f, -5.7f, -3.0f, -0.5f, -4.0f, RUBBER, T_NONE);
		b(HE, H, 3.0f, -2.7f, -5.7f, 4.8f, -0.5f, -4.0f, RUBBER, T_NONE);
		b(HE, H, -1.6f, -2.3f, -5.95f, 1.6f, -0.7f, -5.45f, VENT, T_NONE);
		// ---------------- chest
		m(ModuleType.REACTIVE_PLATING);
		for (float x : new float[] { -3.0f, -1.6f }) {
			for (float y : new float[] { 1.2f, 2.8f, 4.4f }) {
				b(BO, SuitPanel.CHEST_R, x, y, -3.95f, x + 0.9f, y + 0.9f, -3.4f, LIGHT, T_SECONDARY);
				b(BO, SuitPanel.CHEST_L, -x - 0.9f, y, -3.95f, -x, y + 0.9f, -3.4f, LIGHT, T_SECONDARY);
			}
		}
		m(ModuleType.CAPACITOR_BANK);
		for (float s : new float[] { -1f, 1f }) {
			float xa = s < 0 ? -5.8f : 4.4f, xb = s < 0 ? -4.4f : 5.8f;
			b(BO, S, xa, 6.0f, -1.3f, xb, 10.6f, 1.3f, COPPER, T_NONE);
			b(BO, S, xa - 0.1f, 5.6f, -1.0f, xb + 0.1f, 6.0f, 1.0f, LIGHT, T_NONE);
			g(BO, S, s < 0 ? -5.85f : 5.75f, 7.0f, -0.5f, s < 0 ? -5.75f : 5.85f, 9.8f, 0.5f, 0, K_STATIC);
		}
		m(ModuleType.OVERCLOCK);
		for (float s : new float[] { -1f, 1f }) {
			float xa = s < 0 ? -5.0f : 4.4f, xb = s < 0 ? -4.4f : 5.0f;
			b(BO, S, xa, 1.4f, -1.7f, xb, 5.2f, 1.7f, VENT, T_NONE);
			g(BO, S, s < 0 ? -5.05f : 4.95f, 2.0f, -1.2f, s < 0 ? -4.95f : 5.05f, 4.6f, 1.2f, 0xFF7020, K_STATIC);
		}
		m(ModuleType.SOLAR_PLATE);
		b(RA, S, -4.7f, -4.2f, -2.7f, 1.4f, -3.5f, 2.7f, SOLAR, T_NONE);
		b(LA, S, -1.4f, -4.2f, -2.7f, 4.7f, -3.5f, 2.7f, SOLAR, T_NONE);
		b(RA, S, -4.9f, -3.6f, -2.9f, 1.6f, -3.4f, 2.9f, LIGHT, T_NONE);
		b(LA, S, -1.6f, -3.6f, -2.9f, 4.9f, -3.4f, 2.9f, LIGHT, T_NONE);
		m(ModuleType.BREACHER_PLATING);
		b(BO, SuitPanel.CHEST_R, -3.8f, 0.2f, -4.5f, 0.0f, 2.6f, -3.4f, LIGHT, T_SECONDARY);
		b(BO, SuitPanel.CHEST_L, 0.0f, 0.2f, -4.5f, 3.8f, 2.6f, -3.4f, LIGHT, T_SECONDARY);
		b(BO, SuitPanel.CHEST_R, -1.1f, 0.7f, -5.3f, 0.0f, 2.1f, -4.5f, MID, T_PRIMARY);
		b(BO, SuitPanel.CHEST_L, 0.0f, 0.7f, -5.3f, 1.1f, 2.1f, -4.5f, MID, T_PRIMARY);
		// ---------------- legs
		m(ModuleType.SERVO_BOOSTERS);
		b(RL, SuitPanel.RLEG_OUTER, -4.0f, 1.0f, -0.6f, -3.4f, 8.2f, 0.6f, LIGHT, T_NONE);
		b(RL, SuitPanel.RLEG_OUTER, -4.2f, 0.6f, -1.0f, -3.4f, 2.2f, 1.0f, DARK, T_NONE);
		b(RL, SuitPanel.RLEG_OUTER, -4.2f, 6.4f, -1.0f, -3.4f, 8.6f, 1.0f, MID, T_SECONDARY);
		b(LL, SuitPanel.LLEG_OUTER, 3.4f, 1.0f, -0.6f, 4.0f, 8.2f, 0.6f, LIGHT, T_NONE);
		b(LL, SuitPanel.LLEG_OUTER, 3.4f, 0.6f, -1.0f, 4.2f, 2.2f, 1.0f, DARK, T_NONE);
		b(LL, SuitPanel.LLEG_OUTER, 3.4f, 6.4f, -1.0f, 4.2f, 8.6f, 1.0f, MID, T_SECONDARY);
		m(ModuleType.SEISMIC_PLATES);
		for (Part p : new Part[] { RL, LL }) {
			b(p, S, -2.8f, 13.2f, -3.6f, 2.8f, 13.8f, 2.8f, DARK, T_NONE);
			b(p, S, -2.6f, 12.4f, -3.9f, 2.6f, 13.6f, -3.3f, HAZARD, T_NONE);
		}
		m(ModuleType.STABILISERS);
		b(RL, S, -4.0f, 9.0f, -1.9f, -3.4f, 11.8f, 1.9f, LIGHT, T_SECONDARY);
		b(LL, S, 3.4f, 9.0f, -1.9f, 4.0f, 11.8f, 1.9f, LIGHT, T_SECONDARY);
		// ---------------- calf jets (every suit)
		cur = CALF;
		for (Part p : new Part[] { RL, LL }) {
			SuitPanel back = p == RL ? SuitPanel.RLEG_BACK : SuitPanel.LLEG_BACK;
			b(p, back, -1.3f, 5.8f, 3.3f, 1.3f, 8.8f, 4.5f, MID, T_SECONDARY);
			b(p, back, -0.9f, 8.8f, 3.5f, 0.9f, 9.6f, 4.3f, BLACK, T_NONE);
			g(p, back, -0.6f, 9.6f, 3.65f, 0.6f, 11.6f, 4.15f, 0xFFB060, K_JETS);
		}
	}

	private SuitModules() {
	}

	static void render(PoseStack pose, MultiBufferSource buffers, int light, int overlay, Part part, SuitRenderer.Look look) {
		SuitData suit = look.suit;
		for (int pass = 0; pass <= suit.modules.length; pass++) {
			List<B> list;
			ModuleType type = null;
			if (pass == suit.modules.length) {
				list = CALF;
			} else {
				type = suit.modules[pass];
				if (type == null) {
					continue;
				}
				list = GEO.get(type);
			}
			if (list == null) {
				continue;
			}
			boolean back = type != null && type.part == com.projecthero.mod.powerarmour.BodyPart.BACK;
			if (back && !look.backDocked && (type == ModuleType.REPAIR_DRONE || type == ModuleType.SENTRY_TURRET)) {
				continue;
			}
			int barrel = 0;
			for (B b : list) {
				if (b.part != part) {
					continue;
				}
				pose.pushPose();
				SuitRenderer.applyPanel(pose, b.panel, look);
				if (back) {
					float k = look.open(SuitPanel.BACK_R); // only when the back is the side that opens
					pose.translate(0f, -4.5f * k / 16f, 2.5f * k / 16f);
				}
				float y1 = b.y1;
				boolean draw = true;
				int rgb = colour(b, suit, look);
				switch (b.kind) {
					case K_BARREL -> {
						float spin = look.firing ? look.time * 45f : 0f;
						pose.translate(BARREL_X / 16f, 0f, 0f);
						pose.mulPose(Axis.YP.rotationDegrees(spin + barrel * 120f));
						pose.translate(0.62f / 16f, 0f, 0f);
						barrel++;
					}
					case K_THRUST -> {
						draw = look.thrust;
						y1 = b.y0 + (b.y1 - b.y0) * (0.8f + 0.4f * Mth.sin(look.time * 2.7f + b.x0));
					}
					case K_JETS -> {
						draw = look.jets || look.thrust;
						y1 = b.y0 + (b.y1 - b.y0) * (0.7f + 0.5f * Mth.sin(look.time * 3.1f + b.x0));
					}
					case K_MUZZLE -> draw = look.firing && ((int) look.time & 1) == 0;
					default -> {
					}
				}
				if (draw) {
					int cx = (b.sw % 8) * 4, cy = (b.sw / 8) * 4;
					IronManBoxes.Box box = IronManBoxes.Box.swatch(b.x0, b.y0, b.z0, b.x1, y1, b.z1, cx + 1f, cy + 1f, 2f, 2f);
					// fetched per box: asking the buffer source for another render type ends the previous one
					VertexConsumer vc = buffers.getBuffer(b.glow() ? RenderType.eyes(SuitRenderer.MODULE_TEX) : RenderType.entityCutout(SuitRenderer.MODULE_TEX));
					IronManBoxes.draw(pose, vc, box, 32, 32, b.glow() ? LightTexture.FULL_BRIGHT : light, overlay, rgb);
				}
				pose.popPose();
			}
		}
	}

	private static int colour(B b, SuitData suit, SuitRenderer.Look look) {
		int rgb = switch (b.tint) {
			case T_PRIMARY -> suit.primaryColour();
			case T_SECONDARY -> suit.secondaryColour();
			case T_GLOW -> suit.glowColour();
			case T_FIXED -> b.rgb;
			default -> 0xFFFFFF;
		};
		if (b.glow() && b.kind == K_STATIC) {
			rgb = SuitRenderer.scale(rgb, 0.25f + 0.75f * look.glow);
		}
		return 0xFF000000 | rgb;
	}
}
