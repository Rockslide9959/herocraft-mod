package com.projecthero.mod.hero.power.p18;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.22 (batch E): <b>Crushing Density</b>, the payload of Density Manipulation's Crushing Touch (N). The target
 * is made super-dense: crawling pace, no jump at all, triple gravity -- and anything that flies is dragged out of the
 * sky. Being a real mob effect, its attribute modifiers are applied and removed by vanilla (expiry, milk, death), so
 * nothing can be left behind on the target.
 */
public final class CrushingDensityEffect extends MobEffect {
	public static final Holder<MobEffect> HOLDER = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
			ProjectHeroMod.id("crushing_density"), new CrushingDensityEffect()
					.addAttributeModifier(Attributes.MOVEMENT_SPEED, ProjectHeroMod.id("crushing_density_speed"), -0.7,
							AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
					.addAttributeModifier(Attributes.FLYING_SPEED, ProjectHeroMod.id("crushing_density_fly"), -0.8,
							AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
					.addAttributeModifier(Attributes.JUMP_STRENGTH, ProjectHeroMod.id("crushing_density_jump"), -1.0,
							AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
					.addAttributeModifier(Attributes.GRAVITY, ProjectHeroMod.id("crushing_density_gravity"), 2.0,
							AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
					.addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, ProjectHeroMod.id("crushing_density_kb"), 0.6,
							AttributeModifier.Operation.ADD_VALUE));

	private CrushingDensityEffect() {
		super(MobEffectCategory.HARMFUL, 0xFF8A2A);
	}

	/** Class-load hook (registers the effect). */
	public static void initialize() {
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return true;
	}

	@Override
	public boolean applyEffectTick(LivingEntity e, int amplifier) {
		if (e.level().isClientSide() || e.getMaxHealth() > 200.0f) {
			return true; // bosses keep their flight -- the slow and the lost jump still apply
		}
		boolean flier = e.isNoGravity() || e instanceof net.minecraft.world.entity.FlyingMob
				|| e instanceof net.minecraft.world.entity.animal.FlyingAnimal
				|| (e instanceof Player pl && pl.isFallFlying());
		if (flier && !e.onGround()) {
			Vec3 v = e.getDeltaMovement();
			e.setDeltaMovement(v.x * 0.5, Math.min(v.y - 0.12, -0.55), v.z * 0.5);
			e.hurtMarked = true;
			if (e instanceof Player pl && pl.isFallFlying()) {
				pl.stopFallFlying();
			}
		}
		if (e.tickCount % 6 == 0 && e.level() instanceof ServerLevel sl) {
			sl.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0f, 0.5f, 0.12f), 1.0f),
					e.getX(), e.getY() + 0.1, e.getZ(), 3, e.getBbWidth() * 0.4, 0.05, e.getBbWidth() * 0.4, 0.0);
		}
		return true;
	}
}
