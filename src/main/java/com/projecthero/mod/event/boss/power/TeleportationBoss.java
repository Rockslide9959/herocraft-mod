package com.projecthero.mod.event.boss.power;

import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.entity.EmpoweredZombie;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Teleportation: the anti-kite and anti-corner power. It blinks to a target that has got too far away,
 * and blinks <em>out</em> when something hits it hard from range.
 *
 * <h2>Never a free hit, never a softlock</h2>
 * A blink always lands on legal ground next to the target, never inside them and never inside geometry
 * -- {@link #safeSpotNear} walks a ring of candidate positions and takes the first that has a solid
 * floor and two blocks of clear space, giving up entirely if none does. It also does no damage on
 * arrival, so appearing beside a player is an opportunity for them to react, not an unavoidable hit.
 * As a side benefit this is the roster's answer to terrain softlocks: a Teleportation boss that gets
 * stuck simply blinks out of the problem.
 */
public class TeleportationBoss extends BossPowerController {
	public static final String POWER_KEY = "power_11_teleportation";

	private static final int SLOT_BLINK_IN = 0;
	private static final int SLOT_BLINK_OUT = 1;
	private static final int SLOT_PHASE_STRIKE = 2;
	private static final int SLOT_SCATTER = 3;

	public TeleportationBoss(EmpoweredZombie boss) {
		super(boss);
	}

	@Override
	public String powerKey() {
		return POWER_KEY;
	}

	@Override
	public double preferredRange() {
		return 3.0;
	}

	@Override
	public ParticleOptions auraParticle() {
		return ParticleTypes.PORTAL;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.PURPLE;
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		double distance = boss.distanceTo(target);
		if (distance > 9.0 && distance < 48.0 && ready(SLOT_BLINK_IN)) {
			BlockPos spot = safeSpotNear(level, target.blockPosition(), 3);
			if (spot != null) {
				blinkTo(level, spot);
				startCooldown(SLOT_BLINK_IN, 120);
			}
			return;
		}
		// Mid-range: blink onto the target and land a single telegraphed hit, then it is free to blink
		// away again. Turns a Teleportation boss from "closes distance, then melees" into a proper
		// hit-and-run threat that actually uses the blink offensively.
		if (distance > 3.5 && distance <= 9.0 && ready(SLOT_PHASE_STRIKE) && freshChoice(SLOT_PHASE_STRIKE)) {
			BlockPos spot = safeSpotNear(level, target.blockPosition(), 2);
			if (spot != null) {
				blinkTo(level, spot);
				sound(level, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.9f);
				if (boss.distanceTo(target) < 4.0) {
					hurt(target, 6.0f);
					knockAway(target, boss.position(), 0.6, 0.2);
				}
				startCooldown(SLOT_PHASE_STRIKE, 150);
			}
			return;
		}
		// Badly hurt: scatter -- blink clear of everyone and leave a puff of decoy particles, buying a
		// moment before it re-engages.
		if (lowHealth() && ready(SLOT_SCATTER) && !playersNear(level, 6.0).isEmpty()) {
			BlockPos spot = farSpot(level, target);
			if (spot != null) {
				blinkTo(level, spot);
				particles(level, ParticleTypes.PORTAL, boss.position().add(0, 1.0, 0), 60, 1.2);
				startCooldown(SLOT_SCATTER, 300);
			}
		}
	}

	/** A legal standing spot well away from the target, for the low-health scatter. */
	private BlockPos farSpot(ServerLevel level, LivingEntity target) {
		for (int radius = 10; radius >= 6; radius -= 2) {
			BlockPos spot = safeSpotNear(level, target.blockPosition(), radius);
			if (spot != null) {
				return spot;
			}
		}
		return null;
	}

	/** Emergency: a heavy ranged hit makes it reposition rather than stand there being shot. */
	@Override
	public void onDamaged(ServerLevel level, DamageSource source, float amount) {
		if (!readyReactive(SLOT_BLINK_OUT) || amount < 12.0f || source.getEntity() == null) {
			return;
		}
		BlockPos spot = safeSpotNear(level, source.getEntity().blockPosition(), 4);
		if (spot != null) {
			blinkTo(level, spot);
			startCooldown(SLOT_BLINK_OUT, 200);
		}
	}

	private void blinkTo(ServerLevel level, BlockPos pos) {
		Vec3 from = boss.position();
		boss.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		boss.getNavigation().stop();
		particles(level, ParticleTypes.PORTAL, from.add(0, 1.0, 0), 30, 0.6);
		particles(level, ParticleTypes.PORTAL, boss.position().add(0, 1.0, 0), 30, 0.6);
		sound(level, SoundEvents.ENDERMAN_TELEPORT, 1.0f, 0.7f);
	}

	/**
	 * First legal standing position on a ring around {@code around}. Bounded: eight compass directions
	 * times a small vertical window, so it can never turn into a search.
	 */
	private BlockPos safeSpotNear(ServerLevel level, BlockPos around, int radius) {
		for (int i = 0; i < 8; i++) {
			double angle = i * Math.PI / 4.0;
			int x = around.getX() + (int) Math.round(Math.cos(angle) * radius);
			int z = around.getZ() + (int) Math.round(Math.sin(angle) * radius);
			for (int dy = 0; dy <= 3; dy++) {
				for (int sign = 1; sign >= -1; sign -= 2) {
					BlockPos candidate = new BlockPos(x, around.getY() + dy * sign, z);
					if (!level.isLoaded(candidate)) {
						continue;
					}
					if (standable(level, candidate)) {
						return candidate;
					}
					if (dy == 0) {
						break;
					}
				}
			}
		}
		return null;
	}

	private boolean standable(ServerLevel level, BlockPos pos) {
		if (level.isOutsideBuildHeight(pos) || level.isOutsideBuildHeight(pos.above(2))) {
			return false;
		}
		if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) {
			return false;
		}
		// The boss is oversized -- it needs three blocks of headroom, not two.
		for (int dy = 0; dy < 3; dy++) {
			if (!level.getBlockState(pos.above(dy)).getCollisionShape(level, pos.above(dy)).isEmpty()) {
				return false;
			}
		}
		return true;
	}
}
