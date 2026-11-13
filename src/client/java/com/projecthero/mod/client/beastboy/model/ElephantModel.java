package com.projecthero.mod.client.beastboy.model;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy's elephant (2.6 blocks tall), HQ round: a blocky Minecraft-style model ({@link ElephantGeo}, texture
 * {@code elephant.png}): a big body with a shoulder mass, rump and belly, a domed head with a forehead bulge, big flat ears (veined
 * pink-green inside) that fan, a seven-link ringed trunk with a nostril tip, three-link curved tusks, lidded eyes, column legs with fold
 * rings and four toenails, a tufted tail. Gait: a slow four-beat amble with a heavy dip, roll and shoulder sway; the trunk swings with
 * lag. Idle: ear fanning, trunk sway and curl, tail swish, blinks. Moves: stomp (rears, trumpets, slams), spray (trunk curls up over the
 * head then whips forward and sprays), grab / throw (trunk reaches, curls round, lifts and tosses).
 */
public final class ElephantModel {
	private static final BigBox.Lazy RIG = new BigBox.Lazy(ElephantGeo::layer, ElephantGeo.TREE, "elephant");
	private static final float TAU = BigRig.TAU;

	private ElephantModel() {
	}

	/** Unused: {@link BigAnimals} draws the elephant before BeastFormArt's mesh switch is reached (kept so that switch needs no edit). */
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
		float cyc = s.walkPos * 0.075f;
		float breathe = Mth.sin(t * 0.08f);
		float stomp = s.is("stomp") ? s.prog : -1f;
		float spray = s.is("spray") ? s.prog : -1f;
		float grab = s.isAny("grab", "throw") ? s.prog : -1f;

		// ---- stomp: rear up on the hind legs, trumpet, slam down with a shudder
		float rear = 0f, slam = 0f;
		if (stomp >= 0f) {
			rear = stomp < 0.45f ? BigRig.easeOut(stomp / 0.45f) : (stomp < 0.56f ? 1f - BigRig.easeIn((stomp - 0.45f) / 0.11f) : 0f);
			slam = stomp >= 0.56f ? BigRig.wobble((stomp - 0.56f) * 12f, 3f, 0.8f) : 0f;
		}
		float down = BigRig.dip(cyc - 0.06f) * 1.0f * amt - breathe * 0.25f + Math.abs(slam) * 1.4f;
		float pitch = -rear * 0.48f + slam * 0.06f + Mth.sin(cyc * TAU * 2f) * 0.012f * amt;
		float roll = Mth.sin(cyc * TAU) * 0.05f * amt + Mth.sin(t * 0.027f) * 0.012f * (1f - amt);
		float yaw = Mth.sin(cyc * TAU) * 0.04f * amt;
		ModelPart body = r.p("body");
		body.y += down;
		body.xRot += pitch;
		body.zRot += roll;
		body.yRot += -yaw;

		// ---- legs: LF, RF, LH, RH, lateral sequence
		String[] sd = { "L", "R" };
		for (int i = 0; i < 4; i++) {
			boolean front = i < 2;
			float ph = front ? (i == 0 ? 0.25f : 0.75f) : (i == 2 ? 0f : 0.5f);
			float[] g = BigRig.gait(cyc + ph, 0.65f);
			ModelPart up = r.p((front ? "legF" : "legH") + sd[i % 2]);
			ModelPart shin = r.p((front ? "shinF" : "shinH") + sd[i % 2]);
			ModelPart foot = r.p((front ? "footF" : "footH") + sd[i % 2]);
			if (front) {
				up.xRot -= pitch;
				up.y -= down * 0.6f;
				if (rear > 0f) {
					float near = i == 0 ? 1f : 0.75f;
					up.xRot += -rear * 0.65f * near;
					shin.xRot += rear * 0.9f * near;
					foot.xRot += rear * 0.2f;
				}
			}
			BigAnimals.leg(up, shin, foot, g[0] * amt, g[1] * amt, 0.27f, 0.75f, 1.6f);
		}

		// ---- head: follows the look, nods with the steps, up when rearing
		ModelPart head = r.p("head");
		float nod = Mth.cos(cyc * TAU * 2f - 0.7f) * 0.04f * amt;
		head.xRot += 0.05f + s.pitch(0.6f) * 0.3f - rear * 0.25f - pitch * 0.5f + nod + breathe * 0.01f;
		head.yRot += -(s.yaw(0.6f) * 0.7f + Mth.sin(t * 0.015f) * 0.08f * (1f - amt)) + yaw * 0.7f;
		head.zRot += -roll * 0.5f;

