package com.projecthero.mod.client.beastboy.model;

import net.minecraft.util.Mth;

/**
 * v0.17.3 hq round: Beast Boy's mouse as a blocky box model ({@link MouseParts}, texture mouse.png): a pear-shaped body (big rump,
 * smaller chest), a round head with a pointed snout, a pink nose, buck tooth, beady black eyes with a shine, big round pink ears,
 * whiskers, tiny pink paws and a long ten-box tail.
 * <p>Animation: a quick scurry that becomes a bounding run, the long tail trailing with lag. Idle: a constant nose-and-whisker twitch,
 * blinks, ear flicks, glances, and every few seconds it sits up on its haunches to sniff the air. Move: bite (a quick nibbling chatter).
 */
final class MouseModel extends QuadBeast {
	MouseModel() {
		super(MouseParts.create(), 1f);
	}

	@Override
	String[] names() {
		return MouseParts.NAMES;
	}

	@Override
	void pose(BeastPose s) {
		float t = s.age;
		int seed = seed(s, 13);
		float bite = s.isAny("bite", "strike") ? s.prog : (s.move == null && s.attack > 0f ? s.attack : -1f);
		float nib = bite >= 0f ? Mth.sin(Math.min(1f, bite) * Mth.PI) : 0f;
		// sit up and sniff every ~7 s while standing still
		float cycT = (t + (seed % 50)) % 140f;
		float sitUp = (cycT > 90f ? LandRig.win(cycT, 90f, 98f) * (1f - LandRig.win(cycT, 128f, 136f)) : 0f) * (1f - Math.min(1f, s.walkAmt * 3f))
				* (1f - nib);
		gait(s, 0.05f, 0.12f, 0.16f, 3f, 5f, 1.2f, 2.2f, sitUp);
		float sniff = Mth.sin(t * 1.9f) * (1f - amt * 0.7f);

		// ---- body: low and quick; sits up onto the haunches
		float pitch = rock * 0.12f - sitUp * 0.95f + nib * 0.1f;
		move(p("body"), 0f, bob * Mth.lerp(gal, 0.25f, 0.6f) + sitUp * 1.8f + Mth.sin(t * 0.3f) * 0.05f, -sitUp * 2.2f + nib * 0.8f);
		rot(p("body"), pitch, 0f, Mth.sin(cyc) * 0.05f * amt * (1f - gal));

		// ---- legs: the fore paws lift to the chest when it sits up
		float[] lfF = feet[LandRig.LF], rfF = feet[LandRig.RF];
		if (sitUp > 0.01f) {
			float[] up = { 0f, 4.5f, 2.5f };
			foreLegTo("LF", lerp(new float[] { restLF[0], pawH + lfF[1], restLF[2] + lfF[0] }, new float[] { restLF[0] * 0.6f, up[1], up[2] }, sitUp), -1f, sitUp * 0.8f);
			foreLegTo("RF", lerp(new float[] { restRF[0], pawH + rfF[1], restRF[2] + rfF[0] }, new float[] { restRF[0] * 0.6f, up[1], up[2] }, sitUp), -1f, sitUp * 0.8f);
		} else {
			foreLeg("LF", restLF, lfF, 0f, 0f);
			foreLeg("RF", restRF, rfF, 0f, 0f);
		}
		hindLeg("LH", restLH, feet[LandRig.LH], sitUp * 1.5f, 0f, 0.5f);
		hindLeg("RH", restRH, feet[LandRig.RH], sitUp * 1.5f, 0f, 0.5f);

		// ---- head: sniffing (tiny quick nods), looking about, nibbling
		float[] gl = LandRig.glance(t, seed);
		float idle = (1f - amt) * (1f - Math.min(1f, Math.abs(s.headYaw) * 4f));
		float hy = s.yaw(1.0f) + gl[0] * idle;
		float hp = s.pitch(0.8f) * 0.7f + gl[1] * idle - sniff * 0.03f;
		rot(p("head"), hp - pitch * 0.8f + nib * 0.25f + sitUp * -0.15f, hy, Math.sin(t * 0.07f) > 0.85 ? 0.15f * idle : 0f);
		rot(p("jaw"), nib * (0.25f + 0.2f * Mth.sin(t * 2.4f)), 0f, 0f);
		// whiskers: thin (a third of a unit) and twitching with the sniff
		rot(p("whiskL"), 0f, Mth.sin(t * 1.9f) * 0.12f - 0.25f, 0.08f);
		rot(p("whiskR"), 0f, -Mth.sin(t * 1.9f) * 0.12f + 0.25f, -0.08f);
		scale(p("whiskL"), 0.85f, 0.3f, 1f);
		scale(p("whiskR"), 0.85f, 0.3f, 1f);
		lids(LandRig.blink(t, seed));
		ears(t, seed, -0.1f - run * 0.4f, 0.35f);

		// ---- tail: trails low with a lazy S, lifting a little at a run
		tail(s, "tail", 10, Mth.lerp(run, -0.15f, 0.05f) - pitch * 0.9f + sitUp * -0.2f, Mth.lerp(run, -0.04f, 0.0f), 0.12f, 0.12f, 0.7f);
		for (int i = 1; i < 10; i++) {
			turn(p("tail" + i), 0f, Mth.sin(t * 0.12f - i * 0.6f) * 0.06f, 0f);
		}
	}
}
