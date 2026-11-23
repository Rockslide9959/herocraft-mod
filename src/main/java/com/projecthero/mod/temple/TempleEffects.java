package com.projecthero.mod.temple;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.7: <b>Curse of the Bound King</b> -- Ankhatep's curse. Drains 1 health every {@link #PERIOD} ticks (half that at
 * amplifier 1, his second phase), ignoring armour. It lasts only a few seconds and he keeps renewing it on everyone in his
 * arena while any canopic jar stands, so breaking the jars ends it ({@code AnkhatepBoss}). Never kills: it stops at
 * half a heart.
 */
public final class TempleEffects {
	public static final int PERIOD = 40;
	public static Holder<MobEffect> BOUND_CURSE;

	private TempleEffects() {
	}

	public static void initialize() {
		BOUND_CURSE = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, ProjectHeroMod.id("bound_curse"), new BoundCurse());
	}

	static final class BoundCurse extends MobEffect {
		BoundCurse() {
			super(MobEffectCategory.HARMFUL, 0x9CC25A);
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
			int period = Math.max(10, PERIOD >> amplifier);
			return duration % period == 0;
		}

		@Override
		public boolean applyEffectTick(LivingEntity entity, int amplifier) {
			if (entity.level() instanceof ServerLevel level && entity.getHealth() > 1.0f) {
				entity.hurt(level.damageSources().magic(), Math.min(1.0f, entity.getHealth() - 1.0f));
				level.sendParticles(ParticleTypes.SCULK_SOUL, entity.getX(), entity.getY() + 1.0, entity.getZ(), 3, 0.3, 0.4, 0.3, 0.02);
			}
			return true;
		}
	}
}
