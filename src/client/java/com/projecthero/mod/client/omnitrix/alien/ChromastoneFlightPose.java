package com.projecthero.mod.client.omnitrix.alien;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.flight.LightTrail;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.alien.jetray.RecalFx;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.chromastone.ChromastoneKit;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 round 4 (user: "upgrade his flight animations please"): Chromastone's flight, read from how he is really moving (other
 * players' velocity is not synced, so everything comes from how far the body moved each tick, on every viewer's client). Six poses
 * blend into each other: the hover (upright on a hexagon of his own light, arms a little out), the cruise (laid forward, arms swept
 * back), the sprint (nearly flat, the right fist leading), the climb (the fist raised overhead), the descent (arms spread, feet down)
 * and the brake when he backs up (leaning away, palms out). He banks into a strafe or a turn, and comes most of the way upright while
 * an attack clip plays so the aim stays true. A ribbon of the seven colours streams from his feet; it faces whoever is looking, so it
 * reads from every side. {@code StinkflyFlightPoseMixin} applies the whole-body attitude.
 */
public final class ChromastoneFlightPose {
	/** Blocks a tick: his cruise and his sprint in vanilla flight at his flying speed. */
	private static final float CRUISE = 0.5f, SPRINT = 1.05f;
	private static final int TRAIL_LIFE = 18;
	private static final int[] SPECTRUM = RecalFx.SPECTRUM;

	/** The poses: {rArm xyz, lArm xyz, rLeg xyz, lLeg xyz}. */
	private static final float[] HOVER = { 0.1f, 0f, 0.42f, 0.16f, 0f, -0.42f, 0.04f, 0f, 0.07f, 0.2f, 0f, -0.07f };
	private static final float[] CRUISE_POSE = { 0.42f, 0f, 0.3f, 0.52f, 0f, -0.3f, 0.1f, 0f, 0.03f, 0.3f, 0f, -0.03f };
	private static final float[] SPRINT_POSE = { -3.0f, 0f, 0.1f, 0.34f, 0f, -0.2f, 0.06f, 0f, 0.02f, 0.22f, 0f, -0.02f };
	private static final float[] CLIMB = { -2.95f, 0f, 0.14f, 0.16f, 0f, -0.24f, 0.05f, 0f, 0.03f, 0.2f, 0f, -0.03f };
	private static final float[] DESCEND = { -0.25f, 0f, 1.05f, -0.25f, 0f, -1.05f, -0.08f, 0f, 0.09f, 0.14f, 0f, -0.09f };
	private static final float[] BRAKE = { -1.3f, -0.2f, 0.3f, -1.3f, 0.2f, -0.3f, -0.4f, 0f, 0.05f, -0.12f, 0f, -0.05f };

	private static final class State {
		float on, onO, fwd, fwdO, sprint, sprintO, right, rightO, climb, climbO, turn, turnO, clip, clipO, lastYaw;
		boolean seen;
		final LightTrail.History path = new LightTrail.History(TRAIL_LIFE);
	}

	private static final Map<AbstractClientPlayer, State> STATES = new WeakHashMap<>();

	private ChromastoneFlightPose() {
	}

