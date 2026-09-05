package com.herocraft.mod.event.boss.power;

import java.util.List;

import com.herocraft.mod.event.boss.BossPowerController;
import com.herocraft.mod.event.entity.EmpoweredZombie;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Laser Vision: controlled bursts at a tracked target, and a sweeping beam that rakes an arc.
 *
 * <h2>Deliberately imperfect aim</h2>
 * The design is explicit that a laser boss must "avoid perfect unavoidable aim". So the burst does not
 * hit whatever the boss is looking at -- it fires at where the target <em>was</em> when the burst was
 * committed, half a second earlier, and only damages what is actually standing in that line when the
 * beam resolves. Strafing beats it, standing still does not. The sweep is worse to stand in and easier
 * to walk out of: it telegraphs by drawing its own arc across the ground before the damage lands.
 */
public class LaserVisionBoss extends BossPowerController {
	public static final String POWER_KEY = "power_02_laser_vision";

	private static final int SLOT_BURST = 0;
	private static final int SLOT_SWEEP = 1;

	/** Ticks between committing to a burst and it firing -- the window to move. */
	private static final int TELEGRAPH_TICKS = 10;

	private Vec3 aimPoint;
	private int chargeTicks;
	private LivingEntity burstTarget;

	public LaserVisionBoss(EmpoweredZombie boss) {
		super(boss);
	}

	@Override
	public String powerKey() {
		return POWER_KEY;
	}

	@Override
	public double preferredRange() {
		return 12.0;
	}

	@Override
	public ParticleOptions auraParticle() {
		return ParticleTypes.FLAME;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.RED;
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		if (chargeTicks > 0) {
			// It plants and its eyes glow brighter as the beam charges -- a clear "about to fire" beat.
			boss.getNavigation().stop();
			boss.addEffect(new net.minecraft.world.effect.MobEffectInstance(
					net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 14, 5, false, false));
			if (aimPoint != null) {
				particles(level, ParticleTypes.SMALL_FLAME, aimPoint, 4, 0.2);
			}
			chargeTicks -= 10;
			if (chargeTicks <= 0 && aimPoint != null) {
				// Fire between where they were and where they are now: better aim than a purely stale
				// shot (the design's "shouldn't miss a lot"), still beaten by actually moving.
				Vec3 resolved = aimPoint;
				if (burstTarget != null && burstTarget.isAlive()) {
					resolved = aimPoint.lerp(burstTarget.getEyePosition(), 0.6);
				}
				fireBeam(level, resolved, 11.0f, 1.7);
				aimPoint = null;
				burstTarget = null;
			}
			return;
		}

		double distance = boss.distanceTo(target);
		if (distance > 32.0 || !boss.hasLineOfSight(target)) {
			return;
		}

		// Sweep against a group, or against a single target once they are close enough that raking a
		// wide arc is worth it -- so the sweep is not a move only ever seen in multiplayer.
		List<Player> clustered = playersNear(level, 16.0);
		boolean sweepWorthwhile = clustered.size() >= 2 || distance < 16.0;
		if (sweepWorthwhile && ready(SLOT_SWEEP) && freshChoice(SLOT_SWEEP)) {
			sweep(level, target);
			startCooldown(SLOT_SWEEP, 260);
			return;
		}
		if (ready(SLOT_BURST)) {
			// Commit to where they are now; it fires ~1s later, re-aimed partway to their new position.
			aimPoint = target.getEyePosition();
			burstTarget = target;
			chargeTicks = TELEGRAPH_TICKS + 10;
			boss.getNavigation().stop();
			sound(level, SoundEvents.BLAZE_SHOOT, 1.0f, 1.6f);
			sound(level, SoundEvents.CONDUIT_ACTIVATE, 0.8f, 1.4f);
			particleLine(level, ParticleTypes.SMALL_FLAME, eye(), aimPoint, 1.0);
			startCooldown(SLOT_BURST, 100);
		}
	}

	/** A five-step arc across the boss's facing, each step a thin beam. Walk out through the side. */
	private void sweep(ServerLevel level, LivingEntity target) {
		sound(level, SoundEvents.BLAZE_SHOOT, 1.2f, 0.8f);
		Vec3 base = target.position().subtract(boss.position());
		double baseAngle = Math.atan2(base.z, base.x);
		double reach = Math.min(20.0, base.length() + 4.0);
		for (int step = -2; step <= 2; step++) {
			double angle = baseAngle + step * 0.22;
			Vec3 point = boss.position().add(Math.cos(angle) * reach, 1.0, Math.sin(angle) * reach);
			fireBeam(level, point, 5.0f, 1.1);
		}
	}

	/**
	 * Resolve one beam: draw it, then damage anything within {@code width} of the segment. The width
	 * check is a simple point-to-segment distance -- no ray tracing per entity, and the candidate set
	 * is a single bounded query around the midpoint.
	 */
	private void fireBeam(ServerLevel level, Vec3 to, float damage, double width) {
		Vec3 from = eye();
		particleLine(level, ParticleTypes.FLAME, from, to, 2.0);
		Vec3 mid = from.add(to).scale(0.5);
		double half = from.distanceTo(to) * 0.5 + 1.0;
		for (Player player : level.getEntitiesOfClass(Player.class, box(mid, half),
				p -> p.isAlive() && !p.isSpectator() && !p.isCreative())) {
			if (distanceToSegment(player.getEyePosition(), from, to) <= width) {
				hurt(player, damage);
			}
		}
		sound(level, SoundEvents.FIRECHARGE_USE, 0.8f, 1.4f);
	}

	private Vec3 eye() {
		return boss.getEyePosition();
	}

	private static double distanceToSegment(Vec3 point, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double lengthSq = ab.lengthSqr();
		if (lengthSq < 1.0e-6) {
			return point.distanceTo(a);
		}
		double t = Math.max(0.0, Math.min(1.0, point.subtract(a).dot(ab) / lengthSq));
		return point.distanceTo(a.add(ab.scale(t)));
	}
}
