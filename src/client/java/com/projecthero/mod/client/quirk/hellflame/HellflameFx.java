package com.projecthero.mod.client.quirk.hellflame;

import com.projecthero.mod.quirk.hellflame.Hellflame;
import com.projecthero.mod.quirk.hellflame.HellflameCombat;
import com.projecthero.mod.quirk.hellflame.HellflameConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 revamp: Hellflame's flames, client side, for every Hellflame player near the viewer (from the synced state, so
 * all viewers see the same). Particles only -- the strings, the Mesh and the Prominence beam are drawn as geometry by
 * {@link HellflameRender}.
 * <ul>
 *   <li>the <b>Flame Cloak</b>: the flame beard, eyebrows and shoulder flames (beard and brows left out for your own
 *       first-person camera), forearm flames, a heat ring at the feet;</li>
 *   <li><b>Fire Jet Flight</b>: jets from both feet and both palms, a smoke trail;</li>
 *   <li><b>Prominence Burn</b>: flame drawn into the drawn-back fist while it charges; a sheath of sparks and a recoil
 *       flare along the beam while it fires;</li>
 *   <li><b>Fire Wall</b>: a sheet of flame rising off its base line; <b>Fire Pillar</b>: the ground glows and cracks, then
 *       the column roars; the <b>Mesh</b>: embers dripping off the dome;</li>
 *   <li>the <b>decoy</b> of Vanishing Fist: a man-shaped flame where he stood; <b>Plus Ultra</b>: a roaring white-hot
 *       aura; <b>overheat / burnout</b>: smoke and steam.</li>
 * </ul>
 */
public final class HellflameFx {
	private HellflameFx() {
	}

