package com.projecthero.mod.hero.power.p25;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Shift+G -- Water Prison: a sphere of water closes round the target for {@link WaterHandlers#PRISON_TICKS} ticks. It is
 * held where it was caught (no moving, no jumping, its AI loses its target), it cannot breathe -- its air runs out and it
 * takes {@link WaterHandlers#PRISON_DAMAGE_PER_SECOND} drowning damage a second -- and then the sphere bursts. The sphere
 * is this entity (the client draws a wobbling translucent ball of water with bubbles rising through it), never blocks.
 */
public class WaterPrisonEntity extends WaterFxEntity {
	private Vec3 pin;

	public WaterPrisonEntity(EntityType<? extends WaterPrisonEntity> type, Level level) {
		super(type, level);
	}

	public static WaterPrisonEntity trap(ServerPlayer owner, LivingEntity target) {
		ServerLevel level = AbilityHelpers.level(owner);
		WaterPrisonEntity e = new WaterPrisonEntity(WaterEntities.WATER_PRISON, level);
		float r = (float) Math.max(0.7, Math.max(target.getBbWidth(), target.getBbHeight()) * 0.62 + 0.25);
		e.setup(owner, target, r);
		e.pin = target.position();
		Vec3 m = mid(target);
		e.setPos(m.x, m.y, m.z);
		level.addFreshEntity(e);
		return e;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			LivingEntity t = target();
			if (t != null) {
				Vec3 m = mid(t);
				setPos(m.x, m.y, m.z);
			}
			return;
		}
		ServerLevel level = (ServerLevel) level();
		LivingEntity t = target();
		ServerPlayer owner = owner();
		if (t == null || owner == null || tickCount > WaterHandlers.PRISON_TICKS) {
			burst(level);
			return;
		}
		hold(t);
		if (tickCount % 20 == 0) {
			squeeze(owner, t);
		}
		Vec3 m = mid(t);
		setPos(m.x, m.y, m.z);
		if (tickCount % 3 == 0) {
			level.sendParticles(ParticleTypes.BUBBLE, m.x, m.y - size() * 0.6, m.z, 3, size() * 0.4, 0.1, size() * 0.4, 0.05);
		}
	}

	/** Held fast where it was caught, its air running out. */
	private void hold(LivingEntity t) {
		if (pin != null) {
			if (t instanceof ServerPlayer sp) {
				if (sp.position().distanceToSqr(pin) > 0.06) {
					sp.connection.teleport(pin.x, pin.y, pin.z, sp.getYRot(), sp.getXRot()); // a player moves himself
				}
			} else {
				t.setPos(pin.x, pin.y, pin.z);
			}
		}
		t.setDeltaMovement(Vec3.ZERO);
		t.hurtMarked = true;
		t.fallDistance = 0;
		t.clearFire(); // nothing burns inside a ball of water
		t.setAirSupply(Math.max(-10, Math.min(t.getAirSupply(), t.getMaxAirSupply() - tickCount * 4)));
		if (t instanceof Mob mob) {
			mob.setTarget(null);
			mob.getNavigation().stop();
		}
	}

	/** One second of drowning: 4. */
	public void squeeze(ServerPlayer owner, LivingEntity t) {
		AbilityHelpers.hurtBurst(owner, t, owner.level().damageSources().source(DamageTypes.DROWN, owner),
				WaterHandlers.PRISON_DAMAGE_PER_SECOND);
		Vec3 m = mid(t);
		((ServerLevel) level()).sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, m.x, m.y, m.z, 10, 0.25, 0.3, 0.25, 0.1);
	}

	private void burst(ServerLevel level) {
		LivingEntity t = target();
		if (t != null && t.getAirSupply() < t.getMaxAirSupply()) {
			t.setAirSupply(t.getMaxAirSupply());
		}
		level.sendParticles(ParticleTypes.SPLASH, getX(), getY(), getZ(), 50, size() * 0.6, size() * 0.6, size() * 0.6, 0.3);
		level.sendParticles(ParticleTypes.FALLING_WATER, getX(), getY(), getZ(), 20, size() * 0.5, size() * 0.3, size() * 0.5, 0.0);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 1.0f, 0.8f);
		discard();
	}
}
