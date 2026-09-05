package com.herocraft.mod.event.boss.power;

import com.herocraft.mod.event.boss.BossPowerController;
import com.herocraft.mod.event.entity.EmpoweredZombie;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Shockwave Manipulation: kinetic control. It throws people off it with an expanding ring, and it can
 * scatter incoming projectiles.
 *
 * <p>The deflection is the interesting half. Rather than granting immunity, it applies a one-off push
 * to arrows and other projectiles that are currently in the air near the boss, so a shot fired from
 * far away is likely to be knocked off course while one fired from close range mostly is not. That
 * gives ranged players a real answer -- get closer -- instead of shutting them out, which a flat
 * projectile immunity would.
 */
public class ShockwaveBoss extends BossPowerController {
	public static final String POWER_KEY = "power_21_shockwave_manipulation";

	private static final int SLOT_RING = 0;
	private static final int SLOT_DEFLECT = 1;

	public ShockwaveBoss(EmpoweredZombie boss) {
		super(boss);
	}

	@Override
	public String powerKey() {
		return POWER_KEY;
	}

	@Override
	public double preferredRange() {
		return 5.0;
	}

	@Override
	public ParticleOptions auraParticle() {
		return ParticleTypes.POOF;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.WHITE;
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		if (ready(SLOT_DEFLECT)) {
			if (deflect(level)) {
				startCooldown(SLOT_DEFLECT, 100);
				return;
			}
		}
		if (areaAnchor(level, target, 6.0, 2) != null && ready(SLOT_RING)) {
			ring(level);
			startCooldown(SLOT_RING, 170);
		}
	}

	private void ring(ServerLevel level) {
		sound(level, SoundEvents.GENERIC_EXPLODE.value(), 0.9f, 1.4f);
		level.sendParticles(ParticleTypes.POOF, boss.getX(), boss.getY() + 0.6, boss.getZ(),
				50, 3.0, 0.3, 3.0, 0.1);
		for (Player player : playersNear(level, 8.0)) {
			double d = Math.max(1.0, player.distanceTo(boss));
			hurt(player, (float) Math.max(2.0, 9.0 - d));
			knockAway(player, boss.position(), 2.0, 0.6);
		}
	}

	/** @return true if anything was actually deflected, so the cooldown is only spent on a real use. */
	private boolean deflect(ServerLevel level) {
		boolean any = false;
		for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, boss.getBoundingBox().inflate(6.0),
				p -> p.isAlive() && p.getOwner() != boss)) {
			Vec3 away = projectile.position().subtract(boss.position());
			if (away.lengthSqr() < 1.0e-4) {
				continue;
			}
			// A nudge, not a reversal: close shots barely change course, distant ones miss.
			projectile.setDeltaMovement(projectile.getDeltaMovement().scale(0.4).add(away.normalize().scale(0.5)));
			projectile.hurtMarked = true;
			any = true;
		}
		if (any) {
			sound(level, SoundEvents.SHIELD_BLOCK, 0.8f, 1.6f);
			level.sendParticles(ParticleTypes.POOF, boss.getX(), boss.getY() + 1.2, boss.getZ(),
					16, 1.2, 0.8, 1.2, 0.05);
		}
		return any;
	}
}
