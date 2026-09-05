package com.herocraft.mod.hero.mutation;

import com.herocraft.mod.HeroCraftMod;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * The single "Unstable Mutation" effect applied by every experimental serum. It has no mechanical
 * payload of its own -- it is a timed marker. Its <b>amplifier encodes which power</b> the serum was
 * for (the power's index in {@link com.herocraft.mod.hero.Powers#all()}), which is how one effect
 * covers all 27 serums without 27 effects. While it is active the player must perform that power's
 * exposure event; see {@link MutationManager}.
 *
 * <p><b>Implementation note (deviation):</b> the design's "temporary Unstable Mutation effect" is
 * implemented as a benign HARMFUL-category marker effect. Carrying the target power in the amplifier
 * is a deliberate, contained encoding so vanilla brewing + potion drinking can be reused unchanged.
 */
public final class ModMobEffects {
	private static final class UnstableMutation extends MobEffect {
		private UnstableMutation() {
			super(MobEffectCategory.HARMFUL, 0x5A2E7A);
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
			return false;
		}
	}

	public static final Holder<MobEffect> UNSTABLE_MUTATION = Registry.registerForHolder(
			BuiltInRegistries.MOB_EFFECT, HeroCraftMod.id("unstable_mutation"), new UnstableMutation());

	private ModMobEffects() {
	}

	public static void initialize() {
		// Class-load registers the effect.
	}
}
