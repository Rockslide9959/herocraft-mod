package com.projecthero.mod.event.boss.power;

import java.util.List;

import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.entity.EmpoweredZombie;
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
 * Gravity Manipulation (v0.14.1 kit): Gravity Push (with a splash), Gravity Crush (held on the target), Levitate
 * (Gravity Lift into the Shift slam), Heavy Ground (a zone that drags fliers down and cancels jumps -- the boss's
 * answer to anyone in the air) and -- once badly hurt -- Gravity Well, a black hole.
 */
public class GravityBoss extends BossPowerController {
	public static final String POWER_KEY = "power_23_gravity_manipulation";

	private static final int PUSH = 0;
	private static final int CRUSH = 1;
	private static final int LIFT = 2;
	private static final int HEAVY = 3;
	private static final int WELL = 4;
	private static final List<String> IDS = List.of("gravity_push", "gravity_crush", "levitate", "heavy_ground", "gravity_well");

	private LivingEntity crushed;
	private int crushTicks;
	private Vec3 heavyAt;
	private int heavyTicks;
	private Vec3 wellAt;
	private int wellTicks;

	public GravityBoss(EmpoweredZombie boss) {
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
		return ParticleTypes.REVERSE_PORTAL;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.PURPLE;
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		double d = boss.distanceTo(target);
		if (lowHealth() && wellTicks <= 0 && ready(WELL)) {
			beginCast(level, target, WELL, 1500, 4, SoundEvents.WARDEN_HEARTBEAT, ParticleTypes.REVERSE_PORTAL, t -> {
				wellAt = boss.getEyePosition().add(flatDirTo(t.position()).scale(5.0));
				wellTicks = 160;
			});
			return;
		}
		if (heavyTicks <= 0 && d < 24.0 && ready(HEAVY) && (airborne(target) || freshChoice(HEAVY))) {
			heavyAt = target.position();
			heavyTicks = 160;
			sound(level, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 0.4f);
			sound(level, SoundEvents.ANVIL_LAND, 0.8f, 0.5f);
			startCooldown(HEAVY, 340);
			return;
		}
		if (d < 20.0 && sees(target) && ready(LIFT) && freshChoice(LIFT)) {
			effect(target, MobEffects.LEVITATION, 30, 1);
			particles(level, ParticleTypes.PORTAL, mid(target), 20, 0.5);
			BatchDFx.distortion(level, mid(target), 0.6, 12);
			sound(level, SoundEvents.AMETHYST_BLOCK_HIT, 1.0f, 0.6f);
			LivingEntity t = target;
			schedule(3, () -> {
				if (isVictim(t)) {
					t.removeEffect(MobEffects.LEVITATION);
					fling(t, new Vec3(t.getDeltaMovement().x * 0.3, -1.6, t.getDeltaMovement().z * 0.3));
					hurt(t, bossDamage(17.0f));
					particles(level, BatchDFx.GRAVITY, mid(t), 20, 0.4);
					soundAt(level, t.position(), SoundEvents.ANVIL_LAND, 0.9f, 0.6f);
				}
			});
			startCooldown(LIFT, 210);
			return;
		}
		if (crushTicks <= 0 && d < 24.0 && sees(target) && ready(CRUSH) && freshChoice(CRUSH)) {
			crushed = target;
			crushTicks = 80;
			sound(level, SoundEvents.SCULK_CLICKING, 1.0f, 0.5f);
			startCooldown(CRUSH, 240);
			return;
		}
		if (d < 30.0 && sees(target) && ready(PUSH)) {
			face(target);
			Vec3 eye = boss.getEyePosition();
			Vec3 at = mid(target);
			particleLine(level, BatchDFx.GRAVITY, eye, at, 2.0);
			for (LivingEntity e : victimsAround(level, at, 4.0)) {
				hurt(e, bossDamage(12.0f));
				Vec3 away = e.position().subtract(eye);
				away = new Vec3(away.x, 0, away.z).normalize().scale(1.8);
				fling(e, new Vec3(away.x, 0.5, away.z));
			}
			particles(level, ParticleTypes.PORTAL, at, 30, 1.0);
			BatchDFx.distortion(level, at, 1.0, 16);
			sound(level, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 1.4f);
			startCooldown(PUSH, 50);
		}
	}

	@Override
	public void serverTick(ServerLevel level, LivingEntity target) {
		if (crushTicks > 0) {
			crushTicks--;
			if (crushed == null || !isVictim(crushed) || crushed.distanceTo(boss) > 32.0) {
				crushTicks = 0;
			} else {
				for (LivingEntity e : victimsAround(level, mid(crushed), 2.8)) {
					effect(e, MobEffects.MOVEMENT_SLOWDOWN, 20, 1);
					if (crushTicks % 20 == 0) {
						hurt(e, bossDamage(5.0f) + 1.0f);
					}
				}
				if (crushTicks % 3 == 0) {
					particles(level, ParticleTypes.SCULK_CHARGE_POP, mid(crushed), 4, 0.6);
					BatchDFx.distortion(level, mid(crushed), 0.8, 4);
				}
			}
		}
		if (heavyTicks > 0) {
			heavyTicks--;
			for (LivingEntity e : victimsAround(level, heavyAt.add(0, 3.0, 0), 8.0)) {
				Vec3 v = e.getDeltaMovement();
				if (!e.onGround() && v.y > -0.6) {
					e.setDeltaMovement(v.x * 0.8, -0.6, v.z * 0.8);
					e.hurtMarked = true;
				}
				if (heavyTicks % 10 == 0) {
					effect(e, MobEffects.JUMP, 12, -10);
					effect(e, MobEffects.MOVEMENT_SLOWDOWN, 12, 1);
				}
				if (heavyTicks % 20 == 0) {
					hurt(e, bossDamage(3.0f) + 0.5f);
				}
			}
			if (heavyTicks % 4 == 0) {
				BatchDFx.ring(level, heavyAt.add(0, 0.2, 0), 7.0, BatchDFx.GRAVITY, 28, heavyTicks * 0.05);
				level.sendParticles(ParticleTypes.FALLING_OBSIDIAN_TEAR, heavyAt.x, heavyAt.y + 6, heavyAt.z, 12, 4.0, 1.0, 4.0, 0.0);
			}
		}
		if (wellTicks > 0) {
			wellTicks--;
			for (LivingEntity e : victimsAround(level, wellAt, 16.0)) {
				Vec3 to = wellAt.subtract(e.position());
				if (to.lengthSqr() > 1.0) {
					e.setDeltaMovement(e.getDeltaMovement().scale(0.7).add(to.normalize().scale(0.3)));
					e.hurtMarked = true;
				}
				if (wellTicks % 20 == 0 && e.position().distanceTo(wellAt) < 6.0) {
					hurt(e, bossDamage(7.0f));
				}
			}
			if (wellTicks % 2 == 0) {
				particles(level, ParticleTypes.REVERSE_PORTAL, wellAt, 20, 1.0);
				BatchDFx.ring(level, wellAt, 2.2, ParticleTypes.SQUID_INK, 16, wellTicks * 0.2);
			}
			if (wellTicks % 20 == 0) {
				soundAt(level, wellAt, SoundEvents.WARDEN_HEARTBEAT, 3.0f, 0.3f);
			}
		}
	}
}
