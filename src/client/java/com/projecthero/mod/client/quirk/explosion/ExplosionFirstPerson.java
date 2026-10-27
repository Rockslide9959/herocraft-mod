package com.projecthero.mod.client.quirk.explosion;

import static com.projecthero.mod.client.quirk.QuirkFirstPersonPose.clip;
import static com.projecthero.mod.client.quirk.QuirkFirstPersonPose.f;

import com.projecthero.mod.client.quirk.QuirkFirstPersonPose;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.explosion.Explosion;
import com.projecthero.mod.quirk.explosion.ExplosionConfig;

/**
 * v0.17.1 (Quirks revamp): Bakugo's first-person arm clips ({@link QuirkFirstPersonPose}, 13 columns: tick, main pitch / yaw /
 * roll / x / y / z, off pitch / yaw / roll / x / y / z). Calibrated in the screenshot harness: a POSITIVE pitch tips the arm
 * forward so the fist points into the screen (a palm thrust, 35-50), a large one (60+) brings the fist up high, a NEGATIVE
 * one stands the arm up beside the view; yaw + swings it toward the centre; offsets x toward the centre, y up, z negative
 * into the screen. Every move has one; the Howitzer, the ultimate and the Spin Dodge show the whole body ({@code fullBody}).
 */
final class ExplosionFirstPerson {
	private ExplosionFirstPerson() {
	}

