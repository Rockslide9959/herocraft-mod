package com.projecthero.mod.blackpanther.plane;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.7 Black Panther Ancestral Plane: <b>Bast's Blessing</b> -- what the Heart-Shaped Herb Soup gives (60 s). It does nothing
 * by itself: while it lasts, falling asleep in a bed (day or night) carries the player into the Ancestral Plane
 * ({@link AncestralPlane}), and the blessing is spent. Purple and gold icon ({@code textures/mob_effect/bast_blessing.png}); its
 * particles are the faint ambient kind.
 */
public final class BastBlessing extends MobEffect {
	public static final int DURATION = 20 * 60;
	public static final int COLOR = 0x9A5BD6;

	public static final Holder<MobEffect> EFFECT = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, ProjectHeroMod.id("bast_blessing"),
			new BastBlessing());

	private BastBlessing() {
		super(MobEffectCategory.BENEFICIAL, COLOR);
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return false;
	}

	public static void initialize() {
		// class-load registers the effect
	}

	/** Gives {@code entity} the blessing for its full minute (ambient: soft particles). */
	public static void give(LivingEntity entity) {
		entity.addEffect(new MobEffectInstance(EFFECT, DURATION, 0, true, true, true));
	}

	public static boolean has(LivingEntity entity) {
		return entity.hasEffect(EFFECT);
	}
}
