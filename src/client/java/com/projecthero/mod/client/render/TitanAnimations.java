package com.projecthero.mod.client.render;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.projecthero.mod.titan.entity.TitanEntity;
import com.projecthero.mod.titan.entity.TitanEntity.Anim;

import net.minecraft.util.Mth;

/**
 * v0.14.4: keyframed body animations for the Titan world boss, played on the vanilla humanoid (zombie) rig.
 *
 * <p>A pose is a flat float array: head / arms / legs rotations in <b>degrees</b>, plus three whole-torso
 * controls -- {@code BEND} (lean forward, pivoting at the hips), {@code TWIST} (turn the shoulders), {@code ROLL}
 * (lean sideways) -- a {@code DROP} (lower the upper body, model pixels) and a weight {@code W} that fades the
 * clip in over the Titan's own walk/idle animation. Keyframes are eased (smoothstep, or accelerate-into for
 * strike frames). Every telegraphed attack strikes at {@code TitanEntity.WINDUP_TICKS} (40) to line up with the
 * server's damage tick; the first 40 ticks are the readable wind-up.
 *
 * <p>Conventions (vanilla model space): arm/leg xRot negative = raised forward; right arm +zRot = out to the side
 * (left arm mirrored); right arm +yRot = swung outward, -yRot = across the body.
 */
public final class TitanAnimations {
	public static final int HX = 0, HY = 1, HZ = 2, BEND = 3, TWIST = 4, ROLL = 5, DROP = 6,
			RX = 7, RY = 8, RZ = 9, LX = 10, LY = 11, LZ = 12,
			RLX = 13, RLY = 14, RLZ = 15, LLX = 16, LLY = 17, LLZ = 18, W = 19, N = 20;

	/** Cross-fade length between two clips, in ticks (a clip may shorten it -- impacts should be instant). */
	private static final float DEFAULT_BLEND = 5.0f;

	private TitanAnimations() {
	}

	// ------------------------------------------------------------------ pose builder

	/** Fluent pose builder. Starts from the combat-ready stance (zombie arms forward, full weight). */
	static final class P {
		final float[] v = new float[N];

		P() {
			v[RX] = -80;
			v[RZ] = 5;
			v[LX] = -80;
			v[LZ] = -5;
			v[W] = 1;
		}

		P head(float x, float y, float z) {
			v[HX] = x;
			v[HY] = y;
			v[HZ] = z;
			return this;
		}

		P torso(float bend, float twist, float roll) {
			v[BEND] = bend;
			v[TWIST] = twist;
			v[ROLL] = roll;
			return this;
		}

		P drop(float d) {
			v[DROP] = d;
			return this;
		}

		P rArm(float x, float y, float z) {
			v[RX] = x;
			v[RY] = y;
			v[RZ] = z;
			return this;
		}

		P lArm(float x, float y, float z) {
			v[LX] = x;
			v[LY] = y;
			v[LZ] = z;
			return this;
		}

		P rLeg(float x, float y, float z) {
			v[RLX] = x;
			v[RLY] = y;
			v[RLZ] = z;
			return this;
		}

		P lLeg(float x, float y, float z) {
			v[LLX] = x;
			v[LLY] = y;
			v[LLZ] = z;
			return this;
		}

		P w(float w) {
			v[W] = w;
			return this;
		}
	}

	static P p() {
		return new P();
	}

	/** The neutral stance at zero weight -- a clip's first and last frame, so it fades in/out of the walk. */
	static P rest() {
		return new P().w(0);
	}

	private enum Ease { SMOOTH, IN, OUT }

	private record Key(float t, float[] v, Ease ease) {
	}

	static final class Clip {
		final List<Key> keys = new ArrayList<>();
		boolean legsFromBase;
		float blendIn = DEFAULT_BLEND;

		Clip k(float t, P pose) {
			keys.add(new Key(t, pose.v.clone(), Ease.SMOOTH));
			return this;
		}

		/** A strike frame: accelerates into it, so the blow lands fast. */
		Clip strike(float t, P pose) {
			keys.add(new Key(t, pose.v.clone(), Ease.IN));
			return this;
		}

		Clip out(float t, P pose) {
			keys.add(new Key(t, pose.v.clone(), Ease.OUT));
			return this;
		}

		Clip legsFromBase() {
			legsFromBase = true;
			return this;
		}

