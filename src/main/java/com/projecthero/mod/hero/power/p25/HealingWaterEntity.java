package com.projecthero.mod.hero.power.p25;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Shift+V -- Healing Water: one of these wraps each creature it heals (the caster and every squadmate in range) -- a
 * ribbon of water spiralling up round the body (the client draws it) -- and heals it {@link #HEAL_PER_PULSE} every
 * {@link #PULSE} ticks for {@link #LIFE} ticks: {@link WaterHandlers#HEAL_TOTAL} in all. The caster's one also carries the
 * expanding ring of water that marks the 6-block reach (its synced size).
 */
public class HealingWaterEntity extends WaterFxEntity {
	public static final int LIFE = 80;
	public static final int PULSE = 10;
	public static final float HEAL_PER_PULSE = WaterHandlers.HEAL_TOTAL / (LIFE / PULSE);

	public HealingWaterEntity(EntityType<? extends HealingWaterEntity> type, Level level) {
		super(type, level);
	}

	public static HealingWaterEntity wrap(ServerPlayer owner, LivingEntity target, float ring) {
		ServerLevel level = AbilityHelpers.level(owner);
		HealingWaterEntity e = new HealingWaterEntity(WaterEntities.HEALING_WATER, level);
		e.setup(owner, target, ring);
		e.setPos(target.getX(), target.getY(), target.getZ());
		level.addFreshEntity(e);
		return e;
	}

	@Override
	public void tick() {
		super.tick();
		LivingEntity t = target();
		if (t != null) {
			setPos(t.getX(), t.getY(), t.getZ());
		}
		if (level().isClientSide()) {
			return;
		}
		if (t == null || tickCount > LIFE) {
			discard();
			return;
		}
		if (tickCount % PULSE == 0) {
			pulse(t);
		}
	}

	/** One pulse of the heal (fire goes out too). */
	public void pulse(LivingEntity t) {
		t.heal(HEAL_PER_PULSE);
		t.clearFire();
		Vec3 top = t.position().add(0, t.getBbHeight() + 0.2, 0);
		((ServerLevel) level()).sendParticles(ParticleTypes.FALLING_WATER, top.x, top.y, top.z, 6, 0.3, 0.05, 0.3, 0.0);
		if (tickCount % (PULSE * 2) == 0) {
			((ServerLevel) level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(),
					2, 0.3, 0.4, 0.3, 0.0);
		}
	}
}
