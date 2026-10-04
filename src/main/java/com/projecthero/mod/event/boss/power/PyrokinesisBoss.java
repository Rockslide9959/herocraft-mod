package com.projecthero.mod.event.boss.power;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.hero.revamp.BatchCFx;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Pyrokinesis (v0.14.1 kit): Fireball, Fire Whip (an S-curve lash that drags victims in), Flamethrower (a held cone),
 * Heat Wave (an expanding ring scaled by heat) and -- once badly hurt -- Inferno, a slow, huge fireball. Like the
 * player's it builds heat with every move (damage x(1 + 0.4 heat)); past 75 heat its flames burn blue.
 */
public class PyrokinesisBoss extends BossPowerController {
	public static final String POWER_KEY = "power_08_pyrokinesis";

	private static final int FIREBALL = 0;
	private static final int WHIP = 1;
	private static final int FLAMETHROWER = 2;
	private static final int HEAT_WAVE = 3;
	private static final int INFERNO = 4;
	private static final List<String> IDS = List.of("fireball", "fire_whip", "flamethrower", "heat_wave", "inferno");

	private float heat;
	private int throwerTicks;

	public PyrokinesisBoss(EmpoweredZombie boss) {
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
		return 9.0;
	}

	@Override
	public ParticleOptions auraParticle() {
		return blue() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.RED;
	}

	private boolean blue() {
		return heat >= 75.0f;
	}

	private ParticleOptions flame() {
		return blue() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
	}

	private float heatMult() {
		return 1.0f + 0.4f * heat / 100.0f;
	}

	private void burn(LivingEntity e, float dmg, int seconds) {
		hurt(e, dmg * heatMult());
		ignite(e, seconds);
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		if (throwerTicks > 0) {
			return;
		}
		double d = boss.distanceTo(target);
		if (lowHealth() && d < 30.0 && sees(target) && ready(INFERNO)) {
			beginCast(level, target, INFERNO, 940, 3, SoundEvents.FIRE_AMBIENT, flame(), t -> inferno(level, t));
			heat = Math.min(100.0f, heat + 30.0f);
			return;
		}
		if (d < 7.0 && heat >= 10.0f && ready(HEAT_WAVE) && freshChoice(HEAT_WAVE)) {
			heatWave(level);
			startCooldown(HEAT_WAVE, 200);
			return;
		}
		if (d < 6.5 && ready(FLAMETHROWER) && freshChoice(FLAMETHROWER)) {
			throwerTicks = 40;
			startCooldown(FLAMETHROWER, 120);
			return;
		}
		if (d < 9.0 && sees(target) && ready(WHIP) && freshChoice(WHIP)) {
			fireWhip(level, target);
			heat = Math.min(100.0f, heat + 7.0f);
			startCooldown(WHIP, 60);
			return;
		}
		if (d > 3.0 && d < 30.0 && sees(target) && ready(FIREBALL)) {
			face(target);
			Vec3 from = boss.getEyePosition().add(boss.getLookAngle());
			float dmg = bossDamage(14.0f);
			projectile(level, from, aimFromEyes(lead(target, 8.0)), 1.1, 40, 0.7, flame(), 5, (at, hit) -> {
				for (LivingEntity e : victimsAround(level, at, 2.0)) {
					burn(e, e == hit ? dmg : dmg * 0.5f, 5);
				}
				particles(level, ParticleTypes.EXPLOSION, at, 1, 0.0);
				particles(level, flame(), at, 20, 0.5);
				soundAt(level, at, SoundEvents.GENERIC_EXPLODE, 0.6f, 1.4f);
			});
			sound(level, SoundEvents.BLAZE_SHOOT, 1.0f, 0.9f);
			heat = Math.min(100.0f, heat + 9.0f);
			startCooldown(FIREBALL, 40);
		}
	}

