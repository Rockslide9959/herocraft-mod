package com.projecthero.mod.event.boss.power;

import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.entity.EmpoweredZombie;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.phys.Vec3;

/**
 * Pyrokinesis: fireballs at range, a nova when crowded.
 *
 * <p>Both are dodgeable for the same reason: the fireball is a slow vanilla {@link SmallFireball} with
 * visible travel, and the nova has a fixed radius the player can simply be outside of. Neither sets
 * the world on fire -- the fireball is spawned without ignition and the nova only burns entities --
 * so a raid fought in a wooden base does not end with the base gone.
 */
public class PyrokinesisBoss extends BossPowerController {
	public static final String POWER_KEY = "power_08_pyrokinesis";

	private static final int SLOT_FIREBALL = 0;
	private static final int SLOT_NOVA = 1;

	public PyrokinesisBoss(EmpoweredZombie boss) {
		super(boss);
	}

	@Override
	public String powerKey() {
		return POWER_KEY;
	}

	@Override
	public double preferredRange() {
		return 11.0;
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
		Vec3 anchor = areaAnchor(level, target, 5.5, 2);
		if (anchor != null && ready(SLOT_NOVA) && freshChoice(SLOT_NOVA)) {
			nova(level);
			startCooldown(SLOT_NOVA, 220);
			return;
		}
		double distance = boss.distanceTo(target);
		if (distance > 4.0 && distance < 26.0 && ready(SLOT_FIREBALL) && boss.hasLineOfSight(target)) {
			// Wind up visibly -- it plants, glows and roars for ~1s -- then the fireball leaves aimed
			// at wherever the target is by then, so standing still through the tell gets you hit.
			beginRangedCast(level, target, SLOT_FIREBALL, 90, SoundEvents.BLAZE_AMBIENT,
					ParticleTypes.SMALL_FLAME, t -> fireball(level, t));
		}
	}

	private void fireball(ServerLevel level, LivingEntity target) {
		Vec3 from = boss.getEyePosition();
		// A short lead so a strafing player is still threatened, but a slow enough ball to see coming.
		Vec3 predicted = target.position().add(0, target.getBbHeight() * 0.5, 0)
				.add(target.getDeltaMovement().scale(4.0));
		Vec3 to = predicted.subtract(from);
		SmallFireball ball = new SmallFireball(level, boss, to.normalize().scale(1.05));
		ball.setPos(from.x, from.y, from.z);
		level.addFreshEntity(ball);
		sound(level, SoundEvents.BLAZE_SHOOT, 1.0f, 0.9f);
	}

	private void nova(ServerLevel level) {
		sound(level, SoundEvents.FIRECHARGE_USE, 1.2f, 0.6f);
		level.sendParticles(ParticleTypes.FLAME, boss.getX(), boss.getY() + 1.0, boss.getZ(),
				60, 2.5, 1.0, 2.5, 0.08);
		for (Player player : playersNear(level, 6.5)) {
			hurt(player, 8.0f);
			player.setRemainingFireTicks(Math.max(player.getRemainingFireTicks(), 80));
			knockAway(player, boss.position(), 0.6, 0.35);
		}
	}
}
