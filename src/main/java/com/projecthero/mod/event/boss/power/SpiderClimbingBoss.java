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
 * Spider Climbing / Adhesion (v0.14.1 kit): Pounce (a leap with a hit window), Adhesive Strike (pins the target),
 * Venom Bite (Poison II), Spider-Sense (the next hit it sees coming is dodged) and -- once badly hurt -- Predator Rush.
 */
public class SpiderClimbingBoss extends BossPowerController {
	public static final String POWER_KEY = "power_16_spider_climbing_adhesion";

	private static final int POUNCE = 0;
	private static final int STRIKE = 1;
	private static final int BITE = 2;
	private static final int SENSE = 3;
	private static final int RUSH = 4;
	private static final List<String> IDS = List.of("pounce", "adhesive_strike", "venom_bite", "spider_sense", "predator_rush");

	private static final ParticleOptions VENOM = BatchCFx.dust(0x59D933, 1.0f);

	private int pounceTicks;
	private int senseTicks;

	public SpiderClimbingBoss(EmpoweredZombie boss) {
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
		return ParticleTypes.ITEM_SLIME;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.RED;
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		if (pounceTicks > 0) {
			return;
		}
		double d = boss.distanceTo(target);
		if (lowHealth() && ready(RUSH)) {
			boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 1, false, true));
			boss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 0, false, true));
			boss.addEffect(new MobEffectInstance(MobEffects.JUMP, 400, 2, false, true));
			particles(level, BatchCFx.dust(0xCC0D0D, 1.2f), boss.position().add(0, 1, 0), 30, 0.6);
			sound(level, SoundEvents.SPIDER_AMBIENT, 1.0f, 0.7f);
			startCooldown(RUSH, 900);
			return;
		}
		if (senseTicks <= 0 && ready(SENSE) && (d < 6.0 || freshChoice(SENSE))) {
			senseTicks = 60; // the player's 24 t window, longer so the AI's guess is worth something
			particles(level, BatchCFx.dust(0xCC0D0D, 0.8f), boss.getEyePosition(), 10, 0.3);
			sound(level, SoundEvents.SPIDER_AMBIENT, 0.6f, 1.8f);
			startCooldown(SENSE, 120);
			return;
		}
		if (d < 4.5 && ready(STRIKE) && freshChoice(STRIKE)) {
			face(target);
			hurt(target, bossDamage(8.5f));
			control(target, MobEffects.MOVEMENT_SLOWDOWN, 80, 9);
			control(target, MobEffects.WEAKNESS, 140, 2);
			fling(target, new Vec3(0, Math.min(0.0, target.getDeltaMovement().y), 0));
			particleLine(level, ParticleTypes.WHITE_ASH, boss.getEyePosition(), mid(target), 3.0);
			particles(level, ParticleTypes.ITEM_SLIME, mid(target), 14, 0.3);
			sound(level, SoundEvents.SPIDER_HURT, 0.8f, 1.4f);
			startCooldown(STRIKE, 90);
			return;
		}
		if (d < 4.0 && ready(BITE)) {
			face(target);
			hurt(target, bossDamage(7.0f));
			control(target, MobEffects.POISON, 120, 1);
			control(target, MobEffects.MOVEMENT_SLOWDOWN, 80, 1);
			particles(level, VENOM, mid(target), 16, 0.3);
			particles(level, ParticleTypes.DAMAGE_INDICATOR, mid(target), 3, 0.2);
			sound(level, SoundEvents.SPIDER_HURT, 1.0f, 0.6f);
			startCooldown(BITE, 140);
			return;
		}
		if (d > 4.0 && d < 14.0 && sees(target) && ready(POUNCE)) {
			Vec3 to = target.position().subtract(boss.position());
			Vec3 flat = new Vec3(to.x, 0, to.z);
			Vec3 dir = flat.normalize().scale(Math.min(1.9, 0.5 + flat.length() * 0.11));
			boss.setDeltaMovement(dir.x, Math.max(0.45, to.y * 0.12 + 0.45), dir.z);
			boss.hasImpulse = true;
			boss.hurtMarked = true;
			boss.getNavigation().stop();
			pounceTicks = 30;
			particles(level, ParticleTypes.CRIT, boss.position(), 12, 0.4);
			sound(level, SoundEvents.SPIDER_AMBIENT, 0.7f, 1.5f);
			startCooldown(POUNCE, 85);
		}
	}

	@Override
	public void serverTick(ServerLevel level, LivingEntity target) {
		if (senseTicks > 0) {
			senseTicks--;
		}
		if (pounceTicks > 0) {
			pounceTicks--;
			boss.fallDistance = 0.0f;
			for (LivingEntity e : victimsAround(level, boss.position().add(0, 1.0, 0), 1.7 + boss.getBbWidth() * 0.5)) {
				hurt(e, bossDamage(7.0f));
				control(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
				knockAway(e, boss.position(), 0.6, 0.2);
				particles(level, ParticleTypes.CRIT, mid(e), 14, 0.3);
				sound(level, SoundEvents.SPIDER_HURT, 1.0f, 1.1f);
				boss.setDeltaMovement(boss.getDeltaMovement().multiply(0.2, 0.5, 0.2));
				pounceTicks = 0;
				break;
			}
		}
	}

	@Override
	public float modifyIncomingDamage(DamageSource source, float amount) {
		// Spider-Sense: the next hit inside the window is sensed and sidestepped
		if (senseTicks > 0 && source.getEntity() != null && boss.level() instanceof ServerLevel level) {
			senseTicks = 0;
			Vec3 from = source.getEntity().position();
			Vec3 away = boss.position().subtract(from);
			away = new Vec3(away.x, 0, away.z);
			away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
			Vec3 side = new Vec3(-away.z, 0, away.x).scale(random().nextBoolean() ? 0.95 : -0.95);
			boss.setDeltaMovement(side.x + away.x * 0.25, 0.28, side.z + away.z * 0.25);
			boss.hurtMarked = true;
			boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, false, false));
			particles(level, ParticleTypes.CLOUD, boss.position(), 10, 0.3);
			sound(level, SoundEvents.PLAYER_ATTACK_NODAMAGE, 1.0f, 1.4f);
			return 0.0f;
		}
		return amount;
	}
}
