package com.projecthero.mod.galactus.entity;

import com.projecthero.mod.galactus.GalactusConfig;
import com.projecthero.mod.galactus.GalactusEvent;
import com.projecthero.mod.galactus.GalactusFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: a <b>Power Pylon</b> -- one of the four towers of crackling Power Cosmic round the arena in Act 3. While any
 * stands, Galactus regenerates and his weak points are sealed; ground fighters break them. Never moves, no brain; drawn
 * with blocks by the client (crying obsidian, purple glass, an amethyst crown).
 */
public class PowerPylonEntity extends GalactusMob {
	public PowerPylonEntity(EntityType<? extends PowerPylonEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, GalactusConfig.pylons().health)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.ARMOR, 6.0);
	}

	@Override
	public String model() {
		return null;
	}

	public void configure(double health) {
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		setHealth((float) health);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
		if (level() instanceof ServerLevel server && tickCount % 6 == 0) {
			server.sendParticles(GalactusFx.COSMIC_SMALL, getX(), getY() + getBbHeight() * 0.8, getZ(), 2, 0.3, 0.6, 0.3, 0.01);
			if (tickCount % 30 == 0) {
				server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + getBbHeight(), getZ(), 6, 0.3, 0.3, 0.3, 0.1);
			}
		}
	}

	/** Only fighters (and their things) break a pylon; Galactus' side never hurts it. */
	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (source.getEntity() instanceof LivingEntity attacker && com.projecthero.mod.galactus.GalactusCombat.isCosmic(attacker)) {
			return false;
		}
		return super.hurt(source, amount);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel server) {
			Vec3 top = position().add(0, getBbHeight(), 0);
			GalactusFx.burst(server, top, 1.5);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 3.0f, 0.5f);
			GalactusEvent e = event();
			if (e != null) {
				e.onPylonDestroyed(server, this);
			}
		}
	}
}
