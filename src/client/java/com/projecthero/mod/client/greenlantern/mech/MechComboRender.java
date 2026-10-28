package com.projecthero.mod.client.greenlantern.mech;

import java.util.Iterator;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import com.projecthero.mod.client.greenlantern.HardLightDraw;
import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.greenlantern.mech.GreenLanternMechConfig;
import com.projecthero.mod.greenlantern.mech.HardLightMechEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: draws the Hard-Light Mech's construct combos ({@link MechWeaponDraw} builds the meshes): the katana in the
 * right fist (forming out of the tsuba as it is drawn, sliding back into light as it is sheathed), its slash trails,
 * the iaido crescent in flight, the Giga Drill on the right forearm (growing as it spins up, spinning faster, full size
 * in the charge, shrinking back after) and the GIGA DRILL BREAK flash (a giant ghost drill punching on, shockwave rings).
 * The renderer hands over the right fist's / forearm's poses caught while GeckoLib drew them, so the weapons follow
 * every clip exactly.
 */
public final class MechComboRender {
	/** Fist bone space (GeckoLib: x mirrored): the middle of the right fist, where the katana's grip passes through. */
	private static final float FIST_X = 28f / 16f, FIST_Y = 34.5f / 16f, FIST_Z = 1f / 16f;
	/** Forearm bone space: the wrist, where the drill's collar sits. */
	private static final float WRIST_Y = 41.5f / 16f;
	/** How long a slash's trail is kept (ticks). */
	private static final float TRAIL_TICKS = 3.2f;

	private MechComboRender() {
	}

	/** 0..1: how big the drill is right now (0 = not out). */
	public static float drillScale(HardLightMechEntity e, float partial) {
		float t = e.drillStateTicks() + partial;
		return switch (e.drillState()) {
			case HardLightMechEntity.DRILL_SPIN -> Mth.clamp(t / 5f, 0f, 1f);
			case HardLightMechEntity.DRILL_CHARGE -> 1f;
			case HardLightMechEntity.DRILL_RECOVER -> Mth.clamp(1f - t / GreenLanternMechConfig.DRILL_RECOVER_TICKS, 0f, 1f);
			default -> 0f;
		};
	}

	/** {reveal, fit}: how much of the blade is out, how solid the mounts are. */
	public static float[] katanaShown(HardLightMechEntity e, float partial) {
		float t = e.katanaStateTicks() + partial;
		return switch (e.katanaState()) {
			case HardLightMechEntity.KATANA_DRAWING -> new float[] { Mth.clamp((t - 3f) / 6f, 0f, 1f), Mth.clamp(t / 3f, 0f, 1f) };
			case HardLightMechEntity.KATANA_DRAWN -> new float[] { 1f, 1f };
			case HardLightMechEntity.KATANA_SHEATHING -> new float[] { 1f - Mth.clamp((t - 2f) / 6f, 0f, 1f), 1f - Mth.clamp((t - 7.5f) / 2.5f, 0f, 1f) };
			default -> new float[] { 0f, 0f };
		};
	}

	/** When the cut proper starts (ticks after the server's swing): the clip's wind-up plus its lag on the client. */
	private static float trailStart(int kind) {
		return switch (kind) {
			case HardLightMechEntity.SLASH_3 -> 5.2f;
			case HardLightMechEntity.SLASH_IAIDO -> 4.8f;
			case HardLightMechEntity.SLASH_SPIN -> 3.5f;
			default -> 3.6f;
		};
	}

	private static float slashLength(int kind) {
		return switch (kind) {
			case HardLightMechEntity.SLASH_3 -> 14f;
			case HardLightMechEntity.SLASH_SPIN -> 16f;
			case HardLightMechEntity.SLASH_IAIDO -> 13f;
			default -> 9f;
		};
	}

