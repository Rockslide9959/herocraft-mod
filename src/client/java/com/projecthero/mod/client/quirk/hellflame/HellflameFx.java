package com.projecthero.mod.client.quirk.hellflame;

import com.projecthero.mod.quirk.hellflame.Hellflame;
import com.projecthero.mod.quirk.hellflame.HellflameCombat;
import com.projecthero.mod.quirk.hellflame.HellflameConfig;
import com.projecthero.mod.quirk.hellflame.HellflameMoves;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: Hellflame's flames, client side, for every Hellflame player near the viewer (from the synced state, so all
 * viewers see the same): the Flame Cloak's flame beard, eyebrows and shoulder flames (the beard and brows are left out
 * for your own first-person camera), the foot jets of Flame Boost, the Hellflame Beam (a white-hot core in an orange
 * sheath), the Prominence charge drawing flame into the body, and the little sun of Hellflame Sun.
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
		for (Player p : level.players()) {
			if (!Hellflame.is(p) || p.isInvisible() || p.distanceToSqr(mc.player) > 96 * 96) {
				continue;
			}
			boolean firstPerson = p == mc.player && mc.options.getCameraType().isFirstPerson();
			if (Hellflame.cloaked(p)) {
				cloak(level, rnd, p, firstPerson);
			}
			if (Hellflame.boosting(p)) {
				jets(level, rnd, p);
			}
			if (Hellflame.beaming(p)) {
				beam(level, rnd, p, firstPerson);
			}
			if (Hellflame.charging(p)) {
				charge(level, rnd, p);
			}
			if (Hellflame.sunUp(p)) {
				sun(level, rnd, p);
			}
			if (Hellflame.overheated(p) && rnd.nextInt(3) == 0) {
				level.addParticle(ParticleTypes.SMOKE, p.getX() + (rnd.nextDouble() - 0.5) * 0.6, p.getY() + 1.2 + rnd.nextDouble() * 0.6,
						p.getZ() + (rnd.nextDouble() - 0.5) * 0.6, 0, 0.04, 0);
			}
		}
	}

	private static void add(ClientLevel level, ParticleOptions type, Vec3 at, double vx, double vy, double vz) {
		level.addParticle(type, at.x, at.y, at.z, vx, vy, vz);
	}

	/** The body's frame: forward and right on the horizontal plane from the body yaw. */
	private static Vec3[] frame(Player p, boolean head) {
		float yaw = head ? p.getYHeadRot() : p.yBodyRot;
		double r = Math.toRadians(yaw);
		Vec3 fwd = new Vec3(-Math.sin(r), 0, Math.cos(r));
		Vec3 right = new Vec3(-Math.cos(r), 0, -Math.sin(r));
		return new Vec3[] { fwd, right };
	}

	private static void cloak(ClientLevel level, RandomSource rnd, Player p, boolean firstPerson) {
		Vec3[] body = frame(p, false);
		double crouch = p.isCrouching() ? -0.3 : 0.0;
		// the shoulder flames: tall tongues rising off both shoulders
		for (int side = -1; side <= 1; side += 2) {
			Vec3 sh = p.position().add(0, 1.42 + crouch, 0).add(body[1].scale(side * 0.36));
			for (int i = 0; i < 2; i++) {
				Vec3 at = sh.add((rnd.nextDouble() - 0.5) * 0.22, rnd.nextDouble() * 0.1, (rnd.nextDouble() - 0.5) * 0.22);
				add(level, ParticleTypes.FLAME, at, side * body[1].x * 0.01, 0.05 + rnd.nextDouble() * 0.04, side * body[1].z * 0.01);
			}
			if (rnd.nextInt(3) == 0) {
				add(level, HellflameCombat.YELLOW, sh.add(0, 0.12, 0), 0, 0.02, 0);
			}
			if (rnd.nextInt(5) == 0) {
				add(level, HellflameCombat.WHITE_HOT, sh.add(0, 0.05, 0), 0, 0.01, 0);
			}
		}
		// a few flames licking up the forearms
		if (rnd.nextInt(2) == 0) {
			int side = rnd.nextBoolean() ? 1 : -1;
			Vec3 arm = p.position().add(0, 0.95 + crouch, 0).add(body[1].scale(side * 0.38));
			add(level, ParticleTypes.SMALL_FLAME, arm.add((rnd.nextDouble() - 0.5) * 0.15, 0, (rnd.nextDouble() - 0.5) * 0.15), 0, 0.03, 0);
		}
		if (firstPerson) {
			return;
		}
		// the flame beard (round the jaw and chin) and the flaming eyebrows
		Vec3[] head = frame(p, true);
		Vec3 eye = p.getEyePosition();
		for (int i = 0; i < 3; i++) {
			double s = (rnd.nextDouble() - 0.5) * 0.5;
			double jaw = 1.0 - Math.abs(s) * 1.2; // the chin hangs lowest
			Vec3 at = eye.add(head[0].scale(0.24 + 0.06 * jaw)).add(head[1].scale(s)).add(0, -0.3 - 0.12 * jaw, 0);
			add(level, ParticleTypes.FLAME, at, head[0].x * 0.01, 0.035 + rnd.nextDouble() * 0.02, head[0].z * 0.01);
		}
		if (rnd.nextInt(2) == 0) {
			Vec3 chin = eye.add(head[0].scale(0.3)).add(0, -0.42, 0);
			add(level, HellflameCombat.YELLOW, chin, 0, 0.015, 0);
		}
		for (int side = -1; side <= 1; side += 2) {
			if (rnd.nextInt(2) == 0) {
				Vec3 brow = eye.add(head[0].scale(0.3)).add(head[1].scale(side * (0.1 + rnd.nextDouble() * 0.08))).add(0, 0.1, 0);
				add(level, ParticleTypes.SMALL_FLAME, brow, 0, 0.02, 0);
			}
		}
	}

	private static void jets(ClientLevel level, RandomSource rnd, Player p) {
		Vec3[] body = frame(p, false);
		Vec3 v = p.getDeltaMovement();
		for (int side = -1; side <= 1; side += 2) {
			Vec3 foot = p.position().add(body[1].scale(side * 0.12)).add(0, 0.05, 0);
			for (int i = 0; i < 3; i++) {
				add(level, ParticleTypes.FLAME, foot.add((rnd.nextDouble() - 0.5) * 0.12, 0, (rnd.nextDouble() - 0.5) * 0.12),
						v.x * 0.3 + (rnd.nextDouble() - 0.5) * 0.06, -0.28 - rnd.nextDouble() * 0.15, v.z * 0.3 + (rnd.nextDouble() - 0.5) * 0.06);
			}
			add(level, HellflameCombat.WHITE_HOT, foot.add(0, -0.08, 0), 0, -0.1, 0);
			if (rnd.nextInt(2) == 0) {
				add(level, HellflameCombat.ORANGE, foot.add(0, -0.3, 0), 0, -0.05, 0);
			}
			if (rnd.nextInt(4) == 0) {
				add(level, ParticleTypes.SMOKE, foot.add(0, -0.5, 0), 0, -0.05, 0);
			}
		}
		if (rnd.nextInt(6) == 0) {
			add(level, ParticleTypes.LAVA, p.position(), 0, 0, 0);
		}
	}

	private static Vec3 hand(Player p) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		return eye.add(look.scale(0.35)).add(right.scale(0.5)).add(0, -0.45, 0);
	}

	private static void beam(ClientLevel level, RandomSource rnd, Player p, boolean firstPerson) {
		Vec3 from = hand(p);
		Vec3 look = p.getLookAngle();
		Vec3 to = from.add(look.scale(HellflameConfig.BEAM_RANGE));
		BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
		double len = from.distanceTo(end);
		Vec3 dir = end.subtract(from).normalize();
		double start = firstPerson ? 0.8 : 0.0;
		for (double d = start; d <= len; d += 0.4) {
			Vec3 at = from.add(dir.scale(d));
			add(level, HellflameCombat.WHITE_HOT, at, 0, 0, 0);
			if (rnd.nextInt(2) == 0) {
				Vec3 j = at.add((rnd.nextDouble() - 0.5) * 0.3, (rnd.nextDouble() - 0.5) * 0.3, (rnd.nextDouble() - 0.5) * 0.3);
				add(level, ParticleTypes.FLAME, j, dir.x * 0.08, dir.y * 0.08 + 0.01, dir.z * 0.08);
			}
			if (rnd.nextInt(4) == 0) {
				add(level, HellflameCombat.ORANGE, at.add((rnd.nextDouble() - 0.5) * 0.4, (rnd.nextDouble() - 0.5) * 0.4, (rnd.nextDouble() - 0.5) * 0.4), 0, 0, 0);
			}
		}
		for (int i = 0; i < 4; i++) {
			add(level, ParticleTypes.FLAME, end, (rnd.nextDouble() - 0.5) * 0.3, rnd.nextDouble() * 0.2, (rnd.nextDouble() - 0.5) * 0.3);
		}
		if (rnd.nextInt(3) == 0) {
			add(level, ParticleTypes.LAVA, end, 0, 0, 0);
		}
	}

	private static void charge(ClientLevel level, RandomSource rnd, Player p) {
		float c = Hellflame.charge(p, 0f);
		Vec3 core = p.position().add(0, 1.0, 0);
		int n = 2 + (int) (c * 6);
		for (int i = 0; i < n; i++) {
			double a = rnd.nextDouble() * Math.PI * 2.0;
			double r = 1.4 + rnd.nextDouble() * (1.0 + c * 2.0);
			double y = (rnd.nextDouble() - 0.3) * 2.0;
			Vec3 at = core.add(Math.cos(a) * r, y, Math.sin(a) * r);
			Vec3 to = core.subtract(at).scale(0.12);
			add(level, ParticleTypes.FLAME, at, to.x, to.y, to.z);
		}
		// the body glows hotter as it fills
		if (rnd.nextFloat() < 0.3f + c) {
			add(level, c >= 1f ? HellflameCombat.WHITE_HOT : HellflameCombat.YELLOW,
					core.add((rnd.nextDouble() - 0.5) * 0.6, (rnd.nextDouble() - 0.5) * 1.4, (rnd.nextDouble() - 0.5) * 0.6), 0, 0.02, 0);
		}
		if (c >= 1f) {
			double a = (p.tickCount % 20) / 20.0 * Math.PI * 2.0;
			for (int k = 0; k < 3; k++) {
				double b = a + k * Math.PI * 2.0 / 3.0;
				add(level, HellflameCombat.WHITE_HOT, p.position().add(Math.cos(b) * 1.2, 0.1, Math.sin(b) * 1.2), 0, 0.05, 0);
			}
		}
	}

	private static void sun(ClientLevel level, RandomSource rnd, Player p) {
		Vec3 c = HellflameMoves.sunPos(p);
		float pulse = 1.0f + 0.1f * Mth.sin(p.tickCount * 0.4f);
		for (int i = 0; i < 6; i++) {
			add(level, HellflameCombat.WHITE_HOT, c.add(sphere(rnd, 0.45 * pulse)), 0, 0, 0);
		}
		for (int i = 0; i < 8; i++) {
			add(level, HellflameCombat.YELLOW, c.add(sphere(rnd, 0.8 * pulse)), 0, 0, 0);
		}
		for (int i = 0; i < 8; i++) {
			add(level, HellflameCombat.ORANGE, c.add(sphere(rnd, 1.1 * pulse)), 0, 0, 0);
		}
		for (int i = 0; i < 4; i++) {
			Vec3 s = sphere(rnd, 1.15).normalize();
			add(level, ParticleTypes.FLAME, c.add(s.scale(1.1)), s.x * 0.06, s.y * 0.06 + 0.02, s.z * 0.06);
		}
		if (rnd.nextInt(3) == 0) {
			add(level, ParticleTypes.LAVA, c, 0, 0, 0);
		}
	}

	/** A random point in a ball of radius {@code r} (biased to the shell). */
	private static Vec3 sphere(RandomSource rnd, double r) {
		Vec3 d = new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5);
		if (d.lengthSqr() < 1.0e-4) {
			return Vec3.ZERO;
		}
		return d.normalize().scale(r * Math.sqrt(rnd.nextDouble() * 0.6 + 0.4));
	}
}