	/** One client tick: {@code flying} in the air, {@code attacking} while an attack clip plays. */
	public static void tick(AbstractClientPlayer p, boolean flying, boolean attacking) {
		if (!flying && !STATES.containsKey(p)) {
			return;
		}
		State s = STATES.computeIfAbsent(p, k -> new State());
		s.onO = s.on;
		s.fwdO = s.fwd;
		s.sprintO = s.sprint;
		s.rightO = s.right;
		s.climbO = s.climb;
		s.turnO = s.turn;
		s.clipO = s.clip;
		s.on = Mth.approach(s.on, flying ? 1f : 0f, flying ? 0.12f : 0.16f);
		s.clip = Mth.approach(s.clip, attacking ? 1f : 0f, attacking ? 0.34f : 0.14f);
		double dx = p.getX() - p.xo, dy = p.getY() - p.yo, dz = p.getZ() - p.zo;
		float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
		float fwd = (float) (dx * fx + dz * fz);
		float right = (float) (dx * -fz + dz * fx);
		float speed = (float) Math.sqrt(dx * dx + dz * dz);
		float head = p.getYRot();
		float turn = s.seen ? Mth.wrapDegrees(head - s.lastYaw) : 0f;
		s.lastYaw = head;
		s.seen = true;
		s.fwd += (Mth.clamp(fwd / CRUISE, -1f, 1f) - s.fwd) * 0.16f;
		s.sprint += (Mth.clamp((speed - CRUISE * 1.25f) / (SPRINT - CRUISE * 1.25f), 0f, 1f) - s.sprint) * 0.12f;
		s.right += (Mth.clamp(right / (CRUISE * 0.8f), -1f, 1f) - s.right) * 0.16f;
		s.climb += (Mth.clamp((float) dy / 0.32f, -1f, 1f) - s.climb) * 0.16f;
		s.turn += (Mth.clamp(turn * 1.6f, -24f, 24f) - s.turn) * 0.16f;
		if (s.on > 0f) {
			s.path.sample(feet(p, s, 1f), p.level().getGameTime());
		} else {
			s.path.prune(p.level().getGameTime());
		}
		if (!flying && s.on <= 0f && s.onO <= 0f && s.path.isEmpty()) {
			STATES.remove(p);
		}
	}

	private static float smooth(float t) {
		return t * t * (3f - 2f * t);
	}

	private static float lean(State s, float partial) {
		float fwd = Mth.lerp(partial, s.fwdO, s.fwd), sprint = Mth.lerp(partial, s.sprintO, s.sprint), climb = Mth.lerp(partial, s.climbO, s.climb);
		float forward = Math.max(fwd, 0f) * Mth.lerp(sprint, 56f, 80f) * (1f - 0.7f * Math.abs(climb)) - Math.max(-fwd, 0f) * 16f;
		// an attack needs him upright enough to aim
		return forward * (1f - 0.7f * smooth(Mth.lerp(partial, s.clipO, s.clip)));
	}

	/** The tilt for {@code StinkflyFlightPoseMixin}: {pitch forward, roll} in degrees and a bob (blocks), or null. */
	public static float[] bodyTilt(AbstractClientPlayer p, float partial) {
		State s = STATES.get(p);
		if (s == null || !Omnitrix.hasPower(p) || !ChromastoneKit.ID.equals(Omnitrix.state(p).active)) {
			return null;
		}
		float k = smooth(Mth.lerp(partial, s.onO, s.on));
		if (k < 0.02f) {
			return null;
		}
		float right = Mth.lerp(partial, s.rightO, s.right), turn = Mth.lerp(partial, s.turnO, s.turn), fwd = Mth.lerp(partial, s.fwdO, s.fwd);
		float roll = -right * 18f - turn * Math.max(fwd, 0f);
		float still = 1f - Math.min(1f, Math.abs(fwd) + Math.abs(right));
		float bob = Mth.sin((p.tickCount + partial) * 0.09f) * 0.05f * still;
		return new float[] { lean(s, partial) * k, roll * k, bob * k };
	}

