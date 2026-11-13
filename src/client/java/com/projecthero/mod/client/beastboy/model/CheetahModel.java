package com.projecthero.mod.client.beastboy.model;

import net.minecraft.util.Mth;

/**
 * v0.17.3 hq round: Beast Boy's cheetah (X on the ground) as a blocky box model ({@link CheetahParts}, texture cheetah.png): a deep
 * narrow chest over a tucked waist, long thin legs, a small round head with tear marks, short muzzle and small round ears, spots
 * everywhere and a long ringed tail.
 * <p>Animation: a loose-limbed walk that breaks into the sprinter's rotary gallop with big spine flexion (the back bunches and
 * stretches every stride) while the head stays level and steady on the target; the tail streams out as a rudder and swings with his
 * turns. Idle: glances, ear flicks, blinks, a twitching tail tip. Left click: a quick fore-paw swipe with a snarl.
 */
final class CheetahModel extends QuadBeast {
	CheetahModel() {
		super(CheetahParts.create(), 2f);
	}

	@Override
	String[] names() {
		return CheetahParts.NAMES;
	}

	@Override
	void pose(BeastPose s) {
		float t = s.age;
		int seed = seed(s, 5);
		gait(s, 0.22f, 0.6f, 0.1f, 8f, 18f, 2.6f, 5.5f, 0f);
		float breathe = LandRig.breathe(s, run, 0.15f, 0.6f);
		float swipe = s.move == null && s.attack > 0f ? s.attack : (s.isAny("swipe", "strike") ? s.prog : -1f);
		float sw = swipe >= 0f ? Mth.sin(Math.min(1f, swipe) * Mth.PI) : 0f;

		// ---- body: the sprint's spine flexion is the cheetah's signature
		float pitch = rock * 0.1f + run * 0.03f - sw * 0.08f;
		float dy = bob * Mth.lerp(gal, 0.5f, 1.6f) + breathe * 0.1f + sw * 0.6f;
		move(p("body"), 0f, dy, gal * amt * Mth.sin(cyc - 0.3f) * 1.2f);
		rot(p("body"), pitch, sw * 0.08f, Mth.sin(cyc) * 0.03f * amt * (1f - gal) - sw * 0.05f);
		rot(p("chest"), -flex * 0.24f, 0f, 0f);
		rot(p("hips"), flex * 0.28f, 0f, 0f);
		move(p("hips"), 0f, 0f, flex * 1.6f);
		float br = 1f + breathe * 0.02f;
		scale(p("chest"), br, br, 1f);

		// ---- legs; the swipe raises the right fore paw up and forward, then rakes it down and across
		float[] f = feet[LandRig.RF];
		if (sw > 0f) {
			float k = swipe;
			float[] up = { restRF[0] + 1f, pawH + 8f, restRF[2] + 5f }, down = { restRF[0] + 3.5f, pawH + 1f, restRF[2] + 7f };
			float[] base = { restRF[0], pawH + f[1], restRF[2] + f[0] };
			float[] tgt = k < 0.45f ? lerp(base, up, LandRig.win(k, 0f, 0.45f)) : lerp(up, down, LandRig.win(k, 0.45f, 0.65f));
			tgt = lerp(tgt, base, LandRig.win(k, 0.65f, 1f));
			foreLegTo("RF", tgt, -1f, sw * 0.6f);
		} else {
			foreLeg("RF", restRF, f, 0f, 0f);
		}
		foreLeg("LF", restLF, feet[LandRig.LF], -sw * 1f, 0f);
		hindLeg("LH", restLH, feet[LandRig.LH], 0f, 0f, 0.3f);
		hindLeg("RH", restRH, feet[LandRig.RH], 0f, 0f, 0.3f);

		// ---- head: level and steady at speed (counters the body), follows the look
		float[] gl = LandRig.glance(t, seed);
		float idle = (1f - amt) * (1f - Math.min(1f, Math.abs(s.headYaw) * 4f)) * (1f - sw);
		float hy = s.yaw(1.0f) + gl[0] * idle;
		float hp = s.pitch(0.8f) * 0.7f + gl[1] * idle;
		float chestPitch = pitch - flex * 0.24f;
		rot(p("neck"), -0.35f - chestPitch * 0.6f + hp * 0.3f + run * 0.3f + sw * 0.15f, hy * 0.4f, 0f);
		rot(p("head"), 0.32f - chestPitch * 0.4f + hp * 0.7f - run * 0.25f - sw * 0.15f, hy * 0.6f, sw * -0.1f);
		float jaw = Math.max(sw * 0.55f, run * (0.2f + Mth.sin(t * 0.6f) * 0.05f));
		rot(p("jaw"), Math.max(0.02f, jaw), 0f, 0f);
		lids(Math.max(LandRig.blink(t, seed), sw * 0.35f + run * 0.15f));
		ears(t, seed, 0.05f - run * 0.5f - sw * 0.7f, 0.25f);

		// ---- tail: a low curve that lifts the tip, flicking at the end when idle; a straight rudder at a sprint
		float base = Mth.lerp(run, -0.7f, -0.12f) - pitch * 0.5f;
		tail(s, "tail", 8, base, Mth.lerp(run, 0.1f, 0.02f), Mth.lerp(run, 0.08f, 0.03f), 0.07f, 1.4f);
		float flick = (1f - run) * Mth.sin(t * 0.35f) * Math.max(0f, Mth.sin(t * 0.045f + seed));
		turn(p("tail6"), 0.15f, flick * 0.4f, 0f);
		turn(p("tail7"), 0.2f, flick * 0.5f, 0f);
	}
}