	@Override
	public void serverTick(ServerLevel level, LivingEntity target) {
		if (throwerTicks > 0) {
			throwerTicks--;
			boss.getNavigation().stop();
			if (target != null) {
				face(target);
			}
			heat = Math.min(100.0f, heat + 0.35f);
			Vec3 eye = boss.getEyePosition();
			Vec3 look = boss.getLookAngle();
			for (int i = 1; i <= 14; i++) {
				double d = i * 0.5;
				Vec3 p = eye.add(look.scale(d));
				level.sendParticles(flame(), p.x, p.y, p.z, 1, 0.1 * d, 0.1 * d, 0.1 * d, 0.01);
			}
			if (throwerTicks % 5 == 0) {
				for (LivingEntity e : victimsInCone(level, eye, look, 7.0, 37.0)) {
					burn(e, bossDamage(7.0f), 4);
				}
				sound(level, SoundEvents.FIRE_AMBIENT, 1.0f, 1.2f);
			}
		} else if (heat > 0) {
			heat = Math.max(0.0f, heat - (boss.isInWaterOrRain() ? 0.6f : 0.2f));
		}
	}

	/** A 9-block lash that uncurls over 4 ticks; each victim touched burns once and is dragged toward the boss. */
	private void fireWhip(ServerLevel level, LivingEntity target) {
		face(target);
		Vec3 origin = boss.getEyePosition().subtract(0, 0.4, 0);
		Vec3 dir = aimFromEyes(mid(target));
		Vec3 side = dir.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 sideF = side;
		Set<LivingEntity> hit = new HashSet<>();
		sound(level, SoundEvents.BLAZE_SHOOT, 0.8f, 1.5f);
		task(level, age -> {
			double len = 9.0 * Math.min(1.0, (age + 1) / 4.0);
			Vec3 prev = origin;
			for (int s = 1; s <= 12; s++) {
				double t = s / 12.0;
				Vec3 p = origin.add(dir.scale(len * t)).add(sideF.scale(Math.sin(t * Math.PI * 2.0 + age) * 0.6 * (1 - t)));
				level.sendParticles(flame(), p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.0);
				for (LivingEntity e : victimsOnSegment(level, prev, p, 0.9)) {
					if (hit.add(e)) {
						burn(e, bossDamage(11.0f), 4);
						Vec3 pull = boss.position().subtract(e.position()).normalize().scale(0.9);
						fling(e, new Vec3(pull.x, 0.3, pull.z));
					}
				}
				prev = p;
			}
			return age < 5;
		});
	}

	private void heatWave(ServerLevel level) {
		float frac = heat / 100.0f;
		double radius = 4.0 + 6.0 * frac;
		float dmg = bossDamage(8.0f + 24.0f * frac);
		Vec3 c = boss.position();
		for (LivingEntity e : victimsAround(level, c, radius)) {
			hurt(e, dmg);
			ignite(e, 5);
			knockAway(e, c, 1.0 + frac, 0.35);
		}
		task(level, age -> {
			double r = 1.0 + 1.6 * age;
			BatchCFx.flatRing(level, c.add(0, 0.3, 0), r, (int) (r * 10), flame(), 0.05);
			return r < radius;
		});
		particles(level, ParticleTypes.LAVA, c.add(0, 1, 0), 20, 0.8);
		sound(level, SoundEvents.BLAZE_SHOOT, 1.6f, 0.4f);
		sound(level, SoundEvents.FIRE_EXTINGUISH, 1.2f, 0.6f);
		heat = 0.0f;
	}

	private void inferno(ServerLevel level, LivingEntity target) {
		Vec3 from = boss.getEyePosition().add(0, 1.2, 0);
		float dmg = bossDamage(14.0f);
		projectile(level, from, aimFromEyes(lead(target, 10.0)), 0.7, 80, 1.4, ParticleTypes.LARGE_SMOKE, 3, (at, hit) -> {
			for (LivingEntity e : victimsAround(level, at, 5.5)) {
				burn(e, dmg * 1.4f, 7);
				knockAway(e, at, 1.2, 0.4);
			}
			particles(level, ParticleTypes.EXPLOSION_EMITTER, at, 1, 0.0);
			particles(level, flame(), at, 80, 2.0);
			soundAt(level, at, SoundEvents.GENERIC_EXPLODE, 1.4f, 0.7f);
		});
		particles(level, flame(), from, 40, 0.6);
		sound(level, SoundEvents.BLAZE_SHOOT, 1.6f, 0.4f);
		sound(level, SoundEvents.FIRECHARGE_USE, 1.5f, 0.4f);
	}
}
