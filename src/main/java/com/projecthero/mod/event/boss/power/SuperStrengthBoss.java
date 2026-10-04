package com.projecthero.mod.event.boss.power;

import java.util.List;

import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.hero.revamp.BatchCFx;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Super Strength (v0.14.5 kit): Haymaker three-hit combo, Ground Slam, Power Leap with a hero landing, Bull Rush and --
 * once badly hurt -- Maximum Effort. Player numbers from {@code SuperStrengthHandlers}, halved for a boss
 * ({@link #bossDamage}).
 */
public class SuperStrengthBoss extends BossPowerController {
	public static final String POWER_KEY = "power_01_super_strength";

	private static final int HAYMAKER = 0;
	private static final int SLAM = 1;
	private static final int LEAP = 2;
	private static final int RUSH = 3;
	private static final int EFFORT = 4;
	private static final List<String> IDS = List.of("haymaker", "ground_slam", "power_leap", "bull_rush", "maximum_effort");
	private static final ParticleOptions DIRT = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState());

	/** Haymaker: hits left in the current combo (3 -> 1). */
	private int comboHit;
	/** Power Leap in flight: ticks left, and the landing tier. */
	private int leapTicks;
	/** Bull Rush: wind-up ticks left, then charge ticks left, and its heading. */
	private int rushWindup;
	private int rushTicks;
	private Vec3 rushDir = Vec3.ZERO;
	private final java.util.Set<LivingEntity> rushHit = new java.util.HashSet<>();
	private int effortTicks;

	public SuperStrengthBoss(EmpoweredZombie boss) {
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
		return 2.5;
	}

	@Override
	public ParticleOptions auraParticle() {
		return ParticleTypes.CRIT;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.RED;
	}

	private float effortMult() {
		return effortTicks > 0 ? 1.5f : 1.0f;
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		if (rushWindup > 0 || rushTicks > 0 || leapTicks > 0) {
			return; // committed to a move driven by serverTick
		}
		double d = boss.distanceTo(target);

		if (comboHit > 0) {
			if (d <= 4.5) {
				haymakerHit(level, target);
			} else {
				comboHit = 0; // dropped combo
			}
			return;
		}
		if (lowHealth() && ready(EFFORT)) {
			maximumEffort(level);
			return;
		}
		if (d <= 3.6 && ready(HAYMAKER) && freshChoice(HAYMAKER)) {
			startCooldown(HAYMAKER, 102);
			comboHit = 3;
			haymakerHit(level, target);
			return;
		}
		if (d <= 5.5 && ready(SLAM)) {
			groundSlam(level);
			startCooldown(SLAM, 136);
			return;
		}
		if (d > 7.0 && d < 22.0 && sees(target) && ready(RUSH) && freshChoice(RUSH)) {
			rushWindup = 30; // the player charges 100 t; the boss winds up 1.5 s
			rushDir = flatDirTo(target.position());
			boss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 50, 0, false, false));
			sound(level, SoundEvents.RAVAGER_ROAR, 1.2f, 0.9f);
			startCooldown(RUSH, 300);
			return;
		}
		if (d > 6.0 && d < 26.0 && sees(target) && ready(LEAP)) {
			Vec3 to = target.position().subtract(boss.position());
			double flat = Math.sqrt(to.x * to.x + to.z * to.z);
			Vec3 dir = flatDirTo(target.position());
			double speed = Math.min(1.5, 0.35 + flat * 0.055);
			boss.setDeltaMovement(dir.x * speed, 0.75 + Math.max(0.0, to.y) * 0.08, dir.z * speed);
			boss.hasImpulse = true;
			boss.hurtMarked = true;
			boss.getNavigation().stop();
			leapTicks = 40;
			particles(level, ParticleTypes.CLOUD, boss.position(), 12, 0.4);
			sound(level, SoundEvents.RAVAGER_ROAR, 0.9f, 1.3f);
			startCooldown(LEAP, 120);
		}
	}

	@Override
	public void serverTick(ServerLevel level, LivingEntity target) {
		if (effortTicks > 0) {
			effortTicks--;
			if (effortTicks % 10 == 0) {
				particles(level, BatchCFx.dust(0xD01818, 1.2f), boss.position().add(0, 1.4, 0), 4, 0.5);
			}
		}
		if (leapTicks > 0) {
			leapTicks--;
			if ((boss.onGround() && leapTicks < 36) || leapTicks == 0) {
				leapTicks = 0;
				heroLanding(level, 3);
			}
		}
		if (rushWindup > 0) {
			rushWindup--;
			boss.setDeltaMovement(0, boss.getDeltaMovement().y, 0);
			if (rushWindup % 5 == 0) {
				sound(level, SoundEvents.PISTON_CONTRACT, 0.8f, 0.6f);
				particles(level, ParticleTypes.CLOUD, boss.position(), 4, 0.4);
			}
			if (target != null && rushWindup > 10) {
				rushDir = flatDirTo(target.position()); // tracks during the wind-up, locks for the last half second
			}
			if (rushWindup == 0) {
				rushTicks = 30;
				rushHit.clear();
				sound(level, SoundEvents.RAVAGER_ROAR, 1.3f, 0.8f);
			}
			return;
		}
		if (rushTicks > 0) {
			rushTicks--;
			dash(rushDir, 0.75 * (effortTicks > 0 ? 1.12 : 1.0), boss.getDeltaMovement().y);
			particles(level, ParticleTypes.CLOUD, boss.position().add(0, 0.3, 0), 2, 0.3);
			for (LivingEntity e : victimsAround(level, boss.position().add(rushDir.scale(1.3)), 2.0)) {
				if (rushHit.add(e)) {
					hurt(e, bossDamage(24.0f) * effortMult());
					knockAway(e, boss.position(), 2.6, 0.45);
					sound(level, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.6f);
				}
			}
			if (boss.horizontalCollision && rushTicks < 26) {
				rushTicks = 0; // hit a wall
				particles(level, DIRT, boss.position().add(rushDir), 20, 0.5);
			}
		}
	}

	private void haymakerHit(ServerLevel level, LivingEntity target) {
		face(target);
		int hit = 4 - comboHit; // 1, 2, 3
		comboHit--;
		float dmg = bossDamage(hit == 3 ? 22.0f : (hit == 2 ? 12.0f : 10.0f)) * effortMult();
		hurtFresh(target, dmg);
		if (hit == 3) {
			knockAway(target, boss.position(), 2.4, 0.6);
			particles(level, ParticleTypes.EXPLOSION, mid(target), 1, 0.1);
			particles(level, ParticleTypes.CRIT, mid(target), 24, 0.4);
			sound(level, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.3f, 0.5f);
			sound(level, SoundEvents.GENERIC_EXPLODE, 0.5f, 1.4f);
		} else {
			knockAway(target, boss.position(), 0.4, 0.1);
			particles(level, ParticleTypes.CRIT, mid(target), 8, 0.3);
			sound(level, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.9f);
		}
	}

	private void groundSlam(ServerLevel level) {
		double r = effortTicks > 0 ? 7.5 : 5.0;
		Vec3 c = boss.position();
		for (LivingEntity e : strikeArea(level, c, r, bossDamage(20.0f) * effortMult(), false, 1.0, 0.85)) {
			control(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 2);
		}
		particles(level, ParticleTypes.EXPLOSION, c, 1, 0.1);
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.2, c.z, 50, r / 2, 0.2, r / 2, 0.02);
		ring(level, DIRT, c, r * 0.45, 16);
		ring(level, DIRT, c, r * 0.8, 24);
		sound(level, SoundEvents.GENERIC_EXPLODE, 0.9f, 1.1f);
	}

	private void heroLanding(ServerLevel level, int tier) {
		Vec3 c = boss.position();
		double r = 2.5 + 0.6 * tier;
		strikeArea(level, c, r, bossDamage(7.0f + 3.0f * tier) * effortMult(), false, 0.8 + 0.2 * tier, 0.35 + 0.06 * tier);
		ring(level, DIRT, c, r * 0.6, 20);
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.1, c.z, 30, r / 2, 0.1, r / 2, 0.02);
		particles(level, ParticleTypes.EXPLOSION, c, 1, 0.1);
		sound(level, SoundEvents.ANVIL_LAND, 0.4f, 0.5f);
		sound(level, SoundEvents.GENERIC_EXPLODE, 0.75f, 0.55f);
	}

	private void maximumEffort(ServerLevel level) {
		effortTicks = 600;
		boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 1, false, true));
		boss.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 600, 1, false, true));
		particles(level, ParticleTypes.CRIT, boss.position().add(0, 1.2, 0), 40, 0.8);
		BatchCFx.flatRing(level, boss.position().add(0, 0.2, 0), 2.0, 32, ParticleTypes.CLOUD, 0.3);
		sound(level, SoundEvents.PLAYER_LEVELUP, 0.9f, 0.6f);
		sound(level, SoundEvents.RAVAGER_ROAR, 1.0f, 1.4f);
		startCooldown(EFFORT, 1400);
	}
}