	/** Poses the limbs for the flight. The attack and take-off clips blend on top of this. */
	public static void limbs(AbstractClientPlayer p, HumanoidModel<?> m, float age) {
		State s = STATES.get(p);
		if (s == null) {
			return;
		}
		float partial = age - Mth.floor(age);
		float fwd = Mth.lerp(partial, s.fwdO, s.fwd), sprint = Mth.lerp(partial, s.sprintO, s.sprint), climb = Mth.lerp(partial, s.climbO, s.climb);
		float ahead = Math.max(fwd, 0f);
		float cruise = ahead * (1f - sprint), fast = ahead * sprint, brake = Math.max(-fwd, 0f);
		float up = Math.max(climb, 0f) * (1f - ahead), down = Math.max(-climb, 0f) * (1f - ahead);
		float total = cruise + fast + brake + up + down;
		float hover = Math.max(0f, 1f - total);
		float norm = 1f / Math.max(1f, total + hover);
		float[] out = new float[12];
		for (int i = 0; i < 12; i++) {
			out[i] = (HOVER[i] * hover + CRUISE_POSE[i] * cruise + SPRINT_POSE[i] * fast + BRAKE[i] * brake + CLIMB[i] * up + DESCEND[i] * down) * norm;
		}
		// a very slow drift, more of it the stiller he is, the two sides out of step so he never looks like a statue
		float drift = Mth.sin(age * 0.08f), lag = Mth.sin(age * 0.08f - 1.1f);
		float loose = hover * 0.05f + 0.012f;
		float k = smooth(Mth.lerp(partial, s.onO, s.on));
		m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, out[0] + lag * loose);
		m.rightArm.yRot = Mth.lerp(k, m.rightArm.yRot, out[1]);
		m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, out[2] + drift * loose);
		m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, out[3] - lag * loose);
		m.leftArm.yRot = Mth.lerp(k, m.leftArm.yRot, out[4]);
		m.leftArm.zRot = Mth.lerp(k, m.leftArm.zRot, out[5] - drift * loose);
		m.rightLeg.xRot = Mth.lerp(k, m.rightLeg.xRot, out[6] + lag * loose);
		m.rightLeg.yRot = Mth.lerp(k, m.rightLeg.yRot, out[7]);
		m.rightLeg.zRot = Mth.lerp(k, m.rightLeg.zRot, out[8]);
		m.leftLeg.xRot = Mth.lerp(k, m.leftLeg.xRot, out[9] - lag * loose);
		m.leftLeg.yRot = Mth.lerp(k, m.leftLeg.yRot, out[10]);
		m.leftLeg.zRot = Mth.lerp(k, m.leftLeg.zRot, out[11]);
		// the head stays on the aim against the body's lean, so he looks where he flies
		m.head.xRot = Math.max(-1.4f, m.head.xRot - Math.min(lean(s, partial), 85f) * k * Mth.DEG_TO_RAD * 0.9f);
		m.hat.copyFrom(m.head);
	}

	/** The body's own up, in the world: tipped forward by the lean. */
	private static Vec3 bodyUp(AbstractClientPlayer p, State s, float partial) {
		float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		float l = lean(s, partial) * smooth(Mth.lerp(partial, s.onO, s.on)) * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw) * Mth.sin(l), Mth.cos(l), Mth.cos(yaw) * Mth.sin(l));
	}

	/** Between his feet, wherever the lean has put them. */
	private static Vec3 feet(AbstractClientPlayer p, State s, float partial) {
		return p.getPosition(partial).add(0, 0.9 * p.getScale(), 0).subtract(bodyUp(p, s, partial).scale(0.92 * p.getScale()));
	}

	/** {@code at}, a point on his upright body, carried round by his lean: where that point really is while he flies. */
	public static Vec3 tilted(net.minecraft.world.entity.player.Player pl, float partial, Vec3 at) {
		if (!(pl instanceof AbstractClientPlayer p)) {
			return at;
		}
		State s = STATES.get(p);
		if (s == null) {
			return at;
		}
		float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 up = bodyUp(p, s, partial);
		Vec3 pivot = p.getPosition(partial).add(0, 0.9 * p.getScale(), 0);
		Vec3 v = at.subtract(pivot);
		double along = v.dot(fwd), high = v.y;
		Vec3 flat = v.subtract(fwd.scale(along)).subtract(0, high, 0);
		Vec3 bodyFwd = fwd.scale(up.y).subtract(0, Math.sqrt(Math.max(0.0, 1.0 - up.y * up.y)), 0);
		return pivot.add(flat).add(up.scale(high)).add(bodyFwd.scale(along));
	}

	/** The light he flies on: the hexagon under his feet, the ribbon behind him and the flare on his leading fist. */
	public static void render(PoseStack pose, MultiBufferSource buffers, Vec3 cam, AbstractClientPlayer p, float partial, float now, boolean ownView) {
		State s = STATES.get(p);
		if (s == null) {
			return;
		}
		float on = smooth(Mth.lerp(partial, s.onO, s.on));
		float fwd = Mth.lerp(partial, s.fwdO, s.fwd), sprint = Mth.lerp(partial, s.sprintO, s.sprint);
		Vec3 feet = feet(p, s, partial);
		float still = Math.max(0f, 1f - Math.abs(fwd) * 1.4f);
		if (on > 0.02f && still > 0.02f) {
			hexagon(pose, buffers, cam, feet.add(0, -0.14, 0), now, on * still);
		}
		if (!ownView) {
			ribbon(pose, buffers, cam, p, s, feet, partial);
			if (on > 0.02f && sprint > 0.05f) {
				Vec3 up = bodyUp(p, s, partial);
				float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
				Vec3 fist = p.getPosition(partial).add(0, 0.9 * p.getScale(), 0).add(up.scale(1.3 * p.getScale()))
						.add(new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)).scale(0.32 * p.getScale()));
				PyroDraw.worldGlow(pose, PyroDraw.glowBuffer(buffers), fist.subtract(cam), 0.45f, PyroDraw.dim(0xFFFFE8FF, on * sprint * Math.max(fwd, 0f) * 0.8f));
			}
		}
	}

	private static void hexagon(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Vec3 at, float now, float k) {
		float r = 0.62f + Mth.sin(now * 0.12f) * 0.03f;
		float spin = now * 0.02f;
		Vec3 c = at.subtract(cam);
		for (int i = 0; i < 6; i++) {
			double a0 = spin + i * Math.PI / 3, a1 = spin + (i + 1) * Math.PI / 3;
			Vec3 p0 = c.add(Math.cos(a0) * r, 0, Math.sin(a0) * r);
			Vec3 p1 = c.add(Math.cos(a1) * r, 0, Math.sin(a1) * r);
			RecalFx.beam(pose, buffers, p0, p1, 0.07f, ChromastoneClient.WHITE, SPECTRUM[(i + (int) (now * 0.15f)) % SPECTRUM.length], 0.75f * k);
		}
		PyroDraw.worldGlow(pose, PyroDraw.glowBuffer(buffers), c, 0.75f, PyroDraw.dim(0xFFC860FF, 0.3f * k));
	}

	/** Seven colours side by side, the band always turned to the viewer. */
	private static void ribbon(PoseStack pose, MultiBufferSource buffers, Vec3 cam, AbstractClientPlayer p, State s, Vec3 feet, float partial) {
		long gameTime = p.level().getGameTime();
		Vec3[] pts = s.path.pointsNewestFirst(feet);
		if (pts == null || pts.length < 3) {
			return;
		}
		float[] ages = s.path.agesNewestFirst(feet, partial, gameTime);
		int n = pts.length;
		for (int c = 0; c < SPECTRUM.length; c++) {
			List<Vec3> strip = new ArrayList<>(n);
			float[] alpha = new float[n];
			for (int i = 0; i < n; i++) {
				Vec3 at = pts[i].subtract(cam);
				Vec3 tan = pts[Math.max(0, i - 1)].subtract(pts[Math.min(n - 1, i + 1)]);
				Vec3 side = tan.cross(at);
				side = side.lengthSqr() < 1.0e-8 ? new Vec3(0, 1, 0) : side.normalize();
				float life = 1f - ages[i];
				float head = Math.min(1f, (i + 0.4f) / 2.5f);
				strip.add(at.add(side.scale((c - 3) * 0.07f * head * (0.5f + 0.5f * life))));
				alpha[i] = life * life * head;
			}
			int col = SPECTRUM[c];
			PyroDraw.ribbon(pose, PyroDraw.additive(buffers), strip, t -> 0.085 * Math.min(1.0, (t * (n - 1) + 0.4) / 2.5),
					t -> PyroDraw.dim(col, 0.6f * alpha[Mth.clamp((int) Math.round(t * (n - 1)), 0, n - 1)]));
		}
	}
}
