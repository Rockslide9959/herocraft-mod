package com.projecthero.mod.client.beastboy.model;

import net.minecraft.util.Mth;

/**
 * v0.17.3 hq round: Beast Boy's tiger (the Shift+R Pounce quick morph) as a blocky box model ({@link TigerParts}, texture tiger.png):
 * a massive chest and shoulder hump, heavy striped legs with claws, a broad head with a cheek ruff, a wide muzzle, big fangs and
 * round white-spotted ears, and a ringed tail with a dark tip.
 * <p>Animation: a heavy, rolling walk (shoulder blades working, head swinging low) and a powerful bounding run. The pounce: a
 * crouch with a rump wiggle, the leap stretched out (fore paws reaching, claws and jaws wide, hind legs kicked back, tail streaming),
 * then the landing with the fore paws slamming down.
 */
final class TigerModel extends QuadBeast {
	TigerModel() {
		super(TigerParts.create(), 2f);
	}

	@Override
	String[] names() {
		return TigerParts.NAMES;
	}

	@Override
	void pose(BeastPose s) {
		float t = s.age;
		int seed = seed(s, 9);
		float pounce = s.isAny("pounce", "strike", "bite") ? s.prog : (s.move == null && s.attack > 0f ? s.attack : -1f);
		// pounce phases: crouch 0 - 0.3 (wiggle), leap 0.3 - 0.72, land 0.72 - 1
		float crouch = 0f, leap = 0f, land = 0f, wig = 0f;
		if (pounce >= 0f) {
			crouch = LandRig.win(pounce, 0f, 0.22f) * (1f - LandRig.win(pounce, 0.28f, 0.38f));
			leap = LandRig.win(pounce, 0.3f, 0.42f) * (1f - LandRig.win(pounce, 0.68f, 0.8f));
			land = LandRig.win(pounce, 0.7f, 0.8f) * (1f - LandRig.win(pounce, 0.82f, 1f));
			wig = pounce < 0.3f ? Mth.sin(pounce * 55f) * crouch : 0f;
		}
		float still = Math.max(crouch, Math.max(leap, land));
		gait(s, 0.15f, 0.36f, 0.085f, 9f, 16f, 3f, 6f, still);
		float breathe = LandRig.breathe(s, run, 0.12f, 0.45f);

		// ---- body: a heavy roll at the walk; crouch low, stretch out in the air, absorb the landing
		float roll = Mth.sin(cyc) * 0.05f * amt * (1f - gal);
		float pitch = rock * 0.1f + crouch * 0.12f - leap * 0.2f + land * 0.25f;
		float dy = bob * Mth.lerp(gal, 0.7f, 1.6f) + breathe * 0.14f - crouch * 6f + leap * 7f - land * 3f;
		move(p("body"), 0f, dy, leap * 4f - crouch * 1.5f);
		rot(p("body"), pitch, wig * 0.06f, roll);
		rot(p("chest"), -flex * 0.14f - leap * 0.12f, 0f, -roll * 0.6f);
		rot(p("hips"), flex * 0.16f + crouch * -0.12f + leap * 0.15f, wig * 0.18f, roll * 0.6f);
		float br = 1f + breathe * 0.018f;
		scale(p("chest"), br, br, 1f);

		// ---- legs
		if (still > 0.01f) {
			// fore paws: tucked under in the crouch, reaching far out in the leap, planted wide for the landing
			for (int side = -1; side <= 1; side += 2) {
				String id = side < 0 ? "RF" : "LF";
				float[] r = side < 0 ? restRF : restLF;
				float[] base = { r[0], pawH, r[2] };
				float[] c = { r[0], pawH, r[2] + 1f };
				float[] l = { r[0] * 1.1f, pawH + 9f + side * 0.6f, r[2] + 14f };
				float[] d = { r[0] * 1.25f, pawH, r[2] + 4f };
				float[] tgt = lerp(lerp(lerp(base, c, crouch), l, leap), d, land);
				foreLegTo(id, tgt, -1f, -leap * 0.6f);
				String hid = side < 0 ? "RH" : "LH";
				float[] hr = side < 0 ? restRH : restLH;
				float[] hb = { hr[0], pawH, hr[2] };
				float[] hc = { hr[0], pawH, hr[2] + 3f };
				float[] hl = { hr[0], pawH + 6f, hr[2] - 9f };
				float[] htgt = lerp(lerp(hb, hc, crouch), hl, leap);
				float[][] j = LandRig.hind(rest("thigh" + hid), htgt, len("shin" + hid), len("meta" + hid), len("hpaw" + hid), 0.3f + leap * 0.9f, -1f);
				hindLegTo(hid, j[2], htgt, -1f);
			}
		} else {
			foreLeg("LF", restLF, feet[LandRig.LF], 0f, 0f);
			foreLeg("RF", restRF, feet[LandRig.RF], 0f, 0f);
			hindLeg("LH", restLH, feet[LandRig.LH], 0f, 0f, 0.3f);
			hindLeg("RH", restRH, feet[LandRig.RH], 0f, 0f, 0.3f);
		}

		// ---- head: swings low at the walk; eyes locked forward in the crouch; jaws wide in the leap
		float[] gl = LandRig.glance(t, seed);
		float idle = (1f - amt) * (1f - Math.min(1f, Math.abs(s.headYaw) * 4f)) * (1f - still);
		float hy = s.yaw(1.0f) + gl[0] * idle + Mth.sin(cyc) * 0.08f * amt * (1f - gal);
		float hp = s.pitch(0.8f) * 0.7f + gl[1] * idle;
		float chestPitch = pitch - flex * 0.14f - leap * 0.12f;
		rot(p("neck"), -0.2f - chestPitch * 0.6f + hp * 0.3f + amt * 0.12f + crouch * 0.35f - leap * 0.1f, hy * 0.4f, 0f);
		rot(p("head"), 0.22f - chestPitch * 0.4f + hp * 0.7f - crouch * 0.25f - leap * 0.25f, hy * 0.6f, 0f);
		float snarl = Math.max(leap, crouch * 0.25f);
		float jaw = Math.max(snarl * 0.75f, run * (0.18f + Mth.sin(t * 0.5f) * 0.05f));
		rot(p("jaw"), Math.max(0.02f, jaw + (1f - amt) * Math.max(0f, Mth.sin(t * 0.04f)) * 0.05f), 0f, 0f);
		lids(Math.max(LandRig.blink(t, seed), crouch * 0.4f + leap * 0.25f));
		ears(t, seed, 0.05f - crouch * 0.4f - leap * 0.8f - run * 0.3f, 0.2f);

		// ---- tail: low and swaying; lashing in the crouch; streaming out behind in the leap
		float base = Mth.lerp(run, -0.75f, -0.25f) - pitch * 0.5f + leap * 0.55f;
		tail(s, "tail", 7, base, Mth.lerp(Math.max(run, leap), 0.12f, 0.0f), 0.1f + crouch * 0.3f, 0.08f + crouch * 0.4f, 1.0f);
	}
}
