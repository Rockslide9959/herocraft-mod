package com.projecthero.mod.client.greenlantern.mech;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import com.projecthero.mod.client.greenlantern.HardLightDraw;
import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.greenlantern.mech.GreenLanternMechCombos;
import com.projecthero.mod.greenlantern.mech.GreenLanternMechConfig;
import com.projecthero.mod.greenlantern.mech.HardLightMechEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 round 2 (user: "Give the mech other mech construct combinations too, at least 3 more, make the models look
 * really good and the animations good aswell and make it cool"): the hard-light weapons of the three one-shot mech
 * combos, in the shared holographic look ({@link HardLightDraw#part}) and built pixel by pixel as they form
 * ({@link HardLightDraw#beginBuild}), drawn off the bone poses the renderer caught:
 * <ul>
 *   <li><b>Meteor Mace</b> (right fist): a long banded haft, a flanged head of light ringed with spikes and the Lantern
 *   emblem on its faces; the impact's shockwave rings and pillars of light where it lands.</li>
 *   <li><b>Wrecking Whirl</b> (right fist): a chain of alternating links out to a spiked ball, with a sweeping arc of
 *   light behind the ball as the mech spins.</li>
 *   <li><b>Lantern Cannon</b> (left forearm): a breech over the forearm, an eight-sided barrel past the fist with cooling
 *   fins, a muzzle brake, energy rings that spin faster as it charges and a gathering orb; then the shell's tracer and
 *   the blast where it lands.</li>
 * </ul>
 */
public final class MechMoveRender {
	/** Fist / forearm bone space (GeckoLib: x mirrored): the middle of the right fist, the left wrist. */
	private static final float FIST_X = 28f / 16f, FIST_Y = 34.5f / 16f, FIST_Z = 1f / 16f;
	private static final float WRIST_Y = 41.5f / 16f;
	/** How many pieces each weapon has (the build's piece count, refreshed every frame it builds). */
	private static int macePieces = 40, chainPieces = 60, cannonPieces = 50;

	private MechMoveRender() {
	}

	public static void render(HardLightMechEntity e, float partial, PoseStack pose, MultiBufferSource buffers, PoseStack.Pose fistR,
			PoseStack.Pose forearmL) {
		int move = e.move();
		Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		float age = e.tickCount + partial;
		HardLightDraw.golden = e.golden();
		try {
			VertexConsumer vc = HardLightDraw.buffer(buffers);
			float t = e.moveTicks() + partial;
			if (move == HardLightMechEntity.MOVE_MACE && fistR != null) {
				mace(e, vc, at(fistR), t, age);
				maceImpact(e, vc, pose, cam, t);
			} else if (move == HardLightMechEntity.MOVE_WHIRL && fistR != null) {
				whirl(e, vc, pose, at(fistR), t, age, cam);
			} else if (move == HardLightMechEntity.MOVE_CANNON && forearmL != null) {
				cannon(e, vc, at(forearmL), t, age);
			}
			shot(e, vc, pose, cam, partial, age, forearmL);
		} finally {
			HardLightDraw.endBuild();
			HardLightDraw.golden = false;
		}
	}

	private static PoseStack at(PoseStack.Pose p) {
		PoseStack ps = new PoseStack();
		ps.last().pose().set(p.pose());
		ps.last().normal().set(p.normal());
		return ps;
	}

	/** 0..1 how solid a weapon is: it forms over {@code formTicks} (pixel by pixel) and fades over the last 6 of {@code len}. */
	private static float fade(float t, int len) {
		return Mth.clamp((len - t) / 6f, 0f, 1f);
	}

	// ---------------------------------------------------------------- Meteor Mace

	private static void mace(HardLightMechEntity e, VertexConsumer vc, PoseStack ps, float t, float age) {
		float a = fade(t, GreenLanternMechConfig.MACE_TICKS);
		if (a <= 0f) {
			return;
		}
		float form = Mth.clamp(t / 8f, 0f, 1f);
		if (form < 1f) {
			HardLightDraw.beginBuild(form, macePieces, 0.3f);
		}
		ps.translate(FIST_X, FIST_Y, FIST_Z);
		// the haft: a pommel over the fist, the grip, three bands, the long shaft
		HardLightDraw.glow(vc, ps, 0f, 0.36f, 0f, 0.1f, 0.06f, 0.1f, a);
		HardLightDraw.part(vc, ps, 0f, 0.26f, 0f, 0.2f, 0.07f, 0.2f, a);
		HardLightDraw.part(vc, ps, 0f, -1.1f, 0f, 0.12f, 1.55f, 0.12f, a);
		for (int i = 0; i < 3; i++) {
			HardLightDraw.part(vc, ps, 0f, -0.55f - i * 0.8f, 0f, 0.17f, 0.06f, 0.17f, HardLightDraw.PALE, a);
		}
		// the head: two cores turned against each other (an eight-sided block), flanges, spikes, the emblem
		float hy = -3.25f;
		ps.pushPose();
		ps.translate(0f, hy, 0f);
		ps.mulPose(Axis.YP.rotationDegrees(age * 2f));
		HardLightDraw.part(vc, ps, 0f, 0f, 0f, 0.62f, 0.72f, 0.62f, a);
		ps.pushPose();
		ps.mulPose(Axis.YP.rotationDegrees(45f));
		HardLightDraw.part(vc, ps, 0f, 0f, 0f, 0.58f, 0.66f, 0.58f, HardLightDraw.DEEP, a);
		ps.popPose();
		HardLightDraw.part(vc, ps, 0f, 0.78f, 0f, 0.48f, 0.08f, 0.48f, HardLightDraw.PALE, a);
		HardLightDraw.part(vc, ps, 0f, -0.78f, 0f, 0.48f, 0.08f, 0.48f, HardLightDraw.PALE, a);
		for (int i = 0; i < 8; i++) {
			ps.pushPose();
			ps.mulPose(Axis.YP.rotationDegrees(i * 45f));
			// a flange down each face, and a spike off it
			HardLightDraw.part(vc, ps, 0f, 0f, 0.7f, 0.05f, 0.62f, 0.12f, a);
			ps.translate(0f, i % 2 == 0 ? 0.25f : -0.25f, 0.95f);
			HardLightDraw.part(vc, ps, 0f, 0f, 0f, 0.1f, 0.1f, 0.22f, a);
			HardLightDraw.glow(vc, ps, 0f, 0f, 0.26f, 0.04f, 0.04f, 0.06f, a);
			ps.popPose();
		}
		// the crown spike, the Lantern emblem on two faces, the glowing core
		HardLightDraw.part(vc, ps, 0f, -1.08f, 0f, 0.14f, 0.26f, 0.14f, a);
		HardLightDraw.glow(vc, ps, 0f, -1.38f, 0f, 0.06f, 0.08f, 0.06f, a);
		for (int s = -1; s <= 1; s += 2) {
			ps.pushPose();
			ps.translate(0f, 0f, s * 0.64f);
			ps.mulPose(Axis.YP.rotationDegrees(s > 0 ? 0f : 180f));
			HardLightDraw.emblem(vc, ps, 0.6f, a);
			ps.popPose();
		}
		float pulse = 0.7f + 0.3f * Mth.sin(age * 0.6f);
		HardLightDraw.glow(vc, ps, 0f, 0f, 0f, 0.22f * pulse, 0.26f * pulse, 0.22f * pulse, a);
		ps.popPose();
		if (form < 1f) {
			macePieces = Math.max(1, HardLightDraw.endBuild());
		}
	}

	/** The shockwave where the mace lands: rings racing out over the ground and pillars of light standing up off it. */
	private static void maceImpact(HardLightMechEntity e, VertexConsumer vc, PoseStack pose, Vec3 cam, float t) {
		float since = t - GreenLanternMechConfig.MACE_IMPACT_TICKS - 1.5f; // the clip lands a little after the server
		if (since < 0f || since > 14f) {
			return;
		}
		float k = since / 14f;
		Vec3 at = GreenLanternMechCombos.maceImpactPoint(e).subtract(cam);
		PoseStack ps = new PoseStack();
		ps.translate(at.x, at.y + 0.08, at.z);
		ps.mulPose(Axis.XP.rotationDegrees(90f));
		float r = (float) GreenLanternMechConfig.MACE_RADIUS;
		for (int i = 0; i < 3; i++) {
			float kk = Mth.clamp(k * 1.3f - i * 0.15f, 0f, 1f);
			if (kk <= 0f || kk >= 1f) {
				continue;
			}
			MechWeaponDraw.ring(vc, ps, 0.8f + r * kk, 0.25f + 0.35f * (1f - kk), i == 0 ? 0xF4FFF6 : HardLightDraw.tint(HardLightDraw.GREEN),
					0.95f * (1f - kk));
		}
		// pillars of light shooting up round the crater
		PoseStack up = new PoseStack();
		up.translate(at.x, at.y, at.z);
		for (int i = 0; i < 10; i++) {
			float ang = i * 36f + 13f;
			float rr = 1.4f + (i % 3) * 1.3f;
			float h = (2.2f + (i % 4) * 0.9f) * Mth.sin(Mth.clamp(k * 2f, 0f, 1f) * Mth.PI);
			if (h <= 0.05f) {
				continue;
			}
			up.pushPose();
			up.mulPose(Axis.YP.rotationDegrees(ang));
			up.translate(0f, h / 2f, rr);
			TurboDraw.box(vc, up.last(), 0.12f, h / 2f, 0.12f, 0xF0FFF4, 0.75f * (1f - k));
			TurboDraw.box(vc, up.last(), 0.28f, h / 2f + 0.1f, 0.28f, HardLightDraw.tint(HardLightDraw.GREEN), 0.28f * (1f - k));
			up.popPose();
		}
		if (k < 0.4f) {
			float f = 1f - k / 0.4f;
			TurboDraw.sphere(vc, up, 0.8f + 2.4f * (1f - f), 0xF4FFF6, 0.55f * f);
			TurboDraw.sphere(vc, up, 1.6f + 3.6f * (1f - f), HardLightDraw.tint(HardLightDraw.GREEN), 0.2f * f);
		}
	}

	// ---------------------------------------------------------------- Wrecking Whirl

	private static final float CHAIN_LEN = 4.2f;
	private static final float BALL_R = 1.0f;

	private static void whirl(HardLightMechEntity e, VertexConsumer vc, PoseStack pose, PoseStack ps, float t, float age, Vec3 cam) {
		float a = fade(t, GreenLanternMechConfig.WHIRL_TICKS);
		if (a <= 0f) {
			return;
		}
		float form = Mth.clamp(t / GreenLanternMechConfig.WHIRL_START_TICKS, 0f, 1f);
		ps.translate(FIST_X, FIST_Y - 0.1f, FIST_Z);
		// where the ball is, in camera space (for the sweeping arc behind it)
		Vector3f ballCam = ps.last().pose().transformPosition(new Vector3f(0f, -(CHAIN_LEN + BALL_R), 0f));
		if (form < 1f) {
			HardLightDraw.beginBuild(form, chainPieces, 0.25f);
		}
		// the shackle round the fist, the links (alternate ones turned a quarter), the ball
		HardLightDraw.part(vc, ps, 0f, 0.1f, 0f, 0.42f, 0.1f, 0.42f, HardLightDraw.PALE, a);
		int links = 12;
		for (int i = 0; i < links; i++) {
			float y = -0.2f - (i + 0.5f) * (CHAIN_LEN - 0.2f) / links;
			ps.pushPose();
			ps.translate(0f, y, 0f);
			ps.mulPose(Axis.YP.rotationDegrees(i % 2 == 0 ? 0f : 90f));
			float lh = (CHAIN_LEN - 0.2f) / links * 0.62f;
			HardLightDraw.part(vc, ps, 0.13f, 0f, 0f, 0.035f, lh, 0.05f, a);
			HardLightDraw.part(vc, ps, -0.13f, 0f, 0f, 0.035f, lh, 0.05f, a);
			HardLightDraw.part(vc, ps, 0f, lh, 0f, 0.15f, 0.035f, 0.05f, a);
			ps.popPose();
		}
		ps.pushPose();
		ps.translate(0f, -(CHAIN_LEN + BALL_R), 0f);
		ps.mulPose(Axis.YP.rotationDegrees(age * 9f));
		ps.mulPose(Axis.XP.rotationDegrees(age * 5f));
		// a round ball: three cubes turned against each other
		HardLightDraw.part(vc, ps, 0f, 0f, 0f, BALL_R * 0.78f, BALL_R * 0.78f, BALL_R * 0.78f, a);
		ps.pushPose();
		ps.mulPose(new Quaternionf().rotationXYZ(0.785f, 0.785f, 0f));
		HardLightDraw.part(vc, ps, 0f, 0f, 0f, BALL_R * 0.74f, BALL_R * 0.74f, BALL_R * 0.74f, HardLightDraw.DEEP, a);
		ps.popPose();
		ps.pushPose();
		ps.mulPose(new Quaternionf().rotationXYZ(0f, 0.785f, 0.785f));
		HardLightDraw.part(vc, ps, 0f, 0f, 0f, BALL_R * 0.7f, BALL_R * 0.7f, BALL_R * 0.7f, a);
		ps.popPose();
		// spikes out along the six axes and the eight corners
		float[][] dirs = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 1, 1, 1 }, { -1, 1, 1 }, { 1, -1, 1 },
				{ -1, -1, 1 }, { 1, 1, -1 }, { -1, 1, -1 }, { 1, -1, -1 }, { -1, -1, -1 } };
		for (float[] d : dirs) {
			Vector3f v = new Vector3f(d[0], d[1], d[2]).normalize();
			ps.pushPose();
			ps.mulPose(new Quaternionf().rotationTo(0f, 1f, 0f, v.x, v.y, v.z));
			HardLightDraw.part(vc, ps, 0f, BALL_R * 0.95f, 0f, 0.13f, 0.24f, 0.13f, a);
			HardLightDraw.glow(vc, ps, 0f, BALL_R * 1.25f, 0f, 0.05f, 0.08f, 0.05f, a);
			ps.popPose();
		}
		float pulse = 0.7f + 0.3f * Mth.sin(age * 0.8f);
		HardLightDraw.glow(vc, ps, 0f, 0f, 0f, 0.3f * pulse, 0.3f * pulse, 0.3f * pulse, a);
		ps.popPose();
		if (form < 1f) {
			chainPieces = Math.max(1, HardLightDraw.endBuild());
		}
		// the arc of light swept behind the ball while it whirls
		if (t > GreenLanternMechConfig.WHIRL_START_TICKS + 2 && t < GreenLanternMechConfig.WHIRL_END_TICKS + 2) {
			Vec3 c = e.getPosition(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)).subtract(cam);
			double dx = ballCam.x - c.x, dz = ballCam.z - c.z;
			float r = (float) Math.sqrt(dx * dx + dz * dz);
			float ang = (float) Math.atan2(dz, dx);
			float y = (float) (ballCam.y - c.y);
			int segs = 18;
			float spin = -1f; // the clip turns the body clockwise seen from above: the arc trails the other way
			for (int i = 0; i < segs; i++) {
				float k = i / (float) segs;
				float an = ang - spin * k * 2.4f;
				float al = (1f - k) * (1f - k) * 0.55f * a;
				PoseStack s = new PoseStack();
				s.translate(c.x + Math.cos(an) * r, y, c.z + Math.sin(an) * r);
				s.mulPose(Axis.YP.rotation((float) (-an)));
				TurboDraw.box(vc, s.last(), r * 2.4f / segs * 0.6f + 0.02f, BALL_R * (0.9f - 0.4f * k), 0.04f, HardLightDraw.tint(HardLightDraw.PALE), al);
			}
		}
	}

	// ---------------------------------------------------------------- Lantern Cannon

	private static void cannon(HardLightMechEntity e, VertexConsumer vc, PoseStack ps, float t, float age) {
		float a = fade(t, GreenLanternMechConfig.CANNON_TICKS);
		if (a <= 0f) {
			return;
		}
		float form = Mth.clamp(t / 7f, 0f, 1f);
		float charge = Mth.clamp((t - 4f) / (GreenLanternMechConfig.CANNON_FIRE_TICKS - 4f), 0f, 1f);
		boolean fired = t >= GreenLanternMechConfig.CANNON_FIRE_TICKS + 1.5f;
		if (form < 1f) {
			HardLightDraw.beginBuild(form, cannonPieces, 0.3f);
		}
		ps.translate(-FIST_X, WRIST_Y, FIST_Z);
		// the breech over the forearm, its armour plates and a glowing cell window
		HardLightDraw.part(vc, ps, 0f, 0.75f, 0f, 0.62f, 0.75f, 0.62f, a);
		HardLightDraw.part(vc, ps, 0f, 0.75f, -0.66f, 0.42f, 0.55f, 0.06f, HardLightDraw.DEEP, a);
		HardLightDraw.part(vc, ps, 0.66f, 0.8f, 0f, 0.06f, 0.5f, 0.38f, HardLightDraw.PALE, a);
		HardLightDraw.part(vc, ps, -0.66f, 0.8f, 0f, 0.06f, 0.5f, 0.38f, HardLightDraw.PALE, a);
		HardLightDraw.glow(vc, ps, 0f, 0.75f, -0.72f, 0.22f, 0.36f, 0.02f, a * (0.4f + 0.6f * charge));
		// the barrel: eight-sided (two boxes turned 45 degrees), past the fist, with cooling fins
		float bl = 3.4f;
		HardLightDraw.part(vc, ps, 0f, -bl / 2f, 0f, 0.5f, bl / 2f, 0.5f, a);
		ps.pushPose();
		ps.mulPose(Axis.YP.rotationDegrees(45f));
		HardLightDraw.part(vc, ps, 0f, -bl / 2f, 0f, 0.46f, bl / 2f, 0.46f, HardLightDraw.DEEP, a);
		ps.popPose();
		for (int i = 0; i < 4; i++) {
			ps.pushPose();
			ps.mulPose(Axis.YP.rotationDegrees(i * 90f + 45f));
			HardLightDraw.part(vc, ps, 0f, -0.9f, 0.62f, 0.05f, 0.6f, 0.14f, HardLightDraw.PALE, a);
			ps.popPose();
		}
		// the muzzle brake
		HardLightDraw.part(vc, ps, 0f, -bl - 0.2f, 0f, 0.66f, 0.2f, 0.66f, a);
		HardLightDraw.part(vc, ps, 0f, -bl - 0.2f, 0f, 0.7f, 0.08f, 0.24f, HardLightDraw.PALE, a);
		HardLightDraw.part(vc, ps, 0f, -bl - 0.2f, 0f, 0.24f, 0.08f, 0.7f, HardLightDraw.PALE, a);
		if (form < 1f) {
			cannonPieces = Math.max(1, HardLightDraw.endBuild());
		}
		// energy rings round the barrel, spinning faster as it charges; the core glowing down its length
		if (!fired) {
			for (int i = 0; i < 3; i++) {
				ps.pushPose();
				float y = -0.8f - i * 1.0f - (age * (0.05f + 0.25f * charge)) % 1.0f;
				ps.translate(0f, y, 0f);
				ps.mulPose(Axis.XP.rotationDegrees(90f));
				ps.mulPose(Axis.ZP.rotationDegrees(age * (8f + 40f * charge) + i * 40f));
				MechWeaponDraw.ring(vc, ps, 0.72f + 0.12f * charge, 0.06f + 0.06f * charge, i == 1 ? 0xF4FFF6 : HardLightDraw.tint(HardLightDraw.GREEN),
						(0.4f + 0.6f * charge) * a);
				ps.popPose();
			}
			// the shell gathering at the muzzle
			ps.pushPose();
			ps.translate(0f, -bl - 0.55f, 0f);
			TurboDraw.orb(vc, ps, 0.15f + 0.6f * charge, HardLightDraw.tint(HardLightDraw.GREEN), age * 14f, (0.3f + 0.7f * charge) * a);
			ps.popPose();
		}
		HardLightDraw.glow(vc, ps, 0f, -bl / 2f, 0f, 0.2f, bl / 2f - 0.1f, 0.2f, a * (0.25f + 0.6f * charge));
	}

	/** The shell's tracer from the muzzle and the blast where it landed. */
	private static void shot(HardLightMechEntity e, VertexConsumer vc, PoseStack pose, Vec3 cam, float partial, float age, PoseStack.Pose forearmL) {
		float since = e.level().getGameTime() - e.shotAt() + partial;
		if (since < 0f || since > GreenLanternMechConfig.CANNON_SHOT_TICKS) {
			return;
		}
		float k = since / GreenLanternMechConfig.CANNON_SHOT_TICKS;
		Vec3 end = e.shotEnd().subtract(cam);
		Vec3 from;
		if (forearmL != null) {
			Vector3f m = new Vector3f(-FIST_X, WRIST_Y - 3.8f, FIST_Z);
			forearmL.pose().transformPosition(m);
			from = new Vec3(m.x, m.y, m.z);
		} else {
			from = GreenLanternMechCombos.cannonMuzzle(e, e.moveDir()).subtract(cam);
		}
		Vec3 d = end.subtract(from);
		float len = (float) d.length();
		if (len > 0.5f) {
			PoseStack ps = new PoseStack();
			ps.translate(from.x, from.y, from.z);
			ps.mulPose(Axis.YP.rotation((float) Math.atan2(d.x, d.z)));
			ps.mulPose(Axis.XP.rotation((float) -Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
			float travel = Math.min(1f, since / 2f);
			float w = 0.75f * (1f - k * 0.7f);
			HardLightDraw.streak(vc, ps, len * travel, w, age, 0.95f * (1f - k));
			HardLightDraw.streak(vc, ps, len * travel, w * 1.8f, -age, 0.3f * (1f - k));
			if (since < 4f) { // the muzzle flash
				float f = 1f - since / 4f;
				TurboDraw.sphere(vc, ps, 0.7f + 0.9f * (1f - f), 0xF4FFF6, 0.7f * f);
				MechWeaponDraw.ring(vc, ps, 0.8f + 2.2f * (1f - f), 0.3f, HardLightDraw.tint(HardLightDraw.GREEN), 0.8f * f);
			}
		}
		if (since >= 1.5f) { // the blast
			float b = Mth.clamp((since - 1.5f) / (GreenLanternMechConfig.CANNON_SHOT_TICKS - 1.5f), 0f, 1f);
			PoseStack ps = new PoseStack();
			ps.translate(end.x, end.y, end.z);
			float r = (float) GreenLanternMechConfig.CANNON_BLAST_RADIUS;
			TurboDraw.sphere(vc, ps, 0.6f + r * 0.5f * b, 0xF4FFF6, 0.75f * (1f - b));
			TurboDraw.sphere(vc, ps, 1.0f + r * b, HardLightDraw.tint(HardLightDraw.GREEN), 0.32f * (1f - b));
			ps.mulPose(Axis.XP.rotationDegrees(90f));
			MechWeaponDraw.ring(vc, ps, 1.2f + r * 1.3f * b, 0.4f, 0xF4FFF6, 0.85f * (1f - b));
		}
	}
}
