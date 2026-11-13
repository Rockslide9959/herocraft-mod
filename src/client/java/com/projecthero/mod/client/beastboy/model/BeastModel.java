package com.projecthero.mod.client.beastboy.model;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;

/**
 * v0.17.3 the Beast ("The Beast Within", about 2.6 blocks tall), HQ round: a blocky Minecraft-style model ({@link BeastGeo}, texture
 * {@code beast.png}): a hulking hunched torso (narrow waist, deep chest, huge shoulders and traps) under a shaggy dark mane and back
 * ridge, a pale chest ruff and belly; a wolfish head with a heavy brow, glowing eyes, a long snout with a black nose, fangs top and
 * bottom, a tongue, cheek ruffs and swept-back pointed ears; long arms (deltoid, upper arm, thick forearm with fur tufts) ending in
 * clawed five-fingered hands that hang to the knees; digitigrade legs (thigh, shin, long foot, clawed toes). Locomotion: a lumbering
 * hunch, heavy footfalls, big shoulder roll, the arms swinging opposite the legs; legs and arms by IK. Idle: heaving breath, snarl
 * twitches, ear flicks, blinks. Moves: swipe / strike (wind-up, claw slash across with a body twist), roar (rears, arms flung wide,
 * jaws wide, shaking), pounce (crouch, leap stretched out claws first, land), smash (both fists overhead, slam).
 */
public final class BeastModel {
	private static final BigBox.Lazy RIG = new BigBox.Lazy(BeastGeo::layer, BeastGeo.TREE, "beast");
	private static final float TAU = BigRig.TAU;
	private static final float SH_Y = 16f, SH_Z = 2f, UPPER = 11.5f, FORE = 11f, THIGH = 7.5f, SHIN = 6.5f, META = 4.5f;
	/** The hips sit this much lower than the model's rest so the digitigrade legs stay bent. */
	private static final float CROUCH = 2f;

	private BeastModel() {
	}

	/** Unused: {@link BigAnimals} draws the Beast before BeastFormArt's mesh switch is reached (kept so that switch needs no edit). */
	public static void draw(PoseStack pose, BeastPose s) {
	}

	public static void render(BeastPose s, PoseStack pose, MultiBufferSource buffers, int light, int tint, float flash) {
		BigBox.Rig r = RIG.get();
		r.reset();
		pose(r, s);
		BigBox.render(r, pose, buffers, light, tint, flash);
	}

