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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Flight (v0.13.22 kit): takes to the air (Flight toggle) and holds station above its target, Air Dash shoulder-checks,
 * Dive Bomb craters scaled by the drop height, Orbital Drop (rise, hang -- the landing ring telegraphs -- then meteor
 * down) and a Barrel Roll that dodges a hit. A heavy hit or any ranged hit while flying knocks it out of the sky and
 * grounds it for 6 s, so archers and fliers always have an answer.
 */
public class FlightBoss extends BossPowerController {
	public static final String POWER_KEY = "power_03_flight";

	private static final int FLY = 0;
	private static final int DASH = 1;
	private static final int DIVE = 2;
	private static final int ORBITAL = 3;
	private static final int ROLL = 4;
	private static final List<String> IDS = List.of("flight_toggle", "air_dash", "dive_bomb", "orbital_drop", "barrel_roll");

	private int flyTicks;
	private int groundedTicks;
	private int dashTicks;
	private Vec3 dashDir = Vec3.ZERO;
	private int diveTicks;
	private double diveStartY;
	/** Orbital Drop phase: 1 rise, 2 hang, 3 drop. */
	private int orbitalPhase;
	private int orbitalTicks;
	private double orbitalStartY;
	private Vec3 orbitalMark;
	private int rollTicks;

	public FlightBoss(EmpoweredZombie boss) {
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
		return flyTicks > 0 ? 5.0 : 2.5;
	}

	@Override
	public ParticleOptions auraParticle() {
		return ParticleTypes.CLOUD;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.WHITE;
	}

	@Override
	public boolean compatibleWith(String otherPowerKey) {
		return super.compatibleWith(otherPowerKey) && !otherPowerKey.equals(TeleportationBoss.POWER_KEY);
	}

	private boolean busy() {
		return dashTicks > 0 || diveTicks > 0 || orbitalPhase > 0;
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		if (busy()) {
			return;
		}
		double d = boss.distanceTo(target);
		double above = boss.getY() - target.getY();
		Vec3 flat = target.position().subtract(boss.position());
		double flatDist = Math.sqrt(flat.x * flat.x + flat.z * flat.z);

		if (flyTicks > 0 && above > 3.5 && flatDist < 6.0 && ready(DIVE)) {
			diveTicks = 40;
			diveStartY = boss.getY();
			flyTicks = 0;
			boss.setNoGravity(false);
			sound(level, SoundEvents.BREEZE_JUMP, 1.0f, 0.7f);
			startCooldown(DIVE, 136);
			return;
		}
		if (d < 20.0 && sees(target) && ready(ORBITAL) && freshChoice(ORBITAL)) {
			orbitalPhase = 1;
			orbitalTicks = 14;
			orbitalStartY = boss.getY();
			flyTicks = 0;
			boss.setNoGravity(true);
			sound(level, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.4f, 0.6f);
			startCooldown(ORBITAL, lowHealth() ? 500 : 900);
			return;
		}
		if (d > 3.0 && d < 14.0 && sees(target) && ready(DASH)) {
			dashTicks = 8;
			dashDir = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(boss.position()).normalize();
			sound(level, SoundEvents.BREEZE_SHOOT, 0.9f, 1.4f);
			BatchCFx.flatRing(level, boss.position().add(0, 1.0, 0), 1.2, 16, ParticleTypes.CLOUD, 0.25);
			startCooldown(DASH, 51);
			return;
		}
		if (flyTicks <= 0 && groundedTicks <= 0 && d < 40.0 && ready(FLY)) {
			flyTicks = 200;
			boss.setNoGravity(true);
			boss.setDeltaMovement(boss.getDeltaMovement().x, 0.8, boss.getDeltaMovement().z);
			boss.hurtMarked = true;
			BatchCFx.flatRing(level, boss.position().add(0, 0.2, 0), 1.5, 24, ParticleTypes.CLOUD, 0.35);
			sound(level, SoundEvents.BREEZE_JUMP, 1.0f, 1.2f);
			startCooldown(FLY, 300);
		}
	}

	@Override
	public void serverTick(ServerLevel level, LivingEntity target) {
		if (groundedTicks > 0) {
			groundedTicks--;
		}
		if (rollTicks > 0) {
			rollTicks--;
		}
		if (orbitalPhase > 0) {
			driveOrbital(level, target);
			return;
		}
		if (dashTicks > 0) {
			dashTicks--;
			boss.setDeltaMovement(dashDir.scale(1.6));
			boss.hasImpulse = true;
			boss.hurtMarked = true;
			particles(level, ParticleTypes.CLOUD, boss.position().add(0, 1.0, 0), 2, 0.2);
			for (LivingEntity e : victimsAround(level, boss.position().add(dashDir).add(0, 1.0, 0), 1.9)) {
				if (e.invulnerableTime <= 0) {
					hurt(e, bossDamage(10.0f));
					knockAway(e, boss.position(), 1.4, 0.3);
					particles(level, ParticleTypes.CRIT, mid(e), 8, 0.3);
				}
			}
			return;
		}
		if (diveTicks > 0) {
			diveTicks--;
			boss.setDeltaMovement(boss.getDeltaMovement().x * 0.5, -2.8, boss.getDeltaMovement().z * 0.5);
			boss.hurtMarked = true;
			boss.fallDistance = 0.0f;
			if (boss.onGround() || diveTicks == 0) {
				diveTicks = 0;
				double h = Math.max(0.0, diveStartY - boss.getY());
				impact(level, Math.min(12.0, 3.0 + 0.18 * h), bossDamage((float) Math.min(80.0, 18.0 + 1.9 * h)), 1.0 + 0.05 * h);
			}
			return;
		}
		if (flyTicks > 0) {
			flyTicks--;
			boss.fallDistance = 0.0f;
			if (target != null) {
				// hold station 7 blocks over the target with a gentle weave
				double weave = Math.sin(boss.tickCount * 0.08) * 2.5;
				Vec3 side = new Vec3(-(target.getZ() - boss.getZ()), 0, target.getX() - boss.getX());
				side = side.lengthSqr() < 1.0e-4 ? Vec3.ZERO : side.normalize().scale(weave);
				Vec3 want = target.position().add(side).add(0, 7.0, 0);
				Vec3 to = want.subtract(boss.position());
				Vec3 v = to.lengthSqr() > 0.25 ? to.normalize().scale(Math.min(0.55, to.length() * 0.2)) : Vec3.ZERO;
				boss.setDeltaMovement(v);
				boss.hurtMarked = true;
				boss.getNavigation().stop();
			}
			if (boss.tickCount % 6 == 0) {
				particles(level, ParticleTypes.CLOUD, boss.position(), 2, 0.3);
			}
			if (flyTicks == 0) {
				boss.setNoGravity(false);
			}
		}
	}

