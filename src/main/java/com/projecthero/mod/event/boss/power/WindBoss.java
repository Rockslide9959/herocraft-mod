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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Wind Manipulation (v0.14.1 kit): Wind Blade, Wind Burst (the {@code tornado} key: a cone blast that also turns
 * projectiles away), Wind Push (a pure shove), Vacuum (drags victims in and steals their air) and Hurricane (a storm
 * around the boss that spins everyone and reverses arrows -- used against ranged attackers or once badly hurt).
 */
public class WindBoss extends BossPowerController {
	public static final String POWER_KEY = "power_24_wind_manipulation";

	private static final int BLADE = 0;
	private static final int BURST = 1;
	private static final int PUSH = 2;
	private static final int VACUUM = 3;
	private static final int HURRICANE = 4;
	private static final List<String> IDS = List.of("wind_blade", "tornado", "wind_push", "vacuum", "hurricane");

	private static final ParticleOptions AIR = BatchCFx.dust(0xE6F4FF, 1.2f);

	private int vacuumTicks;
	private Vec3 vacuumAt;
	private int hurricaneTicks;

	public WindBoss(EmpoweredZombie boss) {
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
		return ParticleTypes.CLOUD;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.WHITE;
	}

	private void deflect(ServerLevel level, Vec3 c, double r, Vec3 dir) {
		for (Projectile p : level.getEntitiesOfClass(Projectile.class, box(c, r), p -> p.getOwner() != boss)) {
			p.setDeltaMovement(dir.scale(Math.max(0.6, p.getDeltaMovement().length() * 0.5)));
			p.hurtMarked = true;
		}
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		double d = boss.distanceTo(target);
		boolean ranged = d > 14.0 || playersNear(level, 15.0).size() >= 3;
		if (hurricaneTicks <= 0 && d < 20.0 && ready(HURRICANE) && (lowHealth() || ranged)) {
			beginCast(level, target, HURRICANE, 1020, 3, SoundEvents.BREEZE_INHALE, ParticleTypes.CLOUD, t -> hurricaneTicks = 160);
			return;
		}
		if (d < 4.5 && ready(PUSH) && freshChoice(PUSH)) {
			face(target);
			Vec3 dir = flatDirTo(target.position());
			for (LivingEntity e : victimsAround(level, boss.position().add(dir.scale(2.5)).add(0, 1, 0), 4.5)) {
				knockAway(e, boss.position(), 2.6, 0.3);
			}
			deflect(level, boss.position().add(0, 1, 0), 6.0, dir);
			for (int i = 1; i <= 3; i++) {
				BatchCFx.ring(level, boss.getEyePosition().add(dir.scale(i * 1.4)), dir, 0.6 + i * 0.4, 16, AIR, 0.1);
			}
			sound(level, SoundEvents.BREEZE_SHOOT, 1.2f, 0.9f);
			startCooldown(PUSH, 90);
			return;
		}
		if (d < 7.0 && ready(BURST) && freshChoice(BURST)) {
			face(target);
			Vec3 eye = boss.getEyePosition();
			Vec3 dir = aimFromEyes(mid(target));
			for (LivingEntity e : victimsInCone(level, eye, dir, 6.5, 56.0)) {
				hurt(e, bossDamage(14.0f));
				knockAway(e, eye, 2.4, 0.35);
			}
			deflect(level, eye, 6.0, dir);
			BatchCFx.ringTrail(level, eye, dir, 6.0, 1.0, 0.4, 0.4, AIR);
			particles(level, ParticleTypes.GUST, eye.add(dir.scale(3.0)), 1, 0.0);
			sound(level, SoundEvents.BREEZE_SHOOT, 1.3f, 0.7f);
			startCooldown(BURST, 170);
			return;
		}
		if (vacuumTicks <= 0 && d < 14.0 && ready(VACUUM) && freshChoice(VACUUM)) {
			vacuumAt = boss.position().add(flatDirTo(target.position()).scale(4.0)).add(0, 1, 0);
			vacuumTicks = 40;
			sound(level, SoundEvents.BREEZE_INHALE, 1.2f, 0.8f);
			startCooldown(VACUUM, 240);
			return;
		}
		if (d < 26.0 && sees(target) && ready(BLADE)) {
			face(target);
			Vec3 from = boss.getEyePosition();
			Vec3 to = clipEnd(level, from, from.add(aimFromEyes(lead(target, 2.0)).scale(26.0)));
			particleLine(level, ParticleTypes.SWEEP_ATTACK, from, to, 0.4);
			particleLine(level, AIR, from, to, 2.0);
			for (LivingEntity e : strikeLine(level, from, to, 0.8, bossDamage(10.0f) + 1.0f)) {
				knockAway(e, from, 0.7, 0.1);
			}
			sound(level, SoundEvents.BREEZE_SHOOT, 1.0f, 1.4f);
			startCooldown(BLADE, 40);
		}
	}

	@Override
	public void serverTick(ServerLevel level, LivingEntity target) {
		if (vacuumTicks > 0) {
			vacuumTicks--;
			for (LivingEntity e : victimsAround(level, vacuumAt, 12.0)) {
				Vec3 to = vacuumAt.subtract(e.position());
				double dist = to.length();
				if (dist > 1.0) {
					e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(to.normalize().scale(Math.min(0.45, 0.06 * dist))));
					e.hurtMarked = true;
				}
				if (vacuumTicks % 10 == 0 && dist < 5.0) {
					hurt(e, bossDamage(2.5f) + 0.5f);
					control(e, MobEffects.MOVEMENT_SLOWDOWN, 20, 1);
					e.setAirSupply(Math.max(-20, e.getAirSupply() - 60));
				}
			}
			if (vacuumTicks % 2 == 0) {
				BatchCFx.inwardSpiral(level, vacuumAt, 4.5, 16, AIR, 0.3, vacuumTicks);
			}
		}
		if (hurricaneTicks > 0) {
			hurricaneTicks--;
			Vec3 c = boss.position();
			if (hurricaneTicks % 20 == 0) {
				for (LivingEntity e : victimsAround(level, c, 15.0)) {
					Vec3 out = e.position().subtract(c);
					out = new Vec3(out.x, 0, out.z);
					if (out.lengthSqr() < 1.0e-4) {
						continue;
					}
					Vec3 tangent = new Vec3(-out.z, 0, out.x).normalize().scale(0.8);
					fling(e, new Vec3(tangent.x, 0.35, tangent.z));
					hurt(e, bossDamage(10.0f) * 0.7f); // eight pulses: kept lighter than the player's 10/s
				}
				sound(level, SoundEvents.BREEZE_WHIRL, 1.0f, 0.6f);
			}
			if (hurricaneTicks % 5 == 0) {
				for (Projectile p : level.getEntitiesOfClass(Projectile.class, box(c, 15.0), p -> p.getOwner() != boss)) {
					p.setDeltaMovement(p.getDeltaMovement().scale(-0.5));
					p.hurtMarked = true;
				}
			}
			if (hurricaneTicks % 2 == 0) {
				double a = hurricaneTicks * 0.4;
				for (int r = 3; r <= 15; r += 3) {
					level.sendParticles(ParticleTypes.CLOUD, c.x + Math.cos(a + r) * r, c.y + 1 + r * 0.15, c.z + Math.sin(a + r) * r,
							1, 0.2, 0.2, 0.2, 0.0);
				}
			}
		}
	}
}
