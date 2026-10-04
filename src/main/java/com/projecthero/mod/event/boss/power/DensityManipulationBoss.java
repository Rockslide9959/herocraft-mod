package com.projecthero.mod.event.boss.power;

import java.util.List;

import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.hero.power.p18.CrushingDensityEffect;
import com.projecthero.mod.hero.revamp.BatchCFx;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Density Manipulation (v0.14.1 kit): Heavy Impact (a launch and a plunge -- its landing ring telegraphs -- with
 * falloff damage), Crushing Touch (the player power's real Crushing Density effect: pinned, heavy, dragged out of the
 * air), Zero Density (light and fast to close distance), Intangible Dodge (phases through one hit) and -- once hurt --
 * Density Anchor (rooted, 60% damage reduction, immovable).
 */
public class DensityManipulationBoss extends BossPowerController {
	public static final String POWER_KEY = "power_18_density_manipulation";

	private static final int IMPACT = 0;
	private static final int CRUSH = 1;
	private static final int ZERO = 2;
	private static final int DODGE = 3;
	private static final int ANCHOR = 4;
	private static final List<String> IDS = List.of("heavy_impact", "crushing_touch", "zero_density", "intangible_dodge", "density_anchor");

	private static final ParticleOptions ORANGE = BatchCFx.dust(0xFF8C26, 1.3f);

	/** Heavy Impact: 1 rising, 2 plunging. */
	private int impactPhase;
	private int impactTicks;
	private Vec3 impactMark;
	private int anchorTicks;
	private int intangibleTicks;

	public DensityManipulationBoss(EmpoweredZombie boss) {
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
		return 3.0;
	}

	@Override
	public ParticleOptions auraParticle() {
		return ORANGE;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.YELLOW;
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		if (impactPhase > 0 || anchorTicks > 0) {
			return;
		}
		double d = boss.distanceTo(target);
		if (d < 18.0 && ready(IMPACT) && freshChoice(IMPACT)) {
			impactPhase = 1;
			impactTicks = 16;
			impactMark = target.position();
			boss.setDeltaMovement(0, 1.6, 0);
			boss.hurtMarked = true;
			sound(level, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 1.4f);
			startCooldown(IMPACT, 220);
			return;
		}
		if (d < 4.0 && ready(CRUSH)) {
			face(target);
			hurt(target, bossDamage(9.0f));
			control(target, CrushingDensityEffect.HOLDER, 100, 0);
			fling(target, new Vec3(target.getDeltaMovement().x * 0.2, target.getDeltaMovement().y - 0.4, target.getDeltaMovement().z * 0.2));
			particles(level, ParticleTypes.CRIT, mid(target), 20, 0.3);
			particles(level, ORANGE, mid(target), 16, 0.4);
			sound(level, SoundEvents.ANVIL_LAND, 1.0f, 0.4f);
			startCooldown(CRUSH, 160);
			return;
		}
		if (d > 8.0 && ready(ZERO)) {
			boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 2, false, true));
			particles(level, ParticleTypes.CLOUD, boss.position().add(0, 1, 0), 20, 0.5);
			sound(level, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.8f);
			startCooldown(ZERO, 200);
		}
	}

	@Override
	public void serverTick(ServerLevel level, LivingEntity target) {
		if (intangibleTicks > 0) {
			intangibleTicks--;
			particles(level, ParticleTypes.ELECTRIC_SPARK, boss.position().add(0, 1, 0), 3, 0.4);
		}
		if (anchorTicks > 0) {
			anchorTicks--;
			boss.setDeltaMovement(0, Math.min(0.0, boss.getDeltaMovement().y), 0);
			boss.getNavigation().stop();
			if (anchorTicks % 4 == 0) {
				particles(level, ParticleTypes.ELECTRIC_SPARK, boss.position().add(0, 1, 0), 3, 0.5);
			}
			if (anchorTicks == 0) {
				sound(level, SoundEvents.BEACON_DEACTIVATE, 0.8f, 0.7f);
			}
		}
		if (impactPhase == 1) {
			impactTicks--;
			boss.setDeltaMovement(0, Math.max(boss.getDeltaMovement().y, 0.6), 0);
			boss.hurtMarked = true;
			if (target != null && impactTicks > 6) {
				impactMark = target.position();
			}
			ring(level, ORANGE, impactMark, 6.0, 24);
			if (impactTicks <= 0) {
				impactPhase = 2;
				impactTicks = 60;
			}
		} else if (impactPhase == 2) {
			impactTicks--;
			Vec3 to = impactMark.subtract(boss.position());
			boss.setDeltaMovement(to.x / 6.0, -2.8, to.z / 6.0);
			boss.hurtMarked = true;
			particles(level, ParticleTypes.CRIT, boss.position(), 3, 0.3);
			if (boss.onGround() || impactTicks <= 0) {
				impactPhase = 0;
				boss.fallDistance = 0.0f;
				Vec3 c = boss.position();
				strikeArea(level, c, 10.0, bossDamage(54.0f), true, 1.6, 0.4);
				particles(level, ParticleTypes.EXPLOSION_EMITTER, c, 2, 0.5);
				level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.2, c.z, 80, 4.0, 0.3, 4.0, 0.05);
				sound(level, SoundEvents.GENERIC_EXPLODE, 1.4f, 0.4f);
			}
		}
	}

	@Override
	public float modifyIncomingDamage(DamageSource source, float amount) {
		if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL)) {
			return amount;
		}
		if (source.is(DamageTypes.FALL)) {
			return 0.0f;
		}
		if (intangibleTicks > 0) {
			return 0.0f;
		}
		return anchorTicks > 0 ? amount * 0.4f : amount;
	}

	@Override
	public void onDamaged(ServerLevel level, DamageSource source, float amount) {
		if (lowHealth() && anchorTicks <= 0 && readyReactive(ANCHOR)) {
			anchorTicks = 200;
			boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 3, false, false));
			particles(level, ParticleTypes.CRIT, boss.position().add(0, 1, 0), 40, 0.6);
			sound(level, SoundEvents.ANVIL_LAND, 1.2f, 0.5f);
			startCooldown(ANCHOR, 510);
			return;
		}
		if (amount >= 7.0f && readyReactive(DODGE) && random().nextInt(2) == 0) {
			intangibleTicks = 10;
			Vec3 look = boss.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0, look.z);
			if (flat.lengthSqr() > 1.0e-4) {
				flat = flat.normalize().scale(1.25);
				boss.setDeltaMovement(flat.x, 0.12, flat.z);
				boss.hurtMarked = true;
			}
			particles(level, ParticleTypes.PORTAL, boss.position().add(0, 1, 0), 30, 0.5);
			sound(level, SoundEvents.ENDERMAN_TELEPORT, 0.5f, 1.6f);
			startCooldown(DODGE, 120);
		}
	}
}
