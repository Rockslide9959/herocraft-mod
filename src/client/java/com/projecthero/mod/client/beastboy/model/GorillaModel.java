package com.projecthero.mod.client.beastboy.model;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy's gorilla (2 blocks tall upright), HQ round: a blocky Minecraft-style model ({@link GorillaGeo}, texture
 * {@code gorilla.png}): a wedge torso (pelvis, pot belly, deep chest, wide shoulders, a hump behind the head, a lighter "silver" saddle,
 * bare chest), a head with a sagittal crest, heavy brow, deep-set eyes, broad nostrils, muzzle and a jaw with canines; huge arms
 * (deltoid, upper arm, thick forearm, hair tufts at the elbow and down the forearm) ending in knuckle-walking hands; short legs with
 * hand-like feet. The torso leans forward and the arms reach the ground by IK, so the knuckles plant and roll. Idle: breathing,
 * looking round, blinks, a shift of weight. Moves: chest_beat / beat / roar (rears up, slaps the chest, hoots), smash (both fists up,
 * slam), grab / throw (reach, grip, lift overhead, hurl), punch / strike (jab with a shoulder twist).
 */
public final class GorillaModel {
	private static final BigBox.Lazy RIG = new BigBox.Lazy(GorillaGeo::layer, GorillaGeo.TREE, "gorilla");
	private static final float TAU = BigRig.TAU;
	/** Upright-frame offsets (from the hip pivot) of the shoulders and the chest. */
	private static final float SH_Y = 15f, SH_Z = 0.5f, UPPER = 11.5f, FORE = 10f;

	private GorillaModel() {
	}

	/** Unused: {@link BigAnimals} draws the gorilla before BeastFormArt's mesh switch is reached (kept so that switch needs no edit). */
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
		float cyc = s.walkPos * 0.085f;
		float breathe = Mth.sin(t * 0.1f);
		float beat = s.isAny("chest_beat", "beat", "roar") ? s.env(s.move, 0.18f, 0.2f) : 0f;
		float smash = s.is("smash") ? s.prog : -1f;
		float grab = s.isAny("grab", "throw") ? s.prog : -1f;
		float punch = s.isAny("punch", "strike") ? s.prog : -1f;

		// ---- stance: knuckle-walk lean -> upright for the chest beat and the smash wind-up
		float upright = beat;
		float slam = 0f;
		if (smash >= 0f) {
			upright = smash < 0.4f ? BigRig.easeOut(smash / 0.4f) * 0.8f : (smash < 0.55f ? 0.8f - BigRig.easeIn((smash - 0.4f) / 0.15f) * 1.15f : -0.35f);
			if (smash >= 0.55f) {
				upright = -0.35f * (1f - BigRig.smooth((smash - 0.55f) / 0.45f));
				slam = BigRig.wobble((smash - 0.55f) * 10f, 3.4f, 0.9f);
			}
		}
		float P = Mth.lerp(upright, 0.72f, 0.12f) + Mth.sin(cyc * TAU * 2f) * 0.03f * amt + breathe * 0.015f;
		float lift = upright * 2.6f;
		float down = BigRig.dip(cyc) * 0.8f * amt + Math.abs(slam) * 0.8f - breathe * 0.15f - lift;
		float roll = Mth.sin(cyc * TAU) * 0.07f * amt + Mth.sin(t * 0.029f) * 0.02f * (1f - amt);
		float yaw = Mth.sin(cyc * TAU) * 0.07f * amt;
		float twist = 0f;
		if (punch >= 0f) {
			twist = Mth.sin(BigRig.ph(punch, 0f, 0.8f) * Mth.PI) * 0.35f;
		}
		ModelPart body = r.p("body");
		body.y += down;
		body.xRot += P;
		body.zRot += roll;
		body.yRot += -yaw - twist;
		float hipY = 14f - down;

		// ---- legs (knees forward), IK to the feet
		for (int k = 0; k < 2; k++) {
			String sd = k == 0 ? "L" : "R";
			float[] g = BigRig.gait(cyc + (k == 0 ? 0f : 0.5f), 0.6f);
			float fz = g[0] * 6f * amt + upright * 1f;
			float fy = 2.5f + g[1] * 2.2f * amt;
			float[] a = BigAnimals.ik(fz + 1.2f, fy - hipY, 5.5f, 6f, false);
			r.p("leg" + sd).y += down;
			r.p("leg" + sd).xRot += a[0];
			r.p("shin" + sd).xRot += a[1] - a[0];
			r.p("foot" + sd).xRot += -a[1] + g[1] * amt * 0.3f;
		}

