package com.projecthero.mod.client.beastboy.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * v0.17.3 hq round: Beast Boy's wolf as a blocky box model ({@link WolfParts}, texture wolf.png): layered chest, shoulder mane and
 * hips, a stepped muzzle with a nose, fangs and an opening jaw, cartoon eyes with blinking lids, stepped ears, jointed legs
 * (shoulder / elbow / wrist / paw, hip / knee / hock / paw) with claws, and a six-box bushy tail.
 * <p>Animation: walk (lateral sequence) -> trot -> rotary gallop from his speed, feet planted by IK, body bob / pitch rock / spine
 * flex, head following the look (idle glances when he stands), panting with the tongue out at speed, a tail with lag and sway, ear
 * flicks and blinks. Crouching sits him down (eased); moves: bite (anticipate, lunge, snap, shake), howl (sits, head back).
 */
final class WolfModel extends QuadBeast {
	WolfModel() {
		super(WolfParts.create(), 2f);
	}

	@Override
	String[] names() {
		return WolfParts.NAMES;
	}

	@Override
	void pose(BeastPose s) {
		float t = s.age;
		int seed = seed(s, 3);
		float bite = s.isAny("bite", "strike") ? s.prog : (s.move == null && s.attack > 0f ? s.attack : -1f);
		float howl = s.env("howl", 0.18f, 0.22f);
		float sit = BeastPose.smooth(Math.max(s.sit, howl * 0.95f));
		gait(s, 0.2f, 0.38f, 0.105f, 7f, 13f, 2.4f, 4.6f, sit);
		float breathe = Mth.sin(t * Mth.lerp(run, 0.16f, 0.55f));

		// ---- bite: anticipation (draw back, ears up), lunge with the jaws wide, snap, a shake, recover
		float back = 0f, lunge = 0f, jawBite = 0f, shake = 0f;
		if (bite >= 0f) {
			back = LandRig.win(bite, 0f, 0.22f) * (1f - LandRig.win(bite, 0.22f, 0.36f));
			lunge = LandRig.win(bite, 0.2f, 0.4f) * (1f - LandRig.win(bite, 0.62f, 1f));
			jawBite = bite < 0.4f ? LandRig.win(bite, 0.12f, 0.36f) : 1f - LandRig.win(bite, 0.4f, 0.48f);
			shake = bite > 0.45f && bite < 0.75f ? Mth.sin((bite - 0.45f) * 60f) * (1f - (bite - 0.45f) / 0.3f) : 0f;
		}

		// ---- body
		float pitch = rock * 0.09f + run * 0.04f + lunge * 0.1f - back * 0.05f;
		pitch = Mth.lerp(sit, pitch, -0.62f);
		float dy = bob * Mth.lerp(gal, 0.55f, 1.3f) + breathe * 0.12f - back * 1.2f + lunge * 0.4f - sit * 4.6f;
		float dz = -back * 1.8f + lunge * 5f - sit * 3.2f;
		move(p("body"), 0f, dy, dz);
		rot(p("body"), pitch, shake * 0.03f, Mth.sin(cyc) * 0.035f * amt * (1f - gal));
		rot(p("chest"), -flex * 0.12f, 0f, 0f);
		rot(p("hips"), flex * 0.14f - sit * 0.28f, 0f, 0f);
		float br = 1f + breathe * 0.018f;
		scale(p("chest"), br, br, 1f);

		// ---- legs (the lunge reaches the front paws forward and lifts them a little)
		float reach = lunge * 3f, lift = Mth.sin(lunge * Mth.PI) * 1.5f;
		foreLeg("LF", restLF, feet[LandRig.LF], reach - sit * 1.5f, lift);
		foreLeg("RF", restRF, feet[LandRig.RF], reach - sit * 1.5f, lift * 0.6f);
		sitHind("LH", restLH, feet[LandRig.LH], sit);
		sitHind("RH", restRH, feet[LandRig.RH], sit);

		// ---- neck and head: follow the look, held up and steadied against the body's pitch; glances about when idle
		float[] gl = LandRig.glance(t, seed);
		float idle = (1f - amt) * (1f - Math.min(1f, Math.abs(s.headYaw) * 4f)) * (1f - howl) * (bite >= 0f ? 0f : 1f) * (1f - sit * 0.5f);
		float hy = s.yaw(1.0f) + gl[0] * idle;
		float hp = s.pitch(0.8f) * 0.7f + gl[1] * idle;
		float bodyPitch = pitch - flex * 0.12f;
		rot(p("neck"), -0.55f - bodyPitch * 0.5f + hp * 0.35f - howl * 0.4f + lunge * 0.35f - back * 0.15f + run * 0.25f, hy * 0.4f, 0f);
		float head = 0.5f - bodyPitch * 0.5f + hp * 0.65f - howl * 0.5f - lunge * 0.05f - back * 0.2f - run * 0.1f - jawBite * 0.22f
				+ Mth.sin(t * 0.33f) * 0.01f;
		rot(p("head"), head, hy * 0.6f + shake * 0.25f, shake * 0.12f + (howl > 0f ? Mth.sin(t * 0.7f) * 0.03f * howl : 0f));

		// jaw: the bite, a howl, panting at speed
		float pant = run * (bite >= 0f ? 0f : 1f);
		float jaw = Math.max(jawBite * 0.62f, howl * (0.55f + Mth.sin(t * 1.1f) * 0.05f));
		jaw = Math.max(jaw, pant * (0.3f + Mth.sin(t * 0.55f) * 0.07f));
		jaw = Math.max(jaw, 0.03f + (1f - amt) * Math.max(0f, Mth.sin(t * 0.05f)) * 0.04f);
		rot(p("jaw"), jaw, 0f, 0f);
		ModelPart tongue = p("tongue");
		tongue.visible = pant > 0.25f;
		if (tongue.visible) {
			rot(tongue, 0.35f + pant * 0.5f + Mth.sin(t * 0.55f) * 0.08f, 0f, 0f);
			move(tongue, 0f, 0f, 1.2f * pant);
		}

		lids(Math.max(LandRig.blink(t, seed), Math.max(howl * 0.55f, jawBite * 0.3f)));
		ears(t, seed, 0.08f + back * 0.3f - lunge * 0.7f - howl * 0.55f - run * 0.35f, 0.22f);

		// ---- tail: hangs relaxed with a lazy sway, streams out at speed with a gallop wave, curls round when he sits
		float base = Mth.lerp(run, -0.85f, -0.22f) - pitch * 0.6f;
		base = Mth.lerp(lunge * 0.6f, base, -0.2f);
		tail(s, "tail", 6, base, Mth.lerp(run, 0.11f, 0.02f), Mth.lerp(run, 0.12f, 0.03f), 0.09f, 0.9f);
		if (sit > 0f) {
			for (int i = 0; i < 6; i++) {
				ModelPart seg = p("tail" + i);
				seg.xRot = Mth.lerp(sit, seg.xRot, i == 0 ? -0.75f : (i < 3 ? 0.18f : 0.05f));
				seg.yRot = Mth.lerp(sit, seg.yRot, -0.33f);
			}
		}
	}

	/** Hind legs; sitting brings the paw forward under the hip and lays the cannon bone flat behind it. */
	private void sitHind(String id, float[] restPaw, float[] f, float sit) {
		float[] foot = { restPaw[0], pawH + f[1], restPaw[2] + f[0] };
		float[][] j = LandRig.hind(rest("thigh" + id), foot, len("shin" + id), len("meta" + id), len("hpaw" + id), 0.34f, f[2]);
		float[] hock = j[2];
		if (sit > 0f) {
			foot = lerp(foot, new float[] { restPaw[0] * 1.05f, pawH, restPaw[2] + 6.5f }, sit);
			hock = lerp(hock, new float[] { restPaw[0] * 1.1f, pawH + 0.6f, restPaw[2] + 2.2f }, sit);
		}
		hindLegTo(id, hock, foot, f[2]);
	}
}
