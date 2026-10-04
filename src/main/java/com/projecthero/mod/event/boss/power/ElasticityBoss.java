package com.projecthero.mod.event.boss.power;

import java.util.List;

import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.hero.revamp.BatchCFx;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Elasticity (v0.14.1 kit): Stretch Punch (a telegraphed long-reach punch), Double Fist Slam, Slingshot (it flings
 * itself at a far target), Giant Hammer Fist (a marked slam) and Rubber Shield once hurt (melee bounces off, projectiles
 * bounce off). The player's stretched arm is a client overlay keyed to a player, so the boss's reach is drawn as a
 * slime line.
 */
public class ElasticityBoss extends BossPowerController {
	public static final String POWER_KEY = "power_17_elasticity";

	private static final int PUNCH = 0;
	private static final int SLAM = 1;
	private static final int SLINGSHOT = 2;
	private static final int HAMMER = 3;
	private static final int SHIELD = 4;
	private static final List<String> IDS = List.of("stretch_punch", "double_fist_slam", "slingshot", "giant_hammer_fist", "rubber_shield");

	private int slingTicks;
	private LivingEntity slingTarget;
	private int shieldTicks;

	public ElasticityBoss(EmpoweredZombie boss) {
		super(boss);
	}

	@Override
	public String powerKey() {
		return POWER_KEY;
	}

	@Override
	public List<String> abilityIds() {
		return IDS;
	}

	@Override
	public double preferredRange() {
		return 8.0;
	}

	@Override
	public ParticleOptions auraParticle() {
		return ParticleTypes.ITEM_SLIME;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.BLUE;
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		if (slingTicks > 0) {
			return;
		}
		double d = boss.distanceTo(target);
		if (d < 12.0 && ready(HAMMER) && freshChoice(HAMMER)) {
			Vec3 mark = target.position();
			BatchCFx.flatRing(level, mark.add(0, 0.2, 0), 4.5, 32, ParticleTypes.ITEM_SLIME, 0.0);
			beginCast(level, target, HAMMER, 600, 2, SoundEvents.SLIME_JUMP, ParticleTypes.ITEM_SLIME, t -> {
				for (LivingEntity e : strikeArea(level, mark, 4.5, bossDamage(41.0f), true, 1.5, 0.0)) {
					fling(e, e.getDeltaMovement().add(0, -0.4, 0));
					control(e, MobEffects.MOVEMENT_SLOWDOWN, 50, 2);
				}
				level.sendParticles(ParticleTypes.ITEM_SLIME, mark.x, mark.y + 0.3, mark.z, 80, 2.5, 0.5, 2.5, 0.1);
				particles(level, ParticleTypes.EXPLOSION, mark, 1, 0.0);
				soundAt(level, mark, SoundEvents.SLIME_SQUISH, 1.4f, 0.4f);
			});
			return;
		}
		if (d < 8.0 && ready(SLAM) && freshChoice(SLAM)) {
			face(target);
			Vec3 at = boss.getEyePosition().add(flatDirTo(target.position()).scale(Math.min(7.0, d)));
			strikeArea(level, at, 4.0, bossDamage(20.5f), false, 2.0, 0.3);
			particles(level, ParticleTypes.ITEM_SLIME, at, 30, 1.0);
			sound(level, SoundEvents.SLIME_SQUISH, 1.2f, 0.7f);
			startCooldown(SLAM, 119);
			return;
		}
		if (d > 12.0 && d < 45.0 && sees(target) && ready(SLINGSHOT)) {
			slingTicks = 40;
			slingTarget = target;
			Vec3 dir = target.position().subtract(boss.position()).normalize();
			boss.setDeltaMovement(dir.scale(1.6).add(0, 0.25, 0));
			boss.hurtMarked = true;
			sound(level, SoundEvents.SLIME_JUMP, 1.2f, 0.6f);
			startCooldown(SLINGSHOT, 120);
			return;
		}
		if (d < 17.0 && sees(target) && ready(PUNCH)) {
			beginCast(level, target, PUNCH, 70, 1, SoundEvents.SLIME_JUMP_SMALL, ParticleTypes.ITEM_SLIME, t -> {
				Vec3 from = boss.getEyePosition().subtract(0, 0.3, 0);
				Vec3 to = clipEnd(level, from, from.add(aimFromEyes(mid(t)).scale(17.0)));
				particleLine(level, ParticleTypes.ITEM_SLIME, from, to, 2.0);
				for (LivingEntity e : strikeLine(level, from, to, 0.7, bossDamage(20.5f))) {
					knockAway(e, from, 1.3, 0.2);
					particles(level, ParticleTypes.ITEM_SLIME, mid(e), 16, 0.3);
				}
				sound(level, SoundEvents.SLIME_ATTACK, 1.2f, 0.8f);
			});
		}
	}