		// ---- arms: IK to the knuckles on the ground ahead (gait: arms half a beat after the legs of the same side)
		float[] sh = BigAnimals.pitchPt(P, SH_Y, SH_Z);
		float shY = hipY + sh[0], shZ = -3f + sh[1];
		for (int k = 0; k < 2; k++) {
			float sgn = k == 0 ? 1f : -1f;
			String sd = k == 0 ? "L" : "R";
			float[] g = BigRig.gait(cyc + (k == 0 ? 0.25f : 0.75f), 0.58f);
			float hz = 12f + g[0] * 8f * amt + yaw * sgn * 6f;
			float hy = 7f + g[1] * 3f * amt;
			float[] a = BigAnimals.ik(hz - shZ, hy - shY, UPPER, FORE, true);
			ModelPart arm = r.p("arm" + sd), fore = r.p("fore" + sd), hand = r.p("hand" + sd);
			float au = a[0], al = a[1];
			float hx = -al + g[1] * amt * 0.4f;
			float out = -sgn * 0.08f;
			// chest beat: cupped hands slap the chest in turn
			if (beat > 0f) {
				float hit = Math.max(0f, Mth.sin(t * 1.5f + k * Mth.PI));
				au = Mth.lerp(beat, au, -1.1f - hit * 0.25f);
				al = Mth.lerp(beat, al, -2.7f + hit * 0.15f);
				hx = Mth.lerp(beat, hx, -0.3f);
				out = Mth.lerp(beat, out, -sgn * (0.35f + hit * 0.2f));
			}
			if (smash >= 0f) {
				float upK = smash < 0.4f ? BigRig.easeOut(smash / 0.4f) : (smash < 0.55f ? 1f - BigRig.easeIn((smash - 0.4f) / 0.15f) : 0f);
				float hitK = smash >= 0.45f && smash < 0.75f ? 1f - BigRig.smooth((smash - 0.55f) / 0.2f) : 0f;
				au = Mth.lerp(upK, au, -3.0f);
				al = Mth.lerp(upK, al, -3.2f);
				au = Mth.lerp(hitK * (1f - upK), au, -0.95f);
				al = Mth.lerp(hitK * (1f - upK), al, -1.1f);
				hx = Mth.lerp(Math.max(upK, hitK), hx, 0f);
				out = Mth.lerp(upK, out, sgn * 0.12f);
			}
			if (grab >= 0f && k == 1) {
				float reach = BigRig.smooth(grab / 0.3f) * (1f - BigRig.ph(grab, 0.35f, 0.5f));
				float over = BigRig.smooth(BigRig.ph(grab, 0.35f, 0.62f)) * (1f - BigRig.ph(grab, 0.62f, 0.75f));
				float hurl = BigRig.bump(grab, 0.62f, 0.95f);
				au = Mth.lerp(reach, au, -1.45f);
				al = Mth.lerp(reach, al, -1.55f);
				au = Mth.lerp(over, au, -3.1f);
				al = Mth.lerp(over, al, -2.4f);
				au = Mth.lerp(hurl, au, -1.7f);
				al = Mth.lerp(hurl, al, -1.7f);
				hx = Mth.lerp(Math.max(reach, Math.max(over, hurl)), hx, BigRig.ph(grab, 0.25f, 0.35f) * 0.9f * (1f - hurl));
			}
			if (punch >= 0f && k == 1) {
				float jab = BigRig.bump(punch, 0.15f, 0.85f);
				float cock = BigRig.bump(punch, 0f, 0.3f);
				au = Mth.lerp(Math.max(jab, cock), au, jab > cock ? -1.6f : -0.6f);
				al = Mth.lerp(Math.max(jab, cock), al, jab > cock ? -1.62f : -2.4f);
				hx = Mth.lerp(jab, hx, 0f);
			}
			arm.xRot += au - P;
			arm.zRot += out;
			fore.xRot += al - au;
			hand.xRot += hx;
			hand.zRot += sgn * 0.05f;
		}

		// ---- head: kept level and looking; hoots with the chest beat
		ModelPart head = r.p("head");
		float hoot = beat * (0.55f + 0.25f * Mth.sin(t * 0.9f));
		head.xRot += -P + 0.12f + s.pitch(0.6f) * 0.5f - beat * 0.35f + Mth.cos(cyc * TAU * 2f - 0.6f) * 0.04f * amt;
		head.yRot += -(s.yaw(0.7f) * 0.8f + Mth.sin(t * 0.019f) * 0.12f * (1f - amt) * (1f - beat)) + yaw * 0.8f + twist * 0.6f;
		head.zRot += -roll * 0.5f;
		float jaw = Math.max(hoot, smash >= 0.4f && smash < 0.75f ? 0.6f : 0f);
		jaw = Math.max(jaw, grab >= 0.6f && grab < 0.9f ? 0.45f : 0f);
		r.p("jaw").xRot += jaw * 0.7f;
		float bl = BigRig.blink(t, 0.83f);
		r.p("eyeL").yScale = 1f - 0.85f * bl;
		r.p("eyeR").yScale = 1f - 0.85f * bl;
	}
}
