package com.projecthero.mod.client.beastboy.model;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy's rhino (1.5 blocks tall, 2.8 long), HQ round: a blocky Minecraft-style model ({@link RhinoGeo}, texture
 * {@code rhino.png}): a barrel body with raised shoulder and hip plates, a neck hump, a big skull with square lips, a four-box curved
 * front horn and a second horn, tube ears, lidded eyes under brows, jointed legs (upper / shin / foot with three hoof nails and a fold
 * ring) and a tufted tail. Gaits: a four-beat walk (hind-fore lateral sequence) with a double dip and roll; a gallop for the charge.
 * Idle: breathing, ear flicks, tail swish, blinks, head drift. Moves: charge / ram (head down, horn level, ears pinned, tail up),
 * stomp (rears, paws the air, slams with a shudder).
 */
public final class RhinoModel {
	private static final BigBox.Lazy RIG = new BigBox.Lazy(RhinoGeo::layer, RhinoGeo.TREE, "rhino");
	private static final float TAU = BigRig.TAU;

	private RhinoModel() {
	}

	/** Unused: {@link BigAnimals} draws the rhino before BeastFormArt's mesh switch is reached (kept so that switch needs no edit). */
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
		float charge = s.isAny("charge", "ram") ? s.env(s.move, 0.12f, 0.22f) : 0f;
		float stomp = s.is("stomp") ? s.prog : -1f;
		float amt = s.walkAmt * (1f - charge);
		float cyc = s.walkPos * 0.09f;
		float gcyc = t * 0.14f;
		float run = Math.max(s.walkAmt, charge);
		float breathe = Mth.sin(t * 0.09f);

		// ---- body: double dip and side roll in the walk, rocking in the gallop
		float galBob = 0.5f - 0.5f * Mth.cos(gcyc * TAU);
		float down = BigRig.dip(cyc - 0.08f) * 0.8f * amt + galBob * 1.4f * charge + charge * 1.2f - breathe * 0.2f;
		float pitch = charge * (0.06f + Mth.sin(gcyc * TAU + 0.6f) * 0.08f) + Mth.sin(cyc * TAU * 2f) * 0.015f * amt;
		float roll = Mth.sin(cyc * TAU) * 0.04f * amt + Mth.sin(t * 0.031f) * 0.012f * (1f - run) + Mth.sin(gcyc * TAU) * 0.03f * charge;
		float yaw = Mth.sin(cyc * TAU) * 0.035f * amt;
		float rear = 0f, slam = 0f;
		if (stomp >= 0f) {
			rear = stomp < 0.5f ? BigRig.easeOut(stomp / 0.5f) : (stomp < 0.6f ? 1f - BigRig.easeIn((stomp - 0.5f) / 0.1f) : 0f);
			slam = stomp >= 0.6f ? BigRig.wobble((stomp - 0.6f) * 14f, 3.2f, 0.9f) : 0f;
		}
		pitch += -rear * 0.42f + slam * 0.05f;
		down += Math.abs(slam) * 1.0f;
		var body = r.p("body");
		body.y += down;
		body.xRot += pitch;
		body.zRot += roll;
		body.yRot += -yaw;

		// ---- legs: LF, RF, LH, RH {walk phase, gallop phase}
		String[] sides = { "L", "R" };
		for (int i = 0; i < 4; i++) {
			boolean front = i < 2;
			String sd = sides[i % 2];
			float pw = front ? (i == 0 ? 0.25f : 0.75f) : (i == 2 ? 0f : 0.5f);
			float pg = front ? (i == 0 ? 0.62f : 0.5f) : (i == 2 ? 0.1f : 0f);
			float[] gw = BigRig.gait(cyc + pw, 0.62f);
			float[] gg = BigRig.gait(gcyc + pg, 0.42f);
			float fwd = Mth.lerp(charge, gw[0] * amt, gg[0]);
			float lift = Mth.lerp(charge, gw[1] * amt, gg[1]);
			var up = r.p((front ? "legF" : "legH") + sd);
			var shin = r.p((front ? "shinF" : "shinH") + sd);
			var foot = r.p((front ? "footF" : "footH") + sd);
			if (front) {
				up.xRot -= pitch; // stay vertical while the body pitches
				up.y -= down * 0.6f; // and keep the feet near the ground while it dips
				if (rear > 0f) {
					float paw = Mth.sin(stomp * TAU * 2.4f + (i == 0 ? 0f : 1.6f)) * 0.5f + 0.5f;
					up.xRot += -rear * (0.35f + paw * 0.35f);
					shin.xRot += rear * (0.5f + paw * 0.5f);
					foot.xRot += rear * 0.3f;
				}
			}
			BigAnimals.leg(up, shin, foot, fwd, lift, Mth.lerp(charge, 0.3f, 0.5f), front ? 0.8f : 0.7f, Mth.lerp(charge, 1.2f, 2f));
		}

		// ---- head: low and forward, follows the look, nods with the steps; down with the horn level in the charge
		var head = r.p("head");
		float nod = Mth.cos(cyc * TAU * 2f - 0.8f) * 0.05f * amt + charge * Mth.sin(gcyc * TAU + 1.2f) * 0.07f;
		head.xRot += 0.3f + s.pitch(0.6f) * 0.35f + charge * 0.4f - rear * 0.5f + nod - pitch * 0.6f + breathe * 0.01f;
		head.yRot += -(s.yaw(0.6f) * 0.75f + Mth.sin(t * 0.017f) * 0.08f * (1f - run)) + yaw * 0.8f;
		head.zRot += -roll * 0.6f + Mth.sin(t * 0.023f) * 0.03f * (1f - run);
		r.p("jaw").xRot += rear * 0.25f + Math.abs(slam) * 0.2f;
		// ears: flick now and then, perk forward on the rear, pinned back in the charge
		for (int k = 0; k < 2; k++) {
			float sgn = k == 0 ? 1f : -1f;
			float flick = Math.max(0f, Mth.sin(t * 0.19f + k * 1.3f) - 0.9f) * 6f + Math.max(0f, Mth.sin(t * 0.071f + k * 2.1f) - 0.95f) * 8f;
			var ear = r.p(k == 0 ? "earL" : "earR");
			ear.xRot += -charge * 0.95f + rear * 0.3f - flick * 0.35f;
			ear.zRot += sgn * (-flick * 0.25f - charge * 0.2f);
			ear.yRot += sgn * flick * 0.3f;
			float bl = BigRig.blink(t, 0.37f);
			r.p(k == 0 ? "eyeL" : "eyeR").yScale = 1f - 0.85f * Math.max(bl, charge * 0.3f);
		}
		// tail: hangs and swishes, lags turns; up and back in the charge
		var tail1 = r.p("tail1");
		var tail2 = r.p("tail2");
		var tuft = r.p("tuft");
		float sw = Mth.sin(t * 0.13f) * 0.35f + Mth.sin(cyc * TAU) * 0.2f * amt + s.tailLag * 0.7f;
		tail1.zRot += sw * 0.6f;
		tail1.xRot += 0.15f + charge * 1.3f;
		tail2.zRot += Mth.sin(t * 0.13f - 0.7f) * 0.35f * 0.7f;
		tail2.xRot += charge * 0.3f;
		tuft.zRot += Mth.sin(t * 0.13f - 1.4f) * 0.3f;
	}
}