		Clip blendIn(float ticks) {
			blendIn = ticks;
			return this;
		}

		void sample(float t, float[] out) {
			Key first = keys.get(0);
			if (t <= first.t) {
				System.arraycopy(first.v, 0, out, 0, N);
				return;
			}
			for (int i = 1; i < keys.size(); i++) {
				Key b = keys.get(i);
				if (t <= b.t) {
					Key a = keys.get(i - 1);
					float s = (t - a.t) / Math.max(1.0e-3f, b.t - a.t);
					s = switch (b.ease) {
						case IN -> s * s;
						case OUT -> 1.0f - (1.0f - s) * (1.0f - s);
						default -> s * s * (3.0f - 2.0f * s);
					};
					for (int j = 0; j < N; j++) {
						out[j] = Mth.lerp(s, a.v[j], b.v[j]);
					}
					return;
				}
			}
			System.arraycopy(keys.get(keys.size() - 1).v, 0, out, 0, N);
		}
	}

	// ------------------------------------------------------------------ clips

	private static final Map<Anim, Clip> CLIPS = new EnumMap<>(Anim.class);

	static {
		// Right haymaker: shoulder cocks back through the wind-up, then the whole torso turns into the punch.
		CLIPS.put(Anim.PUNCH, new Clip()
				.k(0, rest())
				.k(10, p().torso(0, 25, 0).rArm(35, 0, 20).lArm(-70, 20, -5).rLeg(12, 0, 0).lLeg(-10, 0, 0))
				.k(36, p().torso(-3, 38, 0).drop(0.8f).rArm(55, 10, 25).lArm(-75, 25, -5).head(-5, 0, 0)
						.rLeg(18, 0, 0).lLeg(-15, 0, 0))
				.strike(40, p().torso(15, -30, 0).drop(0.5f).rArm(-100, -15, 0).lArm(-25, 0, -10).head(-15, 0, 0)
						.rLeg(22, 0, 0).lLeg(-28, 0, 0))
				.k(50, p().torso(15, -30, 0).drop(0.5f).rArm(-100, -15, 0).lArm(-25, 0, -10).head(-15, 0, 0)
						.rLeg(22, 0, 0).lLeg(-28, 0, 0))
				.k(62, rest()));

		// Wide backhand: winds the right arm far out to the side, then sweeps it low across the front.
		P sweepThrough = p().torso(28, -50, 8).drop(1).rArm(-55, -80, 0).lArm(-40, 0, -30).head(-25, 0, 0)
				.rLeg(0, 0, 8).lLeg(0, 0, -8);
		CLIPS.put(Anim.SWEEP, new Clip()
				.k(0, rest())
				.k(12, p().torso(18, 40, -8).rArm(-35, 70, 30).lArm(-50, -20, -35).head(-15, 0, 0)
						.rLeg(0, 0, 5).lLeg(0, 0, -5))
				.k(36, p().torso(28, 55, -10).drop(1).rArm(-25, 85, 40).lArm(-45, -25, -40).head(-25, 0, 0)
						.rLeg(0, 0, 8).lLeg(0, 0, -8))
				.strike(40, p().torso(30, 0, 0).drop(1.2f).rArm(-55, 0, 10).lArm(-45, -10, -35).head(-25, 0, 0)
						.rLeg(0, 0, 8).lLeg(0, 0, -8))
				.out(45, sweepThrough)
				.k(52, sweepThrough)
				.k(64, rest()));

		// Stomp: right knee high, arms out for balance, leaning away -- then the foot comes down.
		P stompDown = p().torso(15, 0, 0).drop(1.5f).rArm(-30, 0, 35).lArm(-30, 0, -35).rLeg(-5, 0, 5)
				.lLeg(8, 0, -3).head(15, 0, 0);
		CLIPS.put(Anim.STOMP, new Clip()
				.k(0, rest())
				.k(14, p().torso(-5, 0, 8).rArm(-40, 0, 50).lArm(-40, 0, -50).rLeg(-65, 0, 5).lLeg(5, 0, 0)
						.head(10, 0, 0))
				.k(34, p().torso(-8, 0, 10).drop(-0.5f).rArm(-50, 0, 60).lArm(-50, 0, -60).rLeg(-85, 0, 8)
						.lLeg(6, 0, 0).head(20, 0, 0))
				.strike(40, stompDown)
				.k(48, stompDown)
				.k(62, rest()));

		// Ground Slam: both fists raised high overhead, leaning back -- then a double hammer-fist into the ground.
		P slamDown = p().torso(45, 0, 0).drop(2.2f).rArm(-75, 0, -8).lArm(-75, 0, 8).head(-25, 0, 0)
				.rLeg(-10, 0, 12).lLeg(8, 0, -12);
		CLIPS.put(Anim.SLAM, new Clip()
				.k(0, rest())
				.k(16, p().torso(-18, 0, 0).rArm(-170, 0, -12).lArm(-170, 0, 12).head(-20, 0, 0))
				.k(36, p().torso(-25, 0, 0).drop(-0.5f).rArm(-185, 0, -15).lArm(-185, 0, 15).head(-25, 0, 0)
						.rLeg(0, 0, 4).lLeg(0, 0, -4))
				.strike(40, slamDown)
				.k(50, slamDown)
				.k(64, rest()));

		// Shockwave: arms spread wide and raised (gathering), then a deep squat driving both fists down at its sides.
		P shockDown = p().torso(35, 0, 0).drop(3).rArm(-35, 0, 25).lArm(-35, 0, -25).head(-20, 0, 0)
				.rLeg(-5, 0, 25).lLeg(-5, 0, -25);
		CLIPS.put(Anim.SHOCKWAVE, new Clip()
				.k(0, rest())
				.k(20, p().torso(-10, 0, 0).rArm(-20, 0, 140).lArm(-20, 0, -140).head(-25, 0, 0)
						.rLeg(0, 0, 6).lLeg(0, 0, -6))
				.k(36, p().torso(-15, 0, 0).drop(-0.8f).rArm(-15, 0, 160).lArm(-15, 0, -160).head(-35, 0, 0)
						.rLeg(0, 0, 6).lLeg(0, 0, -6))
				.strike(40, shockDown)
				.k(52, shockDown)
				.k(66, rest()));

		// Grab: right hand raised and drawn back, open -- then a lunging downward snatch.
		P grabbed = p().torso(35, -15, 0).drop(1).rArm(-70, -10, 0).lArm(-55, 0, -15).head(-30, 0, 0)
				.rLeg(10, 0, 0).lLeg(-20, 0, 0);
		CLIPS.put(Anim.GRAB, new Clip()
				.k(0, rest())
				.k(16, p().torso(5, 20, 0).rArm(-120, 40, 20).lArm(-70, 0, -10).head(-5, 0, 0))
				.k(36, p().torso(8, 30, 0).rArm(-135, 50, 25).lArm(-70, 0, -10).head(-5, 0, 0))
				.strike(40, grabbed)
				.k(48, grabbed)
				.k(62, rest()));

		// The grab landed: lift the victim to the shoulder, wind back over the head, hurl (at tick 30).
		P hurl = p().torso(25, -35, 0).drop(0.8f).rArm(-45, -35, 0).lArm(-30, 0, -15).head(-20, 0, 0)
				.rLeg(20, 0, 0).lLeg(-25, 0, 0);
		CLIPS.put(Anim.GRAB_THROW, new Clip().blendIn(2)
				.k(0, grabbed)
				.k(10, p().torso(0, 20, 0).rArm(-100, 30, 0).lArm(-60, 0, -10))
				.k(24, p().torso(-10, 35, 0).rArm(-165, 10, 5).lArm(-50, 0, -20).head(-10, 0, 0)
						.rLeg(12, 0, 0).lLeg(-8, 0, 0))
				.strike(30, hurl)
				.k(38, hurl)
				.k(52, rest()));

		// Boulder: bend to scoop a rock from the ground, heave it overhead, lean back, throw.
		P throwFwd = p().torso(25, 0, 0).drop(0.5f).rArm(-70, 0, -5).lArm(-70, 0, 5).head(-20, 0, 0)
				.rLeg(15, 0, 0).lLeg(-20, 0, 0);
		CLIPS.put(Anim.BOULDER, new Clip()
				.k(0, rest())
				.k(12, p().torso(50, 0, 0).drop(2).rArm(-60, 0, -5).lArm(-60, 0, 5).head(-40, 0, 0)
						.rLeg(-10, 0, 6).lLeg(5, 0, -6))
				.k(24, p().rArm(-165, 0, -15).lArm(-165, 0, 15).head(-10, 0, 0))
				.k(36, p().torso(-20, 0, 0).drop(-0.5f).rArm(-195, 0, -15).lArm(-195, 0, 15).head(-15, 0, 0)
						.rLeg(8, 0, 0).lLeg(-8, 0, 0))
				.strike(40, throwFwd)
				.k(50, throwFwd)
				.k(62, rest()));

		// Charge: head down like a bull, arms swept back, pawing the ground (procedural) -- then a sprint loop.
		P bull = p().torso(30, 0, 0).drop(1).rArm(30, 0, 15).lArm(30, 0, -15).head(-40, 0, 0);
		CLIPS.put(Anim.CHARGE, new Clip()
				.k(0, rest())
				.k(10, bull)
				.k(40, bull));

		// Leaping Slam wind-up: a deep crouch, arms swung back, one foot forward.
		P crouch = p().torso(40, 0, 0).drop(4).rArm(65, 0, 20).lArm(65, 0, -20).head(-45, 0, 0)
				.rLeg(-40, 0, 12).lLeg(30, 0, -12);
		CLIPS.put(Anim.LEAP, new Clip()
				.k(0, rest())
				.k(20, p().torso(30, 0, 0).drop(3).rArm(45, 0, 15).lArm(45, 0, -15).head(-35, 0, 0)
						.rLeg(-35, 0, 10).lLeg(25, 0, -10))
				.k(38, crouch));

		// Airborne: arms thrown up at take-off, then knees tucked, fists raised -- held until landing.
		P tucked = p().torso(5, 0, 0).rArm(-155, 0, 20).lArm(-155, 0, -20).head(-10, 0, 0)
				.rLeg(-55, 0, 5).lLeg(-40, 0, -5);
		CLIPS.put(Anim.LEAP_AIR, new Clip().blendIn(2)
				.k(0, crouch)
				.out(3, p().torso(-15, 0, 0).drop(-1).rArm(-170, 0, 10).lArm(-170, 0, -10).head(-10, 0, 0)
						.rLeg(20, 0, 0).lLeg(20, 0, 0))
				.k(12, tucked));

		// Landing: slammed down into a three-point crouch, fists in the dirt, then rising.
		P landed = p().torso(45, 0, 0).drop(4).rArm(-60, 0, 20).lArm(-60, 0, -20).head(-30, 0, 0)
				.rLeg(-20, 0, 22).lLeg(10, 0, -22);
		CLIPS.put(Anim.LEAP_LAND, new Clip().blendIn(1)
				.k(0, landed)
				.k(14, p().torso(38, 0, 0).drop(3.4f).rArm(-60, 0, 20).lArm(-60, 0, -20).head(-30, 0, 0)
						.rLeg(-18, 0, 20).lLeg(8, 0, -20))
				.k(34, rest()));

		// Grave Roar: rear back and inhale, arms spread wide -- then lunge forward roaring, arms flung out.
		P roaring = p().torso(25, 0, 0).drop(1.5f).rArm(-60, 45, 30).lArm(-60, -45, -30).head(-40, 0, 0)
				.rLeg(0, 0, 12).lLeg(0, 0, -12);
		CLIPS.put(Anim.ROAR, new Clip()
				.k(0, rest())
				.k(20, p().torso(-25, 0, 0).drop(-0.5f).rArm(20, 0, 60).lArm(20, 0, -60).head(-30, 0, 0))
				.k(38, p().torso(-30, 0, 0).drop(-0.8f).rArm(25, 0, 70).lArm(25, 0, -70).head(-40, 0, 0))
				.strike(42, roaring)
				.k(70, roaring)
				.out(84, rest()));

		// Melee swat (the quick filler hit): the left arm comes up and chops down across. Legs keep walking.
		P swatDown = p().torso(25, 20, 0).drop(0.6f).lArm(-45, 25, 0).rArm(-60, 0, 10).head(-20, 0, 0);
		CLIPS.put(Anim.SWAT, new Clip().legsFromBase()
				.k(0, rest())
				.k(7, p().torso(-5, -20, 0).lArm(-155, -15, -10).rArm(-70, 0, 10))
				.strike(10, swatDown)
				.k(14, swatDown)
				.k(24, rest()));
	}

