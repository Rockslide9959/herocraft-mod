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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Water Manipulation (v0.14.1 kit): Water Shot, Water Whip (a 12-block tendril that drags victims in), Geyser (erupts
 * under the target), Water Prison (holds the target in a bubble) and -- once hurt -- Healing Water. Victims are left
 * "wet" (Slowness I), as the player's.
 */
public class WaterBoss extends BossPowerController {
	public static final String POWER_KEY = "power_25_water_manipulation";

	private static final int SHOT = 0;
	private static final int WHIP = 1;
	private static final int GEYSER = 2;
	private static final int PRISON = 3;
	private static final int HEAL = 4;
	private static final List<String> IDS = List.of("water_shot", "water_whip", "geyser", "water_prison", "healing_water");

	private static final ParticleOptions WATER = BatchCFx.dust(0x3880FF, 1.3f);
	private static final ParticleOptions FOAM = BatchCFx.dust(0xD9F2FF, 1.0f);

	private LivingEntity prisoner;
	private Vec3 prisonAt;
	private int prisonTicks;

	public WaterBoss(EmpoweredZombie boss) {
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
		return ParticleTypes.FALLING_WATER;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.BLUE;
	}

	private void wet(LivingEntity e) {
		control(e, MobEffects.MOVEMENT_SLOWDOWN, 120, 0);
		e.clearFire();
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		double d = boss.distanceTo(target);
		if (healthFraction() < 0.5f && ready(HEAL)) {
			boss.heal(boss.getMaxHealth() * 0.03f);
			boss.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, false, true));
			particles(level, ParticleTypes.FALLING_WATER, boss.position().add(0, 1.5, 0), 30, 0.8);
			particles(level, ParticleTypes.HAPPY_VILLAGER, boss.position().add(0, 1.0, 0), 10, 0.6);
			sound(level, SoundEvents.CONDUIT_AMBIENT_SHORT, 1.0f, 1.2f);
			startCooldown(HEAL, 300);
			return;
		}
		if (prisonTicks <= 0 && d < 16.0 && sees(target) && ready(PRISON) && freshChoice(PRISON)) {
			prisoner = target;
			prisonAt = target.position();
			prisonTicks = target instanceof Player ? 80 : 160;
			sound(level, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.2f, 0.8f);
			startCooldown(PRISON, 260);
			return;
		}
		if (d < 12.0 && sees(target) && ready(WHIP) && freshChoice(WHIP)) {
			whip(level, target);
			startCooldown(WHIP, 102);
			return;
		}
		if (d < 20.0 && ready(GEYSER) && freshChoice(GEYSER)) {
			Vec3 mark = target.position();
			level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, mark.x, mark.y + 0.2, mark.z, 20, 0.6, 0.1, 0.6, 0.05);
			BatchCFx.flatRing(level, mark.add(0, 0.2, 0), 1.4, 16, WATER, 0.0);
			soundAt(level, mark, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.6f, 0.7f);
			schedule(1, () -> {
				for (LivingEntity e : victimsAround(level, mark.add(0, 0.5, 0), 1.8)) {
					hurt(e, bossDamage(12.0f));
					fling(e, new Vec3(e.getDeltaMovement().x, 1.1, e.getDeltaMovement().z));
					wet(e);
				}
				task(level, age -> {
					level.sendParticles(WATER, mark.x, mark.y + age * 0.5, mark.z, 8, 0.4, 0.2, 0.4, 0.0);
					level.sendParticles(ParticleTypes.SPLASH, mark.x, mark.y + age * 0.5, mark.z, 6, 0.4, 0.2, 0.4, 0.0);
					return age < 13;
				});
			});
			startCooldown(GEYSER, 150);
			return;
		}
		if (d < 24.0 && sees(target) && ready(SHOT)) {
			face(target);
			Vec3 from = boss.getEyePosition().add(boss.getLookAngle().scale(0.8));
			projectile(level, from, aimFromEyes(lead(target, 5.0)), 1.8, 20, 0.5, WATER, 6, (at, hit) -> {
				if (hit != null) {
					hurt(hit, bossDamage(10.0f) + 1.0f);
					knockAway(hit, boss.position(), 1.0, 0.15);
					wet(hit);
				}
				particles(level, ParticleTypes.SPLASH, at, 20, 0.3);
			});
			sound(level, SoundEvents.PLAYER_SPLASH, 1.0f, 1.2f);
			startCooldown(SHOT, 40);
		}
	}

	private void whip(ServerLevel level, LivingEntity target) {
		face(target);
		Vec3 origin = boss.getEyePosition().subtract(0, 0.3, 0);
		Vec3 dir = aimFromEyes(mid(target));
		Vec3 side = dir.cross(new Vec3(0, 1, 0));
		Vec3 sideF = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
		Set<LivingEntity> hit = new HashSet<>();
		sound(level, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.0f, 1.0f);
		task(level, age -> {
			double len = 12.0 * Math.min(1.0, (age + 1) / 7.0);
			Vec3 prev = origin;
			for (int s = 1; s <= 14; s++) {
				double t = s / 14.0;
				Vec3 p = origin.add(dir.scale(len * t)).add(sideF.scale(Math.sin(t * Math.PI * 3.0 + age * 0.8) * 0.5));
				level.sendParticles(WATER, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
				if (s % 3 == 0) {
					level.sendParticles(ParticleTypes.FALLING_WATER, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
				}
				for (LivingEntity e : victimsOnSegment(level, prev, p, 1.0)) {
					if (hit.add(e)) {
						hurt(e, bossDamage(19.0f));
						Vec3 pull = boss.position().subtract(e.position()).normalize().scale(1.2);
						fling(e, new Vec3(pull.x, 0.35, pull.z));
						wet(e);
						particles(level, FOAM, mid(e), 10, 0.3);
					}
				}
				prev = p;
			}
			return age < 7;
		});
	}

	@Override
	public void serverTick(ServerLevel level, LivingEntity target) {
		if (prisonTicks <= 0) {
			return;
		}
		prisonTicks--;
		LivingEntity p = prisoner;
		if (p == null || !isVictim(p)) {
			prisonTicks = 0;
			return;
		}
		Vec3 to = prisonAt.subtract(p.position());
		p.setDeltaMovement(to.x * 0.5, Math.min(0.0, p.getDeltaMovement().y), to.z * 0.5);
		p.hurtMarked = true;
		if (prisonTicks % 10 == 0) {
			effect(p, MobEffects.MOVEMENT_SLOWDOWN, 20, p instanceof Player ? 4 : 9);
			effect(p, MobEffects.JUMP, 20, -10);
			effect(p, MobEffects.WEAKNESS, 20, 2);
		}
		p.setAirSupply(p.getMaxAirSupply()); // the bubble refills air -- it is a hold, not a drowning
		if (prisonTicks % 3 == 0) {
			level.sendParticles(ParticleTypes.BUBBLE, p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.6, 0.8, 0.6, 0.02);
			BatchCFx.flatRing(level, p.position().add(0, 1.0, 0), 1.0, 14, WATER, 0.0);
		}
	}
}
