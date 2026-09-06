package com.projecthero.mod.hero.power;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Helpers for toggle/stance abilities: infinite hidden potion effects and fixed-id transient
 * attribute modifiers, applied idempotently so a toggle handler can safely re-apply them every tick
 * (which is how they survive a respawn — see {@code ExperimentalPowers.reconcileToggles}).
 */
public final class PowerToggles {
	private PowerToggles() {
	}

	public static void effect(ServerPlayer player, Holder<MobEffect> effect, int amplifier, boolean showIcon) {
		MobEffectInstance current = player.getEffect(effect);
		if (current == null || !current.isInfiniteDuration() || current.getAmplifier() != amplifier) {
			player.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, amplifier,
					false, false, showIcon));
		}
	}

	public static void clearEffect(ServerPlayer player, Holder<MobEffect> effect) {
		MobEffectInstance current = player.getEffect(effect);
		if (current != null && current.isInfiniteDuration()) {
			player.removeEffect(effect);
		}
	}

	public static void modifier(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id,
			double amount, AttributeModifier.Operation op) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		AttributeModifier existing = instance.getModifier(id);
		if (existing == null || existing.amount() != amount || existing.operation() != op) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
		}
	}

	public static void clearModifier(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null && instance.getModifier(id) != null) {
			instance.removeModifier(id);
		}
	}

	public static ResourceLocation id(String path) {
		return com.projecthero.mod.ProjectHeroMod.id(path);
	}
}
