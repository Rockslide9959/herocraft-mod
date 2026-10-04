package com.projecthero.mod.event.boss.power;

import java.util.List;

import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.hero.power.p19.ShadowManipulationHandlers;
import com.projecthero.mod.hero.revamp.d.BatchDFx;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Shadow Manipulation (v0.14.1 kit): the Shadow Bolt volley (five bolts fanned), Shadow Tendrils, Shadow Bind (roots
 * up to three victims), Shadow Step (out of the dark behind its target) and -- once badly hurt -- Total Darkness, a
 * zone that drags, slows and blinds. Like the player's, it is stronger in the dark: every damage value is scaled by
 * {@link ShadowManipulationHandlers#tier} (bright light 0.5x, darkness 1x, the Deep Dark 1.3x).
 */
public class ShadowManipulationBoss extends BossPowerController {
	public static final String POWER_KEY = "power_19_shadow_manipulation";

	private static final int BOLT = 0;
	private static final int TENDRILS = 1;
	private static final int BIND = 2;
	private static final int STEP = 3;
	private static final int DARKNESS = 4;
	private static final List<String> IDS = List.of("shadow_bolt", "shadow_tendrils", "shadow_bind", "shadow_step", "total_darkness");

	private int zoneTicks;
	private Vec3 zoneAt;

	public ShadowManipulationBoss(EmpoweredZombie boss) {
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
		return ParticleTypes.SQUID_INK;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.PURPLE;
	}

	/** The player's light tier, floored at 0.75 so a boss in a lit village is weaker but not toothless. */
	private float tier(ServerLevel level) {
		return Math.max(0.75f, ShadowManipulationHandlers.tier(level, boss.blockPosition()));
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		double d = boss.distanceTo(target);
		float tier = tier(level);
		if (lowHealth() && zoneTicks <= 0 && ready(DARKNESS)) {
			beginCast(level, target, DARKNESS, 1020, 4, SoundEvents.WARDEN_HEARTBEAT, ParticleTypes.SQUID_INK, t -> {
				zoneTicks = 220;
				zoneAt = boss.position();
				sound(level, SoundEvents.WARDEN_HEARTBEAT, 1.4f, 0.4f);
				particles(level, ParticleTypes.SQUID_INK, zoneAt.add(0, 1, 0), 60, 3.0);
			});
			return;
		}
		if (d > 6.0 && d < 30.0 && ready(STEP) && freshChoice(STEP)) {
			Vec3 look = target.getLookAngle();
			Vec3 back = new Vec3(look.x, 0, look.z);
			back = back.lengthSqr() < 1.0e-4 ? flatDirTo(target.position()) : back.normalize();
			Vec3 spot = safeSpotNear(level, target.position().subtract(back.scale(1.6 + target.getBbWidth())));
			if (spot != null) {
				puddle(level, boss.position());
				blinkTo(level, spot, ParticleTypes.SQUID_INK, SoundEvents.ENDERMAN_TELEPORT);
				puddle(level, spot);
				face(target);
				for (LivingEntity e : victimsAround(level, spot.add(0, 1, 0), 1.6 + boss.getBbWidth())) {
					hurt(e, bossDamage(6.0f) * tier + 1.0f);
					knockAway(e, spot, 0.6, 0.1);
					control(e, MobEffects.BLINDNESS, 80, 0);
				}
				startCooldown(STEP, 102);
				return;
			}
		}
		if (d < 8.0 && ready(TENDRILS) && freshChoice(TENDRILS)) {
			face(target);
			Vec3 c = boss.position().add(flatDirTo(target.position()).scale(Math.min(4.0, d)));
			for (LivingEntity e : victimsAround(level, c.add(0, 0.5, 0), 4.0)) {
				hurt(e, bossDamage(18.0f) * tier);
				control(e, MobEffects.BLINDNESS, 160, 0);
			}
			for (int i = 0; i < 6; i++) {
				double a = i * Math.PI / 3.0;
				Vec3 base = c.add(Math.cos(a) * 2.0, 0, Math.sin(a) * 2.0);
				for (int h = 0; h < 6; h++) {
					Vec3 p = base.add(Math.cos(a + h * 0.6) * 0.4, h * 0.4, Math.sin(a + h * 0.6) * 0.4);
					level.sendParticles(BatchDFx.SHADOW, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
				}
			}
			particles(level, ParticleTypes.SQUID_INK, c.add(0, 0.5, 0), 20, 1.5);
			sound(level, SoundEvents.WARDEN_ATTACK_IMPACT, 1.0f, 0.8f);
			startCooldown(TENDRILS, 136);
			return;
		}
		if (d < 24.0 && sees(target) && ready(BIND) && freshChoice(BIND)) {
			int bound = 0;
			for (LivingEntity e : victimsAround(level, mid(target), 2.5)) {
				if (bound++ >= 3) {
					break;
				}
				hurt(e, bossDamage(9.0f) * tier);
				control(e, MobEffects.MOVEMENT_SLOWDOWN, (int) Math.max(20, 100 * tier), 9);
				control(e, MobEffects.JUMP, (int) Math.max(20, 100 * tier), -10);
				control(e, MobEffects.WEAKNESS, 100, 1);
				control(e, MobEffects.BLINDNESS, 60, 0);
				BatchDFx.tether(level, boss.getEyePosition(), mid(e), BatchDFx.SHADOW, 20);
				BatchDFx.ring(level, e.position().add(0, 0.3, 0), 0.8, BatchDFx.SHADOW, 12, 0);
			}
			sound(level, SoundEvents.CHAIN_PLACE, 1.0f, 0.6f);
			sound(level, SoundEvents.SCULK_CLICKING, 1.0f, 0.6f);
			startCooldown(BIND, 200);
			return;
		}
		if (d < 24.0 && sees(target) && ready(BOLT)) {
			beginRangedCast(level, target, BOLT, 120, SoundEvents.SCULK_CLICKING, BatchDFx.SHADOW_EYE, t -> {
				Vec3 from = boss.getEyePosition();
				Vec3 dir = aimFromEyes(mid(t));
				double baseYaw = Math.atan2(dir.z, dir.x);
				for (int i = -2; i <= 2; i++) {
					double yaw = baseYaw + Math.toRadians(10.0 * i);
					Vec3 dd = new Vec3(Math.cos(yaw), dir.y, Math.sin(yaw)).normalize();
					Vec3 to = clipEnd(level, from, from.add(dd.scale(24.0)));
					particleLine(level, ParticleTypes.SQUID_INK, from, to, 1.5);
					particleLine(level, BatchDFx.SHADOW_EYE, from, to, 0.6);
					for (LivingEntity e : strikeLine(level, from, to, 0.6, bossDamage(13.0f) * tier)) {
						control(e, MobEffects.BLINDNESS, 80, 0);
					}
				}
				sound(level, SoundEvents.SCULK_CLICKING, 1.2f, 0.5f);
			});
		}
	}

	@Override
	public void serverTick(ServerLevel level, LivingEntity target) {
		if (zoneTicks <= 0) {
			return;
		}
		zoneTicks--;
		double r = 12.0; // the player's reaches 20
		for (LivingEntity e : victimsAround(level, zoneAt, r)) {
			if (e.getDeltaMovement().y > -0.05) {
				e.setDeltaMovement(e.getDeltaMovement().x, -0.05, e.getDeltaMovement().z);
			}
			effect(e, MobEffects.MOVEMENT_SLOWDOWN, 20, 2);
			effect(e, MobEffects.BLINDNESS, 30, 0);
			if (zoneTicks % 20 == 0) {
				hurt(e, bossDamage(10.0f));
			}
		}
		if (zoneTicks % 4 == 0) {
			level.sendParticles(BatchDFx.SHADOW, zoneAt.x, zoneAt.y + 1, zoneAt.z, 30, r * 0.45, 1.0, r * 0.45, 0.0);
			BatchDFx.ring(level, zoneAt.add(0, 0.2, 0), r, ParticleTypes.SQUID_INK, 32, zoneTicks * 0.05);
		}
	}

	private void puddle(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 0.1, at.z, 20, 0.6, 0.05, 0.6, 0.02);
		level.sendParticles(BatchDFx.SHADOW, at.x, at.y + 0.1, at.z, 16, 0.6, 0.05, 0.6, 0.0);
	}
}