	@Override
	public void serverTick(ServerLevel level, LivingEntity target) {
		if (shieldTicks > 0) {
			shieldTicks--;
			for (Projectile p : level.getEntitiesOfClass(Projectile.class, boss.getBoundingBox().inflate(3.5),
					p -> p.getOwner() != boss && p.getDeltaMovement().lengthSqr() > 0.01)) {
				Vec3 v = p.getDeltaMovement();
				Vec3 toBoss = boss.position().subtract(p.position());
				if (v.dot(toBoss) > 0) {
					// bounced off the rubber and dropped (the player's sends it back at its owner; a boss-owned rebound
					// could hit the boss's own horde on the way, so it just falls dead)
					p.setDeltaMovement(v.scale(-0.3).add(0, 0.15, 0));
					p.hurtMarked = true;
					particles(level, ParticleTypes.ITEM_SLIME, p.position(), 8, 0.2);
					sound(level, SoundEvents.SLIME_BLOCK_HIT, 1.0f, 1.2f);
				}
			}
			if (shieldTicks % 6 == 0) {
				BatchCFx.flatRing(level, boss.position().add(0, 1.0, 0), 1.5, 14, ParticleTypes.ITEM_SLIME, 0.0);
			}
		}
		if (slingTicks > 0) {
			slingTicks--;
			LivingEntity t = slingTarget;
			if (t == null || !t.isAlive()) {
				slingTicks = 0;
				return;
			}
			double dist = boss.distanceTo(t);
			Vec3 dir = t.position().add(0, 0.5, 0).subtract(boss.position()).normalize();
			boss.setDeltaMovement(dir.scale(Math.min(2.2, 0.9 + dist * 0.12)));
			boss.hurtMarked = true;
			boss.fallDistance = 0.0f;
			particles(level, ParticleTypes.ITEM_SLIME, boss.position().add(0, 1, 0), 3, 0.3);
			if (dist <= 2.6 + boss.getBbWidth() * 0.5) {
				slingTicks = 0;
				hurt(t, bossDamage(17.0f));
				knockAway(t, boss.position(), 1.2, 0.3);
				control(t, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
				particles(level, ParticleTypes.ITEM_SLIME, mid(t), 40, 0.6);
				sound(level, SoundEvents.SLIME_SQUISH, 1.2f, 0.6f);
				boss.setDeltaMovement(boss.getDeltaMovement().scale(0.2));
			}
		}
	}

	@Override
	public float modifyIncomingDamage(DamageSource source, float amount) {
		if (shieldTicks > 0 && source.getDirectEntity() == source.getEntity() && source.getEntity() instanceof LivingEntity attacker) {
			knockAway(attacker, boss.position(), 2.0, 0.5);
			return amount * 0.35f; // rubber soaks melee
		}
		return amount;
	}

	@Override
	public void onDamaged(ServerLevel level, DamageSource source, float amount) {
		if (healthFraction() < 0.6f && shieldTicks <= 0 && readyReactive(SHIELD)) {
			shieldTicks = 100;
			sound(level, SoundEvents.SLIME_BLOCK_PLACE, 1.2f, 0.6f);
			startCooldown(SHIELD, 400);
		}
	}
}