	static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.isPaused() || mc.player == null) {
			return;
		}
		RandomSource rnd = level.getRandom();
		long now = level.getGameTime();
		for (Player p : level.players()) {
			if (!Hellflame.is(p) || p.distanceToSqr(mc.player) > 128 * 128) {
				continue;
			}
			boolean firstPerson = p == mc.player && mc.options.getCameraType().isFirstPerson();
			boolean visible = !p.isInvisible();
			if (visible && (Hellflame.cloaked(p) || Hellflame.plusUltra(p))) {
				cloak(level, rnd, p, firstPerson, Hellflame.plusUltra(p));
			}
			if (visible && Hellflame.flying(p)) {
				jets(level, rnd, p, firstPerson);
			}
			if (Hellflame.charging(p)) {
				charge(level, rnd, p);
			}
			if (Hellflame.beaming(p)) {
				beamSparks(level, rnd, p);
			}
			if (Hellflame.wallUp(p)) {
				wall(level, rnd, p, now);
			}
			if (Hellflame.val(p, Hellflame.V_PILLAR) != 0L) {
				pillar(level, rnd, p, now);
			}
			if (Hellflame.meshUp(p)) {
				meshEmbers(level, rnd, p);
			}
			if (Hellflame.decoyUp(p)) {
				decoy(level, rnd, p, now);
			}
			if (Hellflame.overheated(p) && !firstPerson && rnd.nextInt(2) == 0) {
				boolean burnt = Hellflame.burntOut(p);
				add(level, burnt ? ParticleTypes.CLOUD : ParticleTypes.SMOKE,
						new Vec3(p.getX() + (rnd.nextDouble() - 0.5) * 0.6, p.getY() + 1.2 + rnd.nextDouble() * 0.6, p.getZ() + (rnd.nextDouble() - 0.5) * 0.6),
						0, burnt ? 0.06 : 0.04, 0);
			}
		}
	}

	private static void add(ClientLevel level, ParticleOptions type, Vec3 at, double vx, double vy, double vz) {
		level.addParticle(type, at.x, at.y, at.z, vx, vy, vz);
	}

	/** The body's frame: forward and right on the horizontal plane from the body (or head) yaw. */
	static Vec3[] frame(Player p, boolean head) {
		float yaw = head ? p.getYHeadRot() : p.yBodyRot;
		double r = Math.toRadians(yaw);
		Vec3 fwd = new Vec3(-Math.sin(r), 0, Math.cos(r));
		Vec3 right = new Vec3(-Math.cos(r), 0, -Math.sin(r));
		return new Vec3[] { fwd, right };
	}

	private static void cloak(ClientLevel level, RandomSource rnd, Player p, boolean firstPerson, boolean plus) {
		Vec3[] body = frame(p, false);
		double crouch = p.isCrouching() ? -0.3 : 0.0;
		int boost = plus ? 3 : 1;
		// the shoulder flames: tongues rising off both shoulders, leaning outward (clear of the face) -- not in your own
		// first-person view, where they would sit right in front of the camera
		for (int side = -1; side <= 1 && !firstPerson; side += 2) {
			Vec3 sh = p.position().add(0, 1.42 + crouch, 0).add(body[1].scale(side * 0.4));
			Vec3 out = body[1].scale(side * 0.018);
			for (int k = 0; k < boost; k++) {
				if (rnd.nextInt(3) != 0) {
					Vec3 at = sh.add((rnd.nextDouble() - 0.5) * 0.2, rnd.nextDouble() * 0.08, (rnd.nextDouble() - 0.5) * 0.2);
					add(level, ParticleTypes.FLAME, at, out.x, 0.02 + rnd.nextDouble() * 0.03 * boost, out.z);
				}
				if (rnd.nextInt(3) == 0) {
					add(level, HellflameCombat.YELLOW, sh.add(out.scale(4)).add(0, 0.1, 0), out.x, 0.02, out.z);
				}
			}
			if (rnd.nextInt(plus ? 2 : 5) == 0) {
				add(level, HellflameCombat.WHITE_HOT, sh.add(0, 0.04, 0), 0, 0.01, 0);
			}
		}
		// flames licking up the forearms
		if (rnd.nextInt(2) == 0) {
			int side = rnd.nextBoolean() ? 1 : -1;
			Vec3 arm = p.position().add(0, 0.95 + crouch, 0).add(body[1].scale(side * 0.38));
			add(level, ParticleTypes.SMALL_FLAME, arm.add((rnd.nextDouble() - 0.5) * 0.15, 0, (rnd.nextDouble() - 0.5) * 0.15), 0, 0.03, 0);
		}
		// a ring of heat round his feet
		if (rnd.nextInt(plus ? 1 : 3) == 0) {
			double a = rnd.nextDouble() * Math.PI * 2.0;
			double r = HellflameConfig.CLOAK_RADIUS * (plus ? HellflameConfig.PLUS_SIZE_MULT : 1.0) * (0.6 + rnd.nextDouble() * 0.4);
			add(level, plus ? HellflameCombat.BIG_ORANGE : ParticleTypes.FLAME, p.position().add(Math.cos(a) * r, 0.1, Math.sin(a) * r), 0, 0.04, 0);
		}
		if (plus && !firstPerson) {
			// Plus Ultra: the whole body roars, white-hot at the core
			for (int i = 0; i < 4; i++) {
				Vec3 at = p.position().add((rnd.nextDouble() - 0.5) * 0.8, rnd.nextDouble() * 2.0, (rnd.nextDouble() - 0.5) * 0.8);
				add(level, i == 0 ? HellflameCombat.WHITE_HOT : HellflameCombat.ORANGE, at, 0, 0.06 + rnd.nextDouble() * 0.05, 0);
			}
			if (rnd.nextInt(3) == 0) {
				add(level, ParticleTypes.LAVA, p.position().add(0, 1.0, 0), 0, 0, 0);
			}
		}
		if (firstPerson) {
			return;
		}
		// the flame beard along the jaw and chin, and the flaming eyebrows -- small, low flames that hug the face
		Vec3[] head = frame(p, true);
		Vec3 eye = p.getEyePosition();
		if (rnd.nextInt(3) != 0) {
			double s = (rnd.nextDouble() - 0.5) * 2.0; // -1 (right jaw) .. 1 (left jaw)
			double fwd = 0.27 - 0.12 * s * s;
			Vec3 at = eye.add(head[0].scale(fwd)).add(head[1].scale(-s * 0.26)).add(0, -0.1 - 0.04 * (1 - Math.abs(s)), 0);
			add(level, ParticleTypes.SMALL_FLAME, at, head[0].x * 0.004, 0.004, head[0].z * 0.004);
		}
		if (rnd.nextInt(4) == 0) {
			add(level, HellflameCombat.YELLOW, eye.add(head[0].scale(0.29)).add(0, -0.14, 0), 0, 0.004, 0);
		}
		if (rnd.nextInt(2) == 0) {
			int side = rnd.nextBoolean() ? 1 : -1;
			Vec3 brow = eye.add(head[0].scale(0.27)).add(head[1].scale(side * (0.06 + rnd.nextDouble() * 0.1))).add(0, 0.19, 0);
			add(level, ParticleTypes.SMALL_FLAME, brow, 0, 0.006, 0);
		}
	}

	private static void jets(ClientLevel level, RandomSource rnd, Player p, boolean firstPerson) {
		Vec3[] body = frame(p, false);
		Vec3 v = p.getDeltaMovement();
		double speed = Math.hypot(v.x, v.z);
		// the feet: the main jets, roaring straight down (and back as he speeds up)
		Vec3 thrust = new Vec3(-v.x * 0.6, -0.32 - rnd.nextDouble() * 0.15, -v.z * 0.6);
		for (int side = -1; side <= 1; side += 2) {
			Vec3 foot = p.position().add(body[1].scale(side * 0.12)).add(0, 0.05, 0);
			for (int i = 0; i < 3; i++) {
				add(level, ParticleTypes.FLAME, foot.add((rnd.nextDouble() - 0.5) * 0.12, 0, (rnd.nextDouble() - 0.5) * 0.12),
						thrust.x + (rnd.nextDouble() - 0.5) * 0.06, thrust.y, thrust.z + (rnd.nextDouble() - 0.5) * 0.06);
			}
			add(level, HellflameCombat.WHITE_HOT, foot.add(0, -0.08, 0), thrust.x * 0.3, -0.1, thrust.z * 0.3);
			if (rnd.nextInt(2) == 0) {
				add(level, HellflameCombat.ORANGE, foot.add(0, -0.3, 0), thrust.x * 0.2, -0.05, thrust.z * 0.2);
			}
			if (rnd.nextInt(3) == 0) {
				add(level, ParticleTypes.SMOKE, foot.add(0, -0.6, 0), 0, -0.03, 0);
			}
			// the palms: smaller stabiliser jets, angled down and back
			if (!firstPerson || rnd.nextInt(3) == 0) {
				Vec3 palm = p.position().add(0, 0.65 + (speed > 0.4 ? 0.25 : 0), 0).add(body[1].scale(side * 0.5)).add(body[0].scale(speed > 0.4 ? -0.25 : 0));
				add(level, ParticleTypes.FLAME, palm, -v.x * 0.4 + body[1].x * side * 0.03, -0.2, -v.z * 0.4 + body[1].z * side * 0.03);
				if (rnd.nextInt(2) == 0) {
					add(level, HellflameCombat.YELLOW, palm.add(0, -0.1, 0), 0, -0.08, 0);
				}
			}
		}
		if (rnd.nextInt(6) == 0) {
			add(level, ParticleTypes.LAVA, p.position(), 0, 0, 0);
		}
	}

	/** Where his right fist is (roughly: front-right, at chest height). */
	static Vec3 hand(Player p) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		return eye.add(look.scale(0.55)).add(right.scale(0.36)).add(0, -0.42, 0);
	}

	private static void charge(ClientLevel level, RandomSource rnd, Player p) {
		float c = Hellflame.charge(p, 0f);
		Vec3[] body = frame(p, false);
		// the fist is drawn back at his right hip
		Vec3 fist = p.position().add(0, 0.95, 0).add(body[1].scale(0.42)).add(body[0].scale(-0.35));
		int n = 3 + (int) (c * 8);
		for (int i = 0; i < n; i++) {
			Vec3 at = fist.add((rnd.nextDouble() - 0.5) * 3.0, (rnd.nextDouble() - 0.5) * 2.4, (rnd.nextDouble() - 0.5) * 3.0);
			Vec3 to = fist.subtract(at).scale(0.18);
			add(level, i % 3 == 0 ? HellflameCombat.ORANGE : ParticleTypes.FLAME, at, to.x, to.y, to.z);
		}
		add(level, c > 0.7f ? HellflameCombat.WHITE_HOT : HellflameCombat.YELLOW, fist.add((rnd.nextDouble() - 0.5) * 0.2, (rnd.nextDouble() - 0.5) * 0.2,
				(rnd.nextDouble() - 0.5) * 0.2), 0, 0.01, 0);
		if (rnd.nextInt(3) == 0) {
			add(level, HellflameCombat.BIG_ORANGE, fist, 0, 0.02, 0);
		}
	}

	private static void beamSparks(ClientLevel level, RandomSource rnd, Player p) {
		Vec3 from = hand(p);
		Vec3 look = p.getLookAngle();
		double size = Hellflame.size(p);
		// sparks and tongues peeling off the beam's flaming edge
		for (int i = 0; i < 10; i++) {
			double d = 1.0 + rnd.nextDouble() * 22.0;
			Vec3 at = from.add(look.scale(d));
			Vec3 jit = new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5).scale(1.6 * size);
			add(level, i % 3 == 0 ? HellflameCombat.BIG_ORANGE : ParticleTypes.FLAME, at.add(jit), look.x * 0.3 + jit.x * 0.05, look.y * 0.3 + 0.03,
					look.z * 0.3 + jit.z * 0.05);
		}
		// the recoil flare at the fist and the heat shimmer round his feet
		add(level, HellflameCombat.WHITE_HOT, from, look.x * 0.1, look.y * 0.1, look.z * 0.1);
		if (rnd.nextInt(5) == 0) {
			add(level, ParticleTypes.SMOKE, p.position().add((rnd.nextDouble() - 0.5), 0.1, (rnd.nextDouble() - 0.5)), 0, 0.02, 0);
		}
	}

	private static void wall(ClientLevel level, RandomSource rnd, Player p, long now) {
		Vec3 a = Hellflame.vec(p, Hellflame.VEC_WALL_A);
		Vec3 b = Hellflame.vec(p, Hellflame.VEC_WALL_B);
		if (a == null || b == null) {
			return;
		}
		long until = Hellflame.val(p, Hellflame.V_WALL);
		float left = Mth.clamp((until - now) / 20f, 0f, 1f);
		double len = a.distanceTo(b);
		int n = (int) (len * 3 * (0.4 + 0.6 * left));
		for (int i = 0; i < n; i++) {
			Vec3 at = a.lerp(b, rnd.nextDouble()).add((rnd.nextDouble() - 0.5) * 0.4, rnd.nextDouble() * HellflameConfig.WALL_HEIGHT * 0.9, (rnd.nextDouble() - 0.5) * 0.4);
			double h = (at.y - a.y) / HellflameConfig.WALL_HEIGHT;
			ParticleOptions type = h < 0.25 ? HellflameCombat.WHITE_HOT : h < 0.5 ? HellflameCombat.YELLOW : rnd.nextBoolean() ? HellflameCombat.ORANGE
					: ParticleTypes.FLAME;
			add(level, type, at, 0, 0.05 + rnd.nextDouble() * 0.06, 0);
		}
		if (rnd.nextInt(2) == 0) {
			add(level, HellflameCombat.BIG_ORANGE, a.lerp(b, rnd.nextDouble()).add(0, 0.6 + rnd.nextDouble() * 2.0, 0), 0, 0.06, 0);
		}
		if (rnd.nextInt(8) == 0) {
			add(level, ParticleTypes.SMOKE, a.lerp(b, rnd.nextDouble()).add(0, HellflameConfig.WALL_HEIGHT, 0), 0, 0.05, 0);
		}
	}

	private static void pillar(ClientLevel level, RandomSource rnd, Player p, long now) {
		Vec3 c = Hellflame.vec(p, Hellflame.VEC_PILLAR);
		long at = Hellflame.val(p, Hellflame.V_PILLAR);
		if (c == null || at == 0L) {
			return;
		}
		double r = HellflameConfig.PILLAR_RADIUS * Hellflame.size(p);
		if (now < at) {
			// the warning: a glowing ring on the ground, cracks of light closing in
			float t = 1f - (at - now) / (float) HellflameConfig.PILLAR_WARN_TICKS;
			for (int i = 0; i < 6; i++) {
				double a = rnd.nextDouble() * Math.PI * 2.0;
				add(level, i % 2 == 0 ? HellflameCombat.ORANGE : ParticleTypes.SMALL_FLAME, c.add(Math.cos(a) * r, 0.05, Math.sin(a) * r), 0, 0.02, 0);
			}
			double rr = r * (1.0 - t);
			add(level, HellflameCombat.YELLOW, c.add(Math.cos(now * 0.7) * rr, 0.05, Math.sin(now * 0.7) * rr), 0, 0.01, 0);
			if (rnd.nextInt(3) == 0) {
				add(level, ParticleTypes.LAVA, c, 0, 0, 0);
			}
			return;
		}
		if (now > at + HellflameConfig.PILLAR_BURN_TICKS) {
			return;
		}
		float fade = 1f - (now - at) / (float) HellflameConfig.PILLAR_BURN_TICKS;
		int n = (int) (26 * fade) + 4;
		for (int i = 0; i < n; i++) {
			double a = rnd.nextDouble() * Math.PI * 2.0;
			double rr = r * Math.sqrt(rnd.nextDouble());
			double y = rnd.nextDouble() * HellflameConfig.PILLAR_HEIGHT * (0.5 + 0.5 * fade);
			ParticleOptions type = rr < r * 0.35 ? HellflameCombat.WHITE_HOT : rr < r * 0.7 ? HellflameCombat.YELLOW : HellflameCombat.BIG_ORANGE;
			add(level, type, c.add(Math.cos(a) * rr, y, Math.sin(a) * rr), 0, 0.25 + rnd.nextDouble() * 0.2, 0);
		}
		for (int i = 0; i < 4; i++) {
			double a = rnd.nextDouble() * Math.PI * 2.0;
			add(level, ParticleTypes.FLAME, c.add(Math.cos(a) * r, 0.2, Math.sin(a) * r), Math.cos(a) * 0.08, 0.3, Math.sin(a) * 0.08);
		}
	}

	private static void meshEmbers(ClientLevel level, RandomSource rnd, Player p) {
		Vec3 c = Hellflame.vec(p, Hellflame.VEC_MESH);
		if (c == null) {
			return;
		}
		double r = HellflameConfig.MESH_RADIUS * Hellflame.size(p);
		for (int i = 0; i < 3; i++) {
			double a = rnd.nextDouble() * Math.PI * 2.0;
			double el = rnd.nextDouble() * Math.PI * 0.5;
			Vec3 at = c.add(Math.cos(a) * Math.cos(el) * r, Math.sin(el) * r * 0.9, Math.sin(a) * Math.cos(el) * r);
			add(level, rnd.nextBoolean() ? ParticleTypes.SMALL_FLAME : HellflameCombat.ORANGE, at, 0, -0.03, 0);
		}
	}

	/** The decoy: a man-shaped flame where he stood (legs, body, arms, head), flickering out. */
	private static void decoy(ClientLevel level, RandomSource rnd, Player p, long now) {
		Vec3 at = Hellflame.vec(p, Hellflame.VEC_DECOY);
		if (at == null) {
			return;
		}
		long yawVal = Hellflame.val(p, Hellflame.V_DECOY_YAW);
		double yaw = Math.toRadians((yawVal - 100000L) / 10.0);
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		float left = Mth.clamp((Hellflame.val(p, Hellflame.V_DECOY) - now) / (float) HellflameConfig.VANISH_DECOY_TICKS, 0f, 1f);
		int n = 4 + (int) (10 * left);
		for (int i = 0; i < n; i++) {
			int part = rnd.nextInt(6);
			Vec3 q = switch (part) {
				case 0 -> at.add(right.scale(0.12)).add(0, rnd.nextDouble() * 0.75, 0);
				case 1 -> at.add(right.scale(-0.12)).add(0, rnd.nextDouble() * 0.75, 0);
				case 2 -> at.add(right.scale((rnd.nextDouble() - 0.5) * 0.45)).add(0, 0.75 + rnd.nextDouble() * 0.7, 0);
				case 3 -> at.add(right.scale(0.36)).add(0, 0.8 + rnd.nextDouble() * 0.6, 0);
				case 4 -> at.add(right.scale(-0.36)).add(0, 0.8 + rnd.nextDouble() * 0.6, 0);
				default -> at.add(right.scale((rnd.nextDouble() - 0.5) * 0.4)).add(0, 1.5 + rnd.nextDouble() * 0.4, 0);
			};
			add(level, part == 5 ? HellflameCombat.YELLOW : rnd.nextInt(3) == 0 ? HellflameCombat.WHITE_HOT : HellflameCombat.ORANGE, q, 0, 0.015, 0);
		}
	}
}
