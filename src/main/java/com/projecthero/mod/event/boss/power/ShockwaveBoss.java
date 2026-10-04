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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Shockwave Manipulation (v0.14.1 kit): Shockwave Punch (a cone), Ground Wave (a wave that travels along the ground),
 * Aftershock (a marked eruption under the target), Kinetic Parry (a melee hit is negated and thrown back) and -- once
 * badly hurt -- Kinetic Detonation. It stores kinetic charge from the hits it takes, which powers its moves up to x1.5
 * as the player's does.
 */
public class ShockwaveBoss extends BossPowerController {
	public static final String POWER_KEY = "power_21_shockwave_manipulation";

	private static final int PUNCH = 0;
	private static final int WAVE = 1;
	private static final int AFTERSHOCK = 2;
	private static final int PARRY = 3;
	private static final int DETONATION = 4;
	private static final List<String> IDS = List.of("shockwave_punch", "ground_wave", "aftershock", "kinetic_parry", "kinetic_detonation");

	private static final ParticleOptions DISTORT = BatchCFx.dust(0xE8ECF2, 1.2f);
	private static final ParticleOptions KINETIC = BatchCFx.dust(0xFFB347, 1.2f);

	private float kinetic;
	private int parryTicks;

	public ShockwaveBoss(EmpoweredZombie boss) {
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
		return 6.0;
	}

	@Override
	public ParticleOptions auraParticle() {
		return ParticleTypes.POOF;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.WHITE;
	}

	/** Spend up to {@code max} kinetic for up to +{@code bonus} damage, as the player's {@code boost}. */
	private float boost(float max, float bonus) {
		float spend = Math.min(max, kinetic);
		kinetic -= spend;
		return 1.0f + bonus * (spend / max);
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		double d = boss.distanceTo(target);
		if (lowHealth() && d < 16.0 && ready(DETONATION)) {
			beginCast(level, target, DETONATION, 1200, 4, SoundEvents.WARDEN_SONIC_CHARGE, KINETIC, t -> {
				Vec3 c = boss.position();
				for (LivingEntity e : strikeArea(level, c, 14.0, bossDamage(72.0f), true, 3.0, 0.8)) {
					control(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
				}
				for (int i = 1; i <= 5; i++) {
					BatchCFx.flatRing(level, c.add(0, 0.3, 0), i * 2.8, 40, DISTORT, 0.3);
				}
				particles(level, ParticleTypes.EXPLOSION_EMITTER, c, 1, 0.0);
				sound(level, SoundEvents.GENERIC_EXPLODE, 1.6f, 0.4f);
			});
			return;
		}
		if (parryTicks <= 0 && d < 4.5 && ready(PARRY) && freshChoice(PARRY)) {
			parryTicks = 30;
			BatchCFx.flatRing(level, boss.position().add(0, 1.0, 0), 1.2, 16, DISTORT, 0.0);
			sound(level, SoundEvents.SHIELD_BLOCK, 0.8f, 1.6f);
			startCooldown(PARRY, 120);
			return;
		}
		if (d < 7.0 && ready(PUNCH) && freshChoice(PUNCH)) {
			face(target);
			float mult = boost(10.0f, 0.5f);
			Vec3 eye = boss.getEyePosition();
			Vec3 dir = aimFromEyes(mid(target));
			for (LivingEntity e : victimsInCone(level, eye, dir, 7.0, 56.0)) {
				hurt(e, bossDamage(12.0f) * mult);
				knockAway(e, eye, 1.3 * mult, 0.25);
			}
			BatchCFx.ringTrail(level, eye, dir, 6.0, 1.0, 0.3, 0.3, DISTORT);
			particles(level, ParticleTypes.GUST, eye.add(dir.scale(2.0)), 1, 0.0);
			sound(level, SoundEvents.WIND_CHARGE_BURST, 1.0f, 1.2f);
			startCooldown(PUNCH, 50);
			return;
		}
		if (d < 18.0 && ready(AFTERSHOCK) && freshChoice(AFTERSHOCK)) {
			Vec3 at = target.position();
			float mult = boost(10.0f, 0.5f);
			task(level, age -> {
				double frac = 1.0 - age / 16.0;
				if (age % 2 == 0) {
					BatchCFx.flatRing(level, at.add(0, 0.2, 0), 0.6 + 4.0 * frac, 20, KINETIC, 0.0);
				}
				if (age >= 16) {
					for (LivingEntity e : victimsAround(level, at.add(0, 0.5, 0), 5.0)) {
						hurt(e, bossDamage(14.0f) * mult);
						knockAway(e, at, 0.8, 0.75);
					}
					for (int i = 1; i <= 3; i++) {
						BatchCFx.flatRing(level, at.add(0, 0.2, 0), i * 1.6, 24, DISTORT, 0.3);
					}
					particles(level, ParticleTypes.GUST_EMITTER_SMALL, at, 1, 0.0);
					soundAt(level, at, SoundEvents.WIND_CHARGE_BURST, 1.4f, 0.6f);
					return false;
				}
				return true;
			});
			startCooldown(AFTERSHOCK, 160);
			return;
		}
		if (d < 22.0 && ready(WAVE)) {
			Vec3 dir = flatDirTo(target.position());
			float mult = boost(15.0f, 0.5f);
			Set<LivingEntity> hit = new HashSet<>();
			Vec3 start = boss.position();
			task(level, age -> {
				Vec3 p = start.add(dir.scale(1.5 + age * 1.3));
				BatchCFx.flatRing(level, p.add(0, 0.2, 0), 1.2, 12, DISTORT, 0.1);
				level.sendParticles(ParticleTypes.POOF, p.x, p.y + 0.2, p.z, 3, 0.4, 0.1, 0.4, 0.02);
				for (LivingEntity e : victimsAround(level, p.add(0, 0.5, 0), 2.2)) {
					if (hit.add(e)) {
						hurt(e, bossDamage(16.0f) * mult);
						knockAway(e, p.subtract(dir), 1.4, 0.35);
					}
				}
				return age < 16;
			});
			sound(level, SoundEvents.WIND_CHARGE_BURST, 1.0f, 0.9f);
			startCooldown(WAVE, 100);
		}
	}

	@Override
	public void serverTick(ServerLevel level, LivingEntity target) {
		if (parryTicks > 0) {
			parryTicks--;
		}
	}

	@Override
	public float modifyIncomingDamage(DamageSource source, float amount) {
		// Kinetic Parry: a melee hit inside the window is negated, stored twice over, and thrown back
		if (parryTicks > 0 && source.getDirectEntity() == source.getEntity() && source.getEntity() instanceof LivingEntity attacker
				&& boss.level() instanceof ServerLevel level) {
			parryTicks = 0;
			kinetic = Math.min(115.0f, kinetic + amount * 2.0f);
			hurt(attacker, 5.0f);
			knockAway(attacker, boss.position(), 2.5, 0.3);
			control(attacker, MobEffects.MOVEMENT_SLOWDOWN, 30, 3);
			particles(level, ParticleTypes.GUST, boss.position().add(0, 1.2, 0), 1, 0.0);
			sound(level, SoundEvents.SHIELD_BLOCK, 1.0f, 1.0f);
			sound(level, SoundEvents.WIND_CHARGE_BURST, 1.0f, 1.2f);
			return 0.0f;
		}
		kinetic = Math.min(115.0f, kinetic + amount * 0.5f); // the passive: blocked force is stored
		return amount * 0.85f;
	}
}
