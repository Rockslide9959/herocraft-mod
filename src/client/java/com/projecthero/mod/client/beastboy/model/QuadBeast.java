package com.projecthero.mod.client.beastboy.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

/**
 * v0.17.3 hq round: the shared four-legged animator of the blocky land mammals (bone names from the generator's leg builders:
 * upper / fore / wrist / paw and thigh / shin / meta / hpaw + LF RF LH RH, body / chest / hips, tail0..n, lidL / lidR, earL / earR).
 * Gait: walk -> trot -> rotary gallop picked from the speed, feet planted by IK; body bob, pitch rock and spine flex; blinks, ear flicks;
 * a tail chain with lag. The animals add their heads, faces and moves on top.
 */
abstract class QuadBeast extends BoxBeast {
	final float[] restLF, restRF, restLH, restRH;
	/** Paw pivot height above the ground (the paw box's height). */
	final float pawH;

	/** This frame's gait (filled by {@link #gait}). */
	float phase, cyc, run, amt, gal, bob, rock, flex;
	float[][] feet;

	QuadBeast(LayerDefinition def, float pawH) {
		super(def);
		this.pawH = pawH;
		restLF = rest("pawLF");
		restRF = rest("pawRF");
		restLH = rest("hpawLH");
		restRH = rest("hpawRH");
	}

	static int seed(BeastPose s, int k) {
		return s.entityId < 0 ? k : s.entityId * 7 + k;
	}

	/**
	 * Works out the gait for this frame. {@code cadence}: gait cycles per walk-position unit; strides / lifts in units; {@code still}
	 * 0..1 holds the legs (sitting, standing up).
	 */
	void gait(BeastPose s, float walkSpd, float runSpd, float cadence, float strideW, float strideR, float liftW, float liftR, float still) {
		amt = s.walkAmt * (1f - still);
		run = LandRig.run(s, walkSpd, runSpd) * (1f - still);
		phase = s.walkPos * cadence;
		cyc = phase * Mth.TWO_PI;
		gal = BeastPose.smooth(Math.max(0f, run * 2f - 1f));
		bob = Mth.cos(cyc * 2f) * amt * (1f - gal) + Mth.sin(cyc + 0.6f) * gal * amt;
		rock = Mth.cos(cyc) * gal * amt;
		flex = Mth.sin(cyc - 0.3f) * gal * amt;
		feet = LandRig.feet(phase, run, amt, strideW, strideR, liftW, liftR, true);
	}

	/** A fore leg to its gait foot (+ offsets: {@code fwd} forward, {@code up} lifted), the paw folding in swing. */
	void foreLeg(String id, float[] restPaw, float[] f, float fwd, float up) {
		float[] foot = { restPaw[0], pawH + f[1] + up, restPaw[2] + f[0] + fwd };
		foreLegTo(id, foot, f[2], 0f);
	}

	/** A fore leg with its paw pivot at {@code foot}; {@code swing} -1 planted or 0..1; {@code curl} extra paw curl (toes down). */
	void foreLegTo(String id, float[] foot, float swing, float curl) {
		float[][] j = LandRig.fore(rest("upper" + id), foot, len("fore" + id), len("wrist" + id), len("paw" + id), swing);
		aimLeg("upper" + id, "fore" + id, "wrist" + id, "paw" + id, j[2], j[3], false);
		float fl = swing >= 0f ? Mth.sin(Math.min(1f, swing * 1.15f) * Mth.PI) : 0f;
		level("paw" + id, fl * 1.1f + curl);
	}

	/** A hind leg to its gait foot (+ offsets). */
	void hindLeg(String id, float[] restPaw, float[] f, float fwd, float up, float lean) {
		float[] foot = { restPaw[0], pawH + f[1] + up, restPaw[2] + f[0] + fwd };
		float[][] j = LandRig.hind(rest("thigh" + id), foot, len("shin" + id), len("meta" + id), len("hpaw" + id), lean, f[2]);
		hindLegTo(id, j[2], foot, f[2]);
	}

	void hindLegTo(String id, float[] hock, float[] foot, float swing) {
		aimLeg("thigh" + id, "shin" + id, "meta" + id, "hpaw" + id, hock, foot, true);
		float fl = swing >= 0f ? Mth.sin(swing * Mth.PI) : 0f;
		level("hpaw" + id, fl * 0.8f);
	}

	/** Blink / squint both lids (0 open .. 1 shut). */
	void lids(float lid) {
		for (String n : new String[] { "lidL", "lidR" }) {
			ModelPart l = p(n);
			l.visible = lid > 0.04f;
			l.yScale = Math.max(0.05f, Math.min(1f, lid));
		}
	}

	/** Both ears: {@code tilt} back (-) / forward (+), splay outward, with a flick each now and then. */
	void ears(float t, int seed, float tilt, float splay) {
		for (int side = -1; side <= 1; side += 2) {
			float flick = LandRig.earFlick(t, seed, side);
			rot(p(side < 0 ? "earR" : "earL"), tilt - flick * 0.6f, side * (0.08f + flick * 0.25f), side * splay);
		}
	}

	/**
	 * The tail chain {@code prefix}0..n-1: base pitch (+ = up), per-segment curl, a lazy idle sway and a gallop wave both travelling down
	 * the tail (each segment lagging the last), and the body-turn lag from {@link BeastPose#tailLag}.
	 */
	void tail(BeastPose s, String prefix, int n, float basePitch, float curl, float swayAmt, float swaySpd, float turnLag) {
		float t = s.age;
		for (int i = 0; i < n; i++) {
			float lag = i * 0.55f;
			float pt = i == 0 ? basePitch : curl;
			pt += Mth.sin(cyc - 0.8f - lag) * 0.1f * gal * amt;
			float yw = Mth.sin(t * swaySpd - lag) * swayAmt * (i == 0 ? 1f : 0.6f) + s.tailLag * turnLag * (i == 0 ? 1f : 0.3f);
			rot(p(prefix + i), pt, yw, 0f);
		}
	}

	static float[] lerp(float[] a, float[] b, float k) {
		return new float[] { Mth.lerp(k, a[0], b[0]), Mth.lerp(k, a[1], b[1]), Mth.lerp(k, a[2], b[2]) };
	}
}
