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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Super Speed (v0.14.5 kit): Rapid Assault (four i-frame-ignoring punches), Blitz (zips beside the target, a heavy hit
 * and a shockwave), Momentum Dash (a sidestep out of a hit or around the target), Speed Vortex (Shift+Blitz: a cyclone
 * that drags everyone in and flings them out) and -- once badly hurt -- Overdrive. Time Slow and Speed Mode are
 * player-only (a server tick rate and a movement mode) and are not used.
 */
public class SuperSpeedBoss extends BossPowerController {
	public static final String POWER_KEY = "power_04_super_speed";

	private static final int ASSAULT = 0;
	private static final int BLITZ = 1;
	private static final int DASH = 2;
	private static final int OVERDRIVE = 3;
	private static final int VORTEX = 4;
	private static final List<String> IDS = List.of("rapid_assault", "blitz", "momentum_dash", "overdrive", "blitz");

	private static final ParticleOptions STREAK = BatchCFx.dust(0xFFD23A, 1.0f);

	private int overdriveTicks;
	private int vortexTicks;

	public SuperSpeedBoss(EmpoweredZombie boss) {
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
		return ParticleTypes.ELECTRIC_SPARK;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.YELLOW;
	}

	private float mult() {
		return overdriveTicks > 0 ? 1.5f : 1.0f;
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		if (vortexTicks > 0) {
			return;
		}
		double d = boss.distanceTo(target);
		if (lowHealth() && ready(OVERDRIVE)) {
			overdriveTicks = 400;
			boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 2, false, true));
			boss.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 400, 2, false, true));
			BatchCFx.flatRing(level, boss.position().add(0, 1.0, 0), 1.5, 30, ParticleTypes.ELECTRIC_SPARK, 0.3);
			sound(level, SoundEvents.WARDEN_SONIC_BOOM, 1.2f, 1.5f);
			startCooldown(OVERDRIVE, 850);
			return;
		}
		if (d <= 4.5 && ready(ASSAULT) && freshChoice(ASSAULT)) {
			rapidAssault(level, target);
			startCooldown(ASSAULT, 45);
			return;
		}
		if (d <= 8.0 && playersNear(level, 8.0).size() + (d <= 6.0 ? 1 : 0) >= 2 && ready(VORTEX)) {
			vortexTicks = 50;
			sound(level, SoundEvents.ELYTRA_FLYING, 1.0f, 1.6f);
			startCooldown(VORTEX, 280);
			return;
		}
		if (d > 4.0 && d < 24.0 && sees(target) && ready(BLITZ)) {
			blitz(level, target);
			startCooldown(BLITZ, 100);
			return;
		}
		if (d > 4.0 && d < 16.0 && ready(DASH)) {
			// Momentum Dash around to the target's flank
			Vec3 to = target.position().subtract(boss.position());
			Vec3 side = new Vec3(-to.z, 0, to.x).normalize().scale(random().nextBoolean() ? 1.0 : -1.0);
			Vec3 dir = to.normalize().add(side.scale(0.6)).normalize();
			afterImage(level, boss.position(), boss.position().add(dir.scale(6.0)));
			dash(dir, 1.7 * mult(), 0.15);
			sound(level, SoundEvents.BREEZE_SHOOT, 0.6f, 1.8f);
			startCooldown(DASH, 34);
		}
	}

	@Override
	public void serverTick(ServerLevel level, LivingEntity target) {
		if (overdriveTicks > 0) {
			overdriveTicks--;
			if (overdriveTicks % 3 == 0) {
				particles(level, ParticleTypes.ELECTRIC_SPARK, boss.position().add(0, 1.0, 0), 3, 0.4);
			}
		}
		if (vortexTicks > 0) {
			vortexTicks--;
			Vec3 c = boss.position();
			boss.getNavigation().stop();
			for (LivingEntity e : victimsAround(level, c, 8.0)) {
				Vec3 to = c.subtract(e.position());
				Vec3 flat = new Vec3(to.x, 0, to.z);
				double dist = flat.length();
				if (dist < 1.0e-3) {
					continue;
				}
				Vec3 in = flat.normalize();
				Vec3 swirl = new Vec3(-in.z, 0, in.x).scale(0.4);
				Vec3 pull = dist > 3.0 ? in.scale(0.24) : in.scale(-0.08);
				double lift = e.getY() < c.y + 2.5 ? 0.12 : 0.0;
				e.setDeltaMovement(pull.add(swirl).add(0, lift, 0));
				e.hurtMarked = true;
				if (vortexTicks % 10 == 0) {
					hurtFresh(e, bossDamage(3.0f) * mult());
				}
			}
			double a = boss.tickCount * 0.6;
			for (int arm = 0; arm < 2; arm++) {
				double ang = a + arm * Math.PI;
				double r = 2.0 + (vortexTicks % 10) * 0.5;
				level.sendParticles(ParticleTypes.CLOUD, c.x + Math.cos(ang) * r, c.y + 1.0, c.z + Math.sin(ang) * r, 1, 0, 0.3, 0, 0.0);
			}
			if (vortexTicks == 0) {
				for (LivingEntity e : victimsAround(level, c, 8.0)) {
					hurtFresh(e, bossDamage(8.0f) * mult());
					knockAway(e, c, 1.6, 0.9);
				}
				particles(level, ParticleTypes.EXPLOSION, c.add(0, 1.0, 0), 3, 1.0);
				BatchCFx.flatRing(level, c.add(0, 0.5, 0), 3.0, 36, ParticleTypes.CLOUD, 0.6);
				sound(level, SoundEvents.GENERIC_EXPLODE, 0.8f, 1.5f);
			}
		}
	}

	private void rapidAssault(ServerLevel level, LivingEntity target) {
		face(target);
		Vec3 c = boss.getEyePosition().add(boss.getLookAngle().scale(1.5));
		for (LivingEntity e : victimsAround(level, c, 3.5)) {
			for (int i = 0; i < 4; i++) {
				hurtFresh(e, bossDamage(5.0f) * mult());
			}
			knockAway(e, boss.position(), 0.35, 0.1);
			particles(level, ParticleTypes.CRIT, mid(e), 12, 0.3);
			particles(level, ParticleTypes.ELECTRIC_SPARK, mid(e), 4, 0.3);
		}
		sound(level, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 1.8f);
	}

	/** Blitz: zip to the target's side, one heavy hit, a shockwave around it. */
	private void blitz(ServerLevel level, LivingEntity target) {
		Vec3 from = boss.position();
		Vec3 toTarget = target.position().subtract(from);
		Vec3 flat = new Vec3(toTarget.x, 0, toTarget.z).normalize();
		Vec3 spot = null;
		for (Vec3 offset : new Vec3[] { flat.scale(-1.6), new Vec3(-flat.z, 0, flat.x).scale(1.6),
				new Vec3(flat.z, 0, -flat.x).scale(1.6), flat.scale(1.6) }) {
			spot = safeSpotNear(level, target.position().add(offset));
			if (spot != null) {
				break;
			}
		}
		if (spot != null) {
			afterImage(level, from, spot);
			boss.teleportTo(spot.x, spot.y, spot.z);
			boss.getNavigation().stop();
			boss.fallDistance = 0.0f;
		}
		face(target);
		if (boss.distanceTo(target) <= 4.0) {
			hurtFresh(target, bossDamage(20.0f) * mult());
			knockAway(target, boss.position(), 2.0, 0.35);
			particles(level, ParticleTypes.SWEEP_ATTACK, mid(target), 1, 0.0);
			particles(level, ParticleTypes.CRIT, mid(target), 14, 0.3);
		}
		Vec3 c = target.position();
		for (LivingEntity e : victimsAround(level, c, 4.0)) {
			if (e != target) {
				hurt(e, bossDamage(10.0f) * mult());
				knockAway(e, c, 1.4, 0.25);
			}
		}
		BatchCFx.flatRing(level, c.add(0, 0.3, 0), 2.0, 16, ParticleTypes.ELECTRIC_SPARK, 0.3);
		particles(level, ParticleTypes.EXPLOSION, c.add(0, 0.5, 0), 1, 0.0);
		sound(level, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 1.6f);
		sound(level, SoundEvents.WARDEN_SONIC_BOOM, 0.5f, 1.9f);
	}

	private void afterImage(ServerLevel level, Vec3 from, Vec3 to) {
		particleLine(level, STREAK, from.add(0, 1.0, 0), to.add(0, 1.0, 0), 2.0);
		particleLine(level, ParticleTypes.ELECTRIC_SPARK, from.add(0, 0.6, 0), to.add(0, 0.6, 0), 1.0);
	}

	@Override
	public void onDamaged(ServerLevel level, DamageSource source, float amount) {
		// Momentum Dash as a reaction: blur sideways out of a big hit
		if (amount >= 8.0f && readyReactive(DASH) && source.getEntity() != null) {
			Vec3 away = boss.position().subtract(source.getEntity().position());
			Vec3 side = new Vec3(-away.z, 0, away.x).normalize().scale(random().nextBoolean() ? 1.0 : -1.0);
			afterImage(level, boss.position(), boss.position().add(side.scale(5.0)));
			dash(side, 1.5, 0.2);
			startCooldown(DASH, 60);
		}
	}
}
