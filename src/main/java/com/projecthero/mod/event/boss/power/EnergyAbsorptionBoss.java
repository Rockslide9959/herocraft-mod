package com.projecthero.mod.event.boss.power;

import java.util.List;

import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.hero.power.p20.EnergyAbsorptionHandlers;
import com.projecthero.mod.hero.revamp.BatchCFx;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Energy Absorption (v0.14.1 kit): it soaks energy from every hit it takes (in the element of that hit --
 * {@link EnergyAbsorptionHandlers#elementOf}) and spends it: Energy Blast (charged by stored energy), Energy Beam's
 * Shift burst, Redirect (a window that turns incoming projectiles back at their shooter) and Overload, a nova that
 * empties the gauge. Element riders as the player's: fire burns, lightning roots, explosion knocks back, magic withers.
 */
public class EnergyAbsorptionBoss extends BossPowerController {
	public static final String POWER_KEY = "power_20_energy_absorption";

	private static final int BLAST = 0;
	private static final int BURST = 1;
	private static final int REDIRECT = 2;
	private static final int OVERLOAD = 3;
	private static final List<String> IDS = List.of("energy_blast", "energy_beam", "redirect", "overload");
	private static final float MAX_ENERGY = 575.0f;

	private float energy = 60.0f;
	private int element = EnergyAbsorptionHandlers.RAW;
	private int redirectTicks;

	public EnergyAbsorptionBoss(EmpoweredZombie boss) {
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
		return color(1.0f);
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.BLUE;
	}

	private ParticleOptions color(float size) {
		return BatchCFx.dust(EnergyAbsorptionHandlers.ELEMENT_RGB[Math.max(0, Math.min(4, element))], size);
	}

	/** The player's elemental rider. */
	private void elemental(ServerLevel level, LivingEntity e, float damage) {
		hurt(e, damage);
		switch (element) {
			case EnergyAbsorptionHandlers.FIRE -> {
				ignite(e, 4);
				particles(level, ParticleTypes.FLAME, mid(e), 12, 0.3);
			}
			case EnergyAbsorptionHandlers.LIGHTNING -> {
				control(e, MobEffects.MOVEMENT_SLOWDOWN, 20, 6);
				particles(level, ParticleTypes.ELECTRIC_SPARK, mid(e), 12, 0.3);
			}
			case EnergyAbsorptionHandlers.EXPLOSION -> {
				knockAway(e, boss.position(), 1.6, 0.35);
				particles(level, ParticleTypes.EXPLOSION, mid(e), 1, 0.0);
			}
			case EnergyAbsorptionHandlers.MAGIC -> {
				control(e, MobEffects.WITHER, 60, 0);
				control(e, MobEffects.WEAKNESS, 80, 0);
				particles(level, ParticleTypes.WITCH, mid(e), 12, 0.3);
			}
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		double d = boss.distanceTo(target);
		if (energy >= 100.0f && d < 12.0 && ready(OVERLOAD) && (lowHealth() || energy >= 300.0f || freshChoice(OVERLOAD))) {
			float spent = energy;
			beginCast(level, target, OVERLOAD, 500, 3, SoundEvents.BEACON_POWER_SELECT, color(1.5f), t -> overload(level, spent));
			return;
		}
		if (d > 10.0 && redirectTicks <= 0 && ready(REDIRECT) && freshChoice(REDIRECT)) {
			redirectTicks = 60;
			BatchCFx.flatRing(level, boss.position().add(0, 1.0, 0), 1.8, 24, color(1.0f), 0.0);
			sound(level, SoundEvents.SHIELD_BLOCK, 1.0f, 1.4f);
			startCooldown(REDIRECT, 200);
			return;
		}
		if (d < 9.0 && energy >= 50.0f && ready(BURST) && freshChoice(BURST)) {
			energy -= 50.0f;
			Vec3 c = boss.position().add(0, 1.0, 0);
			for (LivingEntity e : victimsAround(level, c, 9.0)) {
				elemental(level, e, bossDamage(18.0f));
				knockAway(e, c, 1.0, 0.2);
			}
			for (int i = 1; i <= 3; i++) {
				BatchCFx.flatRing(level, c, i * 2.5, 24, color(1.2f), 0.3);
			}
			sound(level, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.4f);
			startCooldown(BURST, 340);
			return;
		}
		if (d < 26.0 && sees(target) && ready(BLAST)) {
			float seconds = Math.min(3.0f, energy / 100.0f);
			energy = Math.max(0.0f, energy - 10.0f);
			beginRangedCast(level, target, BLAST, 60, SoundEvents.BEACON_POWER_SELECT, color(1.0f), t -> {
				Vec3 from = boss.getEyePosition();
				Vec3 to = clipEnd(level, from, from.add(aimFromEyes(mid(t)).scale(26.0)));
				particleLine(level, color(1.0f), from, to, 3.0);
				particleLine(level, ParticleTypes.END_ROD, from, to, 0.6);
				for (LivingEntity e : victimsOnSegment(level, from, to, 0.6)) {
					elemental(level, e, bossDamage(16.0f + 6.0f * seconds));
					knockAway(e, from, 0.6, 0.1);
				}
				sound(level, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.4f);
			});
		}
	}

	@Override
	public void serverTick(ServerLevel level, LivingEntity target) {
		if (redirectTicks <= 0) {
			return;
		}
		redirectTicks--;
		for (Projectile p : level.getEntitiesOfClass(Projectile.class, boss.getBoundingBox().inflate(3.5),
				p -> p.getOwner() != boss && p.getDeltaMovement().lengthSqr() > 0.01)) {
			Vec3 v = p.getDeltaMovement();
			if (v.dot(boss.position().subtract(p.position())) <= 0) {
				continue;
			}
			// turned back toward whoever fired it -- only if that shooter is a victim, so it never lands on the horde
			if (p.getOwner() instanceof LivingEntity shooter && isVictim(shooter)) {
				Vec3 back = shooter.getEyePosition().subtract(p.position()).normalize().scale(Math.max(1.2, v.length() * 1.3));
				p.setDeltaMovement(back);
			} else {
				p.setDeltaMovement(v.scale(-0.3));
			}
			p.hurtMarked = true;
			energy = Math.min(MAX_ENERGY, energy + 25.0f);
			particles(level, ParticleTypes.FLASH, p.position(), 1, 0.0);
			sound(level, SoundEvents.SHIELD_BLOCK, 1.0f, 1.2f);
		}
		if (redirectTicks % 6 == 0) {
			BatchCFx.flatRing(level, boss.position().add(0, 1.0, 0), 1.8, 16, color(0.8f), 0.0);
		}
	}

	private void overload(ServerLevel level, float spent) {
		energy = 0.0f;
		Vec3 c = boss.position().add(0, boss.getBbHeight() + 0.5, 0);
		double r = Math.min(10.0, 6.0 + spent / 115.0);
		for (LivingEntity e : victimsAround(level, c, r + 1.5)) {
			elemental(level, e, bossDamage(25.0f + 0.1f * spent));
			knockAway(e, c, 2.0, 0.4);
		}
		particles(level, ParticleTypes.FLASH, c, 1, 0.0);
		particles(level, ParticleTypes.EXPLOSION_EMITTER, c, 1, 0.0);
		for (int i = 1; i <= 4; i++) {
			BatchCFx.flatRing(level, boss.position().add(0, 0.5, 0), i * r / 4.0, 32, color(1.6f), 0.4);
		}
		sound(level, SoundEvents.GENERIC_EXPLODE, 1.6f, 0.5f);
	}

	@Override
	public void onDamaged(ServerLevel level, DamageSource source, float amount) {
		// absorption: every hit is stored as energy in its element (the player stores 2x the hit)
		energy = Math.min(MAX_ENERGY, energy + amount * 2.0f);
		element = EnergyAbsorptionHandlers.elementOf(source);
		particles(level, color(1.0f), boss.position().add(0, 1.2, 0), 6, 0.4);
	}
}