	public static void render(HardLightMechEntity e, float partial, PoseStack pose, MultiBufferSource buffers, PoseStack.Pose fist,
			PoseStack.Pose forearm) {
		VertexConsumer vc = HardLightDraw.buffer(buffers);
		float age = e.tickCount + partial;
		Vec3 origin = e.getPosition(partial);
		Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		// the pose handed in is the entity's (translation only: camera space minus the view rotation)
		Vec3 renderOrigin = origin.subtract(cam);
		boolean golden = e.golden();
		float[] k = katanaShown(e, partial);
		if (fist != null && (k[0] > 0f || k[1] > 0f)) {
			PoseStack ps = at(fist);
			ps.translate(FIST_X, FIST_Y, FIST_Z - 0.72f);
			MechWeaponDraw.katana(vc, ps, k[0], k[1], age, golden);
			trailSample(e, partial, ps.last().pose(), cam, k[0]);
		} else {
			e.clientTrail.clear();
		}
		drawTrail(e, partial, vc, pose, renderOrigin.add(cam), golden);
		float ds = drillScale(e, partial);
		if (forearm != null && ds > 0.01f) {
			float pw = e.drillPower();
			boolean charge = e.drillState() == HardLightMechEntity.DRILL_CHARGE;
			float len = (charge ? 2.6f + 3.2f * pw : 1.9f + 2.5f * pw) * (0.25f + 0.75f * ds);
			float r = (charge ? 0.82f + 0.42f * pw : 0.6f + 0.36f * pw) * (0.3f + 0.7f * ds);
			float spin = age * (charge ? 2.4f : 0.3f + 1.6f * pw);
			PoseStack ps = at(forearm);
			ps.translate(FIST_X, WRIST_Y, FIST_Z);
			MechWeaponDraw.drill(vc, ps, len, r, spin, Math.min(1f, 0.4f + ds), golden);
			if (e.drillState() == HardLightMechEntity.DRILL_SPIN && e.drillStateTicks() < 6) {
				// the arm turning into the drill: a flash where the fist was
				float f = 1f - (e.drillStateTicks() + partial) / 6f;
				ps.translate(0f, -0.5f, 0f);
				TurboDraw.orb(vc, ps, 0.9f * f + 0.2f, HardLightDraw.tint(HardLightDraw.GREEN), age * 20f, 0.8f * f);
			}
		}
		HardLightDraw.golden = golden;
		try {
			wave(e, partial, vc, pose, origin);
			gigaBreak(e, partial, vc, pose, origin, age);
		} finally {
			HardLightDraw.golden = false;
		}
	}

	private static PoseStack at(PoseStack.Pose p) {
		PoseStack ps = new PoseStack();
		ps.last().pose().set(p.pose());
		ps.last().normal().set(p.normal());
		return ps;
	}

	// ---------------------------------------------------------------- slash trails

	private static void trailSample(HardLightMechEntity e, float partial, Matrix4f m, Vec3 cam, float reveal) {
		long since = e.level().getGameTime() - e.slashAt();
		float now = e.level().getGameTime() + partial;
		// only the cut itself leaves a trail: from the end of the wind-up (plus the ~2 ticks the clip runs behind the server)
		float t = since + partial;
		boolean slashing = since >= 0 && t >= trailStart(e.slashKind()) && t < slashLength(e.slashKind()) + 2f && reveal > 0.9f;
		Iterator<double[]> it = e.clientTrail.iterator();
		while (it.hasNext()) {
			if (now - it.next()[9] > TRAIL_TICKS) {
				it.remove();
			}
		}
		if (!slashing) {
			return;
		}
		double[] s = new double[10];
		float[][] pts = { MechWeaponDraw.bladePoint(0.5f, 0f), MechWeaponDraw.bladePoint(0.86f, 0f), MechWeaponDraw.bladePoint(1.0f, 0.3f) };
		for (int i = 0; i < 3; i++) {
			Vector3f v = m.transformPosition(new Vector3f(pts[i][0], pts[i][1], pts[i][2]));
			s[i * 3] = v.x + cam.x;
			s[i * 3 + 1] = v.y + cam.y;
			s[i * 3 + 2] = v.z + cam.z;
		}
		s[9] = now;
		double[] last = e.clientTrail.peekLast();
		if (last != null && Math.abs(last[9] - now) < 1.0e-3) {
			return;
		}
		e.clientTrail.addLast(s);
		while (e.clientTrail.size() > 90) {
			e.clientTrail.removeFirst();
		}
	}