	private void driveOrbital(ServerLevel level, LivingEntity target) {
		orbitalTicks--;
		boss.fallDistance = 0.0f;
		boss.getNavigation().stop();
		switch (orbitalPhase) {
			case 1 -> {
				boss.setDeltaMovement(0, 2.0, 0);
				boss.hurtMarked = true;
				particles(level, ParticleTypes.FIREWORK, boss.position(), 3, 0.2);
				if (orbitalTicks <= 0) {
					orbitalPhase = 2;
					orbitalTicks = 20;
					orbitalMark = target != null ? target.position() : boss.position().subtract(0, 20, 0);
					sound(level, SoundEvents.WARDEN_SONIC_BOOM, 1.2f, 1.2f);
				}
			}
			case 2 -> {
				boss.setDeltaMovement(Vec3.ZERO);
				boss.hurtMarked = true;
				if (target != null && orbitalTicks > 8) {
					orbitalMark = target.position(); // tracks for the first half of the hang, then commits
				}
				// the landing ring on the ground: get out of it
				ring(level, ParticleTypes.END_ROD, orbitalMark, 7.0, 28);
				if (orbitalTicks <= 0) {
					orbitalPhase = 3;
					orbitalTicks = 60;
					Vec3 to = orbitalMark.subtract(boss.position());
					Vec3 flat = new Vec3(to.x, 0, to.z);
					boss.setNoGravity(false);
					boss.setDeltaMovement(flat.scale(1.0 / 12.0).add(0, -3.4, 0));
					boss.hurtMarked = true;
				}
			}
			default -> {
				Vec3 to = orbitalMark.subtract(boss.position());
				boss.setDeltaMovement(to.x / 8.0, -3.4, to.z / 8.0);
				boss.hurtMarked = true;
				particles(level, ParticleTypes.FLAME, boss.position(), 4, 0.3);
				if (boss.onGround() || orbitalTicks <= 0) {
					orbitalPhase = 0;
					double fallen = Math.max(0.0, orbitalStartY + 28.0 - boss.getY());
					impact(level, 7.0, bossDamage((float) Math.min(72.0, 24.0 + 1.2 * fallen)), 1.8);
					sound(level, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 1.3f);
				}
			}
		}
	}

	private void impact(ServerLevel level, double radius, float damage, double knockback) {
		Vec3 c = boss.position();
		strikeArea(level, c, radius, damage, true, knockback, 0.5);
		particles(level, ParticleTypes.EXPLOSION_EMITTER, c, 1, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.2, c.z, 60, radius / 2, 0.3, radius / 2, 0.02);
		BatchCFx.flatRing(level, c.add(0, 0.2, 0), radius * 0.9, 32, ParticleTypes.CLOUD, 0.3);
		sound(level, SoundEvents.GENERIC_EXPLODE, 1.0f, 0.9f);
		boss.fallDistance = 0.0f;
	}

	@Override
	public float modifyIncomingDamage(DamageSource source, float amount) {
		// Barrel Roll: half a second of i-frames right after the dodge
		return rollTicks > 0 ? 0.0f : amount;
	}

	@Override
	public void onDamaged(ServerLevel level, DamageSource source, float amount) {
		boolean airborne = flyTicks > 0 || orbitalPhase == 2;
		if (airborne && (amount >= 6.0f || source.getDirectEntity() != source.getEntity())) {
			// shot down: grounded for 6 s
			flyTicks = 0;
			if (orbitalPhase == 2) {
				orbitalPhase = 0;
			}
			boss.setNoGravity(false);
			groundedTicks = 120;
			particles(level, ParticleTypes.CLOUD, boss.position(), 20, 0.6);
			sound(level, SoundEvents.BREEZE_HURT, 1.0f, 0.6f);
			return;
		}
		if (amount >= 4.0f && readyReactive(ROLL) && random().nextInt(3) == 0) {
			Vec3 side = boss.getLookAngle().cross(new Vec3(0, 1, 0)).normalize().scale(random().nextBoolean() ? 1.5 : -1.5);
			boss.setDeltaMovement(side.x, 0.2, side.z);
			boss.hurtMarked = true;
			rollTicks = 10;
			BatchCFx.flatRing(level, boss.position().add(0, 1.0, 0), 1.0, 12, ParticleTypes.CLOUD, 0.2);
			sound(level, SoundEvents.BREEZE_SLIDE, 1.0f, 1.2f);
			startCooldown(ROLL, 140);
		}
	}
}