	static void pose(BigBox.Rig r, BeastPose s) {
		float t = s.age;
		float amt = s.walkAmt;
		float cyc = s.walkPos * 0.08f;
		float breathe = Mth.sin(t * 0.12f);
		float roarK = s.is("roar") ? s.env("roar", 0.18f, 0.22f) : 0f;
		float swipe = s.isAny("swipe", "strike") ? s.prog : -1f;
		float pounce = s.is("pounce") ? s.prog : -1f;
		float smash = s.is("smash") ? s.prog : -1f;

		// ---- pounce phases: crouch, stretch (airborne), land
		float crouch = 0f, stretch = 0f;
		if (pounce >= 0f) {
			crouch = pounce < 0.3f ? BigRig.smooth(pounce / 0.3f) : (pounce < 0.4f ? 1f - BigRig.smooth((pounce - 0.3f) / 0.1f) : 0f);
			crouch = Math.max(crouch, pounce > 0.82f ? BigRig.bump(pounce, 0.82f, 1f) * 0.7f : 0f);
			stretch = BigRig.smooth(BigRig.ph(pounce, 0.32f, 0.45f)) * (1f - BigRig.smooth(BigRig.ph(pounce, 0.75f, 0.88f)));
		}
		float smUp = 0f, smHit = 0f, slam = 0f;
		if (smash >= 0f) {
			smUp = smash < 0.4f ? BigRig.easeOut(smash / 0.4f) : (smash < 0.52f ? 1f - BigRig.easeIn((smash - 0.4f) / 0.12f) : 0f);
			smHit = smash >= 0.45f ? 1f - BigRig.smooth(BigRig.ph(smash, 0.6f, 1f)) : 0f;
			slam = smash >= 0.52f ? BigRig.wobble((smash - 0.52f) * 10f, 3.4f, 0.9f) : 0f;
		}
		float swWind = 0f, swCut = 0f;
		if (swipe >= 0f) {
			swWind = BigRig.smooth(swipe / 0.3f) * (1f - BigRig.smooth(BigRig.ph(swipe, 0.3f, 0.45f)));
			swCut = BigRig.smooth(BigRig.ph(swipe, 0.28f, 0.45f)) * (1f - BigRig.smooth(BigRig.ph(swipe, 0.6f, 1f)));
		}

		// ---- torso: a lumbering hunch
		float P = 0.5f + 0.06f * amt + Mth.sin(cyc * TAU * 2f) * 0.03f * amt + breathe * 0.02f;
		P = Mth.lerp(roarK, P, 0.02f);
		P = Mth.lerp(crouch, P, 0.85f);
		P = Mth.lerp(stretch, P, 1.25f);
		P = Mth.lerp(smUp, P, 0.2f);
		P += smHit * (1f - smUp) * 0.35f + swCut * 0.12f;
		float down = CROUCH + BigRig.dip(cyc) * 1.1f * amt - breathe * 0.3f + crouch * 4f - stretch * 2.5f + Math.abs(slam) * 1.2f - roarK * 1.5f;
		float roll = Mth.sin(cyc * TAU) * 0.09f * amt + Mth.sin(t * 0.031f) * 0.025f * (1f - amt) + roarK * Mth.sin(t * 2.1f) * 0.025f;
		float yaw = Mth.sin(cyc * TAU) * 0.11f * amt;
		float twist = swWind * 0.45f - swCut * 0.55f;
		ModelPart body = r.p("body");
		body.y += down;
		body.xRot += P;
		body.zRot += roll;
		body.yRot += -yaw - twist;
		float hipY = 19f - down;

		// ---- digitigrade legs: the long foot keeps its angle, thigh + shin reach the hock by IK (knee forward)
		for (int k = 0; k < 2; k++) {
			String sd = k == 0 ? "L" : "R";
			float[] g = BigRig.gait(cyc + (k == 0 ? 0f : 0.5f), 0.6f);
			float lift = g[1] * amt;
			float bz = -0.5f + g[0] * 7f * amt + crouch * 1f;
			float by = 0.5f + lift * 3f;
			float fm = -0.45f - lift * 0.4f + crouch * 0.25f;
			float hz = bz + 4.5f * Mth.sin(fm), hy = by + META * Mth.cos(fm);
			ModelPart leg = r.p("leg" + sd), shin = r.p("shin" + sd), meta = r.p("meta" + sd), toes = r.p("toes" + sd);
			leg.y += CROUCH + (down - CROUCH);
			float[] a = BigAnimals.ik(hz - (-2f), hy - hipY, THIGH, SHIN, false);
			float lu = a[0], ll = a[1], lm = fm;
			if (stretch > 0f) {
				// airborne: legs trail back
				lu = Mth.lerp(stretch, lu, 0.85f);
				ll = Mth.lerp(stretch, ll, 1.5f);
				lm = Mth.lerp(stretch, lm, 1.2f);
			}
			leg.xRot += lu;
			shin.xRot += ll - lu;
			meta.xRot += lm - ll;
			toes.xRot += -lm + lift * 0.35f;
		}

		// ---- arms: hang to the knees by IK, swing opposite the legs; the moves take over
		float[] sh = BigAnimals.pitchPt(P, SH_Y, SH_Z);
		float shY = hipY + sh[0], shZ = -2f + sh[1];
		for (int k = 0; k < 2; k++) {
			float sgn = k == 0 ? 1f : -1f;
			String sd = k == 0 ? "L" : "R";
			float swing = Mth.sin(cyc * TAU + (k == 0 ? Mth.PI : 0f));
			float hz = shZ + 2.5f + swing * 6f * amt;
			float hy = 13f + Math.max(0f, -swing) * 1.5f * amt + breathe * 0.4f;
			float[] a = BigAnimals.ik(hz - shZ, hy - shY, UPPER, FORE, true);
			float au = a[0], al = a[1], hx = -al - 0.15f + Mth.sin(t * 0.07f + k) * 0.06f, out = -sgn * (0.1f + Math.abs(roll) * 0.5f);
			float ay = 0f;
			if (roarK > 0f) {
				au = Mth.lerp(roarK, au, P - 2.35f); // arms flung up and out in a V, claws over the head
				al = Mth.lerp(roarK, al, P - 2.95f);
				out = Mth.lerp(roarK, out, -sgn * (0.7f + Mth.sin(t * 2.1f + k) * 0.06f));
				hx = Mth.lerp(roarK, hx, -0.4f);
			}
			if (crouch > 0f) {
				au = Mth.lerp(crouch, au, -0.75f);
				al = Mth.lerp(crouch, al, -1.0f);
			}
			if (stretch > 0f) {
				au = Mth.lerp(stretch, au, -2.55f);
				al = Mth.lerp(stretch, al, -2.65f);
				out = Mth.lerp(stretch, out, -sgn * 0.25f);
				hx = Mth.lerp(stretch, hx, -0.3f);
			}
			if (smash >= 0f) {
				au = Mth.lerp(smUp, au, -3.0f);
				al = Mth.lerp(smUp, al, -3.15f);
				float hit = smHit * (1f - smUp);
				au = Mth.lerp(hit, au, -1.0f);
				al = Mth.lerp(hit, al, -1.15f);
				out = Mth.lerp(Math.max(smUp, hit), out, sgn * 0.15f);
			}
			if (swipe >= 0f && k == 1) {
				// the right arm: up and out behind, then a slash across the front
				au = Mth.lerp(swWind, au, -2.3f);
				al = Mth.lerp(swWind, al, -2.9f);
				out = Mth.lerp(swWind, out, 1.0f);
				au = Mth.lerp(swCut, au, -1.35f);
				al = Mth.lerp(swCut, al, -1.45f);
				out = Mth.lerp(swCut, out, -0.55f);
				ay = swCut * 0.6f;
				hx = Mth.lerp(Math.max(swWind, swCut), hx, -0.2f);
			}
			ModelPart arm = r.p("arm" + sd);
			arm.y += -breathe * 0.35f;
			arm.xRot += au - P;
			arm.zRot += out;
			arm.yRot += ay;
			r.p("fore" + sd).xRot += al - au;
			r.p("hand" + sd).xRot += hx;
		}

		// ---- head: low and forward, tracks the look; snarls; roars with the jaws wide
		ModelPart head = r.p("head");
		head.xRot += -P + 0.15f + s.pitch(0.6f) * 0.5f - roarK * 0.75f + stretch * 0.25f - crouch * 0.1f + Mth.cos(cyc * TAU * 2f - 0.5f) * 0.05f * amt;
		head.yRot += -(s.yaw(0.7f) * 0.8f) + yaw * 0.7f + twist * 0.5f + roarK * Mth.sin(t * 1.7f) * 0.06f;
		head.zRot += -roll * 0.5f;
		float snarl = Math.max(0f, Mth.sin(t * 0.045f) - 0.6f) * 0.5f + 0.04f;
		float jaw = Math.max(snarl, roarK * (0.85f + Mth.sin(t * 1.9f) * 0.08f));
		jaw = Math.max(jaw, Math.max(swCut, Math.max(stretch, smHit * 0.8f)) * 0.55f);
		r.p("jaw").xRot += jaw * 0.85f;
		for (int k = 0; k < 2; k++) {
			float sgn = k == 0 ? 1f : -1f;
			float flick = Math.max(0f, Mth.sin(t * 0.17f + k * 1.9f) - 0.92f) * 7f;
			ModelPart ear = r.p(k == 0 ? "earL" : "earR");
			ear.xRot += -roarK * 0.5f - stretch * 0.4f + flick * 0.3f;
			ear.zRot += sgn * flick * 0.2f;
			r.p(k == 0 ? "eyeL" : "eyeR").yScale = 1f - 0.85f * BigRig.blink(t, 0.29f) * (1f - roarK);
			r.p(k == 0 ? "cheekL" : "cheekR").zRot += sgn * roarK * 0.2f;
		}
	}
}