	/** The arc the edge swept: a ribbon from the blade's middle to its tip, bright at the tip, fading with age. */
	private static void drawTrail(HardLightMechEntity e, float partial, VertexConsumer vc, PoseStack pose, Vec3 worldOrigin, boolean golden) {
		if (e.clientTrail.size() < 2) {
			return;
		}
		float now = e.level().getGameTime() + partial;
		HardLightDraw.golden = golden;
		int green = HardLightDraw.tint(HardLightDraw.GREEN), pale = HardLightDraw.tint(HardLightDraw.PALE);
		HardLightDraw.golden = false;
		PoseStack.Pose P = pose.last();
		// a Catmull-Rom curve through the samples, so the arc stays round however few frames caught it
		java.util.List<double[]> raw = new java.util.ArrayList<>(e.clientTrail);
		java.util.List<double[]> pts = new java.util.ArrayList<>();
		for (int i = 0; i < raw.size() - 1; i++) {
			double[] q0 = raw.get(Math.max(0, i - 1)), q1 = raw.get(i), q2 = raw.get(i + 1), q3 = raw.get(Math.min(raw.size() - 1, i + 2));
			int steps = 6;
			for (int k = 0; k < steps; k++) {
				double t = k / (double) steps;
				double[] o = new double[10];
				for (int c = 0; c < 10; c++) {
					o[c] = c == 9 ? q1[c] + (q2[c] - q1[c]) * t : catmull(q0[c], q1[c], q2[c], q3[c], t);
				}
				pts.add(o);
			}
		}
		pts.add(raw.get(raw.size() - 1));
		double[] prev = null;
		for (double[] s : pts) {
			if (prev != null) {
				float a0 = Mth.clamp(1f - (now - (float) prev[9]) / TRAIL_TICKS, 0f, 1f);
				float a1 = Mth.clamp(1f - (now - (float) s[9]) / TRAIL_TICKS, 0f, 1f);
				float[] b0 = rel(prev, 0, worldOrigin), m0 = rel(prev, 1, worldOrigin), t0 = rel(prev, 2, worldOrigin);
				float[] b1 = rel(s, 0, worldOrigin), m1 = rel(s, 1, worldOrigin), t1 = rel(s, 2, worldOrigin);
				// the body of the arc (green, fading in from the blade's middle) and the bright edge band near the tip
				MechWeaponDraw.quad(vc, P, b0, b1, m1, m0, argb(green, 0f), argb(green, 0f), argb(green, 0.45f * a1 * a1), argb(green, 0.45f * a0 * a0));
				MechWeaponDraw.quad(vc, P, m0, m1, t1, t0, argb(pale, 0.75f * a0 * a0), argb(pale, 0.75f * a1 * a1), argb(0xFFFFFF, 0.95f * a1),
						argb(0xFFFFFF, 0.95f * a0));
			}
			prev = s;
		}
	}

	private static double catmull(double p0, double p1, double p2, double p3, double t) {
		double t2 = t * t, t3 = t2 * t;
		return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
	}

	private static float[] rel(double[] s, int i, Vec3 o) {
		return new float[] { (float) (s[i * 3] - o.x), (float) (s[i * 3 + 1] - o.y), (float) (s[i * 3 + 2] - o.z) };
	}

	private static int argb(int rgb, float a) {
		return (Mth.clamp(Math.round(a * 255f), 0, 255) << 24) | (rgb & 0xFFFFFF);
	}

	// ---------------------------------------------------------------- the iaido crescent

