package com.projecthero.mod.event.boss.power;

import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.entity.EmpoweredZombie;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Super Speed: the blitz. Its signature move is a committed rush -- it blurs across the arena to the
 * target, lands a rapid three-hit flurry, launches them away, and then <em>will not do it again for
 * several seconds</em>, which is the window the player gets to punish it. Between blitzes it is a fast
 * but ordinary brawler, and when badly hurt it buys itself a burst of Speed so it reads as getting
 * harder to pin down rather than simply tankier.
 *
 * <p>Every part of the blitz is readable: a loud wind-up, a straight-line charge you can sidestep, and
 * a fixed recovery afterwards.
 */
public class SuperSpeedBoss extends BossPowerController {
	public static final String POWER_KEY = "power_04_super_speed";

	private static final int SLOT_BLITZ = 0;
	private static final int SLOT_FRENZY = 1;
	private static final int SLOT_FLURRY = 2;

	/** Blitz phases. */
	private int blitzUntil;
	private boolean blitzing;

	public SuperSpeedBoss(EmpoweredZombie boss) {
		super(boss);
	}

	@Override
	public String powerKey() {
		return POWER_KEY;
	}

	@Override
	public double preferredRange() {
		return blitzing ? 1.5 : 3.0;
	}

	@Override
	public ParticleOptions auraParticle() {
		return ParticleTypes.SWEEP_ATTACK;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.WHITE;
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		if (blitzing) {
			driveBlitz(level, target);
			return;
		}

		if (healthFraction() < 0.35f && ready(SLOT_FRENZY)) {
			boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 12 * 20, 2, false, true));
			sound(level, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.0f, 1.4f);
			startCooldown(SLOT_FRENZY, 600);
			return;
		}

		double distance = boss.distanceTo(target);
		if (distance > 4.0 && distance < 26.0 && ready(SLOT_BLITZ) && boss.hasLineOfSight(target)
				&& freshChoice(SLOT_BLITZ)) {
			startBlitz(level);
			return;
		}

		// Point-blank the blitz has no room to build up, so it still has a quick close-range flurry.
		if (distance <= 4.0 && ready(SLOT_FLURRY)) {
			flurry(level, target, false);
			startCooldown(SLOT_FLURRY, 110);
		}
	}

	// ---------------- blitz ----------------

	private void startBlitz(ServerLevel level) {
		blitzing = true;
		blitzUntil = boss.tickCount + 44;
		boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 4, false, false));
		sound(level, SoundEvents.PLAYER_ATTACK_SWEEP, 1.3f, 1.6f);
		sound(level, SoundEvents.ILLUSIONER_CAST_SPELL, 1.0f, 1.7f);
		particles(level, ParticleTypes.SWEEP_ATTACK, boss.position().add(0, 1.0, 0), 12, 0.5);
		// Cooldown is set now so the several-second lull is measured from the commitment, not the finish.
		startCooldown(SLOT_BLITZ, 170);
	}

	/** Steer hard at the target each cycle; connect once in range, or give up when the timer runs out. */
	private void driveBlitz(ServerLevel level, LivingEntity target) {
		Vec3 to = target.position().add(0, 0.4, 0).subtract(boss.position());
		if (to.lengthSqr() > 1.0e-4) {
			Vec3 v = to.normalize().scale(1.35);
			boss.setDeltaMovement(v.x, Math.max(-0.2, boss.getDeltaMovement().y * 0.4), v.z);
			boss.hasImpulse = true;
		}
		boss.getNavigation().stop();
		particleLine(level, ParticleTypes.CLOUD, boss.position().add(0, 0.6, 0),
				target.position().add(0, 0.6, 0), 1.5);

		if (boss.distanceTo(target) <= 3.6) {
			flurry(level, target, true);
			knockAway(target, boss.position(), 2.4, 0.5);
			sound(level, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.7f);
			particles(level, ParticleTypes.SWEEP_ATTACK,
					target.position().add(0, target.getBbHeight() * 0.5, 0), 12, 0.4);
			endBlitz();
			return;
		}
		if (boss.tickCount >= blitzUntil) {
			endBlitz();
		}
	}

	private void endBlitz() {
		blitzing = false;
		blitzUntil = 0;
	}

	/** Three quick strikes in the space of one attack. {@code heavy} is the blitz payoff hit. */
	private void flurry(ServerLevel level, LivingEntity target, boolean heavy) {
		sound(level, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 1.7f);
		float each = heavy ? 4.0f : 3.0f;
		for (int i = 0; i < 3; i++) {
			if (boss.distanceTo(target) <= 5.5) {
				hurt(target, each);
			}
		}
		if (!heavy) {
			knockAway(target, boss.position(), 0.4, 0.15);
			particles(level, ParticleTypes.SWEEP_ATTACK,
					target.position().add(0, target.getBbHeight() * 0.5, 0), 6, 0.3);
		}
	}
}