	private static final float[] ZERO = f(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

	private static float[] end(float tick) {
		return f(tick, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
	}

	/** The right palm thrust at the crosshair with the left hand clamped on the wrist; {@code kick} the recoil (the fist rises). */
	private static float[] brace(float tick, float kick) {
		return f(tick, 42 - kick, 20, -4, 0.14f, 0.33f + kick * 0.005f, -0.36f + kick * 0.008f, 36, 44, 10, 0.32f, 0.28f, -0.3f);
	}

	/** Both arms dropped low out of the view (thrown back for a blast, or hanging). */
	private static float[] low(float tick) {
		return f(tick, -4, -8, 0, -0.06f, -0.4f, 0.28f, -4, -8, 0, -0.06f, -0.4f, 0.28f);
	}

	static void register() {
		Quirk q = Quirk.EXPLOSION;
		// R: AP Shot -- palm out at the crosshair, the left hand braces the wrist, a sharp recoil
		QuirkFirstPersonPose.register(q, Explosion.ANIM_AP_SHOT, clip(ZERO, brace(2, 0), brace(4, 16), brace(7, 3), end(11)).twoHanded());
		// Shift+R: the Auto-Cannon -- both palms out, pumping in turn with the shots
		{
			int shots = ExplosionConfig.AUTO_SHOTS, iv = ExplosionConfig.AUTO_INTERVAL;
			float[][] fr = new float[shots + 3][];
			fr[0] = ZERO;
			fr[1] = f(1, 40, 22, 0, 0.12f, 0.3f, -0.32f, 40, 22, 0, 0.12f, 0.3f, -0.32f);
			for (int i = 0; i < shots; i++) {
				boolean right = i % 2 == 0;
				fr[2 + i] = f(2 + i * iv, right ? 28 : 42, 22, 0, 0.12f, right ? 0.36f : 0.3f, right ? -0.24f : -0.34f, right ? 42 : 28, 22, 0, 0.12f,
						right ? 0.3f : 0.36f, right ? -0.34f : -0.24f);
			}
			fr[shots + 2] = end(2 + shots * iv + 5);
			QuirkFirstPersonPose.register(q, Explosion.ANIM_AUTO, clip(fr).twoHanded());
		}
		// hold R: Pierce charging (braced, held), then the shot's big kick
		QuirkFirstPersonPose.register(q, Explosion.ANIM_PIERCE_CHARGE,
				clip(ZERO, brace(3, 0), brace(ExplosionConfig.PIERCE_CHARGE_TICKS + 4, 0), end(ExplosionConfig.PIERCE_CHARGE_TICKS + 8)).twoHanded());
		QuirkFirstPersonPose.register(q, Explosion.ANIM_PIERCE, clip(brace(0, 0), brace(2, 30), brace(8, 5), end(13)).twoHanded());
		// G: Explosive Punch -- a short wind-up, then the fist driven deep into the screen
		QuirkFirstPersonPose.register(q, Explosion.ANIM_PUNCH, clip(ZERO, f(2, 12, -10, 0, -0.05f, 0.0f, 0.22f, 0, 0, 0, 0, 0, 0),
				f(4, 46, 18, -6, 0.15f, 0.33f, -0.58f, 0, 0, 0, 0, 0, 0), f(7, 44, 16, -6, 0.14f, 0.31f, -0.52f, 0, 0, 0, 0, 0, 0), end(11)));
		// Shift+G: Blast Rush -- both palms raised high, then slammed down at the ground
		QuirkFirstPersonPose.register(q, Explosion.ANIM_BLAST_RUSH, clip(ZERO, f(3, -55, 12, 0, 0.06f, 0.55f, 0.05f, -55, 12, 0, 0.06f, 0.55f, 0.05f),
				f(5, 78, 18, 0, 0.12f, 0.02f, -0.3f, 78, 18, 0, 0.12f, 0.02f, -0.3f), f(13, 76, 18, 0, 0.12f, 0.02f, -0.3f, 76, 18, 0, 0.12f, 0.02f, -0.3f),
				end(17)).twoHanded());
		// hold G: Crush Grip -- the hand clamped on a face high in front, kicking with each blast, then the throw sweeps aside
		{
			int[] b = ExplosionConfig.GRIP_BEATS;
			QuirkFirstPersonPose.register(q, Explosion.ANIM_GRIP, clip(ZERO, f(3, 58, 18, 0, 0.14f, 0.44f, -0.42f, 0, 0, 0, 0, 0, 0),
					f(b[0], 48, 18, 6, 0.14f, 0.5f, -0.36f, 0, 0, 0, 0, 0, 0), f(b[0] + 2, 58, 18, 0, 0.14f, 0.44f, -0.42f, 0, 0, 0, 0, 0, 0),
					f(b[1], 48, 18, -6, 0.14f, 0.5f, -0.36f, 0, 0, 0, 0, 0, 0), f(b[1] + 2, 58, 18, 0, 0.14f, 0.44f, -0.42f, 0, 0, 0, 0, 0, 0),
					f(b[2], 40, 18, 8, 0.14f, 0.54f, -0.3f, 0, 0, 0, 0, 0, 0), f(ExplosionConfig.GRIP_THROW_AT, 50, -30, 20, -0.15f, 0.36f, -0.4f, 0, 0, 0, 0, 0, 0),
					f(ExplosionConfig.GRIP_THROW_AT + 3, 40, 55, -10, 0.38f, 0.26f, -0.38f, 0, 0, 0, 0, 0, 0), end(ExplosionConfig.GRIP_THROW_AT + 8)));
		}
		// X: the Dash / take-off -- both palms snapped down and back out of sight
		QuirkFirstPersonPose.register(q, Explosion.ANIM_DASH, clip(ZERO, low(2), low(8), end(12)).twoHanded());
		QuirkFirstPersonPose.register(q, Explosion.ANIM_PROPEL, clip(ZERO, low(2), end(8)).twoHanded());
		// Shift+X: the Spin Dodge (the whole body spins; the arms tucked in)
		QuirkFirstPersonPose.register(q, Explosion.ANIM_DODGE, clip(ZERO, f(2, 20, 40, 0, 0.24f, 0.12f, -0.05f, 20, 40, 0, 0.24f, 0.12f, -0.05f),
				end(ExplosionConfig.DODGE_TICKS + 5)).twoHanded());
		QuirkFirstPersonPose.fullBody(q, Explosion.ANIM_DODGE);
		// Z: Cluster -- arms swept out wide and low all round
		QuirkFirstPersonPose.register(q, Explosion.ANIM_CLUSTER, clip(ZERO, f(2, 30, 14, 0, 0.06f, 0.2f, -0.14f, 30, 14, 0, 0.06f, 0.2f, -0.14f),
				f(4, 30, -50, -24, -0.34f, 0.16f, -0.08f, 30, -50, -24, -0.34f, 0.16f, -0.08f),
				f(8, 30, -50, -24, -0.34f, 0.16f, -0.08f, 30, -50, -24, -0.34f, 0.16f, -0.08f), end(12)).twoHanded());
		// Shift+Z: the Uppercut -- the fist rises from low to high through the screen
		QuirkFirstPersonPose.register(q, Explosion.ANIM_UPPERCUT, clip(ZERO, f(2, 6, 6, 0, 0.02f, -0.2f, 0.08f, 0, 0, 0, 0, 0, 0),
				f(4, 70, 16, -6, 0.13f, 0.55f, -0.3f, 0, 0, 0, 0, 0, 0), f(8, 68, 16, -6, 0.13f, 0.53f, -0.3f, 0, 0, 0, 0, 0, 0), end(12)));
		// C: Howitzer Impact and the ultimate: the whole body (the spin), arms flung out
		float[] flung = f(3, 24, -55, -30, -0.4f, 0.24f, 0f, 24, -55, -30, -0.4f, 0.24f, 0f);
		QuirkFirstPersonPose.register(q, Explosion.ANIM_HOWITZER, clip(ZERO, flung, f(40, 24, -55, -30, -0.4f, 0.24f, 0f, 24, -55, -30, -0.4f, 0.24f, 0f),
				end(46)).twoHanded());
		QuirkFirstPersonPose.fullBody(q, Explosion.ANIM_HOWITZER);
		QuirkFirstPersonPose.register(q, Explosion.ANIM_HOWITZER_CLUSTER, clip(ZERO, flung,
				f(120, 24, -55, -30, -0.4f, 0.24f, 0f, 24, -55, -30, -0.4f, 0.24f, 0f), end(126)).twoHanded());
		QuirkFirstPersonPose.fullBody(q, Explosion.ANIM_HOWITZER_CLUSTER);
		// Shift+C: the Gauntlet Grenade -- the left hand pulls the pin, the right levels and fills, then the recoil throws it up
		int gc = ExplosionConfig.GAUNTLET_CHARGE;
		QuirkFirstPersonPose.register(q, Explosion.ANIM_GAUNTLET, clip(ZERO, f(3, 34, 22, 0, 0.12f, 0.24f, -0.24f, 40, 46, 4, 0.34f, 0.3f, -0.28f),
				f(6, 42, 20, 0, 0.14f, 0.32f, -0.38f, 14, 10, 0, 0.05f, 0.0f, -0.04f), f(gc, 42, 20, 0, 0.14f, 0.32f, -0.38f, 10, 12, 0, 0.06f, -0.05f, -0.06f),
				f(gc + 2, 8, 20, 6, 0.14f, 0.5f, -0.1f, 4, 12, 0, 0.06f, 0.0f, -0.06f), f(gc + 7, 34, 20, 0, 0.14f, 0.36f, -0.3f, 4, 8, 0, 0, -0.1f, 0),
				end(gc + 13)).twoHanded());
		// V: Stun Grenade -- palms pressed together before the face, then flung wide in the flash
		QuirkFirstPersonPose.register(q, Explosion.ANIM_STUN, clip(ZERO, f(3, 46, 42, 0, 0.34f, 0.42f, -0.16f, 46, 42, 0, 0.34f, 0.42f, -0.16f),
				f(5, 26, -36, -22, -0.24f, 0.32f, -0.1f, 26, -36, -22, -0.24f, 0.32f, -0.1f),
				f(10, 26, -36, -22, -0.24f, 0.3f, -0.1f, 26, -36, -22, -0.24f, 0.3f, -0.1f), end(15)).twoHanded());
		// Shift+V: Cool Off -- the arms dropped and shaken out
		QuirkFirstPersonPose.register(q, Explosion.ANIM_COOL, clip(ZERO, low(4), low(ExplosionConfig.COOL_TICKS - 4), end(ExplosionConfig.COOL_TICKS)).twoHanded());
		// arms shot: they hang
		QuirkFirstPersonPose.register(q, Explosion.ANIM_STRAIN,
				clip(ZERO, low(4), low(ExplosionConfig.STRAIN_TICKS - 5), end(ExplosionConfig.STRAIN_TICKS)).twoHanded());
		// blast mining: a short punch down at the block
		QuirkFirstPersonPose.register(q, Explosion.ANIM_MINE, clip(ZERO, f(2, 30, 10, 0, 0.05f, 0.12f, 0.08f, 0, 0, 0, 0, 0, 0),
				f(4, 66, 16, 8, 0.12f, 0.05f, -0.44f, 0, 0, 0, 0, 0, 0), end(9)));
	}
}
