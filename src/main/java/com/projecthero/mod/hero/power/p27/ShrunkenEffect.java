package com.projecthero.mod.hero.power.p27;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * v0.13.22 (batch E): <b>Shrunken</b>, the payload of Size Manipulation's Shrink Punch (H). The target is shrunk to
 * half its size via the SCALE attribute -- which also shrinks its hitbox and, for mobs, its melee reach -- and hits
 * 40% softer. Being a real mob effect, its attribute modifiers are transient and removed by vanilla the moment it
 * ends (expiry, milk, death), so nobody can be left permanently small.
 */
public final class ShrunkenEffect extends MobEffect {
	public static final Holder<MobEffect> HOLDER = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
			ProjectHeroMod.id("shrunken"), new ShrunkenEffect()
					.addAttributeModifier(Attributes.SCALE, ProjectHeroMod.id("shrunken_scale"), -0.5,
							AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
					.addAttributeModifier(Attributes.ATTACK_DAMAGE, ProjectHeroMod.id("shrunken_damage"), -0.4,
							AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
					.addAttributeModifier(Attributes.ENTITY_INTERACTION_RANGE, ProjectHeroMod.id("shrunken_reach"), -0.4,
							AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
					.addAttributeModifier(Attributes.MOVEMENT_SPEED, ProjectHeroMod.id("shrunken_speed"), -0.15,
							AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

	private ShrunkenEffect() {
		super(MobEffectCategory.HARMFUL, 0x9C6BFF);
	}

	/** Class-load hook (registers the effect). */
	public static void initialize() {
	}
}
