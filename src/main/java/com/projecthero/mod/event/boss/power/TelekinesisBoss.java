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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Telekinesis (v0.14.1 kit): Force Push (with a splash), Telekinetic Grab (the Force Pull: yanks a far target in and
 * hurls it), Mind Lock (lifts the target off the ground and holds it) and Psychic Detonation (a vacuum that drags
 * everyone in for two seconds, then a radial blast). Psionic purple FX ({@link BatchDFx#PSI}).
 */
public class TelekinesisBoss extends BossPowerController {
	public static final String POWER_KEY = "power_10_telekinesis";

	private static final int PUSH = 0;
	private static final int GRAB = 1;
	private static final int LOCK = 2;
	private static final int DETONATION = 3;
	private static final List<String> IDS = List.of("force_push", "telekinetic_grab", "mind_lock", "telekinetic_explosion");

	private LivingEntity locked;
	private int lockTicks;
	private Vec3 lockAt;
	private int detonationTicks;

	public TelekinesisBoss(EmpoweredZombie boss) {
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
		return 10.0;
	}

	@Override
	public ParticleOptions auraParticle() {
		return BatchDFx.PSI;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.PURPLE;
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		if (detonationTicks > 0) {
			return;
		}
		double d = boss.distanceTo(target);
		if (d < 16.0 && ready(DETONATION) && (lowHealth() || playersNear(level, 16.0).size() >= 2 || freshChoice(DETONATION))) {
			detonationTicks = 40;
			sound(level, SoundEvents.WARDEN_SONIC_CHARGE, 1.2f, 0.8f);
			startCooldown(DETONATION, 1000);
			return;
		}
		if (lockTicks <= 0 && d < 24.0 && sees(target) && ready(LOCK) && freshChoice(LOCK)) {
			locked = target;
			lockTicks = target instanceof Player ? 40 : 80; // the player's 80 t, halved on a player like all hard CC
			lockAt = target.position().add(0, 1.5, 0);
			sound(level, SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 1.0f, 1.0f);
			startCooldown(LOCK, 240);
			return;
		}
		if (d > 10.0 && d < 40.0 && sees(target) && ready(GRAB)) {
			// Force Pull: yank toward the boss
			pullToward(target, boss.position(), 1.6, 0.3);
			BatchDFx.tether(level, boss.getEyePosition(), mid(target), BatchDFx.PSI, 24);
			sound(level, SoundEvents.ILLUSIONER_CAST_SPELL, 1.0f, 1.2f);
			startCooldown(GRAB, 120);
			return;
		}
		if (d < 30.0 && sees(target) && ready(PUSH)) {
			face(target);
			Vec3 eye = boss.getEyePosition();
			Vec3 at = mid(target);
			particleLine(level, ParticleTypes.SCULK_SOUL, eye, at, 1.0);
			particleLine(level, BatchDFx.PSI, eye, at, 2.0);
			for (LivingEntity e : victimsAround(level, at, 3.0)) {
				hurt(e, bossDamage(12.0f));
				Vec3 away = e.position().subtract(eye);
				away = new Vec3(away.x, 0, away.z).normalize().scale(1.6);
				fling(e, new Vec3(away.x, 0.5, away.z));
			}
			particles(level, BatchDFx.PSI_BIG, at, 16, 0.5);
			sound(level, SoundEvents.ILLUSIONER_CAST_SPELL, 1.0f, 0.7f);
			startCooldown(PUSH, 50);
		}
	}

	@Override
	public void serverTick(ServerLevel level, LivingEntity target) {
		if (lockTicks > 0) {
			lockTicks--;
			if (locked == null || !isVictim(locked)) {
				lockTicks = 0;
			} else {
				Vec3 to = lockAt.subtract(locked.position());
				locked.setDeltaMovement(to.scale(0.35));
				locked.hurtMarked = true;
				locked.fallDistance = 0.0f;
				if (lockTicks % 10 == 0) {
					effect(locked, MobEffects.MOVEMENT_SLOWDOWN, 20, 9);
					effect(locked, MobEffects.WEAKNESS, 20, 9);
				}
				if (lockTicks % 2 == 0) {
					BatchDFx.ring(level, mid(locked), 0.9, BatchDFx.PSI, 10, lockTicks * 0.3);
				}
				if (lockTicks == 0) {
					effect(locked, MobEffects.SLOW_FALLING, 30, 0);
					particles(level, BatchDFx.PSI, mid(locked), 16, 0.4);
					locked = null;
				}
			}
		}
		if (detonationTicks > 0) {
			detonationTicks--;
			boss.getNavigation().stop();
			Vec3 c = boss.position();
			for (LivingEntity e : victimsNear(level, 16.0)) {
				Vec3 to = c.subtract(e.position());
				double d = to.length();
				if (d < 1.0e-3) {
					continue;
				}
				Vec3 pull = to.normalize().scale(d > 2.0 ? Math.min(0.35, 0.05 + 0.02 * d) : -0.18);
				e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(pull).add(0, e.onGround() ? 0.42 * 0.3 : 0.0, 0));
				e.hurtMarked = true;
			}
			if (detonationTicks % 2 == 0) {
				BatchDFx.ring(level, c.add(0, 1.0, 0), 1.0 + detonationTicks * 0.2, ParticleTypes.SCULK_SOUL, 16, detonationTicks);
			}
			if (detonationTicks % 10 == 0) {
				sound(level, SoundEvents.WARDEN_HEARTBEAT, 1.2f, 1.0f);
			}
			if (detonationTicks == 0) {
				for (LivingEntity e : victimsNear(level, 16.0)) {
					hurt(e, bossDamage(66.0f) * (float) (1.0 - Math.min(0.55, e.distanceTo(boss) / 16.0)));
					Vec3 away = e.position().subtract(c).normalize().scale(2.0);
					fling(e, new Vec3(away.x, 0.6, away.z));
				}
				particles(level, BatchDFx.PSI_BIG, c.add(0, 1, 0), 60, 2.0);
				particles(level, ParticleTypes.FLASH, c.add(0, 1, 0), 1, 0.0);
				for (int i = 1; i <= 5; i++) {
					BatchDFx.ring(level, c.add(0, 1.0, 0), i * 1.6, ParticleTypes.SCULK_CHARGE_POP, 20, 0);
				}
				sound(level, SoundEvents.WARDEN_SONIC_BOOM, 1.4f, 1.2f);
				sound(level, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0f, 1.0f);
			}
		}
	}
}