	/** INTRO reuses the roar from the end of its inhale. */
	private static final float INTRO_OFFSET = 28.0f;

	// ------------------------------------------------------------------ evaluation

	/**
	 * Final pose parameters (radians for rotations, pixels for DROP; W unused) for {@code entity} at
	 * {@code ageInTicks}, given the {@code base} pose the walk/idle animation already produced.
	 */
	public static void evaluate(TitanEntity entity, float ageInTicks, float[] base, float[] out) {
		Anim cur = entity.clientAnim();
		float tCur = ageInTicks - entity.clientAnimStart();
		evaluateOne(cur, tCur, ageInTicks, base, out);
		Clip clip = CLIPS.get(cur);
		float blend = clip == null ? DEFAULT_BLEND : clip.blendIn;
		if (tCur < blend) {
			float[] prev = new float[N];
			Anim p = entity.clientPrevAnim();
			evaluateOne(p, ageInTicks - entity.clientPrevAnimStart(), ageInTicks, base, prev);
			float f = Math.max(0.0f, tCur) / blend;
			f = f * f * (3.0f - 2.0f * f);
			for (int i = 0; i < N; i++) {
				out[i] = Mth.lerp(f, prev[i], out[i]);
			}
		}
	}

	private static void evaluateOne(Anim anim, float t, float age, float[] base, float[] out) {
		Clip clip = CLIPS.get(anim == Anim.INTRO ? Anim.ROAR : anim);
		if (clip == null) {
			System.arraycopy(base, 0, out, 0, N);
			return;
		}
		if (anim == Anim.INTRO) {
			t += INTRO_OFFSET;
		}
		float[] p = new float[N];
		clip.sample(t, p);
		procedural(anim == Anim.INTRO ? Anim.ROAR : anim, t, age, p);
		float w = Mth.clamp(p[W], 0.0f, 1.0f);
		float d2r = Mth.DEG_TO_RAD;
		for (int i : new int[]{HX, HZ, RX, RY, RZ, LX, LY, LZ, RLX, RLY, RLZ, LLX, LLY, LLZ}) {
			out[i] = Mth.lerp(w, base[i], p[i] * d2r);
		}
		out[HY] = base[HY] + w * p[HY] * d2r;
		out[BEND] = base[BEND] + w * p[BEND] * d2r;
		out[TWIST] = base[TWIST] + w * p[TWIST] * d2r;
		out[ROLL] = base[ROLL] + w * p[ROLL] * d2r;
		out[DROP] = base[DROP] + w * p[DROP];
		if (clip.legsFromBase) {
			for (int i = RLX; i <= LLZ; i++) {
				out[i] = base[i];
			}
		}
		out[W] = w;
	}

