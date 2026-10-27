package com.projecthero.mod.client.quirk.hellflame;

import static com.projecthero.mod.client.quirk.QuirkFirstPersonPose.clip;
import static com.projecthero.mod.client.quirk.QuirkFirstPersonPose.f;

import com.projecthero.mod.client.quirk.QuirkFirstPersonPose;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.hellflame.Hellflame;
import com.projecthero.mod.quirk.hellflame.HellflameConfig;

/**
 * v0.17.1 (Quirks revamp): Endeavor's first-person arm clips ({@link QuirkFirstPersonPose}, 13 columns: tick, main pitch /
 * yaw / roll / x / y / z, off pitch / yaw / roll / x / y / z). Calibrated in the screenshot harness: a POSITIVE pitch tips
 * the arm forward so the fist points into the screen (a punch, 35-50), a large one (60+) brings it up high, a NEGATIVE one
 * stands the arm up beside the view; yaw + swings it toward the centre; x toward the centre, y up, z negative into the
 * screen. Every move has one; Prominence Burn shows the whole body ({@code fullBody}).
 */
final class HellflameFirstPerson {
	private HellflameFirstPerson() {
	}

	private static final float[] ZERO = f(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

	private static float[] end(float tick) {
		return f(tick, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
	}

	/** A main-hand punch driven {@code depth} into the screen. */
	private static float[] punch(float tick, float depth) {
		return f(tick, 44, 18, -6, 0.14f, 0.32f, -depth, 0, 0, 0, 0, 0, 0);
	}

	/** Both arms dropped low out of the view (palms thrust back / down for the jets). */
	private static float[] low(float tick) {
		return f(tick, -4, -8, 0, -0.06f, -0.4f, 0.28f, -4, -8, 0, -0.06f, -0.4f, 0.28f);
	}

	static void register() {
		Quirk q = Quirk.HELLFLAME;
		// R: Hell Spider -- the right hand rises into view out to the side, fingers splayed, then sweeps right to left
		QuirkFirstPersonPose.register(q, Hellflame.ANIM_SPIDER, clip(ZERO, f(3, -38, -24, -16, -0.04f, 0.34f, -0.12f, 0, 0, 0, 0, 0, 0),
				f(6, -42, 34, 14, 0.24f, 0.36f, -0.2f, 0, 0, 0, 0, 0, 0), f(11, -40, 36, 14, 0.24f, 0.35f, -0.2f, 0, 0, 0, 0, 0, 0), end(16)));
		// Shift+R: the Mesh -- both hands weave the strings, crossing and parting
		QuirkFirstPersonPose.register(q, Hellflame.ANIM_MESH, clip(ZERO, f(4, 44, 34, 0, 0.2f, 0.36f, -0.24f, 44, 34, 0, 0.2f, 0.36f, -0.24f),
				f(8, 50, -22, -18, -0.12f, 0.4f, -0.2f, 50, -22, -18, -0.12f, 0.4f, -0.2f),
				f(13, 48, -22, -18, -0.12f, 0.39f, -0.2f, 48, -22, -18, -0.12f, 0.39f, -0.2f), end(18)).twoHanded());
		// G: Flashfire Fist -- the jab (right), the cross (left), the jet-assisted straight (right, deep)
		QuirkFirstPersonPose.register(q, Hellflame.ANIM_FIST_1, clip(ZERO, f(1, 14, -8, 0, -0.04f, 0.04f, 0.16f, 0, 0, 0, 0, 0, 0), punch(3, 0.42f),
				punch(6, 0.38f), end(10)));
		QuirkFirstPersonPose.register(q, Hellflame.ANIM_FIST_2, clip(ZERO, f(1, 10, 0, 0, 0, -0.06f, 0.08f, 14, -8, 0, -0.04f, 0.04f, 0.16f),
				f(3, 6, -6, 0, -0.04f, -0.12f, 0.12f, 44, 18, -6, 0.14f, 0.32f, -0.42f), f(6, 6, -6, 0, -0.04f, -0.12f, 0.12f, 42, 18, -6, 0.14f, 0.31f, -0.38f),
				end(10)).twoHanded());
		QuirkFirstPersonPose.register(q, Hellflame.ANIM_FIST_3, clip(ZERO, f(2, 12, -14, 0, -0.06f, -0.02f, 0.28f, 0, 0, 0, 0, 0, 0), punch(4, 0.62f),
				punch(9, 0.56f), end(14)));
		// Shift+G: Vanishing Fist -- he appears coiled, then strikes
		QuirkFirstPersonPose.register(q, Hellflame.ANIM_VANISH, clip(f(0, 12, -12, 0, -0.06f, -0.02f, 0.22f, 0, 0, 0, 0, 0, 0),
				f(3, 12, -14, 0, -0.06f, -0.04f, 0.24f, 0, 0, 0, 0, 0, 0), punch(5, 0.56f), punch(10, 0.52f), end(14)));
		// hold G: Hellblast -- the fist drawn back to the hip (out of view), the left hand out sighting; then the arm locked
		// out along the beam with the left hand gripping the forearm
		int max = HellflameConfig.HELLBLAST_MAX_CHARGE;
		QuirkFirstPersonPose.register(q, Hellflame.ANIM_HELLBLAST, clip(ZERO, f(4, -4, -10, 0, -0.06f, -0.4f, 0.3f, 42, 30, 0, 0.2f, 0.32f, -0.32f),
				f(max + 2, -4, -10, 0, -0.06f, -0.4f, 0.3f, 42, 30, 0, 0.2f, 0.32f, -0.32f), end(max + 6)).twoHanded());
		int beam = HellflameConfig.HELLBLAST_BASE_TICKS + max;
		QuirkFirstPersonPose.register(q, Hellflame.ANIM_BEAM, clip(f(0, 30, 16, 0, 0.12f, 0.24f, -0.3f, 30, 34, 4, 0.26f, 0.2f, -0.22f),
				f(2, 42, 16, 0, 0.13f, 0.32f, -0.36f, 36, 38, 6, 0.3f, 0.26f, -0.26f), f(beam, 40, 16, 0, 0.13f, 0.31f, -0.34f, 34, 38, 6, 0.3f, 0.25f, -0.26f),
				end(beam + 5)).twoHanded());
		// X: Jet Burst -- palms thrown back and down out of sight
		QuirkFirstPersonPose.register(q, Hellflame.ANIM_BURST, clip(ZERO, low(2), low(7), end(11)).twoHanded());
		// Shift+X: Jet Burn -- a fist-first rocket, the right fist ahead, the left back
		QuirkFirstPersonPose.register(q, Hellflame.ANIM_JET, clip(ZERO, f(3, 12, -12, 0, -0.06f, -0.02f, 0.2f, -4, -8, 0, -0.06f, -0.4f, 0.28f),
				f(5, 46, 18, -6, 0.14f, 0.33f, -0.58f, -4, -8, 0, -0.06f, -0.4f, 0.28f),
				f(10, 46, 18, -6, 0.14f, 0.33f, -0.56f, -4, -8, 0, -0.06f, -0.4f, 0.28f), end(14)).twoHanded());
		// hold X: the take-off -- palms pushed down
		QuirkFirstPersonPose.register(q, Hellflame.ANIM_FLIGHT, clip(ZERO, low(3), end(10)).twoHanded());
		// Z: Hellflame Barrage -- eight alternating jabs
		{
			int jabs = HellflameConfig.BARRAGE_JABS, iv = HellflameConfig.BARRAGE_INTERVAL;
			float[][] fr = new float[2 + jabs * 2 + 1][];
			fr[0] = ZERO;
			fr[1] = f(1, 24, 14, 0, 0.06f, 0.14f, -0.06f, 24, 14, 0, 0.06f, 0.14f, -0.06f);
			int k = 2;
			for (int j = 0; j < jabs; j++) {
				boolean right = j % 2 == 0;
				float t = 1 + j * iv;
				fr[k++] = f(t + 1.5f, right ? 44 : 20, right ? 18 : 12, 0, right ? 0.14f : 0.06f, right ? 0.32f : 0.12f, right ? -0.44f : -0.04f,
						right ? 20 : 44, right ? 12 : 18, 0, right ? 0.06f : 0.14f, right ? 0.12f : 0.32f, right ? -0.04f : -0.44f);
				fr[k++] = f(t + 3.5f, 24, 14, 0, 0.06f, 0.14f, -0.06f, 24, 14, 0, 0.06f, 0.14f, -0.06f);
			}
			fr[k] = end(1 + jabs * iv + 3);
			QuirkFirstPersonPose.register(q, Hellflame.ANIM_BARRAGE, clip(fr).twoHanded());
		}
		// Shift+Z: the Pillar -- the fist raised high, then driven down at the ground
		QuirkFirstPersonPose.register(q, Hellflame.ANIM_PILLAR, clip(ZERO, f(4, -58, 6, 0, 0.04f, 0.55f, 0.06f, 0, 0, 0, 0, 0, 0),
				f(8, 78, 14, 0, 0.1f, 0.0f, -0.32f, 0, 0, 0, 0, 0, 0), f(14, 76, 14, 0, 0.1f, 0.0f, -0.32f, 0, 0, 0, 0, 0, 0), end(18)));
		// C: Flame Wave -- both hands wound to the right, swept round to the left
		QuirkFirstPersonPose.register(q, Hellflame.ANIM_WAVE, clip(ZERO, f(3, 38, -36, -16, -0.18f, 0.3f, -0.2f, 34, -20, -10, -0.08f, 0.26f, -0.18f),
				f(6, 42, 44, 16, 0.28f, 0.33f, -0.32f, 40, 50, 16, 0.32f, 0.3f, -0.3f), f(12, 40, 44, 16, 0.28f, 0.32f, -0.3f, 38, 50, 16, 0.32f, 0.29f, -0.28f),
				end(16)).twoHanded());
		// Shift+C: Flame Cloak -- fists clenched before the chest, chest out
		QuirkFirstPersonPose.register(q, Hellflame.ANIM_CLOAK, clip(ZERO, f(5, 24, 30, 0, 0.14f, 0.12f, 0.04f, 24, 30, 0, 0.14f, 0.12f, 0.04f),
				f(11, 26, 32, 0, 0.14f, 0.14f, 0.04f, 26, 32, 0, 0.14f, 0.14f, 0.04f), end(16)).twoHanded());
		// V: Heat Vent -- the arms flung wide
		QuirkFirstPersonPose.register(q, Hellflame.ANIM_COOL, clip(ZERO, f(4, 24, -44, -30, -0.28f, 0.32f, 0f, 24, -44, -30, -0.28f, 0.32f, 0f),
				f(16, 22, -44, -30, -0.28f, 0.31f, 0f, 22, -44, -30, -0.28f, 0.31f, 0f), end(22)).twoHanded());
		// Shift+V: Fire Wall -- the palm swept low along the ground, right to left
		QuirkFirstPersonPose.register(q, Hellflame.ANIM_WALL, clip(ZERO, f(4, 66, -32, 0, -0.16f, 0.02f, -0.2f, 0, 0, 0, 0, 0, 0),
				f(8, 66, 42, 0, 0.24f, 0.02f, -0.3f, 0, 0, 0, 0, 0, 0), f(13, 64, 42, 0, 0.24f, 0.02f, -0.3f, 0, 0, 0, 0, 0, 0), end(18)));
		// the Forge Hand -- palms cupped together in front
		QuirkFirstPersonPose.register(q, Hellflame.ANIM_FORGE, clip(ZERO, f(3, 42, 30, 0, 0.18f, 0.18f, -0.1f, 42, 30, 0, 0.18f, 0.18f, -0.1f),
				f(10, 42, 30, 0, 0.18f, 0.18f, -0.1f, 42, 30, 0, 0.18f, 0.18f, -0.1f), end(14)).twoHanded());
		// burnt out: the arms drop
		QuirkFirstPersonPose.register(q, Hellflame.ANIM_OVERHEAT, clip(ZERO, low(5), low(34), end(40)).twoHanded());
		// Plus Ultra: Prominence Burn -- the whole body (the sun, then the dive)
		int prom = HellflameConfig.PROM_IGNITE_TICKS + HellflameConfig.PROM_MAX_TICKS;
		QuirkFirstPersonPose.register(q, Hellflame.ANIM_PROMINENCE, clip(ZERO, f(6, 24, -46, -32, -0.28f, 0.32f, 0f, 24, -46, -32, -0.28f, 0.32f, 0f),
				f(HellflameConfig.PROM_IGNITE_TICKS, 24, -46, -32, -0.28f, 0.32f, 0f, 24, -46, -32, -0.28f, 0.32f, 0f),
				f(HellflameConfig.PROM_IGNITE_TICKS + 3, 46, 26, 0, 0.16f, 0.34f, -0.5f, 46, 26, 0, 0.16f, 0.34f, -0.5f),
				f(prom, 46, 26, 0, 0.16f, 0.34f, -0.5f, 46, 26, 0, 0.16f, 0.34f, -0.5f), end(prom + 5)).twoHanded());
		QuirkFirstPersonPose.fullBody(q, Hellflame.ANIM_PROMINENCE);
	}
}