		// ---- ears: slow fanning, faster with the walk, flared wide on the rear and the spray
		float sprayK = spray >= 0f ? BeastPose.envelope(spray, 0.15f, 0.2f) : 0f;
		float flare = Math.max(rear, sprayK * 0.7f);
		for (int k = 0; k < 2; k++) {
			float sgn = k == 0 ? 1f : -1f;
			float fan = 0.55f + Mth.sin(t * 0.11f + k * 0.5f) * 0.16f + Mth.sin(cyc * TAU + k * Mth.PI) * 0.12f * amt + flare * 0.8f;
			ModelPart ear = r.p(k == 0 ? "earL" : "earR");
			ear.yRot += sgn * fan;
			ear.zRot += sgn * (-0.04f - flare * 0.15f + Mth.sin(t * 0.11f + k * 0.5f - 0.6f) * 0.03f);
			r.p(k == 0 ? "eyeL" : "eyeR").yScale = 1f - 0.85f * BigRig.blink(t, 0.61f);
		}

		// ---- trunk: hangs with a forward curl at the tip, sways with lag; the moves bend it link by link
		float[] tx = new float[7], tz = new float[7];
		for (int i = 0; i < 7; i++) {
			float lagT = t * 0.09f - i * 0.55f;
			tx[i] = (i < 2 ? 0.05f : (i < 4 ? 0f : -0.16f)) + Mth.sin(lagT) * 0.04f + Mth.sin(cyc * TAU - i * 0.45f) * 0.07f * amt
					+ Mth.sin(t * 0.21f - i * 0.4f) * 0.02f;
			tz[i] = Mth.sin(t * 0.07f - i * 0.6f) * 0.06f + Mth.sin(cyc * TAU - i * 0.5f) * 0.05f * amt + s.tailLag * 0.25f;
		}
		float trunkUp = rear; // trumpet on the rear
		if (spray >= 0f) {
			// 0-0.3 curl up over the head (fill), 0.3-0.42 whip forward, hold and spray, then let go
			float up = BigRig.smooth(spray / 0.3f);
			float whip = BigRig.ph(spray, 0.3f, 0.42f);
			float rel = BigRig.ph(spray, 0.82f, 1f);
			for (int i = 0; i < 7; i++) {
				float curl = i == 0 ? -0.75f : -0.48f;
				float aim = i == 0 ? -1.45f : (i < 3 ? -0.12f : 0.02f);
				float v = Mth.lerp(BigRig.easeOut(whip), curl * up, aim + Mth.sin(t * 1.6f - i) * 0.03f);
				tx[i] = Mth.lerp(BigRig.smooth(rel), v, tx[i]);
			}
			trunkUp = 0f;
		}
		if (grab >= 0f) {
			// 0-0.35 reach forward and low, 0.35-0.5 curl the tip round, 0.5-0.75 lift and toss up, then settle
			float reach = BigRig.smooth(grab / 0.35f) * (1f - BigRig.ph(grab, 0.82f, 1f));
			float curl = BigRig.smooth(BigRig.ph(grab, 0.33f, 0.5f)) * (1f - BigRig.ph(grab, 0.72f, 0.82f));
			float lift = BigRig.back(BigRig.ph(grab, 0.48f, 0.72f)) * (1f - BigRig.ph(grab, 0.78f, 1f));
			for (int i = 0; i < 7; i++) {
				float v = tx[i];
				v = Mth.lerp(reach, v, i == 0 ? -0.75f : -0.08f);
				v += curl * (i >= 4 ? -0.85f : 0f);
				v += lift * (i == 0 ? -1.2f : -0.22f);
				tx[i] = v;
			}
		}
		if (trunkUp > 0f) {
			for (int i = 0; i < 7; i++) {
				tx[i] = Mth.lerp(trunkUp, tx[i], (i == 0 ? -0.9f : -0.42f) + Mth.sin(t * 1.3f - i) * 0.04f);
			}
		}
		for (int i = 0; i < 7; i++) {
			ModelPart seg = r.p("trunk" + (i + 1));
			seg.xRot += tx[i];
			seg.zRot += tz[i];
		}
		r.p("jaw").xRot += Math.max(trunkUp, sprayK) * 0.3f + Math.max(0f, Mth.sin(t * 0.05f)) * 0.04f;

		// ---- tail: swish and lag
		float sw = Mth.sin(t * 0.12f) * 0.3f + Mth.sin(cyc * TAU) * 0.15f * amt + s.tailLag * 0.6f;
		r.p("tail1").zRot += sw * 0.6f;
		r.p("tail1").xRot += 0.1f + rear * 0.4f;
		r.p("tail2").zRot += Mth.sin(t * 0.12f - 0.7f) * 0.25f;
		r.p("tuft").zRot += Mth.sin(t * 0.12f - 1.4f) * 0.25f;
	}
}