	/** Movement a keyframe can't express: the charge's pawing and sprint cycle, the roar's shudder. */
	private static void procedural(Anim anim, float t, float age, float[] p) {
		switch (anim) {
			case CHARGE -> {
				if (t >= 10 && t < 40) {
					p[RLX] = -10.0f + 25.0f * Mth.sin((t - 10.0f) * 0.45f); // pawing the ground
				} else if (t >= 40) {
					float ph = (t - 40.0f) * 0.55f;
					float s = Mth.sin(ph);
					p[BEND] = 35;
					p[HX] = -40;
					p[RLX] = s * 50;
					p[LLX] = -s * 50;
					p[RX] = 15 - s * 35;
					p[LX] = 15 + s * 35;
					p[DROP] = 1.0f + Math.abs(Mth.cos(ph)) * 0.8f;
					p[ROLL] = s * 4;
				}
			}
			case ROAR -> {
				if (t >= 42 && t < 72) {
					p[ROLL] += Mth.sin(age * 2.1f) * 2.5f;
					p[HZ] += Mth.sin(age * 2.7f) * 5.0f;
					p[HY] += Mth.sin(age * 1.3f) * 6.0f;
					p[DROP] += Mth.sin(age * 3.0f) * 0.2f;
				}
			}
			case LEAP_AIR -> {
				if (t >= 12) {
					p[RX] += Mth.sin(age * 0.4f) * 4.0f; // a little flail while hanging in the air
					p[LX] -= Mth.sin(age * 0.4f) * 4.0f;
				}
			}
			default -> {
			}
		}
	}

	/** Client: how far into its current clip the Titan is (ticks, partial-tick precise). */
	public static float clipTime(TitanEntity entity, float ageInTicks) {
		return ageInTicks - entity.clientAnimStart();
	}
}
