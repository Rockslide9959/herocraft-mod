package com.projecthero.mod.client.beastboy.model;

import net.minecraft.util.Mth;

/**
 * v0.17.3 hq round: Beast Boy's bear as a blocky box model ({@link BearParts}, texture bear.png): a massive chest under a shaggy
 * shoulder hump, a fur fringe along the belly, thick pillar legs with big four-clawed paws, a broad head with a heavy brow, small eyes,
 * round ears, a long tan muzzle with fangs, a pale chest crescent and a stubby tail.
 * <p>Animation: a heavy, rolling plod (the hump and head swaying side to side, the paws placed flat) that becomes a lumbering
 * gallop. Moves: swipe / maul (half rears, a big right-paw haymaker raking across), roar (stands up on its hind legs, arms out,
 * jaws wide with a head shake, then drops back down with a thump).
 */
final class BearModel extends QuadBeast {
	BearModel() {
		super(BearParts.create(), 3f);
	}

	@Override
	String[] names() {
		return BearParts.NAMES;
	}

	@Override
	void pose(BeastPose s) {
		float t = s.age;
		int seed = seed(s, 11);
		float roar = s.env("roar", 0.25f, 0.25f);
		float swipe = s.isAny("swipe", "strike", "smash") ? s.prog : (s.move == null && s.attack > 0f ? s.attack : -1f);
		float rear = 0f, sw = 0f;
		if (swipe >= 0f) {
			rear = LandRig.win(swipe, 0f, 0.3f) * (1f - LandRig.win(swipe, 0.55f, 0.9f));
			sw = swipe;
		}
		float stand = Math.max(roar, rear * 0.35f);
		gait(s, 0.12f, 0.3f, 0.085f, 12f, 20f, 4f, 7f, stand);
		float breathe = Mth.sin(t * Mth.lerp(run, 0.1f, 0.4f));

		// ---- body: rolls with every step; rears up for the roar (onto the hind legs) and half-rears for the swipe
		float roll = Mth.sin(cyc) * 0.06f * amt * (1f - gal);
		float pitch = rock * 0.08f - stand * 1.15f + (roar > 0.5f ? 0f : 0f);
		// thump: the drop back down at the end of the roar
		float thump = s.is("roar") && s.prog > 0.8f ? Mth.sin((s.prog - 0.8f) / 0.2f * Mth.PI) * 1.5f : 0f;
		float dy = bob * Mth.lerp(gal, 0.9f, 2f) + breathe * 0.2f + stand * 7f - thump;
		float dz = -stand * 13f;
		move(p("body"), 0f, dy, dz);
		rot(p("body"), pitch, roll * 0.6f, roll);
		rot(p("chest"), -flex * 0.12f + stand * 0.1f, -roll * 0.8f, -roll * 0.5f);
		rot(p("hips"), flex * 0.12f, roll * 0.5f, 0f);
		float br = 1f + breathe * 0.02f + roar * 0.03f;
		scale(p("chest"), br, br, 1f);

		// ---- legs
		if (stand > 0.01f) {
			for (int side = -1; side <= 1; side += 2) {
				String id = side < 0 ? "RF" : "LF";
				float[] r = side < 0 ? restRF : restLF;
				float[] f = side < 0 ? feet[LandRig.RF] : feet[LandRig.LF];
				float[] base = { r[0], pawH + f[1], r[2] + f[0] };
				// roar: arms up and out, paws open; maul: the right paw winds up high then rakes down across
				float[] up = { r[0] * 1.7f, 38f, r[2] - 2f };
				float[] tgt = lerp(base, up, roar);
				if (sw >= 0f && side < 0) {
					float[] wind = { r[0] - 9f, 40f, r[2] - 2f }, hit = { r[0] + 12f, 10f, r[2] + 14f };
					float[] a = sw < 0.4f ? lerp(base, wind, LandRig.win(sw, 0f, 0.4f)) : lerp(wind, hit, LandRig.win(sw, 0.4f, 0.6f));
					tgt = lerp(a, base, LandRig.win(sw, 0.65f, 1f));
				} else if (sw >= 0f) {
					tgt = lerp(base, new float[] { r[0] * 1.3f, 14f, r[2] - 4f }, rear);
				}
				foreLegTo(id, tgt, -1f, -Math.max(roar, rear) * 0.9f);
				String hid = side < 0 ? "RH" : "LH";
				float[] hr = side < 0 ? restRH : restLH;
				float[] hf = side < 0 ? feet[LandRig.RH] : feet[LandRig.LH];
				// standing: the hind paws plant forward under the body
				hindLeg(hid, hr, hf, stand * 9f, 0f, 0.5f + stand * 0.6f);
			}
		} else {
			foreLeg("LF", restLF, feet[LandRig.LF], 0f, 0f);
			foreLeg("RF", restRF, feet[LandRig.RF], 0f, 0f);
			hindLeg("LH", restLH, feet[LandRig.LH], 0f, 0f, 0.5f);
			hindLeg("RH", restRH, feet[LandRig.RH], 0f, 0f, 0.5f);
		}

		// ---- head: low and swinging as it plods; up and forward to roar with a shake
		float[] gl = LandRig.glance(t, seed);
		float idle = (1f - amt) * (1f - Math.min(1f, Math.abs(s.headYaw) * 4f)) * (1f - stand);
		float shake = roar > 0.6f ? Mth.sin(t * 1.6f) * (roar - 0.6f) * 0.4f : 0f;
		float hy = s.yaw(0.9f) + gl[0] * idle - roll * 2f + shake;
		float hp = s.pitch(0.7f) * 0.6f + gl[1] * idle;
		float chestPitch = pitch - flex * 0.12f + stand * 0.1f;
		rot(p("neck"), -0.18f - chestPitch * 0.55f + hp * 0.3f - roar * 0.15f + amt * 0.12f, hy * 0.4f, 0f);
		rot(p("head"), 0.1f - chestPitch * 0.45f + hp * 0.7f - roar * 0.35f + Mth.sin(t * 0.2f) * 0.01f, hy * 0.6f, shake * 0.4f);
		float jaw = Math.max(roar * (0.75f + Mth.sin(t * 1.3f) * 0.06f), sw >= 0f ? Mth.sin(Math.min(1f, sw) * Mth.PI) * 0.6f : 0f);
		jaw = Math.max(jaw, run * 0.2f);
		rot(p("jaw"), Math.max(0.03f, jaw), 0f, 0f);
		lids(Math.max(LandRig.blink(t, seed), roar * 0.45f));
		ears(t, seed, 0.05f - roar * 0.6f - (sw >= 0f ? 0.4f : 0f), 0.15f);
		rot(p("tail0"), -0.2f + Mth.sin(t * 0.1f) * 0.05f + stand * 0.6f, Mth.sin(t * 0.13f) * 0.1f, 0f);
	}
}