	private static void wave(HardLightMechEntity e, float partial, VertexConsumer vc, PoseStack pose, Vec3 origin) {
		float since = e.level().getGameTime() - e.waveAt() + partial;
		if (since < 0f) {
			return;
		}
		float len = e.waveLen();
		float travel = since * (float) GreenLanternMechConfig.IAIDO_SPEED;
		if (travel > len + 5f || len <= 0f) {
			return;
		}
		float alpha = travel <= len ? Math.min(1f, 0.4f + since / 2f) : 1f - (travel - len) / 5f;
		Vec3 dir = e.waveDir();
		float dist = Math.min(travel, len);
		Vec3 at = e.waveFrom().add(dir.scale(dist)).subtract(origin);
		pose.pushPose();
		pose.translate(at.x, at.y, at.z);
		pose.mulPose(Axis.YP.rotation((float) Math.atan2(dir.x, dir.z)));
		pose.mulPose(Axis.XP.rotation((float) -Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z))));
		float hw = (float) GreenLanternMechConfig.IAIDO_HALF_WIDTH * (travel <= len ? Math.min(1f, 0.55f + since / 4f) : 1f + (travel - len) / 10f);
		MechWeaponDraw.crescent(vc, pose, hw, alpha, since, HardLightDraw.golden);
		// two after-images trailing it
		for (int i = 1; i <= 2; i++) {
			pose.translate(0f, 0f, -1.1f);
			MechWeaponDraw.crescent(vc, pose, hw * (1f - 0.06f * i), alpha * (i == 1 ? 0.35f : 0.14f), since, HardLightDraw.golden);
		}
		pose.popPose();
	}

	// ---------------------------------------------------------------- GIGA DRILL BREAK

	private static final float BREAK_TICKS = 18f;

	private static void gigaBreak(HardLightMechEntity e, float partial, VertexConsumer vc, PoseStack pose, Vec3 origin, float age) {
		float since = e.level().getGameTime() - e.breakAt() + partial;
		if (since < 0f || since > BREAK_TICKS) {
			return;
		}
		float k = since / BREAK_TICKS;
		float out = 1f - (1f - Math.min(1f, k * 2.2f)) * (1f - Math.min(1f, k * 2.2f)); // eased punch-out
		Vec3 dir = e.drillDir();
		Vec3 at = e.breakPos().subtract(origin);
		float fade = (float) Math.pow(1f - k, 1.4);
		// the giant ghost drill: rooted at the mech's drill, punching on far ahead, spinning hard
		pose.pushPose();
		pose.translate(at.x - dir.x * 2.0, at.y - dir.y * 2.0, at.z - dir.z * 2.0);
		pose.mulPose(new Quaternionf().rotationTo(0f, -1f, 0f, (float) dir.x, (float) dir.y, (float) dir.z));
		MechWeaponDraw.drill(vc, pose, 6f + 14f * out, 2.0f + 1.4f * out, age * 3.2f, Math.min(1f, 1.3f * fade), HardLightDraw.golden);
		pose.popPose();
		// shockwave rings racing out round the line, and a flash at the point
		pose.pushPose();
		pose.translate(at.x, at.y, at.z);
		pose.mulPose(new Quaternionf().rotationTo(0f, 0f, 1f, (float) dir.x, (float) dir.y, (float) dir.z));
		for (int i = 0; i < 3; i++) {
			float kk = Mth.clamp(k * 1.4f - i * 0.12f, 0f, 1f);
			if (kk <= 0f) {
				continue;
			}
			pose.pushPose();
			pose.translate(0f, 0f, 2f + i * 3.5f * kk);
			MechWeaponDraw.ring(vc, pose, 1.2f + (9f - i * 2f) * kk, 0.35f + 0.4f * kk, i == 0 ? 0xF4FFF6 : HardLightDraw.tint(HardLightDraw.GREEN),
					0.9f * (1f - kk));
			pose.popPose();
		}
		if (k < 0.45f) {
			float f = 1f - k / 0.45f;
			TurboDraw.sphere(vc, pose, 0.6f + 1.6f * (1f - f), 0xF4FFF6, 0.6f * f);
			TurboDraw.sphere(vc, pose, 1.2f + 2.6f * (1f - f), HardLightDraw.tint(HardLightDraw.GREEN), 0.18f * f);
		}
		pose.popPose();
	}
}
